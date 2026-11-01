package com.pohtomc.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.pohtomc.client.duck.UrlPaintingRenderStateAccess;
import com.pohtomc.client.texture.UrlTextureCache;
import com.pohtomc.duck.UrlPaintingDataHolder;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.PaintingRenderer;
import net.minecraft.client.renderer.entity.state.PaintingRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

@Mixin(PaintingRenderer.class)
public class PaintingRendererMixin {
    @Shadow
    @Final
    private static Identifier BACK_SPRITE_LOCATION;

    @Shadow
    @Final
    private TextureAtlas paintingsAtlas;

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/decoration/painting/Painting;Lnet/minecraft/client/renderer/entity/state/PaintingRenderState;F)V", at = @At("TAIL"))
    private void pohtomc$extractUrlRenderState(Painting entity, PaintingRenderState state, float partialTicks, CallbackInfo ci) {
        if (!(entity instanceof UrlPaintingDataHolder holder) || !(state instanceof UrlPaintingRenderStateAccess access)) {
            return;
        }

        access.pohtomc$setImageUrl(holder.pohtomc$getImageUrl());
        access.pohtomc$setCustomWidth(holder.pohtomc$getCustomWidth());
        access.pohtomc$setCustomHeight(holder.pohtomc$getCustomHeight());
        access.pohtomc$setGlowPower(holder.pohtomc$getGlowPower());

        if (holder.pohtomc$hasCustomData() && state.direction != null) {
            int width = holder.pohtomc$getEffectiveWidth();
            int height = holder.pohtomc$getEffectiveHeight();
            Level level = entity.level();
            if (state.lightCoordsPerBlock.length != width * height) {
                state.lightCoordsPerBlock = new int[width * height];
            }
            float offsetX = (float) (-width) / 2.0F;
            float offsetY = (float) (-height) / 2.0F;

            for (int x = 0; x < width; ++x) {
                for (int y = 0; y < height; ++y) {
                    float centerX = (float) x + offsetX + 0.5F;
                    float centerY = (float) y + offsetY + 0.5F;
                    int wx = entity.getBlockX();
                    int wy = Mth.floor(entity.getY() + (double) centerY);
                    int wz = entity.getBlockZ();

                    switch (state.direction) {
                        case NORTH -> wx = Mth.floor(entity.getX() + (double) centerX);
                        case WEST -> wz = Mth.floor(entity.getZ() - (double) centerX);
                        case SOUTH -> wx = Mth.floor(entity.getX() - (double) centerX);
                        case EAST -> wz = Mth.floor(entity.getZ() + (double) centerX);
                    }

                    state.lightCoordsPerBlock[x + y * width] = LightCoordsUtil.getLightCoords(level, new BlockPos(wx, wy, wz));
                }
            }
        }
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/PaintingRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"), cancellable = true)
    private void pohtomc$submitCustomPainting(PaintingRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera, CallbackInfo ci) {
        if (!(state instanceof UrlPaintingRenderStateAccess access)) {
            return;
        }

        String imageUrl = access.pohtomc$getImageUrl();
        int customW = access.pohtomc$getCustomWidth();
        int customH = access.pohtomc$getCustomHeight();
        boolean hasUrl = imageUrl != null && !imageUrl.isBlank();
        boolean hasCustomSize = customW > 0 || customH > 0;

        if (!hasUrl && !hasCustomSize) {
            return;
        }
        if (state.variant == null || state.direction == null) {
            return;
        }

        int width = customW > 0 ? customW : state.variant.width();
        int height = customH > 0 ? customH : state.variant.height();

        if (state.lightCoordsPerBlock.length != width * height) {
            int[] fallbackLight = new int[width * height];
            int defaultLight = state.lightCoordsPerBlock.length > 0 ? state.lightCoordsPerBlock[0] : 15728880;
            Arrays.fill(fallbackLight, defaultLight);
            state.lightCoordsPerBlock = fallbackLight;
        }

        ci.cancel();

        poseStack.pushPose();
        poseStack.rotateDegrees(Axis.YP, (float) (180 - state.direction.get2DDataValue() * 90));

        TextureAtlasSprite backSprite = this.paintingsAtlas.getSprite(BACK_SPRITE_LOCATION);
        TextureAtlasSprite frontSprite = this.paintingsAtlas.getSprite(state.variant.assetId());
        RenderType atlasRenderType = RenderTypes.entitySolidZOffsetForward(backSprite.atlasLocation());

        boolean urlTextureReady = false;
        UrlTextureCache.TextureEntry urlEntry = null;
        if (hasUrl) {
            urlEntry = UrlTextureCache.getOrLoad(imageUrl);
            if (urlEntry != null && urlEntry.status() == UrlTextureCache.Status.READY && urlEntry.textureId() != null) {
                urlTextureReady = true;
            }
        }

        int glowPower = access.pohtomc$getGlowPower();
        boolean isGlow = glowPower > 0;
        int[] ambientLightCoords = state.lightCoordsPerBlock;

        boolean skipVanillaFrontFace = urlTextureReady;
        boolean glowVanillaFront = isGlow && !urlTextureReady;
        submitNodeCollector.submitCustomGeometry(poseStack, atlasRenderType, (pose, buffer) -> {
            pohtomc$renderFrameAndFront(pose, buffer, ambientLightCoords, width, height, frontSprite, backSprite, skipVanillaFrontFace, glowVanillaFront);
        });

        if (urlTextureReady) {
            UrlTextureCache.TextureEntry readyEntry = urlEntry;
            readyEntry.bindFrameForRender(glowPower);

            // Pass 1: Solid opaque base image (entityCutout writes to opaque G-buffer in shaders, preventing any transparency or wood bleed-through)
            RenderType baseOpaqueRenderType = RenderTypes.entityCutout(readyEntry.textureId());
            submitNodeCollector.submitCustomGeometry(poseStack, baseOpaqueRenderType, (pose, buffer) -> {
                pohtomc$renderCustomFrontImage(pose, buffer, ambientLightCoords, width, height, -0.03125F, false, glowPower, 0xFFFFFFFF);
            });

            // Pass 2: Selective OLED emissive layer driven 100% on the GPU via vertex alpha and overdrive pass
            if (isGlow) {
                Identifier emissiveTextureId = readyEntry.getEmissiveTextureId(glowPower);
                if (emissiveTextureId != null) {
                    int baseEmissiveAlpha = glowPower >= 100
                            ? 255
                            : Math.clamp((int) (255.0f * ((float) glowPower / 100.0f)), 6, 255);
                    int pass2Color = (baseEmissiveAlpha << 24) | 0x00FFFFFF;
                    int boostAlpha = glowPower > 100
                            ? Math.clamp((int) (255.0f * ((float) (glowPower - 100) / 100.0f)), 1, 255)
                            : 0;
                    int boostColor = (boostAlpha << 24) | 0x00FFFFFF;

                    RenderType oledEmissiveRenderType = RenderTypes.entityTranslucentEmissive(emissiveTextureId);
                    submitNodeCollector.submitCustomGeometry(poseStack, oledEmissiveRenderType, (pose, buffer) -> {
                        pohtomc$renderCustomFrontImage(pose, buffer, ambientLightCoords, width, height, -0.0325F, true, glowPower, pass2Color);
                        if (boostAlpha > 0) {
                            pohtomc$renderCustomFrontImage(pose, buffer, ambientLightCoords, width, height, -0.0332F, true, glowPower, boostColor);
                        }
                    });
                }
            }
        }

        poseStack.popPose();
    }

    @Unique
    private static void pohtomc$renderFrameAndFront(PoseStack.Pose pose, VertexConsumer buffer, int[] lightCoordsMap, int width, int height, TextureAtlasSprite frontSprite, TextureAtlasSprite backSprite, boolean skipVanillaFrontFace, boolean glowVanillaFront) {
        float offsetX = (float) (-width) / 2.0F;
        float offsetY = (float) (-height) / 2.0F;
        float edgeHalfDepth = 0.03125F;
        float backU0 = backSprite.getU0();
        float backU1 = backSprite.getU1();
        float backV0 = backSprite.getV0();
        float backV1 = backSprite.getV1();
        float backTopV1 = backSprite.getV(0.0625F);
        float backSideU1 = backSprite.getU(0.0625F);
        double uStep = 1.0 / (double) width;
        double vStep = 1.0 / (double) height;

        for (int x = 0; x < width; ++x) {
            for (int y = 0; y < height; ++y) {
                float x1 = offsetX + (float) (x + 1);
                float x0 = offsetX + (float) x;
                float y1 = offsetY + (float) (y + 1);
                float y0 = offsetY + (float) y;
                int lightCoords = lightCoordsMap[x + y * width];

                if (!skipVanillaFrontFace) {
                    int frontLight = glowVanillaFront ? 15728880 : lightCoords;
                    float fu0 = frontSprite.getU((float) (uStep * (double) (width - x)));
                    float fu1 = frontSprite.getU((float) (uStep * (double) (width - (x + 1))));
                    float fv0 = frontSprite.getV((float) (vStep * (double) (height - y)));
                    float fv1 = frontSprite.getV((float) (vStep * (double) (height - (y + 1))));
                    pohtomc$vertex(pose, buffer, x1, y0, fu1, fv0, -edgeHalfDepth, 0, 0, -1, frontLight);
                    pohtomc$vertex(pose, buffer, x0, y0, fu0, fv0, -edgeHalfDepth, 0, 0, -1, frontLight);
                    pohtomc$vertex(pose, buffer, x0, y1, fu0, fv1, -edgeHalfDepth, 0, 0, -1, frontLight);
                    pohtomc$vertex(pose, buffer, x1, y1, fu1, fv1, -edgeHalfDepth, 0, 0, -1, frontLight);
                }

                // Back face
                pohtomc$vertex(pose, buffer, x1, y1, backU1, backV0, edgeHalfDepth, 0, 0, 1, lightCoords);
                pohtomc$vertex(pose, buffer, x0, y1, backU0, backV0, edgeHalfDepth, 0, 0, 1, lightCoords);
                pohtomc$vertex(pose, buffer, x0, y0, backU0, backV1, edgeHalfDepth, 0, 0, 1, lightCoords);
                pohtomc$vertex(pose, buffer, x1, y0, backU1, backV1, edgeHalfDepth, 0, 0, 1, lightCoords);

                // Top edge
                pohtomc$vertex(pose, buffer, x1, y1, backU0, backV0, -edgeHalfDepth, 0, 1, 0, lightCoords);
                pohtomc$vertex(pose, buffer, x0, y1, backU1, backV0, -edgeHalfDepth, 0, 1, 0, lightCoords);
                pohtomc$vertex(pose, buffer, x0, y1, backU1, backTopV1, edgeHalfDepth, 0, 1, 0, lightCoords);
                pohtomc$vertex(pose, buffer, x1, y1, backU0, backTopV1, edgeHalfDepth, 0, 1, 0, lightCoords);

                // Bottom edge
                pohtomc$vertex(pose, buffer, x1, y0, backU0, backV0, edgeHalfDepth, 0, -1, 0, lightCoords);
                pohtomc$vertex(pose, buffer, x0, y0, backU1, backV0, edgeHalfDepth, 0, -1, 0, lightCoords);
                pohtomc$vertex(pose, buffer, x0, y0, backU1, backTopV1, -edgeHalfDepth, 0, -1, 0, lightCoords);
                pohtomc$vertex(pose, buffer, x1, y0, backU0, backTopV1, -edgeHalfDepth, 0, -1, 0, lightCoords);

                // Left edge
                pohtomc$vertex(pose, buffer, x1, y1, backSideU1, backV0, edgeHalfDepth, -1, 0, 0, lightCoords);
                pohtomc$vertex(pose, buffer, x1, y0, backSideU1, backV1, edgeHalfDepth, -1, 0, 0, lightCoords);
                pohtomc$vertex(pose, buffer, x1, y0, backU0, backV1, -edgeHalfDepth, -1, 0, 0, lightCoords);
                pohtomc$vertex(pose, buffer, x1, y1, backU0, backV0, -edgeHalfDepth, -1, 0, 0, lightCoords);

                // Right edge
                pohtomc$vertex(pose, buffer, x0, y1, backSideU1, backV0, -edgeHalfDepth, 1, 0, 0, lightCoords);
                pohtomc$vertex(pose, buffer, x0, y0, backSideU1, backV1, -edgeHalfDepth, 1, 0, 0, lightCoords);
                pohtomc$vertex(pose, buffer, x0, y0, backU0, backV1, edgeHalfDepth, 1, 0, 0, lightCoords);
                pohtomc$vertex(pose, buffer, x0, y1, backU0, backV0, edgeHalfDepth, 1, 0, 0, lightCoords);
            }
        }
    }

    @Unique
    private static void pohtomc$renderCustomFrontImage(PoseStack.Pose pose, VertexConsumer buffer, int[] lightCoordsMap, int width, int height, float frontZ, boolean isEmissivePass, int glowPower, int vertexColor) {
        float totalW = (float) width;
        float totalH = (float) height;
        float offsetX = -totalW / 2.0F;
        float offsetY = -totalH / 2.0F;

        int minBlockLight = 0;
        if (!isEmissivePass && glowPower > 0) {
            // At 1%-100%, keep dark areas at low ambient floor (3/15) for true OLED blacks.
            // At 101%-200% (Vibrant overdrive), smoothly lift base light floor from 3 up to 13 so midtones pop without CPU pixel loops.
            minBlockLight = glowPower <= 100
                    ? 3
                    : Math.clamp(3 + (int) (10.0f * ((float) (glowPower - 100) / 100.0f)), 3, 13);
        }

        for (int x = 0; x < width; ++x) {
            for (int y = 0; y < height; ++y) {
                float tileX0 = offsetX + (float) x;
                float tileX1 = offsetX + (float) (x + 1);
                float tileY0 = offsetY + (float) y;
                float tileY1 = offsetY + (float) (y + 1);

                int lightCoords;
                if (isEmissivePass) {
                    lightCoords = 15728880;
                } else if (minBlockLight > 0) {
                    int ambient = lightCoordsMap[x + y * width];
                    int blockLight = (ambient >> 4) & 0xF;
                    int skyLight = (ambient >> 20) & 0xF;
                    int liftedBlock = Math.max(blockLight, minBlockLight);
                    lightCoords = (skyLight << 20) | (liftedBlock << 4);
                } else {
                    lightCoords = lightCoordsMap[x + y * width];
                }

                float u0 = (float) (width - x) / totalW;
                float u1 = (float) (width - (x + 1)) / totalW;
                float v0 = (float) (height - y) / totalH;
                float v1 = (float) (height - (y + 1)) / totalH;

                pohtomc$vertex(pose, buffer, tileX1, tileY0, u1, v0, frontZ, 0, 0, -1, lightCoords, vertexColor);
                pohtomc$vertex(pose, buffer, tileX0, tileY0, u0, v0, frontZ, 0, 0, -1, lightCoords, vertexColor);
                pohtomc$vertex(pose, buffer, tileX0, tileY1, u0, v1, frontZ, 0, 0, -1, lightCoords, vertexColor);
                pohtomc$vertex(pose, buffer, tileX1, tileY1, u1, v1, frontZ, 0, 0, -1, lightCoords, vertexColor);
            }
        }
    }

    @Unique
    private static void pohtomc$vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float u, float v, float z, int nx, int ny, int nz, int lightCoords) {
        pohtomc$vertex(pose, buffer, x, y, u, v, z, nx, ny, nz, lightCoords, -1);
    }

    @Unique
    private static void pohtomc$vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float u, float v, float z, int nx, int ny, int nz, int lightCoords, int color) {
        buffer.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(lightCoords)
                .setNormal(pose, (float) nx, (float) ny, (float) nz);
    }
}
