package org.lts.litematicaplus.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.lts.litematicaplus.preview.PreviewRaster;
import org.lts.litematicaplus.preview.PreviewService;

final class PreviewWidget extends AbstractWidget {
    private static final double ROTATE_SPEED = 0.012;
    private static final double KEY_STEP = Math.toRadians(15.0);

    private PreviewService.Source source;
    private PreviewRaster.View view;
    private boolean dragged;

    PreviewWidget(int x, int y, int width, int height, PreviewService.Source source, String name) {
        super(x, y, width, height, Component.translatable("litematicaplus.preview.narration", name));
        this.source = source;
        this.visible = source != null;
        this.view = source == null ? PreviewRaster.View.DEFAULT : PreviewService.view(source);
        setTooltip(Tooltip.create(Component.translatable("litematicaplus.preview.controls")));
    }

    void setSource(PreviewService.Source next, String name) {
        this.source = next;
        this.visible = next != null;
        this.dragged = false;
        if (next != null) {
            this.view = PreviewService.view(next);
            setMessage(Component.translatable("litematicaplus.preview.narration", name));
        }
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();
        int width = getWidth();
        int height = getHeight();
        graphics.fill(x, y, x + width, y + height, Theme.TRACK);
        int border = isFocused() ? Theme.FOCUS : isHovered() ? Theme.BORDER : Theme.DIVIDER;
        graphics.outline(x, y, width, height, border);
        Font font = Minecraft.getInstance().font;
        PreviewService.Entry entry = PreviewService.get(this.source);
        if (entry.status() == PreviewService.Status.READY && entry.texture() != null) {
            int side = Math.min(width, height) - 2;
            int imageX = x + (width - side) / 2;
            int imageY = y + (height - side) / 2;
            int size = entry.textureSize();
            graphics.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
            graphics.blit(RenderPipelines.GUI_TEXTURED, entry.texture(), imageX, imageY, 0.0F, 0.0F, side, side, size,
                    size, size, size);
            graphics.disableScissor();
            if (entry.simplified()) {
                Component note = Component.translatable("litematicaplus.preview.simplified");
                String text = font.plainSubstrByWidth(note.getString(), width - 10);
                graphics.fill(x + 1, y + height - 12, x + 7 + font.width(text), y + height - 1, Theme.alpha(Theme.SURFACE, 0xD0));
                graphics.text(font, text, x + 4, y + height - 10, Theme.WARNING, false);
            }
            return;
        }
        String key = switch (entry.status()) {
            case FAILED -> "litematicaplus.preview.failed";
            case EMPTY -> "litematicaplus.preview.empty";
            default -> "litematicaplus.preview.generating";
        };
        int color = entry.status() == PreviewService.Status.FAILED ? Theme.DANGER : Theme.TEXT_DIM;
        Component text = Component.translatable(key);
        graphics.textWithWordWrap(font, text, x + 6, y + height / 2 - 4, width - 12, color, false);
    }

    @Override
    protected boolean isValidClickButton(MouseButtonInfo info) {
        return info.button() == InputConstants.MOUSE_BUTTON_LEFT || info.button() == InputConstants.MOUSE_BUTTON_RIGHT;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        this.dragged = false;
        if (doubleClick && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            apply(PreviewRaster.View.DEFAULT, false);
        }
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dx, double dy) {
        this.dragged = true;
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            double side = Math.max(1.0, Math.min(getWidth(), getHeight()));
            apply(this.view.panned(dx / side, dy / side), true);
        } else {
            apply(this.view.rotated(-dx * ROTATE_SPEED, dy * ROTATE_SPEED), true);
        }
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        if (this.dragged) {
            this.dragged = false;
            apply(this.view, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.visible || !isMouseOver(mouseX, mouseY) || scrollY == 0.0) {
            return false;
        }
        apply(this.view.zoomed(Math.pow(1.15, scrollY)), false);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!isFocused() || this.source == null) {
            return false;
        }
        PreviewRaster.View next = switch (event.key()) {
            case InputConstants.KEY_LEFT -> this.view.rotated(KEY_STEP, 0.0);
            case InputConstants.KEY_RIGHT -> this.view.rotated(-KEY_STEP, 0.0);
            case InputConstants.KEY_UP -> this.view.rotated(0.0, KEY_STEP);
            case InputConstants.KEY_DOWN -> this.view.rotated(0.0, -KEY_STEP);
            case InputConstants.KEY_EQUALS, InputConstants.KEY_ADD -> this.view.zoomed(1.15);
            case InputConstants.KEY_MINUS -> this.view.zoomed(1.0 / 1.15);
            case InputConstants.KEY_0, InputConstants.KEY_R -> PreviewRaster.View.DEFAULT;
            default -> null;
        };
        if (next == null) {
            return false;
        }
        apply(next, false);
        return true;
    }

    private void apply(PreviewRaster.View next, boolean dragging) {
        if (this.source == null) {
            return;
        }
        this.view = next;
        PreviewService.setView(this.source, next, dragging);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
        output.add(NarratedElementType.USAGE, Component.translatable("litematicaplus.preview.controls"));
    }
}
