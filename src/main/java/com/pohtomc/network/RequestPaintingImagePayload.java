package com.pohtomc.network;

import com.pohtomc.PohtoMcMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RequestPaintingImagePayload(int entityId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RequestPaintingImagePayload> TYPE =
            new CustomPacketPayload.Type<>(PohtoMcMod.id("request_painting_image"));
    public static final StreamCodec<ByteBuf, RequestPaintingImagePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    RequestPaintingImagePayload::entityId,
                    RequestPaintingImagePayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
