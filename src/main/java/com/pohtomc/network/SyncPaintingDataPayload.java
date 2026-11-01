package com.pohtomc.network;

import com.pohtomc.PohtoMcMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SyncPaintingDataPayload(
        int entityId,
        String imageUrl,
        int customWidth,
        int customHeight,
        boolean locked,
        int glowPower,
        String ownerUuid,
        String ownerName
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncPaintingDataPayload> TYPE =
            new CustomPacketPayload.Type<>(PohtoMcMod.id("sync_painting_data"));
    public static final StreamCodec<ByteBuf, SyncPaintingDataPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    SyncPaintingDataPayload::entityId,
                    ByteBufCodecs.stringUtf8(1024),
                    SyncPaintingDataPayload::imageUrl,
                    ByteBufCodecs.VAR_INT,
                    SyncPaintingDataPayload::customWidth,
                    ByteBufCodecs.VAR_INT,
                    SyncPaintingDataPayload::customHeight,
                    ByteBufCodecs.BOOL,
                    SyncPaintingDataPayload::locked,
                    ByteBufCodecs.VAR_INT,
                    SyncPaintingDataPayload::glowPower,
                    ByteBufCodecs.stringUtf8(64),
                    SyncPaintingDataPayload::ownerUuid,
                    ByteBufCodecs.stringUtf8(64),
                    SyncPaintingDataPayload::ownerName,
                    SyncPaintingDataPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
