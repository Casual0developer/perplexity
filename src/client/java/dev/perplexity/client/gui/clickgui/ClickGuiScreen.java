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

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * The glass ClickGUI. Everything is drawn with the ported NanoVG renderer inside a single frame that
 * a mixin runs after Minecraft submits its own GUI (see {@link ClickGuiOverlay}). Panels are laid out
 * in a centred grid with a shared scroll, matching the reference client.
 */
public final class ClickGuiScreen extends Screen {
    private static final float GRID_MARGIN = 24.0F;
    private static final float GLOBAL_SCROLL_SPEED = 60.0F;

    private static final float DOCK_HEIGHT = 30.0F;
    private static final float DOCK_BUTTON = 24.0F;
    private static final float DOCK_SPACING = 3.0F;
    private static final float DOCK_PADDING = 3.0F;
    private static final float DOCK_MARGIN_BOTTOM = 10.0F;

    private final List<Panel> panels = new ArrayList<>();
    private float[] slotX = new float[0];
    private float[] slotY = new float[0];

    private DockTab activeTab = DockTab.FEATURES;
    private final float[] dockButtonX = new float[DockTab.values().length];
    private int hoveredDock = -1;

    private float globalScroll;
    private float maxGlobalScroll;
    private int draggingPanel = -1;

    private float lastMouseX;
    private float lastMouseY;

    public ClickGuiScreen() {
        super(Component.literal("ClickGui"));
    }

