package com.pohtomc.client.mixin;

import com.pohtomc.client.duck.UrlPaintingRenderStateAccess;
import net.minecraft.client.renderer.entity.state.PaintingRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PaintingRenderState.class)
public class PaintingRenderStateMixin implements UrlPaintingRenderStateAccess {
    @Unique
    private String pohtomc$imageUrl = "";
    @Unique
    private int pohtomc$customWidth = 0;
    @Unique
    private int pohtomc$customHeight = 0;
    @Unique
    private boolean pohtomc$preserveAspect = false;
    @Unique
    private int pohtomc$glowPower = 0;

    @Override
    public String pohtomc$getImageUrl() {
        return this.pohtomc$imageUrl;
    }

    @Override
    public void pohtomc$setImageUrl(String url) {
        this.pohtomc$imageUrl = url == null ? "" : url;
    }

    @Override
    public int pohtomc$getCustomWidth() {
        return this.pohtomc$customWidth;
    }

    @Override
    public void pohtomc$setCustomWidth(int width) {
        this.pohtomc$customWidth = width;
    }

    @Override
    public int pohtomc$getCustomHeight() {
        return this.pohtomc$customHeight;
    }

    @Override
    public void pohtomc$setCustomHeight(int height) {
        this.pohtomc$customHeight = height;
    }

    @Override
    public boolean pohtomc$isPreserveAspect() {
        return this.pohtomc$preserveAspect;
    }

    @Override
    public void pohtomc$setPreserveAspect(boolean preserveAspect) {
        this.pohtomc$preserveAspect = preserveAspect;
    }

    @Override
    public int pohtomc$getGlowPower() {
        return this.pohtomc$glowPower;
    }

    @Override
    public void pohtomc$setGlowPower(int glowPower) {
        this.pohtomc$glowPower = Math.clamp(glowPower, 0, 200);
    }

    @Override
    public boolean pohtomc$isGlow() {
        return this.pohtomc$glowPower > 0;
    }

    @Override
    public void pohtomc$setGlow(boolean glow) {
        this.pohtomc$glowPower = glow ? 100 : 0;
    }
}
