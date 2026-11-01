package com.pohtomc.client.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.pohtomc.PohtoMcMod;
import com.pohtomc.client.PohtoMcClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UrlTextureCache {
    private static final int MAX_IMAGE_DIMENSION = 1024;
    private static final int MAX_DOWNLOAD_BYTES = 15728640;
    private static final long FAILED_ENTRY_TTL_MS = 15000L;
    private static final Map<String, TextureEntry> CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService DOWNLOAD_EXECUTOR = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "PohtoMC-Image-Downloader");
        thread.setDaemon(true);
        return thread;
    });
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(10L))
            .executor(DOWNLOAD_EXECUTOR)
            .build();

    private UrlTextureCache() {
    }

    public enum Status {
        LOADING,
        READY,
        FAILED
    }

    public static class TextureEntry {
        volatile Status status = Status.LOADING;
        volatile Identifier textureId;
        volatile DynamicTexture dynamicTexture;
        volatile Identifier oledTextureId;
        volatile DynamicTexture oledDynamicTexture;
        volatile int imageWidth = 1;
        volatile int imageHeight = 1;
        volatile String errorMessage = "";
        volatile long failedAt = 0L;
        volatile GifDecoder.GifAnimation animation = null;
        volatile long animationStartTime = 0L;
        volatile int currentFrameIndex = -1;

        public Status status() {
            return this.status;
        }

        public Identifier textureId() {
            return this.textureId;
        }

        public Identifier getEmissiveTextureId(int glowPower) {
            if (glowPower <= 0) {
                return null;
            }
            return this.oledTextureId;
        }

        public int imageWidth() {
            return this.imageWidth;
        }

        public int imageHeight() {
            return this.imageHeight;
        }

        public String errorMessage() {
            return this.errorMessage;
        }

        public long failedAt() {
            return this.failedAt;
        }

        public boolean isAnimated() {
            return this.animation != null && this.animation.frames.size() > 1;
        }

        public GifDecoder.GifAnimation animation() {
            return this.animation;
        }

        public void bindFrameForRender(int glowPower) {
            if (this.status != Status.READY || this.dynamicTexture == null) {
                return;
            }
            if (this.animation != null && !this.animation.frames.isEmpty()) {
                long elapsed = System.currentTimeMillis() - this.animationStartTime;
                GifDecoder.GifFrame target = this.animation.getFrameAt(elapsed);
                if (target != null) {
                    int frameIdx = target.startTimestampMs();
                    if (frameIdx != this.currentFrameIndex) {
                        this.dynamicTexture.getPixels().copyFrom(target.image());
                        this.dynamicTexture.upload();
                        if (this.oledDynamicTexture != null) {
                            populateOledEmissive(target.image(), this.oledDynamicTexture.getPixels());
                            this.oledDynamicTexture.upload();
                        }
                        this.currentFrameIndex = frameIdx;
                    }
                }
            }
        }
    }

    /**
     * Generates a selective OLED emissive overlay texture for Pass 2 on a background thread (or per GIF frame advance):
     * - Blacks and dark shadows stay completely transparent (alpha = 0, discarded by shader) so they emit zero light.
     * - Light and vibrant colors smoothly ramp into the emissive pass based on luminance and colorfulness.
     * - Slider intensity (1%-200%) is modulated purely on the GPU via vertex alpha and multi-pass bloom in PaintingRendererMixin,
     *   eliminating all main-thread CPU pixel loops and mid-frame texture allocations/releases.
     */
    public static NativeImage buildOledEmissiveImage(NativeImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        NativeImage dst = new NativeImage(w, h, false);
        populateOledEmissive(src, dst);
        return dst;
    }

    public static void populateOledEmissive(NativeImage src, NativeImage dst) {
        if (src == null || dst == null) {
            return;
        }
        int w = Math.min(src.getWidth(), dst.getWidth());
        int h = Math.min(src.getHeight(), dst.getHeight());

        // OLED black cutoff threshold:
        // Pixels below blackCutoff emit zero glow (remain deep OLED black on Pass 1).
        final float blackCutoff = 0.14f;
        final float invRange = 1.0f / (1.0f - blackCutoff);

        for (int y = 0; y < h; ++y) {
            for (int x = 0; x < w; ++x) {
                int argb = src.getPixel(x, y);
                int a = (argb >> 24) & 0xFF;
                if (a < 26) {
                    dst.setPixel(x, y, 0);
                    continue;
                }
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;

                float rf = (float) r / 255.0f;
                float gf = (float) g / 255.0f;
                float bf = (float) b / 255.0f;

                float lum = 0.2126f * rf + 0.7152f * gf + 0.0722f * bf;
                float maxC = Math.max(rf, Math.max(gf, bf));
                float minC = Math.min(rf, Math.min(gf, bf));
                float chroma = maxC - minC;
                float oledLevel = Math.max(lum, maxC * 0.72f + chroma * 0.28f);

                if (oledLevel <= blackCutoff) {
                    dst.setPixel(x, y, 0);
                    continue;
                }

                float t = Math.min(1.0f, (oledLevel - blackCutoff) * invRange);
                float oledWeight = t * t * (3.0f - 2.0f * t);

                // Remap active emissive alpha into 28..255 so it clears ENTITY_TRANSLUCENT_EMISSIVE's 0.1f alpha cutout
                int outA = Math.min(a, 28 + (int) (oledWeight * (float) (a - 28)));
                dst.setPixel(x, y, (outA << 24) | (r << 16) | (g << 8) | b);
            }
        }
    }

    public static Path getDiskCachePath(String url) {
        try {
            Path dir = FabricLoader.getInstance().getGameDir().resolve("cache").resolve("pohtomc");
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            return dir.resolve(sha256Hex(url) + ".img");
        } catch (Exception e) {
            return null;
        }
    }

    public static TextureEntry getOrLoad(String rawUrl) {
        if (rawUrl == null) {
            return null;
        }
        String url = rawUrl.trim();
        if (url.isEmpty() || !PohtoMcMod.isValidHttpUrl(url)) {
            return null;
        }
        TextureEntry existing = CACHE.get(url);
        if (existing != null && existing.status() == Status.FAILED) {
            if (System.currentTimeMillis() - existing.failedAt >= FAILED_ENTRY_TTL_MS) {
                CACHE.remove(url, existing);
            }
        }
        return CACHE.computeIfAbsent(url, UrlTextureCache::startDownload);
    }

    public static void clearIfFailed(String rawUrl) {
        if (rawUrl == null) {
            return;
        }
        String url = rawUrl.trim();
        TextureEntry existing = CACHE.get(url);
        if (existing != null && existing.status() == Status.FAILED) {
            CACHE.remove(url, existing);
        }
    }

    public static void clearFailed() {
        CACHE.entrySet().removeIf(e -> e.getValue().status() == Status.FAILED);
    }

    public static void reload(String rawUrl) {
        if (rawUrl == null) {
            return;
        }
        String url = rawUrl.trim();
        TextureEntry old = CACHE.remove(url);
        if (old != null) {
            GifDecoder.GifAnimation oldAnim = old.animation;
            Minecraft.getInstance().execute(() -> {
                if (oldAnim != null) {
                    oldAnim.closeAll();
                }
            });
        }
        Path diskFile = getDiskCachePath(url);
        if (diskFile != null) {
            try {
                Files.deleteIfExists(diskFile);
            } catch (Exception ignored) {
            }
        }
        if (!url.isEmpty() && PohtoMcMod.isValidHttpUrl(url)) {
            getOrLoad(url);
        }
    }

    public static void loadDirectBytes(String url, byte[] imageBytes) {
        if (url == null || imageBytes == null || imageBytes.length == 0 || PohtoMcMod.isWebP(imageBytes)) {
            return;
        }
        Path diskFile = getDiskCachePath(url);
        if (diskFile != null) {
            try {
                Files.write(diskFile, imageBytes);
            } catch (Exception ignored) {
            }
        }
        TextureEntry existing = CACHE.get(url);
        if (existing != null && existing.status() == Status.READY) {
            return;
        }
        TextureEntry entry = CACHE.compute(url, (u, old) -> (old == null || old.status() == Status.FAILED) ? new TextureEntry() : old);
        if (entry.status() == Status.READY) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                uploadImageBytes(url, imageBytes, entry);
            } catch (Exception e) {
                entry.errorMessage = e.getMessage();
                entry.status = Status.FAILED;
            }
        }, DOWNLOAD_EXECUTOR);
    }

    static TextureEntry startDownload(String url) {
        TextureEntry entry = new TextureEntry();
        CompletableFuture.runAsync(() -> {
            downloadAndDecode(url, entry);
        }, DOWNLOAD_EXECUTOR);
        return entry;
    }

    static void downloadAndDecode(String url, TextureEntry entry) {
        try {
            Path diskFile = getDiskCachePath(url);
            if (diskFile != null && Files.isRegularFile(diskFile)) {
                byte[] diskBytes = Files.readAllBytes(diskFile);
                if (diskBytes.length > 0 && !PohtoMcMod.isWebP(diskBytes)) {
                    try {
                        uploadImageBytes(url, diskBytes, entry);
                        return;
                    } catch (IOException corrupt) {
                        Files.deleteIfExists(diskFile);
                    }
                } else {
                    Files.deleteIfExists(diskFile);
                }
            }

            URI uri = PohtoMcMod.toFetchUri(url);
            if (uri == null) {
                entry.errorMessage = "Invalid URL";
                entry.failedAt = System.currentTimeMillis();
                entry.status = Status.FAILED;
                return;
            }

            HttpRequest request = HttpRequest.newBuilder(uri)
                    .GET()
                    .timeout(Duration.ofSeconds(15L))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                    .header("Accept", "image/png,image/jpeg,image/jpg,image/gif,*/*;q=0.8")
                    .build();

            HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode >= 300) {
                entry.errorMessage = "HTTP " + statusCode;
                entry.failedAt = System.currentTimeMillis();
                entry.status = Status.FAILED;
                if (!PohtoMcMod.isGifUrl(url)) {
                    PohtoMcClient.requestServerBackupImage(url);
                }
                return;
            }

            String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase();
            if (contentType.contains("text/html")) {
                entry.errorMessage = "URL returned a webpage (HTML), not a direct image";
                entry.failedAt = System.currentTimeMillis();
                entry.status = Status.FAILED;
                if (!PohtoMcMod.isGifUrl(url)) {
                    PohtoMcClient.requestServerBackupImage(url);
                }
                return;
            }

            byte[] imageBytes = readBoundedBytes(response.body(), MAX_DOWNLOAD_BYTES);
            if (diskFile != null) {
                try {
                    Files.write(diskFile, imageBytes);
                } catch (Exception ignored) {
                }
            }
            uploadImageBytes(url, imageBytes, entry);
        } catch (Exception e) {
            String msg = e.getMessage();
            entry.errorMessage = (msg != null && !msg.isBlank()) ? msg : e.getClass().getSimpleName();
            entry.failedAt = System.currentTimeMillis();
            entry.status = Status.FAILED;
            PohtoMcMod.LOGGER.warn("Failed to download or decode URL painting from {}: {}", url, entry.errorMessage);
            if (!PohtoMcMod.isGifUrl(url)) {
                PohtoMcClient.requestServerBackupImage(url);
            }
        }
    }

    static void uploadImageBytes(String url, byte[] imageBytes, TextureEntry entry) throws IOException {
        if (entry.status == Status.READY) {
            return;
        }
        String hash = sha256Hex(url);
        Identifier textureId = PohtoMcMod.id("dynamic/painting_" + hash);
        Identifier oledTextureId = PohtoMcMod.id("dynamic/painting_" + hash + "_oled");

        if (PohtoMcMod.isGif(imageBytes)) {
            GifDecoder.GifAnimation animation = GifDecoder.decode(imageBytes);
            if (animation != null && !animation.frames.isEmpty()) {
                NativeImage firstFrame = animation.frames.get(0).image();
                NativeImage initialGpu = new NativeImage(animation.width, animation.height, false);
                initialGpu.copyFrom(firstFrame);
                NativeImage initialOledGpu = buildOledEmissiveImage(firstFrame);

                Minecraft.getInstance().execute(() -> {
                    if (entry.status == Status.READY) {
                        initialGpu.close();
                        initialOledGpu.close();
                        animation.closeAll();
                        return;
                    }
                    try {
                        TextureManager textureManager = Minecraft.getInstance().getTextureManager();
                        DynamicTexture texture = new DynamicTexture(() -> "PohtoMC URL Painting GIF", initialGpu);
                        DynamicTexture oledTexture = new DynamicTexture(() -> "PohtoMC URL Painting GIF OLED", initialOledGpu);
                        // TextureManager.register atomically replaces and safeCloses any existing texture without leaving null in byPath
                        textureManager.register(textureId, texture);
                        textureManager.register(oledTextureId, oledTexture);
                        entry.animation = animation;
                        entry.imageWidth = animation.width;
                        entry.imageHeight = animation.height;
                        entry.animationStartTime = System.currentTimeMillis();
                        entry.currentFrameIndex = animation.frames.get(0).startTimestampMs();
                        entry.dynamicTexture = texture;
                        entry.textureId = textureId;
                        entry.oledDynamicTexture = oledTexture;
                        entry.oledTextureId = oledTextureId;
                        entry.status = Status.READY;
                    } catch (Exception e) {
                        initialGpu.close();
                        initialOledGpu.close();
                        animation.closeAll();
                        entry.errorMessage = "GPU upload failed";
                        entry.failedAt = System.currentTimeMillis();
                        entry.status = Status.FAILED;
                        PohtoMcMod.LOGGER.warn("Failed to upload URL painting GIF texture for {}", url, e);
                    }
                });
                return;
            }
        }

        NativeImage nativeImage = decodeToNativeImage(imageBytes);
        int width = nativeImage.getWidth();
        int height = nativeImage.getHeight();
        NativeImage oledImage = buildOledEmissiveImage(nativeImage);

        Minecraft.getInstance().execute(() -> {
            if (entry.status == Status.READY) {
                nativeImage.close();
                oledImage.close();
                return;
            }
            try {
                TextureManager textureManager = Minecraft.getInstance().getTextureManager();
                DynamicTexture texture = new DynamicTexture(() -> "PohtoMC URL Painting", nativeImage);
                DynamicTexture oledTexture = new DynamicTexture(() -> "PohtoMC URL Painting OLED", oledImage);
                // TextureManager.register atomically replaces and safeCloses any existing texture without leaving null in byPath
                textureManager.register(textureId, texture);
                textureManager.register(oledTextureId, oledTexture);
                entry.imageWidth = width;
                entry.imageHeight = height;
                entry.dynamicTexture = texture;
                entry.textureId = textureId;
                entry.oledDynamicTexture = oledTexture;
                entry.oledTextureId = oledTextureId;
                entry.status = Status.READY;
            } catch (Exception e) {
                nativeImage.close();
                oledImage.close();
                entry.errorMessage = "GPU upload failed";
                entry.failedAt = System.currentTimeMillis();
                entry.status = Status.FAILED;
                PohtoMcMod.LOGGER.warn("Failed to upload URL painting texture for {}", url, e);
            }
        });
    }

    static byte[] readBoundedBytes(InputStream input, int maxBytes) throws IOException {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException("Image exceeds 15 MB limit");
                }
                out.write(buffer, 0, read);
            }
            if (total == 0) {
                throw new IOException("Empty response");
            }
            return out.toByteArray();
        }
    }

    static NativeImage decodeToNativeImage(byte[] bytes) throws IOException {
        try {
            BufferedImage buffered = ImageIO.read(new ByteArrayInputStream(bytes));
            if (buffered != null) {
                buffered = scaleDownIfNeeded(buffered);
                int width = buffered.getWidth();
                int height = buffered.getHeight();
                boolean hasAlpha = buffered.getColorModel().hasAlpha();
                int[] argbPixels = buffered.getRGB(0, 0, width, height, null, 0, width);
                NativeImage nativeImage = new NativeImage(width, height, false);
                int idx = 0;
                for (int y = 0; y < height; ++y) {
                    for (int x = 0; x < width; ++x) {
                        int argb = argbPixels[idx++];
                        if (!hasAlpha) {
                            argb |= 0xFF000000;
                        }
                        nativeImage.setPixel(x, y, argb);
                    }
                }
                return nativeImage;
            }
            return NativeImage.read(bytes);
        } catch (Exception e) {
            if (isWebP(bytes)) {
                throw new IOException("WebP format not supported by your Java runtime - use direct PNG/JPG link");
            }
            throw new IOException("Could not decode image - ensure URL points to a valid image file");
        }
    }

    static BufferedImage scaleDownIfNeeded(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= MAX_IMAGE_DIMENSION && h <= MAX_IMAGE_DIMENSION && src.getType() == BufferedImage.TYPE_INT_ARGB) {
            return src;
        }
        double scale = Math.min(1.0, Math.min((double) MAX_IMAGE_DIMENSION / (double) w, (double) MAX_IMAGE_DIMENSION / (double) h));
        int targetW = Math.max(1, (int) Math.round((double) w * scale));
        int targetH = Math.max(1, (int) Math.round((double) h * scale));
        BufferedImage scaled = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaled.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.drawImage(src, 0, 0, targetW, targetH, null);
        g2d.dispose();
        return scaled;
    }

    public static boolean isWebP(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return false;
        }
        return bytes[0] == 82 && bytes[1] == 73 && bytes[2] == 70 && bytes[3] == 70
                && bytes[8] == 87 && bytes[9] == 69 && bytes[10] == 66 && bytes[11] == 80;
    }

    static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