    @Override
    protected void init() {
        if (!panels.isEmpty()) {
            return;
        }
        for (Category category : Category.values()) {
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
            drawWatermark();

            if (activeTab == DockTab.FEATURES) {
                layoutPanels();
                for (Panel panel : panels) {
                    panel.render(lastMouseX, lastMouseY);
                }
            } else if (activeTab == DockTab.CONFIGS) {
                drawPlaceholder("Configs", lastMouseX, lastMouseY);
            } else {
                drawPlaceholder("Friends", lastMouseX, lastMouseY);
            }

            if (ClickGuiConfig.dock) {
                drawDock(lastMouseX, lastMouseY);
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
        VGRenderer.text("perplexity", 12.0F, 10.0F, 9.0F, VGFont.semiboldFace(), 0.0F, 0.0F,
                GlassTheme.textSecondary());
    }

    private void drawHint() {
        String hint = "RightShift to close";
        float textWidth = VGRenderer.textWidth(hint, GlassTheme.FONT_HINT, VGFont.FACE, 0.0F);
        VGRenderer.text(hint, width - textWidth - 12.0F, 13.0F, GlassTheme.FONT_HINT, VGFont.FACE, 0.0F, 0.0F,
                GlassTheme.textMuted());
    }

    private void drawPlaceholder(String title, float mouseX, float mouseY) {
        float panelWidth = GlassTheme.PANEL_WIDTH;
        float x = (width - panelWidth) / 2.0F;
        float y = GlassTheme.INITIAL_Y + 8.0F;
        float height = GlassTheme.PANEL_HEADER_HEIGHT + 24.0F;

        VGRenderer.roundedRect(x, y, panelWidth, height, GlassTheme.RADIUS_LG, GlassTheme.surfacePanel());
        VGRenderer.text(title, x + GlassTheme.PADDING + 0.8F,
                y + GlassTheme.PANEL_HEADER_HEIGHT / 2.0F - GlassTheme.FONT_HEADER * 0.52F,
                GlassTheme.FONT_HEADER, VGFont.mediumFace(), 0.0F, 0.0F, GlassTheme.textSecondary());
        VGRenderer.text("Nothing here yet", x + GlassTheme.PADDING + 2.0F,
                y + GlassTheme.PANEL_HEADER_HEIGHT + 6.0F,
                GlassTheme.FONT_SETTING, VGFont.FACE, 0.0F, 0.0F, GlassTheme.textMuted());
    }

    private void drawDock(float mouseX, float mouseY) {
        DockTab[] tabs = DockTab.values();
        int count = tabs.length;
        float dockWidth = DOCK_PADDING * 2.0F + count * DOCK_BUTTON + (count - 1) * DOCK_SPACING;
        float dockX = (width - dockWidth) / 2.0F;
        float dockY = height - DOCK_HEIGHT - DOCK_MARGIN_BOTTOM;

        VGRenderer.roundedRect(dockX, dockY, dockWidth, DOCK_HEIGHT, GlassTheme.RADIUS_LG,
                GlassTheme.dockBackground());

        hoveredDock = -1;
        float cursor = dockX + DOCK_PADDING;
        float buttonY = dockY + (DOCK_HEIGHT - DOCK_BUTTON) / 2.0F;
        for (int i = 0; i < count; i++) {
            dockButtonX[i] = cursor;
            boolean active = tabs[i] == activeTab;
            boolean hovered = GlassTheme.inside(mouseX, mouseY, cursor, buttonY, DOCK_BUTTON, DOCK_BUTTON);
            if (hovered) {
                hoveredDock = i;
            }

            if (active) {
                VGRenderer.roundedRect(cursor, buttonY, DOCK_BUTTON, DOCK_BUTTON, GlassTheme.RADIUS_MD,
                        GlassTheme.accent());
            } else if (hovered) {
                VGRenderer.roundedRect(cursor, buttonY, DOCK_BUTTON, DOCK_BUTTON, GlassTheme.RADIUS_MD,
                        GlassTheme.moduleHover());
            }

            Color iconColor = active ? GlassTheme.textOnAccent() : GlassTheme.textSecondary();
            drawTabIcon(tabs[i], cursor + DOCK_BUTTON / 2.0F, buttonY + DOCK_BUTTON / 2.0F, iconColor);

            cursor += DOCK_BUTTON + DOCK_SPACING;
        }
    }

    private void drawTabIcon(DockTab tab, float cx, float cy, Color color) {
        switch (tab) {
            case FEATURES -> {
                float s = 5.0F;
                VGRenderer.roundedRect(cx - s - 1.0F, cy - s - 1.0F, s, s, 1.5F, color);
                VGRenderer.roundedRect(cx + 1.0F, cy - s - 1.0F, s, s, 1.5F, color);
                VGRenderer.roundedRect(cx - s - 1.0F, cy + 1.0F, s, s, 1.5F, color);
                VGRenderer.roundedRect(cx + 1.0F, cy + 1.0F, s, s, 1.5F, color);
            }
            case CONFIGS -> {
                float w = 7.0F;
                for (int i = -1; i <= 1; i++) {
                    float ly = cy + i * 3.6F;
                    VGRenderer.line(cx - w, ly, cx + w, ly, 1.3F, color);
                    VGRenderer.circle(cx + (i == 0 ? 2.0F : -3.0F), ly, 1.4F, color);
                }
            }
            case FRIENDS -> {
                VGRenderer.circle(cx, cy - 2.8F, 2.8F, color);
                VGRenderer.roundedRect(cx - 4.6F, cy + 1.0F, 9.2F, 4.6F, 2.3F, color);
            }
            default -> {
            }
        }
    }

    // --- Input ---------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        if (button == 0 && hoveredDock >= 0) {
            activeTab = DockTab.values()[hoveredDock];
            return true;
        }

        if (activeTab == DockTab.FEATURES) {
            for (int i = panels.size() - 1; i >= 0; i--) {
                if (panels.get(i).mouseClicked(mouseX, mouseY, button)) {
                    Panel lifted = panels.remove(i);
                    panels.add(lifted);
                    if (button == 0 && GlassTheme.inside(mouseX, mouseY, slotX[i], slotY[i],
                            GlassTheme.PANEL_WIDTH, GlassTheme.PANEL_HEADER_HEIGHT)) {
                        draggingPanel = panels.size() - 1;
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
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
        if (activeTab == DockTab.FEATURES) {
            for (Panel panel : panels) {
                panel.mouseDragged(event.x(), event.y(), event.button());
            }
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (activeTab == DockTab.FEATURES) {
            for (int i = panels.size() - 1; i >= 0; i--) {
                if (panels.get(i).mouseScrolled(mouseX, mouseY, scrollY)) {
                    return true;
                }
            }
            if (scrollY != 0.0 && maxGlobalScroll > 0.0F) {
                globalScroll = Math.max(0.0F, Math.min(maxGlobalScroll,
                        globalScroll - (float) scrollY * GLOBAL_SCROLL_SPEED));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private enum DockTab {
        FEATURES("Features"),
        CONFIGS("Configs"),
        FRIENDS("Friends");

        private final String label;

        DockTab(String label) {
            this.label = label;
        }
    }
}
