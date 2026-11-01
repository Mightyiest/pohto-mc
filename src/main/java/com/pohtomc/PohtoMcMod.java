package com.pohtomc;

import com.pohtomc.duck.UrlPaintingDataHolder;
import com.pohtomc.network.OpenUrlPaintingScreenPayload;
import com.pohtomc.network.RequestPaintingDataPayload;
import com.pohtomc.network.RequestPaintingImagePayload;
import com.pohtomc.network.SendPaintingImagePayload;
import com.pohtomc.network.SyncPaintingDataPayload;
import com.pohtomc.network.UpdateUrlPaintingPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ClientboundPlayChannelEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PohtoMcMod implements ModInitializer {
    public static final String MOD_ID = "pohtomc";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final Pattern URL_REGEX = Pattern.compile("^(https?)://([^/?#]+)([^?#]*)(?:\\?([^#]*))?(?:#(.*))?$", Pattern.CASE_INSENSITIVE);

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(OpenUrlPaintingScreenPayload.TYPE, OpenUrlPaintingScreenPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SyncPaintingDataPayload.TYPE, SyncPaintingDataPayload.STREAM_CODEC);
        // Image backups can exceed vanilla's 1 MiB custom-payload cap, so let Fabric split them.
        PayloadTypeRegistry.clientboundPlay().registerLarge(SendPaintingImagePayload.TYPE, SendPaintingImagePayload.STREAM_CODEC, 8388608 + 8192);

        PayloadTypeRegistry.serverboundPlay().register(UpdateUrlPaintingPayload.TYPE, UpdateUrlPaintingPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RequestPaintingDataPayload.TYPE, RequestPaintingDataPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RequestPaintingImagePayload.TYPE, RequestPaintingImagePayload.STREAM_CODEC);

        EntityTrackingEvents.START_TRACKING.register((trackedEntity, player) -> {
            if (trackedEntity instanceof Painting painting && painting instanceof UrlPaintingDataHolder holder) {
                if (holder.pohtomc$hasCustomData() && ServerPlayNetworking.canSend(player, SyncPaintingDataPayload.TYPE)) {
                    ServerPlayNetworking.send(player, createSyncPayload(painting, holder));
                }
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            server.execute(() -> {
                if (player != null && player.level() != null) {
                    if (ServerPlayNetworking.canSend(player, SyncPaintingDataPayload.TYPE)) {
                        syncPaintingsForPlayer(player);
                    }
                }
            });
        });

        ClientboundPlayChannelEvents.REGISTER.register((handler, sender, server, channels) -> {
            if (channels.contains(SyncPaintingDataPayload.TYPE.id())) {
                server.execute(() -> {
                    ServerPlayer player = handler.getPlayer();
                    if (player != null && player.level() != null) {
                        syncPaintingsForPlayer(player);
                    }
                });
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(RequestPaintingDataPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            Entity entity = player.level().getEntity(payload.entityId());
            if (entity instanceof Painting painting && painting instanceof UrlPaintingDataHolder holder) {
                if (holder.pohtomc$hasCustomData() && ServerPlayNetworking.canSend(player, SyncPaintingDataPayload.TYPE)) {
                    ServerPlayNetworking.send(player, createSyncPayload(painting, holder));
                }
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(RequestPaintingImagePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            Entity entity = player.level().getEntity(payload.entityId());
            if (entity instanceof Painting painting && painting instanceof UrlPaintingDataHolder holder) {
                String url = holder.pohtomc$getImageUrl();
                if (url != null && !url.isBlank()) {
                    sendServerImageToPlayer(player, painting.getId(), url);
                }
            }
        });

        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }
            if (!(entity instanceof Painting painting) || !(painting instanceof UrlPaintingDataHolder holder)) {
                return InteractionResult.PASS;
            }
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                if (!ServerPlayNetworking.canSend(serverPlayer, OpenUrlPaintingScreenPayload.TYPE)) {
                    return InteractionResult.PASS;
                }
                if (!holder.pohtomc$canEdit(serverPlayer)) {
                    String owner = holder.pohtomc$getOwnerName().isEmpty() ? "another player" : holder.pohtomc$getOwnerName();
                    serverPlayer.sendOverlayMessage(Component.literal("This Painting is locked by " + owner + ".").withStyle(ChatFormatting.RED));
                    return InteractionResult.FAIL;
                }
                ServerPlayNetworking.send(serverPlayer, new OpenUrlPaintingScreenPayload(
                        painting.getId(),
                        holder.pohtomc$getImageUrl(),
                        holder.pohtomc$getEffectiveWidth(),
                        holder.pohtomc$getEffectiveHeight(),
                        holder.pohtomc$isLocked(),
                        holder.pohtomc$getGlowPower(),
                        holder.pohtomc$getOwnerName(),
                        holder.pohtomc$isOwnerOrOp(serverPlayer)
                ));
            }
            return InteractionResult.SUCCESS;
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateUrlPaintingPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            Entity entity = player.level().getEntity(payload.entityId());
            if (!(entity instanceof Painting painting) || !(painting instanceof UrlPaintingDataHolder holder)) {
                return;
            }
            if (player.distanceToSqr(painting) > 4096.0) {
                return;
            }
            if (!holder.pohtomc$canEdit(player)) {
                player.sendOverlayMessage(Component.literal("You do not have permission to edit this Painting.").withStyle(ChatFormatting.RED));
                return;
            }
            String rawUrl = payload.imageUrl() == null ? "" : payload.imageUrl().trim();
            if (!rawUrl.isEmpty() && !isValidHttpUrl(rawUrl)) {
                player.sendOverlayMessage(Component.literal("Invalid URL! Must start with http:// or https://").withStyle(ChatFormatting.RED));
                return;
            }
            if (holder.pohtomc$getOwnerUuid().isEmpty()) {
                holder.pohtomc$setOwnerUuid(player.getStringUUID());
                holder.pohtomc$setOwnerName(player.getScoreboardName());
            }
            holder.pohtomc$setImageUrl(rawUrl);
            holder.pohtomc$setCustomWidth(payload.width());
            holder.pohtomc$setCustomHeight(payload.height());
            holder.pohtomc$setGlowPower(payload.glowPower());
            if (holder.pohtomc$isOwnerOrOp(player)) {
                holder.pohtomc$setLocked(payload.locked());
            }
            int targetW = holder.pohtomc$getEffectiveWidth();
            int targetH = holder.pohtomc$getEffectiveHeight();
            Holder<PaintingVariant> matchingVariant = findBestMatchingVariant(player.level(), targetW, targetH);
            if (matchingVariant != null) {
                holder.pohtomc$setVariant(matchingVariant);
            }
            holder.pohtomc$refreshBoundingBox();
            LevelChunk chunk = player.level().getChunkAt(painting.blockPosition());
            chunk.markUnsaved();

            if (!rawUrl.isEmpty()) {
                cacheServerImage(player.level().getServer(), rawUrl);
            }
            broadcastSync(painting, holder, player);
            painting.playSound(SoundEvents.ITEM_FRAME_ADD_ITEM, 1.0F, 1.0F);
            player.sendOverlayMessage(Component.literal("Painting updated (" + holder.pohtomc$getEffectiveWidth() + "x" + holder.pohtomc$getEffectiveHeight() + ")").withStyle(ChatFormatting.GREEN));
        });

        LOGGER.info("PohtoMC URL Image Paintings initialized (vanilla painting hook mode with persistent disk caching)!");
    }

    private static void syncPaintingsForPlayer(ServerPlayer player) {
        for (Entity entity : player.level().getAllEntities()) {
            if (entity instanceof Painting painting && painting instanceof UrlPaintingDataHolder holder) {
                if (holder.pohtomc$hasCustomData()) {
                    if (PlayerLookup.tracking(painting).contains(player) || player.distanceToSqr(painting) < 16384.0) {
                        ServerPlayNetworking.send(player, createSyncPayload(painting, holder));
                    }
                }
            }
        }
    }

    public static Path getServerImageCachePath(MinecraftServer server, String url) {
        if (server == null || url == null || url.isBlank()) {
            return null;
        }
        try {
            Path dir = server.getWorldPath(LevelResource.ROOT).resolve("pohtomc_images");
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            return dir.resolve(sha256Hex(url) + ".img");
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isPublicRemoteUri(URI uri) {
        if (uri == null || uri.getHost() == null) {
            return false;
        }
        try {
            java.net.InetAddress[] addresses = java.net.InetAddress.getAllByName(uri.getHost());
            for (java.net.InetAddress addr : addresses) {
                if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress() || addr.isAnyLocalAddress()) {
                    return false;
                }
                byte[] raw = addr.getAddress();
                if (raw.length == 4 && (raw[0] & 0xFF) == 169 && (raw[1] & 0xFF) == 254) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] readBoundedStream(java.io.InputStream input, int maxBytes) throws java.io.IOException {
        try (java.io.InputStream in = input; java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int total = 0;
            int r;
            while ((r = in.read(buf)) != -1) {
                total += r;
                if (total > maxBytes) {
                    throw new java.io.IOException("Remote image exceeds max allowed size of " + (maxBytes / (1024 * 1024)) + " MB");
                }
                out.write(buf, 0, r);
            }
            if (total == 0) {
                throw new java.io.IOException("Empty image response from remote server");
            }
            return out.toByteArray();
        }
    }

    public static void cacheServerImage(MinecraftServer server, String url) {
        if (isGifUrl(url)) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                Path file = getServerImageCachePath(server, url);
                if (file != null && Files.isRegularFile(file)) {
                    byte[] existing = Files.readAllBytes(file);
                    if (!isWebP(existing) && !isGif(existing)) {
                        return;
                    }
                    Files.deleteIfExists(file);
                }
                URI uri = toFetchUri(url);
                if (uri == null || !isPublicRemoteUri(uri)) {
                    return;
                }
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .GET()
                        .timeout(Duration.ofSeconds(15L))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                        .header("Accept", "image/png,image/jpeg,image/jpg,*/*;q=0.8")
                        .build();
                HttpClient client = HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.ALWAYS)
                        .connectTimeout(Duration.ofSeconds(10L))
                        .build();
                HttpResponse<java.io.InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                String ctype = response.headers().firstValue("Content-Type").orElse("").toLowerCase();
                if (ctype.contains("image/gif")) {
                    return;
                }
                if (response.statusCode() >= 200 && response.statusCode() < 300 && !ctype.contains("text/html")) {
                    byte[] bytes = readBoundedStream(response.body(), 10 * 1024 * 1024);
                    if (file != null && bytes.length > 0 && !isWebP(bytes) && !isGif(bytes)) {
                        Files.write(file, bytes);
                        LOGGER.info("Successfully cached image on server: {}", file.getFileName());
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to cache image on server for {}: {}", url, e.getMessage());
            }
        });
    }

    private static void sendServerImageToPlayer(ServerPlayer player, int entityId, String url) {
        if (isGifUrl(url)) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                Path file = getServerImageCachePath(player.level().getServer(), url);
                byte[] bytes = null;
                if (file != null && Files.isRegularFile(file)) {
                    bytes = Files.readAllBytes(file);
                }
                if (bytes != null && (isWebP(bytes) || isGif(bytes))) {
                    bytes = null;
                    if (file != null) {
                        Files.deleteIfExists(file);
                    }
                }
                if (bytes == null || bytes.length == 0) {
                    URI uri = toFetchUri(url);
                    if (uri != null && isPublicRemoteUri(uri)) {
                        HttpRequest req = HttpRequest.newBuilder(uri)
                                .GET()
                                .timeout(Duration.ofSeconds(15L))
                                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                                .header("Accept", "image/png,image/jpeg,image/jpg,*/*;q=0.8")
                                .build();
                        HttpClient cl = HttpClient.newBuilder()
                                .followRedirects(HttpClient.Redirect.ALWAYS)
                                .connectTimeout(Duration.ofSeconds(10L))
                                .build();
                        HttpResponse<java.io.InputStream> res = cl.send(req, HttpResponse.BodyHandlers.ofInputStream());
                        String resType = res.headers().firstValue("Content-Type").orElse("").toLowerCase();
                        if (res.statusCode() >= 200 && res.statusCode() < 300 && !resType.contains("text/html") && !resType.contains("image/gif")) {
                            bytes = readBoundedStream(res.body(), 8 * 1024 * 1024);
                            if (isWebP(bytes) || isGif(bytes)) {
                                bytes = null;
                            } else if (file != null && bytes.length > 0) {
                                Files.write(file, bytes);
                            }
                        }
                    }
                }
                if (bytes != null && bytes.length > 0 && bytes.length <= 8388608) {
                    byte[] sendBytes = bytes;
                    player.level().getServer().execute(() -> {
                        if (ServerPlayNetworking.canSend(player, SendPaintingImagePayload.TYPE)) {
                            ServerPlayNetworking.send(player, new SendPaintingImagePayload(entityId, url, sendBytes));
                        }
                    });
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to send server-cached image to player {}: {}", player.getScoreboardName(), e.getMessage());
            }
        });
    }

    public static Holder<PaintingVariant> findBestMatchingVariant(Level level, int targetW, int targetH) {
        Optional<net.minecraft.core.Registry<PaintingVariant>> registryOpt = level.registryAccess().lookup(Registries.PAINTING_VARIANT);
        if (registryOpt.isEmpty()) {
            return null;
        }
        net.minecraft.core.Registry<PaintingVariant> registry = registryOpt.get();
        Holder.Reference<PaintingVariant> bestHolder = null;
        double bestScore = Double.MAX_VALUE;
        double targetAspect = (double) targetW / (double) targetH;

        for (Holder.Reference<PaintingVariant> ref : registry.listElements().toList()) {
            PaintingVariant variant = ref.value();
            int vw = variant.width();
            int vh = variant.height();
            if (vw == targetW && vh == targetH) {
                return ref;
            }
            double variantAspect = (double) vw / (double) vh;
            double aspectDiff = Math.abs(Math.log(targetAspect / variantAspect));
            double sizeDiff = (double) (Math.abs(vw - targetW) + Math.abs(vh - targetH));
            double score = aspectDiff * 100.0 + sizeDiff;
            if (score < bestScore) {
                bestScore = score;
                bestHolder = ref;
            }
        }
        return bestHolder;
    }

    private static SyncPaintingDataPayload createSyncPayload(Painting painting, UrlPaintingDataHolder holder) {
        return new SyncPaintingDataPayload(
                painting.getId(),
                holder.pohtomc$getImageUrl(),
                holder.pohtomc$getCustomWidth(),
                holder.pohtomc$getCustomHeight(),
                holder.pohtomc$isLocked(),
                holder.pohtomc$getGlowPower(),
                holder.pohtomc$getOwnerUuid(),
                holder.pohtomc$getOwnerName()
        );
    }

    private static void broadcastSync(Painting painting, UrlPaintingDataHolder holder, ServerPlayer sender) {
        SyncPaintingDataPayload syncPayload = createSyncPayload(painting, holder);
        boolean sentToSender = false;
        for (ServerPlayer watcher : PlayerLookup.tracking(painting)) {
            if (watcher == sender) {
                sentToSender = true;
            }
            if (ServerPlayNetworking.canSend(watcher, SyncPaintingDataPayload.TYPE)) {
                ServerPlayNetworking.send(watcher, syncPayload);
            }
        }
        if (!sentToSender && ServerPlayNetworking.canSend(sender, SyncPaintingDataPayload.TYPE)) {
            ServerPlayNetworking.send(sender, syncPayload);
        }
    }

    public static boolean isValidHttpUrl(String url) {
        return toSafeUri(url) != null;
    }

    public static boolean isWebP(byte[] bytes) {
        return bytes != null && bytes.length > 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
    }

    public static boolean isGif(byte[] bytes) {
        return bytes != null && bytes.length >= 6
                && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == '8'
                && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a';
    }

    public static boolean isGifUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String lower = url.toLowerCase();
        int queryIdx = lower.indexOf('?');
        String path = queryIdx >= 0 ? lower.substring(0, queryIdx) : lower;
        if (path.endsWith(".gif")) {
            return true;
        }
        if (queryIdx >= 0) {
            String query = lower.substring(queryIdx + 1);
            if (query.contains("format=gif")) {
                return true;
            }
        }
        return false;
    }

    /**
     * URI actually used for downloading. Discord's media proxy honours "format=webp" and returns WebP,
     * which Java cannot decode, so we ask for PNG instead (the original URL stays the cache key).
     */
    public static URI toFetchUri(String url) {
        URI base = toSafeUri(url);
        if (base == null) {
            return null;
        }
        String host = base.getHost();
        if (host != null) {
            String h = host.toLowerCase();
            if (h.endsWith("discordapp.net") || h.endsWith("discordapp.com") || h.endsWith("discord.com")) {
                String rewritten = base.toString().replaceAll("(?i)([?&])format=webp", "$1format=png");
                if (!rewritten.equals(base.toString())) {
                    try {
                        return URI.create(rewritten);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return base;
    }

    public static URI toSafeUri(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        Matcher matcher = URL_REGEX.matcher(trimmed);
        if (!matcher.matches()) {
            return null;
        }
        String scheme = matcher.group(1).toLowerCase();
        String authority = matcher.group(2);
        String path = matcher.group(3) == null ? "" : matcher.group(3);
        String query = matcher.group(4);
        String fragment = matcher.group(5);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return null;
        }
        if (authority.isBlank()) {
            return null;
        }
        try {
            return URI.create(trimmed);
        } catch (Exception e) {
            try {
                return new URI(scheme, authority, path, query, fragment);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
