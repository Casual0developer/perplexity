package dev.perplexity.client.auth;

/**
 * Whether the menu gate has been passed this session, by login, signup or skip.
 * Resets every launch; nothing persists except the accounts file itself.
 */
public final class AuthGate {
    private static boolean passed;

    private AuthGate() {
    }

    public static boolean passed() {
        return passed;
    }

    public static void pass() {
        passed = true;
    }

    /** Locks the menu again after an authenticated user signs out. */
    public static void reset() {
        passed = false;
    }
}
