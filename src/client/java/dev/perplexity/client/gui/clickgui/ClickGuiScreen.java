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
                Panel panel = panels.get(index);
                float slotX = startX + column * (GlassTheme.PANEL_WIDTH + GlassTheme.PANEL_SPACING);
                // Slots are still computed for every panel, so the grid stays put as panels are
                // dragged out of it — but a panel the user has placed is left where they dropped it.
                if (draggingPanel != index && !panel.isPlaced()) {
                    panel.setPosition(slotX, y);
                }
            }
            y += rowHeights[row] + GlassTheme.PANEL_SPACING;
        }
    }

    private void drawWatermark() {
        VGRenderer.textLeft("perplexity", 12.0F, 16.0F, 10.0F, VGFont.semiboldFace(), 0.6F,
                GlassTheme.headerText());
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
        // Tested against where the panel actually is, not its grid slot: a placed panel no longer
        // lives at its slot, and testing the slot would make it impossible to pick up again.
        Panel panel = panels.get(index);
        if (button == 0 && GlassTheme.inside(mouseX, mouseY, panel.getX(), panel.getY(),
                panel.getWidth(), GlassTheme.PANEL_HEADER_HEIGHT)) {
            draggingPanel = index;
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        for (Panel panel : panels) {
            panel.mouseReleased();
        }
        if (draggingPanel >= 0 && draggingPanel < panels.size()) {
            Panel panel = panels.get(draggingPanel);
            // Only a press that actually moved the panel pins it to its new spot. A plain click on a
            // header must leave the panel grid-managed, or merely clicking would stop it reflowing.
            if (panel.consumeMoved()) {
                clampToScreen(panel);
                panel.setPlaced(true);
            }
        }
        draggingPanel = -1;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        for (Panel panel : panels) {
            panel.mouseDragged(event.x(), event.y(), event.button());
        }
        if (draggingPanel >= 0 && draggingPanel < panels.size()) {
            clampToScreen(panels.get(draggingPanel));
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    /**
     * Keeps a dragged panel on screen. Positions now persist, so a panel dragged past an edge would
     * otherwise be lost with no way to grab it again.
     */
    private void clampToScreen(Panel panel) {
        float maxX = Math.max(0.0F, width - panel.getWidth());
        float maxY = Math.max(0.0F, height - GlassTheme.PANEL_HEADER_HEIGHT);
        panel.setX(Math.max(0.0F, Math.min(maxX, panel.getX())));
        panel.setY(Math.max(0.0F, Math.min(maxY, panel.getY())));
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
