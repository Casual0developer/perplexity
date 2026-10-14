package dev.perplexity.client.gui.auth;

import dev.perplexity.client.auth.AccountStore;
import dev.perplexity.client.auth.AuthGate;
import dev.perplexity.client.auth.AuthResult;
import dev.perplexity.client.auth.CurrentAccount;
import dev.perplexity.client.auth.DiscordAuth;
import dev.perplexity.client.config.ConfigManager;
import dev.perplexity.client.gui.clickgui.ClickGuiScreen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import org.lwjgl.glfw.GLFW;

/**
 * Login/signup gate shown instead of the menu until passed. Accounts live only on this
 * device; skip is always available and equally first-class.
 */
public final class AuthScreen extends net.minecraft.client.gui.screens.Screen {
    private static final int FIELD_WIDTH = 200;
    private static final int FIELD_HEIGHT = 20;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 8;
    private static final long FAILED_ATTEMPT_COOLDOWN_MS = 2_000L;

    private EditBox emailBox;
    private EditBox passwordBox;
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
        int centerX = width / 2;
        int y = height / 2 - 62;

        emailBox = new EditBox(font, centerX - FIELD_WIDTH / 2, y, FIELD_WIDTH, FIELD_HEIGHT,
                Component.literal("Email"));
        emailBox.setHint(Component.literal("Email"));
        emailBox.setMaxLength(64);
        addRenderableWidget(emailBox);

        passwordBox = new EditBox(font, centerX - FIELD_WIDTH / 2, y + FIELD_HEIGHT + GAP,
                FIELD_WIDTH, FIELD_HEIGHT, Component.literal("Password"));
        passwordBox.setHint(Component.literal("Password"));
        passwordBox.setMaxLength(64);
        passwordBox.addFormatter((text, cursor) -> FormattedCharSequence.forward(
                "\u2022".repeat(text.length()), Style.EMPTY));
        addRenderableWidget(passwordBox);

        int buttonY = y + (FIELD_HEIGHT + GAP) * 2 + GAP;
        addRenderableWidget(Button.builder(Component.literal("Continue"), button -> attempt())
                .bounds(centerX - FIELD_WIDTH / 2, buttonY, FIELD_WIDTH, BUTTON_HEIGHT)
                .build());

        String last = AccountStore.getLastEmail();
        if (!last.isEmpty()) {
            emailBox.setValue(last);
            setInitialFocus(passwordBox);
        } else {
            setInitialFocus(emailBox);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER) {
            attempt();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (busy && System.currentTimeMillis() - attemptStartMs > 25000L) {
            busy = false;
            status = null;
            error = "Timed out - check connection";
        }
        graphics.drawCenteredString(font, "perplexity", width / 2, height / 2 - 92, 0xF6F3FF);
        graphics.drawCenteredString(font, "Use 8+ characters and never reuse a real password.",
                width / 2, height / 2 + 30, 0xB0A8D0);
        if (status != null) {
            graphics.drawCenteredString(font, status, width / 2, height / 2 + 48, 0xB0A8D0);
        }
        if (error != null) {
            graphics.drawCenteredString(font, error, width / 2, height / 2 + 48, 0xFF5555);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void attempt() {
        if (busy) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextAttemptMs) {
            error = "Wait a moment before trying again";
            status = null;
            return;
        }
        busy = true;
        attemptStartMs = System.currentTimeMillis();
        error = null;
        status = "Continuing...";
        String email = emailBox.getValue();
        String password = passwordBox.getValue();
        Thread worker = new Thread(() -> {
            AuthResult result;
            try {
                if (DiscordAuth.configured()) {
                    result = DiscordAuth.continueWith(email, password);
                } else {
                    result = AccountStore.continueWith(email, password);
                }
            } catch (java.io.IOException | InterruptedException transport) {
                if (transport instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                result = AuthResult.fail("Can't reach login service");
            } catch (Throwable failed) {
                String message = failed.getMessage();
                result = AuthResult.fail(message == null || message.isEmpty()
                        ? "Auth failed (" + failed.getClass().getSimpleName() + ")"
                        : message);
            }
            AuthResult finalResult = result;
            try {
                minecraft.execute(() -> {
                    try {
                        busy = false;
                        if (finalResult != null && finalResult.success()) {
                            error = null;
                            status = null;
                            CurrentAccount.set(finalResult.email(), finalResult.id());
                            AccountStore.setLastEmail(finalResult.email());
                            ConfigManager.onAccountChanged(finalResult.id());
                            pass();
                        } else {
                            nextAttemptMs = System.currentTimeMillis() + FAILED_ATTEMPT_COOLDOWN_MS;
                            error = finalResult == null ? "Login failed" : finalResult.error();
                            status = null;
                        }
                    } catch (Throwable uiFailed) {
                        busy = false;
                        error = "UI error";
                        status = null;
                    }
                });
            } catch (Throwable queueFailed) {
                busy = false;
                error = "UI error";
                status = null;
            }
        }, "perplexity-auth");
        worker.setDaemon(true);
        worker.start();
    }

    private void pass() {
        AuthGate.pass();
        if (minecraft != null && minecraft.screen == this) {
            minecraft.setScreen(new ClickGuiScreen());
        }
    }

}
