package com.pohtomc.mixin;

import com.pohtomc.PohtoMcMod;
import com.pohtomc.duck.UrlPaintingDataHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(Painting.class)
public abstract class PaintingMixin extends HangingEntity implements UrlPaintingDataHolder {
    @Shadow
    public abstract Holder<PaintingVariant> getVariant();

    @Shadow
    protected abstract void setVariant(Holder<PaintingVariant> variant);

    @Unique
    private String pohtomc$imageUrl = "";
    @Unique
    private int pohtomc$customWidth = 0;
    @Unique
    private int pohtomc$customHeight = 0;
    @Unique
    private boolean pohtomc$locked = false;
    @Unique
    private String pohtomc$ownerUuid = "";
    @Unique
    private String pohtomc$ownerName = "";
    @Unique
    private boolean pohtomc$preserveAspect = false;
    @Unique
    private int pohtomc$glowPower = 0;

    protected PaintingMixin(EntityType<? extends HangingEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public String pohtomc$getImageUrl() {
        return this.pohtomc$imageUrl;
    }

    @Override
    public void pohtomc$setImageUrl(String url) {
        String sanitized = url == null ? "" : url.trim();
        if (sanitized.length() > 1024) {
            sanitized = sanitized.substring(0, 1024);
        }
        this.pohtomc$imageUrl = sanitized;
    }

    @Override
    public int pohtomc$getCustomWidth() {
        return this.pohtomc$customWidth;
    }

    @Override
    public void pohtomc$setCustomWidth(int width) {
        this.pohtomc$customWidth = width <= 0 ? 0 : Mth.clamp(width, 1, 16);
    }

    @Override
    public int pohtomc$getCustomHeight() {
        return this.pohtomc$customHeight;
    }

    @Override
    public void pohtomc$setCustomHeight(int height) {
        this.pohtomc$customHeight = height <= 0 ? 0 : Mth.clamp(height, 1, 16);
    }

    @Override
    public int pohtomc$getEffectiveWidth() {
        if (this.pohtomc$customWidth > 0) {
            return Mth.clamp(this.pohtomc$customWidth, 1, 16);
        }
        Holder<PaintingVariant> holder = this.getVariant();
        if (holder != null && holder.value() != null) {
            return holder.value().width();
        }
        return 1;
    }

    @Override
    public int pohtomc$getEffectiveHeight() {
        if (this.pohtomc$customHeight > 0) {
            return Mth.clamp(this.pohtomc$customHeight, 1, 16);
        }
        Holder<PaintingVariant> holder = this.getVariant();
        if (holder != null && holder.value() != null) {
            return holder.value().height();
        }
        return 1;
    }

    @Override
    public boolean pohtomc$isLocked() {
        return this.pohtomc$locked;
    }

    @Override
    public void pohtomc$setLocked(boolean locked) {
        this.pohtomc$locked = locked;
    }

    @Override
    public String pohtomc$getOwnerUuid() {
        return this.pohtomc$ownerUuid;
    }

    @Override
    public void pohtomc$setOwnerUuid(String ownerUuid) {
        this.pohtomc$ownerUuid = ownerUuid == null ? "" : ownerUuid;
    }

    @Override
    public String pohtomc$getOwnerName() {
        return this.pohtomc$ownerName;
    }

    @Override
    public void pohtomc$setOwnerName(String ownerName) {
        this.pohtomc$ownerName = ownerName == null ? "" : ownerName;
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

    @Override
    public boolean pohtomc$hasCustomData() {
        return !this.pohtomc$imageUrl.isEmpty() || this.pohtomc$customWidth > 0 || this.pohtomc$customHeight > 0 || this.pohtomc$glowPower > 0;
    }

    @Override
    public void pohtomc$refreshBoundingBox() {
        this.recalculateBoundingBox();
    }

    @Override
    public boolean pohtomc$isOwnerOrOp(Player player) {
        if (player == null) {
            return false;
        }
        if (this.pohtomc$ownerUuid.isEmpty() || this.pohtomc$ownerUuid.equals(player.getStringUUID())) {
            return true;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (serverPlayer.level().getServer() != null) {
                return serverPlayer.level().getServer().getPlayerList().isOp(serverPlayer.nameAndId());
            }
        }
        return false;
    }

    @Override
    public boolean pohtomc$canEdit(Player player) {
        if (player == null) {
            return false;
        }
        if (!this.pohtomc$locked) {
            return true;
        }
        return this.pohtomc$isOwnerOrOp(player);
    }

    @Override
    public void pohtomc$setVariant(Holder<PaintingVariant> variant) {
        this.setVariant(variant);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void pohtomc$saveUrlData(ValueOutput output, CallbackInfo ci) {
        if (!this.pohtomc$imageUrl.isEmpty()) {
            output.putString("pohtomc_url", this.pohtomc$imageUrl);
        }
        if (this.pohtomc$customWidth > 0) {
            output.putInt("pohtomc_width", this.pohtomc$customWidth);
        }
        if (this.pohtomc$customHeight > 0) {
            output.putInt("pohtomc_height", this.pohtomc$customHeight);
        }
        output.putBoolean("pohtomc_locked", this.pohtomc$locked);
        if (!this.pohtomc$ownerUuid.isEmpty()) {
            output.putString("pohtomc_owner_uuid", this.pohtomc$ownerUuid);
        }
        if (!this.pohtomc$ownerName.isEmpty()) {
            output.putString("pohtomc_owner_name", this.pohtomc$ownerName);
        }
        if (this.pohtomc$glowPower > 0) {
            output.putInt("pohtomc_glow_power", this.pohtomc$glowPower);
            output.putBoolean("pohtomc_glow", true);
        }
        if (this.pohtomc$preserveAspect) {
            output.putBoolean("pohtomc_preserve_aspect", true);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void pohtomc$loadUrlData(ValueInput input, CallbackInfo ci) {
        this.pohtomc$setImageUrl(input.getStringOr("pohtomc_url", ""));
        this.pohtomc$setCustomWidth(input.getIntOr("pohtomc_width", 0));
        this.pohtomc$setCustomHeight(input.getIntOr("pohtomc_height", 0));
        this.pohtomc$setLocked(input.getBooleanOr("pohtomc_locked", false));
        this.pohtomc$setOwnerUuid(input.getStringOr("pohtomc_owner_uuid", ""));
        this.pohtomc$setOwnerName(input.getStringOr("pohtomc_owner_name", ""));
        int savedPower = input.getIntOr("pohtomc_glow_power", -1);
        if (savedPower >= 0) {
            this.pohtomc$setGlowPower(savedPower);
        } else {
            this.pohtomc$setGlow(input.getBooleanOr("pohtomc_glow", false));
        }
        this.pohtomc$setPreserveAspect(input.getBooleanOr("pohtomc_preserve_aspect", false));
        if (this.pohtomc$customWidth > 0 || this.pohtomc$customHeight > 0) {
            this.recalculateBoundingBox();
        }
    }

    @Inject(method = "calculateBoundingBox", at = @At("HEAD"), cancellable = true)
    private void pohtomc$calculateCustomBoundingBox(BlockPos pos, Direction direction, CallbackInfoReturnable<AABB> cir) {
        if (this.pohtomc$customWidth <= 0 && this.pohtomc$customHeight <= 0) {
            return;
        }
        int width = this.pohtomc$getEffectiveWidth();
        int height = this.pohtomc$getEffectiveHeight();
        Vec3 center = Vec3.atCenterOf(pos).relative(direction, -0.46875);
        double offsetX = width % 2 == 0 ? 0.5 : 0.0;
        double offsetY = height % 2 == 0 ? 0.5 : 0.0;
        Direction ccw = direction.getCounterClockWise();
        Vec3 boxCenter = center.relative(ccw, offsetX).relative(Direction.UP, offsetY);
        Direction.Axis axis = direction.getAxis();
        double sizeX = axis == Direction.Axis.X ? 0.0625 : (double) width;
        double sizeY = (double) height;
        double sizeZ = axis == Direction.Axis.Z ? 0.0625 : (double) width;
        cir.setReturnValue(AABB.ofSize(boxCenter, sizeX, sizeY, sizeZ));
    }

    @Override
    public boolean survives() {
        if (!this.pohtomc$hasCustomData()) {
            return super.survives();
        }
        if (this.level().getBlockState(this.pos).isSolid()) {
            return false;
        }
        Direction direction = this.getDirection();
        if (direction == null) {
            return false;
        }
        BlockPos supportPos = this.pos.relative(direction.getOpposite());
        BlockState supportState = this.level().getBlockState(supportPos);
        return supportState.isSolid() || DiodeBlock.isDiode(supportState);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        Entity entity = source.getEntity();
        if (entity instanceof ServerPlayer serverPlayer) {
            if (!this.pohtomc$canEdit(serverPlayer)) {
                String owner = this.pohtomc$ownerName.isEmpty() ? "another player" : this.pohtomc$ownerName;
                serverPlayer.sendOverlayMessage(Component.literal("Cannot break: this Painting is locked by " + owner + ".").withStyle(ChatFormatting.RED));
                return false;
            }
        }
        return super.hurtServer(level, source, amount);
    }

    @Inject(method = "dropItem", at = @At("HEAD"), cancellable = true)
    private void pohtomc$dropCustomItem(ServerLevel level, Entity entity, CallbackInfo ci) {
        if (!this.pohtomc$hasCustomData()) {
            return;
        }
        if (!level.getGameRules().get(GameRules.ENTITY_DROPS)) {
            ci.cancel();
            return;
        }
        this.playSound(SoundEvents.PAINTING_BREAK, 1.0F, 1.0F);
        ItemStack stack = this.pohtomc$createDroppedItemStack();

        if (entity instanceof Player player && player.hasInfiniteMaterials()) {
            if (!player.getInventory().contains(stack)) {
                player.getInventory().add(stack);
            }
            ci.cancel();
            return;
        }

        Direction direction = this.getDirection();
        double offsetFront = 0.25;
        double spawnX = this.getX() + (direction != null ? direction.getStepX() * offsetFront : 0.0);
        double spawnY = this.getY() + 0.1;
        double spawnZ = this.getZ() + (direction != null ? direction.getStepZ() * offsetFront : 0.0);
        ItemEntity itemEntity = new ItemEntity(level, spawnX, spawnY, spawnZ, stack);
        itemEntity.setDefaultPickUpDelay();
        level.addFreshEntity(itemEntity);
        ci.cancel();
    }

    @Inject(method = "getPickResult", at = @At("HEAD"), cancellable = true)
    private void pohtomc$getCustomPickResult(CallbackInfoReturnable<ItemStack> cir) {
        if (this.pohtomc$hasCustomData()) {
            cir.setReturnValue(this.pohtomc$createDroppedItemStack());
        }
    }

    @Inject(method = "applyImplicitComponents", at = @At("TAIL"))
    private void pohtomc$applyCustomDataFromItem(DataComponentGetter components, CallbackInfo ci) {
        CustomData customData = components.get(DataComponents.CUSTOM_DATA);
        if (customData != null && !customData.isEmpty()) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains("pohtomc_url") || tag.contains("pohtomc_width") || tag.contains("pohtomc_height") || tag.contains("pohtomc_glow_power") || tag.contains("pohtomc_glow")) {
                this.pohtomc$setImageUrl(tag.getStringOr("pohtomc_url", ""));
                this.pohtomc$setCustomWidth(tag.getIntOr("pohtomc_width", 0));
                this.pohtomc$setCustomHeight(tag.getIntOr("pohtomc_height", 0));
                this.pohtomc$setPreserveAspect(tag.getBooleanOr("pohtomc_preserve_aspect", false));
                int savedPower = tag.getIntOr("pohtomc_glow_power", -1);
                if (savedPower >= 0) {
                    this.pohtomc$setGlowPower(savedPower);
                } else {
                    this.pohtomc$setGlow(tag.getBooleanOr("pohtomc_glow", false));
                }
                this.pohtomc$setLocked(false);
                this.pohtomc$setOwnerUuid("");
                this.pohtomc$setOwnerName("");
                if (this.pohtomc$customWidth > 0 || this.pohtomc$customHeight > 0) {
                    this.recalculateBoundingBox();
                    if (this.level() != null) {
                        Holder<PaintingVariant> best = PohtoMcMod.findBestMatchingVariant(this.level(), this.pohtomc$getEffectiveWidth(), this.pohtomc$getEffectiveHeight());
                        if (best != null) {
                            this.setVariant(best);
                        }
                    }
                }
                if (this.level() instanceof ServerLevel serverLevel) {
                    if (this.pos != null) {
                        LevelChunk chunk = serverLevel.getChunkAt(this.pos);
                        chunk.markUnsaved();
                    }
                    if (!this.pohtomc$imageUrl.isEmpty() && serverLevel.getServer() != null) {
                        PohtoMcMod.cacheServerImage(serverLevel.getServer(), this.pohtomc$imageUrl);
                    }
                }
            }
        }
    }

    @Unique
    private ItemStack pohtomc$createDroppedItemStack() {
        ItemStack stack = new ItemStack(Items.PAINTING);
        CompoundTag tag = new CompoundTag();
        if (!this.pohtomc$imageUrl.isEmpty()) {
            tag.putString("pohtomc_url", this.pohtomc$imageUrl);
        }
        if (this.pohtomc$customWidth > 0) {
            tag.putInt("pohtomc_width", this.pohtomc$customWidth);
        }
        if (this.pohtomc$customHeight > 0) {
            tag.putInt("pohtomc_height", this.pohtomc$customHeight);
        }
        tag.putBoolean("pohtomc_preserve_aspect", this.pohtomc$preserveAspect);
        if (!this.pohtomc$ownerName.isEmpty()) {
            tag.putString("pohtomc_owner_name", this.pohtomc$ownerName);
        }
        if (this.pohtomc$glowPower > 0) {
            tag.putInt("pohtomc_glow_power", this.pohtomc$glowPower);
            tag.putBoolean("pohtomc_glow", true);
        }
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);

        int w = this.pohtomc$getEffectiveWidth();
        int h = this.pohtomc$getEffectiveHeight();
        stack.set(DataComponents.ITEM_NAME, Component.literal("URL Painting (" + w + "x" + h + ")").withStyle(ChatFormatting.GOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("Dimensions: " + w + "x" + h + " blocks").withStyle(ChatFormatting.GRAY));
        lore.add(Component.literal("Aspect Ratio: " + (this.pohtomc$preserveAspect ? "Fit (Preserve Aspect)" : "Stretch (Fill)")).withStyle(ChatFormatting.GRAY));
        if (this.pohtomc$glowPower > 0) {
            lore.add(Component.literal("Glow: " + this.pohtomc$glowPower + "%").withStyle(ChatFormatting.YELLOW));
        }
        if (!this.pohtomc$ownerName.isEmpty()) {
            lore.add(Component.literal("Created by: " + this.pohtomc$ownerName).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (!this.pohtomc$imageUrl.isEmpty()) {
            String displayUrl = this.pohtomc$imageUrl;
            if (displayUrl.length() > 42) {
                displayUrl = displayUrl.substring(0, 39) + "...";
            }
            lore.add(Component.literal("URL: " + displayUrl).withStyle(ChatFormatting.DARK_AQUA));
        }
        stack.set(DataComponents.LORE, new ItemLore(lore));

        Holder<PaintingVariant> variant = this.getVariant();
        if (variant != null) {
            stack.set(DataComponents.PAINTING_VARIANT, variant);
        }
        return stack;
    }
}
