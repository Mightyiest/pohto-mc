package com.pohtomc.network;

import com.pohtomc.PohtoMcMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SendPaintingImagePayload(int entityId, String imageUrl, byte[] imageBytes) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SendPaintingImagePayload> TYPE =
            new CustomPacketPayload.Type<>(PohtoMcMod.id("send_painting_image"));
    public static final StreamCodec<ByteBuf, SendPaintingImagePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    SendPaintingImagePayload::entityId,
                    ByteBufCodecs.stringUtf8(1024),
                    SendPaintingImagePayload::imageUrl,
                    ByteBufCodecs.byteArray(8388608),
                    SendPaintingImagePayload::imageBytes,
                    SendPaintingImagePayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
