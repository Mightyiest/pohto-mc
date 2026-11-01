package com.pohtomc.network;

import com.pohtomc.PohtoMcMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenUrlPaintingScreenPayload(
        int entityId,
        String imageUrl,
        int width,
        int height,
        boolean locked,
        int glowPower,
        String ownerName,
        boolean canChangeLock
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenUrlPaintingScreenPayload> TYPE =
            new CustomPacketPayload.Type<>(PohtoMcMod.id("open_url_painting_screen"));
    public static final StreamCodec<ByteBuf, OpenUrlPaintingScreenPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    OpenUrlPaintingScreenPayload::entityId,
                    ByteBufCodecs.stringUtf8(1024),
                    OpenUrlPaintingScreenPayload::imageUrl,
                    ByteBufCodecs.VAR_INT,
                    OpenUrlPaintingScreenPayload::width,
                    ByteBufCodecs.VAR_INT,
                    OpenUrlPaintingScreenPayload::height,
                    ByteBufCodecs.BOOL,
                    OpenUrlPaintingScreenPayload::locked,
                    ByteBufCodecs.VAR_INT,
                    OpenUrlPaintingScreenPayload::glowPower,
                    ByteBufCodecs.stringUtf8(64),
                    OpenUrlPaintingScreenPayload::ownerName,
                    ByteBufCodecs.BOOL,
                    OpenUrlPaintingScreenPayload::canChangeLock,
                    OpenUrlPaintingScreenPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
