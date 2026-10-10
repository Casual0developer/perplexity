package dev.perplexity.client.gui.clickgui;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.ModuleManager;
import dev.perplexity.client.render.vg.VGFont;
import dev.perplexity.client.render.vg.VGRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The glass ClickGUI. Everything is drawn with the ported NanoVG renderer inside a single frame that
 * a mixin runs after Minecraft submits its own GUI (see {@link ClickGuiOverlay}). Panels are laid out
 * in a centred grid with a shared scroll.
 */
public final class ClickGuiScreen extends Screen {
    private static final float GRID_MARGIN = 24.0F;
    private static final float GLOBAL_SCROLL_SPEED = 60.0F;

    private final Backdrop backdrop = new Backdrop();
    private final List<Panel> panels = new ArrayList<>();
    private boolean built;
    private float[] slotX = new float[0];
    private float[] slotY = new float[0];

    private float globalScroll;
    private float maxGlobalScroll;
    private int draggingPanel = -1;
    /** Index of the front-most panel. The list order never changes; this only drives draw order. */
    private int focusedPanel = -1;

    private float lastMouseX;
    private float lastMouseY;

    public ClickGuiScreen() {
        super(Component.literal("ClickGui"));
    }

    @Override
    protected void init() {
        if (built) {
            return;
        }
        built = true;
        for (Category category : Category.values()) {
            // Every category keeps its panel even while it has no modules, so the grid stays stable
            // and a category is always in the same place once a module is registered for it.
            panels.add(new Panel(category, ModuleManager.get().byCategory(category), 0.0F, GlassTheme.INITIAL_Y));
        }
        slotX = new float[panels.size()];
        slotY = new float[panels.size()];
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (ClickGuiConfig.blur) {
            renderBlurredBackground(guiGraphics);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        init();

        // 1.21.9+ only *collects* GUI draw state here; the real GPU submission happens later in
        // GuiRenderer.render, so the VG frame is drawn from ClickGuiOverlay instead.
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        GlassTheme.setDark(ClickGuiConfig.dark);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    /** Draws the whole ClickGUI with NanoVG; must run with the main render target bound. */
    public void drawVg() {
        float scale = Minecraft.getInstance().getWindow().getGuiScale();
        if (!VGRenderer.beginFrame()) {
            return;
        }
        try {
            VGRenderer.save();
            VGRenderer.scale(scale, scale);

            VGRenderer.rect(0.0F, 0.0F, width, height, GlassTheme.scrim());
            backdrop.render(width, height);
            drawWatermark();

            layoutPanels();
            // Draw back-to-front with the focused panel last, so raising one never reflows the grid.
            for (int i = 0; i < panels.size(); i++) {
                if (i != focusedPanel) {
                    panels.get(i).render(lastMouseX, lastMouseY);
                }
            }
            if (focusedPanel >= 0 && focusedPanel < panels.size()) {
                panels.get(focusedPanel).render(lastMouseX, lastMouseY);
            }

            drawHint();

            VGRenderer.restore();
        } finally {
            VGRenderer.endFrame();
        }
    }

    private void layoutPanels() {
        int columns = Math.max(1, (int) ((width - GRID_MARGIN) / (GlassTheme.PANEL_WIDTH + GlassTheme.PANEL_SPACING)));
        columns = Math.min(columns, Math.max(1, panels.size()));

        int rows = (panels.size() + columns - 1) / columns;
        float[] rowHeights = new float[rows];
        for (int i = 0; i < panels.size(); i++) {
            int row = i / columns;
            rowHeights[row] = Math.max(rowHeights[row], panels.get(i).getHeight());
        }

        float totalHeight = 0.0F;
        for (float rowHeight : rowHeights) {
            totalHeight += rowHeight + GlassTheme.PANEL_SPACING;
        }
        maxGlobalScroll = Math.max(0.0F, (GlassTheme.INITIAL_Y + totalHeight) - height + 20.0F);
        globalScroll = Math.max(0.0F, Math.min(globalScroll, maxGlobalScroll));

        float y = GlassTheme.INITIAL_Y - globalScroll;
        for (int row = 0; row < rows; row++) {
            int first = row * columns;
            int count = Math.min(columns, panels.size() - first);
            float rowWidth = count * GlassTheme.PANEL_WIDTH + (count - 1) * GlassTheme.PANEL_SPACING;
            float startX = (width - rowWidth) / 2.0F;

            for (int column = 0; column < count; column++) {
                int index = first + column;
                slotX[index] = startX + column * (GlassTheme.PANEL_WIDTH + GlassTheme.PANEL_SPACING);
                slotY[index] = y;
                if (draggingPanel != index) {
                    panels.get(index).setPosition(slotX[index], slotY[index]);
                }
            }
            y += rowHeights[row] + GlassTheme.PANEL_SPACING;
        }
    }

    private void drawWatermark() {
        VGRenderer.textLeft("perplexity", 12.0F, 16.0F, 10.0F, VGFont.semiboldFace(), 0.6F,
                GlassTheme.headerText());
    }

    private void drawHint() {
        String hint = "RightShift to close";
        float textWidth = VGRenderer.textWidth(hint, GlassTheme.FONT_HINT, VGFont.FACE, 0.0F);
        VGRenderer.textLeft(hint, width - textWidth - 12.0F, 16.0F, GlassTheme.FONT_HINT,
                VGFont.FACE, GlassTheme.textMuted());
    }

    // --- Input ---------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        // Front-to-back hit test: the focused panel gets first refusal, then the rest top-down.
        if (focusedPanel >= 0 && focusedPanel < panels.size()
                && panels.get(focusedPanel).mouseClicked(mouseX, mouseY, button)) {
            focus(focusedPanel, mouseX, mouseY, button);
            return true;
        }
        for (int i = panels.size() - 1; i >= 0; i--) {
            if (i == focusedPanel) {
                continue;
            }
            if (panels.get(i).mouseClicked(mouseX, mouseY, button)) {
                focus(i, mouseX, mouseY, button);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void focus(int index, double mouseX, double mouseY, int button) {
        focusedPanel = index;
        if (button == 0 && GlassTheme.inside(mouseX, mouseY, slotX[index], slotY[index],
                GlassTheme.PANEL_WIDTH, GlassTheme.PANEL_HEADER_HEIGHT)) {
            draggingPanel = index;
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        for (Panel panel : panels) {
            panel.mouseReleased();
        }
        if (draggingPanel >= 0 && draggingPanel < panels.size()) {
            panels.get(draggingPanel).setPosition(slotX[draggingPanel], slotY[draggingPanel]);
        }
        draggingPanel = -1;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        for (Panel panel : panels) {
            panel.mouseDragged(event.x(), event.y(), event.button());
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (focusedPanel >= 0 && focusedPanel < panels.size()
                && panels.get(focusedPanel).mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        for (int i = panels.size() - 1; i >= 0; i--) {
            if (i == focusedPanel) {
                continue;
            }
            if (panels.get(i).mouseScrolled(mouseX, mouseY, scrollY)) {
                return true;
            }
        }
        if (scrollY != 0.0 && maxGlobalScroll > 0.0F) {
            globalScroll = Math.max(0.0F, Math.min(maxGlobalScroll,
                    globalScroll - (float) scrollY * GLOBAL_SCROLL_SPEED));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
