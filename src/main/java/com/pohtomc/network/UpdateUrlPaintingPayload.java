package com.pohtomc.network;

import com.pohtomc.PohtoMcMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record UpdateUrlPaintingPayload(
        int entityId,
        String imageUrl,
        int width,
        int height,
        boolean locked,
        int glowPower
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<UpdateUrlPaintingPayload> TYPE =
            new CustomPacketPayload.Type<>(PohtoMcMod.id("update_url_painting"));
    public static final StreamCodec<ByteBuf, UpdateUrlPaintingPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    UpdateUrlPaintingPayload::entityId,
                    ByteBufCodecs.stringUtf8(1024),
                    UpdateUrlPaintingPayload::imageUrl,
                    ByteBufCodecs.VAR_INT,
                    UpdateUrlPaintingPayload::width,
                    ByteBufCodecs.VAR_INT,
                    UpdateUrlPaintingPayload::height,
                    ByteBufCodecs.BOOL,
                    UpdateUrlPaintingPayload::locked,
                    ByteBufCodecs.VAR_INT,
                    UpdateUrlPaintingPayload::glowPower,
                    UpdateUrlPaintingPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
