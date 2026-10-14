package dev.perplexity.client.auth;

/**
 * Who is using the menu this session. Set on login/signup, cleared on skip; emails are
 * shown in the interface, ids stay out of the way unless needed.
 */
public final class CurrentAccount {
    private static String email;
    private static String id;

    private CurrentAccount() {
    }

    public static void set(String email, String id) {
        CurrentAccount.email = email;
        CurrentAccount.id = id;
    }

    public static void clear() {
        email = null;
        id = null;
    }

    public static String email() {
        return email;
    }

    public static String id() {
        return id;
    }

    public static boolean isLoggedIn() {
        return email != null;
    }
}
