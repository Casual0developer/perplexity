package dev.perplexity.client.gui.auth;

import dev.perplexity.client.auth.AccountStore;
import dev.perplexity.client.auth.AuthGate;
import dev.perplexity.client.auth.AuthResult;
import dev.perplexity.client.auth.CurrentAccount;
import dev.perplexity.client.auth.DiscordAuth;
import dev.perplexity.client.config.ConfigManager;
import dev.perplexity.client.gui.clickgui.Backdrop;
import dev.perplexity.client.gui.clickgui.ClickGuiConfig;
import dev.perplexity.client.gui.clickgui.ClickGuiScreen;
import dev.perplexity.client.gui.clickgui.GlassTheme;
import dev.perplexity.client.impl.modules.ui.MenuKeyModule;
import dev.perplexity.client.render.vg.VGFont;
import dev.perplexity.client.render.vg.VGRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

import java.awt.Color;

/** Login gate rendered with the same NanoVG glass system as the Right Shift ClickGUI. */
public final class AuthScreen extends net.minecraft.client.gui.screens.Screen {
    private static final float UI_REFERENCE_SCALE = 2.0F;
    private static final float CARD_WIDTH = 276.0F;
    private static final float CARD_HEIGHT = 238.0F;
    private static final float CARD_PADDING = 18.0F;
    private static final float FIELD_HEIGHT = 34.0F;
    private static final float BUTTON_HEIGHT = 34.0F;
    private static final long FAILED_ATTEMPT_COOLDOWN_MS = 2_000L;

    private final Backdrop backdrop = new Backdrop();

    // EditBox is retained only as a text-editing model (clipboard, selection, Unicode and cursor
    // behavior). It is never registered or rendered; every visible pixel is drawn through NanoVG.
    private EditBox emailBox;
    private EditBox passwordBox;
    private int activeField;
    private float lastMouseX;
    private float lastMouseY;
    private String error;
    private volatile String status;
    private volatile boolean busy;
    private volatile long attemptStartMs;
    private long nextAttemptMs;

    public AuthScreen() {
        super(Component.literal("Login"));
    }

