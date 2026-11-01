package com.pohtomc.network;

import com.pohtomc.PohtoMcMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RequestPaintingDataPayload(int entityId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RequestPaintingDataPayload> TYPE =
            new CustomPacketPayload.Type<>(PohtoMcMod.id("request_painting_data"));
    public static final StreamCodec<ByteBuf, RequestPaintingDataPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    RequestPaintingDataPayload::entityId,
                    RequestPaintingDataPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
