package dev.perplexity.client.auth;

/** Outcome of a signup/login attempt: either an error or the account identity. */
public record AuthResult(String error, String email, String id) {
    public static AuthResult ok(String email, String id) {
        return new AuthResult(null, email, id);
    }

    public static AuthResult fail(String error) {
        return new AuthResult(error, null, null);
    }

    public boolean success() {
        return error == null;
    }
}
