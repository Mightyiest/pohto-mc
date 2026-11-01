package com.pohtomc.client.gui;

import com.pohtomc.PohtoMcMod;
import com.pohtomc.client.texture.UrlTextureCache;
import com.pohtomc.network.OpenUrlPaintingScreenPayload;
import com.pohtomc.network.UpdateUrlPaintingPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class UrlPaintingScreen extends Screen {
    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 236;

    private final int entityId;
    private String currentUrl;
    private int paintingWidth;
    private int paintingHeight;
    private boolean locked;
    private int glowPower;
    private final String ownerName;
    private final boolean canChangeLock;

    private EditBox urlEditBox;
    private GlowSlider glowSlider;
    private Button lockButton;

    public UrlPaintingScreen(OpenUrlPaintingScreenPayload payload) {
        super(Component.translatable("gui.pohtomc.url_painting.title"));
        this.entityId = payload.entityId();
        this.currentUrl = payload.imageUrl() == null ? "" : payload.imageUrl();
        this.paintingWidth = Mth.clamp(payload.width(), 1, 16);
        this.paintingHeight = Mth.clamp(payload.height(), 1, 16);
        this.locked = payload.locked();
        this.glowPower = Mth.clamp(payload.glowPower(), 0, 200);
        this.ownerName = payload.ownerName() == null ? "" : payload.ownerName();
        this.canChangeLock = payload.canChangeLock();
    }

    @Override
    protected void init() {
        UrlTextureCache.clearIfFailed(this.currentUrl);

        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;

        this.urlEditBox = new EditBox(this.font, left + 14, top + 38, 218, 20, Component.translatable("gui.pohtomc.url_painting.url_label"));
        this.urlEditBox.setMaxLength(1024);
        this.urlEditBox.setHint(Component.literal("https://example.com/image or direct image URL").withStyle(ChatFormatting.DARK_GRAY));
        this.urlEditBox.setValue(this.currentUrl);
        this.urlEditBox.setResponder(val -> this.currentUrl = val);
        this.addRenderableWidget(this.urlEditBox);
        this.setInitialFocus(this.urlEditBox);

        this.addRenderableWidget(Button.builder(Component.literal("Paste"), btn -> {
            if (this.minecraft != null && this.minecraft.keyboardHandler != null) {
                String clip = this.minecraft.keyboardHandler.getClipboard();
                if (clip != null && !clip.isBlank()) {
                    this.urlEditBox.setValue(clip.trim());
                }
            }
        }).bounds(left + PANEL_WIDTH - 84, top + 38, 38, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Clear"), btn -> {
            this.urlEditBox.setValue("");
        }).bounds(left + PANEL_WIDTH - 43, top + 38, 30, 20).build());

        int controlsLeft = left + 14;
        int row1Y = top + 74;
        int row2Y = top + 102;
        int row3Y = top + 130;
        int row4Y = top + 156;

        this.addRenderableWidget(Button.builder(Component.literal("-"), btn -> {
            this.paintingWidth = Mth.clamp(this.paintingWidth - 1, 1, 16);
        }).bounds(controlsLeft + 62, row1Y, 22, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("+"), btn -> {
            this.paintingWidth = Mth.clamp(this.paintingWidth + 1, 1, 16);
        }).bounds(controlsLeft + 130, row1Y, 22, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("-"), btn -> {
            this.paintingHeight = Mth.clamp(this.paintingHeight - 1, 1, 16);
        }).bounds(controlsLeft + 62, row2Y, 22, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("+"), btn -> {
            this.paintingHeight = Mth.clamp(this.paintingHeight + 1, 1, 16);
        }).bounds(controlsLeft + 130, row2Y, 22, 20).build());

        double initialNormalized = (double) this.glowPower / 200.0;
        this.glowSlider = this.addRenderableWidget(new GlowSlider(controlsLeft, row3Y, 152, 20, initialNormalized));

        this.lockButton = this.addRenderableWidget(Button.builder(this.getLockButtonLabel(), btn -> {
            if (this.canChangeLock) {
                this.locked = !this.locked;
                btn.setMessage(this.getLockButtonLabel());
            }
        }).bounds(controlsLeft, row4Y, 152, 20).build());
        this.lockButton.active = this.canChangeLock;

        int bottomY = top + PANEL_HEIGHT - 30;

        this.addRenderableWidget(Button.builder(Component.literal("Save & Apply").withStyle(ChatFormatting.GREEN), btn -> {
            this.saveAndClose();
        }).bounds(left + 14, bottomY, 114, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Reload Image"), btn -> {
            UrlTextureCache.reload(this.currentUrl);
        }).bounds(left + 134, bottomY, 92, 20).build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, btn -> {
            this.onClose();
        }).bounds(left + 232, bottomY, 74, 20).build());
    }

    private class GlowSlider extends AbstractSliderButton {
        public GlowSlider(int x, int y, int width, int height, double initialValue) {
            super(x, y, width, height, Component.empty(), initialValue);
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            int percent = (int) Math.round(this.value * 200.0);
            if (percent <= 0) {
                this.setMessage(Component.literal("Glow: ").append(Component.literal("OFF").withStyle(ChatFormatting.GRAY)));
            } else if (percent <= 100) {
                this.setMessage(Component.literal("Glow: ").append(Component.literal(percent + "% (Emissive)").withStyle(ChatFormatting.GOLD)));
            } else {
                this.setMessage(Component.literal("Glow: ").append(Component.literal(percent + "% (Vibrant)").withStyle(ChatFormatting.YELLOW)));
            }
        }

        @Override
        protected void applyValue() {
            UrlPaintingScreen.this.glowPower = (int) Math.round(this.value * 200.0);
        }
    }

    private Component getLockButtonLabel() {
        return Component.literal("Edit Lock: " + (this.locked ? "Owner/OP Only" : "Unlocked"))
                .withStyle(this.locked ? ChatFormatting.GOLD : ChatFormatting.AQUA);
    }

    private void saveAndClose() {
        String trimmedUrl = this.currentUrl == null ? "" : this.currentUrl.trim();
        if (!trimmedUrl.isEmpty() && PohtoMcMod.isValidHttpUrl(trimmedUrl)) {
            UrlTextureCache.clearIfFailed(trimmedUrl);
            UrlTextureCache.getOrLoad(trimmedUrl);
        }
        ClientPlayNetworking.send(new UpdateUrlPaintingPayload(
                this.entityId,
                trimmedUrl,
                this.paintingWidth,
                this.paintingHeight,
                this.locked,
                this.glowPower
        ));
        this.onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;

        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xE010101C);
        graphics.outline(left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFF4A4E68);
        graphics.fill(left + 1, top + 1, left + PANEL_WIDTH - 1, top + 22, 0xFF1F2230);

        graphics.text(this.font, this.title, left + 10, top + 7, -1, true);

        if (!this.ownerName.isEmpty()) {
            String ownerBadge = "Owner: " + this.ownerName;
            int ownerW = this.font.width(ownerBadge);
            graphics.text(this.font, ownerBadge, left + PANEL_WIDTH - ownerW - 10, top + 7, 0xFFAAAAAA, true);
        }

        graphics.text(this.font, Component.literal("Image URL:"), left + 14, top + 26, 0xFFD0D6E2, true);

        int controlsLeft = left + 14;
        int row1Y = top + 74;
        int row2Y = top + 102;

        graphics.text(this.font, "Width:", controlsLeft, row1Y + 6, 0xFFD0D6E2, true);
        graphics.centeredText(this.font, this.paintingWidth + " blk", controlsLeft + 107, row1Y + 6, -1);

        graphics.text(this.font, "Height:", controlsLeft, row2Y + 6, 0xFFD0D6E2, true);
        graphics.centeredText(this.font, this.paintingHeight + " blk", controlsLeft + 107, row2Y + 6, -1);

        int previewBoxX = left + 176;
        int previewBoxY = top + 72;
        int previewBoxW = 130;
        int previewBoxH = 106;

        graphics.fill(previewBoxX, previewBoxY, previewBoxX + previewBoxW, previewBoxY + previewBoxH, 0xFF0C0E14);

        int borderCol;
        if (this.glowPower <= 0) {
            borderCol = 0xFF36394E;
        } else if (this.glowPower <= 100) {
            borderCol = 0xFFFFAA00;
        } else {
            borderCol = 0xFFFFFF55;
        }
        graphics.outline(previewBoxX, previewBoxY, previewBoxW, previewBoxH, borderCol);

        this.renderPreviewAndStatus(graphics, previewBoxX, previewBoxY, previewBoxW, previewBoxH, left + 14, top + 184);

        graphics.nextStratum();
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPreviewAndStatus(GuiGraphicsExtractor graphics, int boxX, int boxY, int boxW, int boxH, int statusX, int statusY) {
        String trimmed = this.currentUrl == null ? "" : this.currentUrl.trim();
        if (trimmed.isEmpty()) {
            graphics.centeredText(this.font, "No URL Set", boxX + boxW / 2, boxY + boxH / 2 - 4, 0xFF777777);
            graphics.text(this.font, "Paste a direct image URL above.", statusX, statusY, 0xFF999999, true);
            return;
        }

        if (!PohtoMcMod.isValidHttpUrl(trimmed)) {
            graphics.centeredText(this.font, "Invalid URL", boxX + boxW / 2, boxY + boxH / 2 - 4, 0xFFFF5555);
            graphics.text(this.font, "URL must start with http:// or https://", statusX, statusY, 0xFFFF6666, true);
            return;
        }

        UrlTextureCache.TextureEntry entry = UrlTextureCache.getOrLoad(trimmed);
        if (entry == null || entry.status() == UrlTextureCache.Status.LOADING) {
            graphics.centeredText(this.font, "Loading...", boxX + boxW / 2, boxY + boxH / 2 - 4, -13244);
            graphics.text(this.font, "Downloading image from URL...", statusX, statusY, -13244, true);
            return;
        }

        if (entry.status() == UrlTextureCache.Status.FAILED || entry.textureId() == null) {
            graphics.centeredText(this.font, "Load Failed", boxX + boxW / 2, boxY + boxH / 2 - 4, 0xFFFF5555);
            String err = entry.errorMessage();
            if (err != null && err.contains("HTML")) {
                graphics.text(this.font, "Error: URL is a webpage, not a direct image!", statusX, statusY - 4, 0xFFFF6666, true);
                graphics.text(this.font, "Tip: Right-click image -> 'Copy Image Address'", statusX, statusY + 6, 0xFFFFAA00, true);
                return;
            }
            int maxDisplayWidth = 292;
            String fullErr = "Error: " + err;
            if (this.font.width(fullErr) <= maxDisplayWidth) {
                graphics.text(this.font, fullErr, statusX, statusY, 0xFFFF6666, true);
            } else {
                int splitIdx = err.lastIndexOf(' ', Math.min(err.length() - 1, 32));
                if (splitIdx > 0) {
                    graphics.text(this.font, "Error: " + err.substring(0, splitIdx), statusX, statusY - 4, 0xFFFF6666, true);
                    graphics.text(this.font, err.substring(splitIdx + 1), statusX, statusY + 6, 0xFFFF6666, true);
                } else {
                    graphics.text(this.font, fullErr.substring(0, Math.min(fullErr.length(), 48)) + "...", statusX, statusY, 0xFFFF6666, true);
                }
            }
            return;
        }

        // Live animated frame binding and glow power reflection in preview
        entry.bindFrameForRender(this.glowPower);

        int pad = 6;
        int maxW = boxW - pad * 2;
        int maxH = boxH - pad * 2;
        int imgW = Math.max(1, entry.imageWidth());
        int imgH = Math.max(1, entry.imageHeight());
        double scale = Math.min((double) maxW / (double) imgW, (double) maxH / (double) imgH);
        int drawW = Math.max(1, (int) Math.round((double) imgW * scale));
        int drawH = Math.max(1, (int) Math.round((double) imgH * scale));
        int drawX0 = boxX + (boxW - drawW) / 2;
        int drawY0 = boxY + (boxH - drawH) / 2;

        graphics.blit(entry.textureId(), drawX0, drawY0, drawX0 + drawW, drawY0 + drawH, 0.0F, 1.0F, 0.0F, 1.0F);
        if (this.glowPower > 0) {
            var emissiveId = entry.getEmissiveTextureId(this.glowPower);
            if (emissiveId != null) {
                graphics.blit(emissiveId, drawX0, drawY0, drawX0 + drawW, drawY0 + drawH, 0.0F, 1.0F, 0.0F, 1.0F);
            }
        }

        String loadedInfo;
        if (entry.isAnimated()) {
            loadedInfo = "GIF (" + entry.animation().frames.size() + " frames, " + entry.imageWidth() + "x" + entry.imageHeight() + " px)";
        } else {
            loadedInfo = "Loaded (" + entry.imageWidth() + "x" + entry.imageHeight() + " px)";
        }
        if (this.glowPower > 0) {
            loadedInfo += " [Glow " + this.glowPower + "%]";
        }

        int textCol;
        if (this.glowPower <= 0) {
            textCol = 0xFF55FF55;
        } else if (this.glowPower <= 100) {
            textCol = 0xFFFFAA00;
        } else {
            textCol = 0xFFFFFF55;
        }
        graphics.text(this.font, loadedInfo, statusX, statusY, textCol, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
