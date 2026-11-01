package com.pohtomc.client.duck;

public interface UrlPaintingRenderStateAccess {
    String pohtomc$getImageUrl();

    void pohtomc$setImageUrl(String url);

    int pohtomc$getCustomWidth();

    void pohtomc$setCustomWidth(int width);

    int pohtomc$getCustomHeight();

    void pohtomc$setCustomHeight(int height);

    boolean pohtomc$isPreserveAspect();

    void pohtomc$setPreserveAspect(boolean preserveAspect);

    int pohtomc$getGlowPower();

    void pohtomc$setGlowPower(int glowPower);

    default boolean pohtomc$isGlow() {
        return pohtomc$getGlowPower() > 0;
    }

    default void pohtomc$setGlow(boolean glow) {
        pohtomc$setGlowPower(glow ? 100 : 0);
    }
}
