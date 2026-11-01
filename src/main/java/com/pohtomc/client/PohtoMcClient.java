package com.pohtomc.client;

import com.pohtomc.PohtoMcMod;
import com.pohtomc.client.gui.UrlPaintingScreen;
import com.pohtomc.client.texture.UrlTextureCache;
import com.pohtomc.duck.UrlPaintingDataHolder;
import com.pohtomc.network.OpenUrlPaintingScreenPayload;
import com.pohtomc.network.RequestPaintingDataPayload;
import com.pohtomc.network.RequestPaintingImagePayload;
import com.pohtomc.network.SendPaintingImagePayload;
import com.pohtomc.network.SyncPaintingDataPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.painting.Painting;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PohtoMcClient implements ClientModInitializer {
    private static final Map<Integer, SyncPaintingDataPayload> PENDING_SYNCS = new ConcurrentHashMap<>();

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(OpenUrlPaintingScreenPayload.TYPE, (payload, context) -> {
            context.client().setScreenAndShow(new UrlPaintingScreen(payload));
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncPaintingDataPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().level == null) {
                    PENDING_SYNCS.put(payload.entityId(), payload);
                    return;
                }
                Entity entity = context.client().level.getEntity(payload.entityId());
                if (entity instanceof Painting painting && painting instanceof UrlPaintingDataHolder holder) {
                    applySync(painting, holder, payload);
                } else {
                    PENDING_SYNCS.put(payload.entityId(), payload);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SendPaintingImagePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                UrlTextureCache.loadDirectBytes(payload.imageUrl(), payload.imageBytes());
            });
        });

        ClientEntityEvents.ENTITY_LOAD.register((entity, clientLevel) -> {
            if (entity instanceof Painting painting && painting instanceof UrlPaintingDataHolder holder) {
                SyncPaintingDataPayload pending = PENDING_SYNCS.remove(painting.getId());
                if (pending != null) {
                    applySync(painting, holder, pending);
                } else if (holder.pohtomc$hasCustomData() && ClientPlayNetworking.canSend(RequestPaintingDataPayload.TYPE)) {
                    ClientPlayNetworking.send(new RequestPaintingDataPayload(painting.getId()));
                }
            }
        });

        ClientEntityEvents.ENTITY_UNLOAD.register((entity, clientLevel) -> {
            PENDING_SYNCS.remove(entity.getId());
        });
    }

    public static void requestServerBackupImage(String url) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }
        client.execute(() -> {
            if (!ClientPlayNetworking.canSend(RequestPaintingImagePayload.TYPE)) {
                return;
            }
            for (Entity entity : client.level.entitiesForRendering()) {
                if (entity instanceof Painting painting && painting instanceof UrlPaintingDataHolder holder) {
                    if (url.equals(holder.pohtomc$getImageUrl())) {
                        ClientPlayNetworking.send(new RequestPaintingImagePayload(painting.getId()));
                        return;
                    }
                }
            }
        });
    }

    static void applySync(Painting painting, UrlPaintingDataHolder holder, SyncPaintingDataPayload payload) {
        holder.pohtomc$setImageUrl(payload.imageUrl());
        holder.pohtomc$setCustomWidth(payload.customWidth());
        holder.pohtomc$setCustomHeight(payload.customHeight());
        holder.pohtomc$setLocked(payload.locked());
        holder.pohtomc$setGlowPower(payload.glowPower());
        holder.pohtomc$setOwnerUuid(payload.ownerUuid());
        holder.pohtomc$setOwnerName(payload.ownerName());
        holder.pohtomc$refreshBoundingBox();

        if (payload.imageUrl() != null && !payload.imageUrl().trim().isEmpty() && PohtoMcMod.isValidHttpUrl(payload.imageUrl())) {
            UrlTextureCache.clearIfFailed(payload.imageUrl());
            UrlTextureCache.getOrLoad(payload.imageUrl());
        }
    }
}
