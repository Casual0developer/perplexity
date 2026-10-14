package dev.perplexity.client.auth;

import java.util.Locale;
import java.util.regex.Pattern;

/** Shared, deliberately small validation rules for both local and Discord-backed accounts. */
final class AuthValidation {
    static final int MIN_PASSWORD_LENGTH = 8;
    static final int MAX_PASSWORD_LENGTH = 128;

    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?"
                    + "(?:\\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+$",
            Pattern.CASE_INSENSITIVE);

    private AuthValidation() {
    }

    static String normalizeEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    static String emailError(String email) {
        if (email.isEmpty() || email.length() > 254 || !EMAIL.matcher(email).matches()) {
            return "Enter a valid email";
        }
        return null;
    }

    static String newPasswordError(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            return "Password needs " + MIN_PASSWORD_LENGTH + "+ characters";
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            return "Password is too long";
        }
        return null;
    }

    static String existingPasswordError(String password) {
        if (password == null || password.isEmpty()) {
            return "Enter your password";
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            return "Password is too long";
        }
        return null;
    }
}
