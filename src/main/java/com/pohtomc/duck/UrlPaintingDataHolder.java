package com.pohtomc.duck;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.entity.player.Player;

public interface UrlPaintingDataHolder {
    int MIN_SIZE = 1;
    int MAX_SIZE = 16;
    int MAX_URL_LENGTH = 1024;

    String pohtomc$getImageUrl();

    void pohtomc$setImageUrl(String url);

    int pohtomc$getCustomWidth();

    void pohtomc$setCustomWidth(int width);

    int pohtomc$getCustomHeight();

    void pohtomc$setCustomHeight(int height);

    int pohtomc$getEffectiveWidth();

    int pohtomc$getEffectiveHeight();

    boolean pohtomc$isLocked();

    void pohtomc$setLocked(boolean locked);

    String pohtomc$getOwnerUuid();

    void pohtomc$setOwnerUuid(String ownerUuid);

    String pohtomc$getOwnerName();

    void pohtomc$setOwnerName(String ownerName);

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

    boolean pohtomc$hasCustomData();

    void pohtomc$refreshBoundingBox();

    boolean pohtomc$isOwnerOrOp(Player player);

    boolean pohtomc$canEdit(Player player);

    void pohtomc$setVariant(Holder<PaintingVariant> variant);
}