    @Override
    protected void init() {
        String email = emailBox == null ? AccountStore.getLastEmail() : emailBox.getValue();
        String password = passwordBox == null ? "" : passwordBox.getValue();

        emailBox = new EditBox(font, 0, 0, 1, 1, Component.literal("Email"));
        emailBox.setMaxLength(254);
        emailBox.setValue(email);
        passwordBox = new EditBox(font, 0, 0, 1, 1, Component.literal("Password"));
        passwordBox.setMaxLength(128);
        passwordBox.setValue(password);
        focus(email.isEmpty() ? 0 : 1);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (int i = 0; i < MenuKeyModule.blurPasses(); i++) {
            renderBlurredBackground(graphics);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        GlassTheme.setDark(ClickGuiConfig.dark);
        super.render(graphics, mouseX, mouseY, partialTick);

        if (busy && System.currentTimeMillis() - attemptStartMs > 25_000L) {
            busy = false;
            status = null;
            error = "Timed out - check connection";
        }
    }

    /** Drawn by ClickGuiOverlay after Minecraft submits its collected GUI pass. */
    public void drawVg() {
        float unit = uiUnitScale();
        float uiWidth = width * unit;
        float uiHeight = height * unit;
        float mouseX = lastMouseX * unit;
        float mouseY = lastMouseY * unit;
        if (!VGRenderer.beginFrame()) return;

        VGFont.setFamily(MenuKeyModule.fontId());
        try {
            VGRenderer.save();
            VGRenderer.scale(UI_REFERENCE_SCALE, UI_REFERENCE_SCALE);
            VGRenderer.rect(0.0F, 0.0F, uiWidth, uiHeight, GlassTheme.scrim());
            backdrop.render(uiWidth, uiHeight);

            float cardX = (uiWidth - CARD_WIDTH) * 0.5F;
            float cardY = (uiHeight - CARD_HEIGHT) * 0.5F;
            drawCard(cardX, cardY, mouseX, mouseY);
            VGRenderer.restore();
        } finally {
            VGRenderer.endFrame();
        }
    }

    private void drawCard(float x, float y, float mouseX, float mouseY) {
        VGRenderer.shadow(x, y, CARD_WIDTH, CARD_HEIGHT, GlassTheme.RADIUS_LG,
                18.0F, new Color(0, 0, 0, 145));
        VGRenderer.verticalGradient(x, y, CARD_WIDTH, CARD_HEIGHT, GlassTheme.RADIUS_LG,
                GlassTheme.surfacePanel(), GlassTheme.surfacePanelBottom());
        VGRenderer.roundedOutline(x, y, CARD_WIDTH, CARD_HEIGHT, GlassTheme.RADIUS_LG,
                GlassTheme.PANEL_BORDER, GlassTheme.panelBorder());

        VGRenderer.textCentered("perplexity", x + CARD_WIDTH * 0.5F, y + 24.0F,
                17.0F, VGFont.semiboldFace(), GlassTheme.headerText());
        VGRenderer.textCentered("Sign in or create your account", x + CARD_WIDTH * 0.5F,
                y + 44.0F, 8.0F, VGFont.face(), GlassTheme.textSecondary());

        float fieldX = x + CARD_PADDING;
        float fieldWidth = CARD_WIDTH - CARD_PADDING * 2.0F;
        float emailY = y + 64.0F;
        float passwordY = emailY + FIELD_HEIGHT + 10.0F;
        drawField(emailBox, false, "Email", fieldX, emailY, fieldWidth, activeField == 0);
        drawField(passwordBox, true, "Password", fieldX, passwordY, fieldWidth, activeField == 1);

        float buttonY = passwordY + FIELD_HEIGHT + 14.0F;
        boolean buttonHover = GlassTheme.inside(mouseX, mouseY, fieldX, buttonY,
                fieldWidth, BUTTON_HEIGHT);
        Color buttonTop = busy ? GlassTheme.interactiveMuted()
                : buttonHover ? GlassTheme.accentBright() : GlassTheme.accent();
        Color buttonBottom = busy ? GlassTheme.interactiveMuted() : GlassTheme.accentDeep();
        VGRenderer.verticalGradient(fieldX, buttonY, fieldWidth, BUTTON_HEIGHT,
                GlassTheme.RADIUS_MD, buttonTop, buttonBottom);
        VGRenderer.textCentered(busy ? "Connecting..." : "Continue", x + CARD_WIDTH * 0.5F,
                buttonY + BUTTON_HEIGHT * 0.5F, 9.0F, VGFont.semiboldFace(),
                GlassTheme.textOnAccent());

        float skipY = buttonY + BUTTON_HEIGHT + 13.0F;
        boolean skipHover = GlassTheme.inside(mouseX, mouseY, x + 92.0F, skipY - 7.0F,
                92.0F, 16.0F);
        VGRenderer.textCentered("Skip for now", x + CARD_WIDTH * 0.5F, skipY,
                8.0F, VGFont.mediumFace(), skipHover
                        ? GlassTheme.textPrimary() : GlassTheme.textMuted());

        String message = error != null ? error : status;
        if (message != null) {
            Color color = error != null ? new Color(255, 105, 135) : GlassTheme.textSecondary();
            VGRenderer.textCentered(message, x + CARD_WIDTH * 0.5F, y + CARD_HEIGHT - 11.0F,
                    7.5F, VGFont.face(), color);
        } else {
            VGRenderer.textCentered("Use 8+ characters and a unique password",
                    x + CARD_WIDTH * 0.5F, y + CARD_HEIGHT - 11.0F,
                    7.0F, VGFont.face(), GlassTheme.textMuted());
        }
    }

    private void drawField(EditBox box, boolean password, String placeholder, float x, float y,
                           float width, boolean focused) {
        VGRenderer.roundedRect(x, y, width, FIELD_HEIGHT, GlassTheme.RADIUS_MD,
                GlassTheme.groupFill());
        VGRenderer.roundedOutline(x, y, width, FIELD_HEIGHT, GlassTheme.RADIUS_MD, 1.0F,
                focused ? GlassTheme.accent(0.92F) : GlassTheme.panelBorder());

        String value = box.getValue();
        String shown = password ? "\u2022".repeat(value.length()) : value;
        if (shown.isEmpty()) {
            VGRenderer.textLeft(placeholder, x + 11.0F, y + FIELD_HEIGHT * 0.5F,
                    8.5F, VGFont.face(), GlassTheme.textMuted());
            return;
        }

        int cursor = Math.min(box.getCursorPosition(), shown.length());
        if (focused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            shown = shown.substring(0, cursor) + "|" + shown.substring(cursor);
        }
        float available = width - 22.0F;
        while (shown.length() > 1
                && VGRenderer.textWidth(shown, 8.5F, VGFont.face(), 0.0F) > available) {
            shown = shown.substring(1);
        }
        VGRenderer.save();
        VGRenderer.scissor(x + 8.0F, y + 2.0F, width - 16.0F, FIELD_HEIGHT - 4.0F);
        VGRenderer.textLeft(shown, x + 11.0F, y + FIELD_HEIGHT * 0.5F,
                8.5F, VGFont.face(), GlassTheme.textPrimary());
        VGRenderer.restore();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        float unit = uiUnitScale();
        double mouseX = event.x() * unit;
        double mouseY = event.y() * unit;
        float uiWidth = width * unit;
        float uiHeight = height * unit;
        float cardX = (uiWidth - CARD_WIDTH) * 0.5F;
        float cardY = (uiHeight - CARD_HEIGHT) * 0.5F;
        float fieldX = cardX + CARD_PADDING;
        float fieldWidth = CARD_WIDTH - CARD_PADDING * 2.0F;
        float emailY = cardY + 64.0F;
        float passwordY = emailY + FIELD_HEIGHT + 10.0F;
        float buttonY = passwordY + FIELD_HEIGHT + 14.0F;
        float skipY = buttonY + BUTTON_HEIGHT + 13.0F;

        if (GlassTheme.inside(mouseX, mouseY, fieldX, emailY, fieldWidth, FIELD_HEIGHT)) {
            focus(0);
            emailBox.moveCursorToEnd(false);
            return true;
        }
        if (GlassTheme.inside(mouseX, mouseY, fieldX, passwordY, fieldWidth, FIELD_HEIGHT)) {
            focus(1);
            passwordBox.moveCursorToEnd(false);
            return true;
        }
        if (GlassTheme.inside(mouseX, mouseY, fieldX, buttonY, fieldWidth, BUTTON_HEIGHT)) {
            attempt();
            return true;
        }
        if (GlassTheme.inside(mouseX, mouseY, cardX + 92.0F, skipY - 7.0F, 92.0F, 16.0F)) {
            skip();
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            attempt();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_TAB) {
            focus(activeField == 0 ? 1 : 0);
            return true;
        }
        return activeBox().keyPressed(event) || super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return activeBox().charTyped(event) || super.charTyped(event);
    }

    private EditBox activeBox() {
        return activeField == 0 ? emailBox : passwordBox;
    }

    private void focus(int field) {
        activeField = field;
        if (emailBox != null) emailBox.setFocused(field == 0);
        if (passwordBox != null) passwordBox.setFocused(field == 1);
    }

    private static float uiUnitScale() {
        return (float) (Minecraft.getInstance().getWindow().getGuiScale() / UI_REFERENCE_SCALE);
    }

    private void attempt() {
        if (busy) return;
        long now = System.currentTimeMillis();
        if (now < nextAttemptMs) {
            error = "Wait a moment before trying again";
            status = null;
            return;
        }
        busy = true;
        attemptStartMs = now;
        error = null;
        status = "Connecting...";
        String email = emailBox.getValue();
        String password = passwordBox.getValue();
        Thread worker = new Thread(() -> authenticate(email, password), "perplexity-auth");
        worker.setDaemon(true);
        worker.start();
    }

    private void authenticate(String email, String password) {
        AuthResult result;
        try {
            result = DiscordAuth.configured()
                    ? DiscordAuth.continueWith(email, password)
                    : AccountStore.continueWith(email, password);
        } catch (java.io.IOException | InterruptedException transport) {
            if (transport instanceof InterruptedException) Thread.currentThread().interrupt();
            result = AuthResult.fail("Can't reach login service");
        } catch (Throwable failed) {
            String message = failed.getMessage();
            result = AuthResult.fail(message == null || message.isEmpty()
                    ? "Auth failed (" + failed.getClass().getSimpleName() + ")" : message);
        }
        AuthResult finalResult = result;
        try {
            minecraft.execute(() -> finishAttempt(finalResult));
        } catch (Throwable queueFailed) {
            busy = false;
            error = "UI error";
            status = null;
        }
    }

    private void finishAttempt(AuthResult result) {
        busy = false;
        if (result != null && result.success()) {
            error = null;
            status = null;
            CurrentAccount.set(result.email(), result.id());
            AccountStore.setLastEmail(result.email());
            ConfigManager.onAccountChanged(result.id());
            pass();
            return;
        }
        nextAttemptMs = System.currentTimeMillis() + FAILED_ATTEMPT_COOLDOWN_MS;
        error = result == null ? "Login failed" : result.error();
        status = null;
    }

    private void skip() {
        if (busy) return;
        CurrentAccount.clear();
        ConfigManager.onAccountChanged(null);
        pass();
    }

    private void pass() {
        AuthGate.pass();
        if (minecraft != null && minecraft.screen == this) {
            minecraft.setScreen(new ClickGuiScreen());
        }
    }
}
