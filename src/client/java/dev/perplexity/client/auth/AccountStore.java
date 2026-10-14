package dev.perplexity.client.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Device-local accounts: email plus salted SHA-256, stored under the Fabric config dir.
 * There is no server here, so there is nothing to verify against and no code to send —
 * signup and login only ever compare against this file.
 */
public final class AccountStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int PBKDF2_ITERATIONS = 600_000;
    private static final int KEY_BITS = 256;

    private AccountStore() {
    }

    public static AuthResult signup(String email, String password) {
        String key = AuthValidation.normalizeEmail(email);
        String emailError = AuthValidation.emailError(key);
        if (emailError != null) return AuthResult.fail(emailError);
        String passwordError = AuthValidation.newPasswordError(password);
        if (passwordError != null) {
            return AuthResult.fail(passwordError);
        }
        try {
            JsonObject root = read();
            if (root.has(key)) {
                return AuthResult.fail("Account already exists");
            }
            String id = UUID.randomUUID().toString();
            byte[] salt = new SecureRandom().generateSeed(16);
            JsonObject entry = new JsonObject();
            entry.addProperty("id", id);
            entry.addProperty("salt", Base64.getEncoder().encodeToString(salt));
            setSecureHash(entry, salt, password);
            root.add(key, entry);
            write(root);
            return AuthResult.ok(key, id);
        } catch (Exception failed) {
            return AuthResult.fail("Storage unavailable");
        }
    }

    public static AuthResult login(String email, String password) {
        String key = AuthValidation.normalizeEmail(email);
        String emailError = AuthValidation.emailError(key);
        if (emailError != null) return AuthResult.fail(emailError);
        String passwordError = AuthValidation.existingPasswordError(password);
        if (passwordError != null) return AuthResult.fail(passwordError);
        try {
            JsonObject root = read();
            if (!root.has(key) || !root.get(key).isJsonObject()) {
                return AuthResult.fail("Unknown email");
            }
            JsonObject entry = root.getAsJsonObject(key);
            byte[] salt = Base64.getDecoder().decode(entry.get("salt").getAsString());
            String expected = entry.get("hash").getAsString();
            if (!constantTimeEquals(expected, storedHash(entry, salt, password))) {
                return AuthResult.fail("Wrong password");
            }
            if (!entry.has("algorithm")) {
                setSecureHash(entry, salt, password);
                root.add(key, entry);
                write(root);
            }
            String id = entry.has("id") ? entry.get("id").getAsString() : null;
            return AuthResult.ok(key, id);
        } catch (Exception failed) {
            return AuthResult.fail("Storage unavailable");
        }
    }

    /** Last email that authenticated here, prefilled next launch. */
    public static AuthResult continueWith(String email, String password) {
        String key = AuthValidation.normalizeEmail(email);
        String emailError = AuthValidation.emailError(key);
        if (emailError != null) return AuthResult.fail(emailError);
        try {
            JsonObject root = read();
            if (!root.has(key) || !root.get(key).isJsonObject()) {
                String passwordError = AuthValidation.newPasswordError(password);
                if (passwordError != null) return AuthResult.fail(passwordError);
                String id = UUID.randomUUID().toString();
                byte[] salt = new SecureRandom().generateSeed(16);
                JsonObject entry = new JsonObject();
                entry.addProperty("id", id);
                entry.addProperty("salt", Base64.getEncoder().encodeToString(salt));
                setSecureHash(entry, salt, password);
                root.add(key, entry);
                write(root);
                return AuthResult.ok(key, id);
            }
            String passwordError = AuthValidation.existingPasswordError(password);
            if (passwordError != null) return AuthResult.fail(passwordError);
            JsonObject entry = root.getAsJsonObject(key);
            byte[] salt = Base64.getDecoder().decode(entry.get("salt").getAsString());
            String expected = entry.get("hash").getAsString();
            if (!constantTimeEquals(expected, storedHash(entry, salt, password))) {
                return AuthResult.fail("Wrong password");
            }
            if (!entry.has("algorithm")) {
                setSecureHash(entry, salt, password);
                root.add(key, entry);
                write(root);
            }
            String id = entry.has("id") ? entry.get("id").getAsString() : null;
            return AuthResult.ok(key, id);
        } catch (Exception failed) {
            return AuthResult.fail("Storage unavailable");
        }
    }

    public static String getLastEmail() {
        try {
            JsonObject root = read();
            return root.has("lastEmail") ? root.get("lastEmail").getAsString() : "";
        } catch (Exception failed) {
            return "";
        }
    }

    public static void setLastEmail(String email) {
        try {
            JsonObject root = read();
            root.addProperty("lastEmail", email == null ? "" : email);
            write(root);
        } catch (Exception ignored) {
        }
    }

    private static String legacyHash(byte[] salt, String password) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(salt);
        return hex(digest.digest(password.getBytes(StandardCharsets.UTF_8)));
    }

    private static String pbkdf2(byte[] salt, String password, int iterations) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
        try {
            return hex(SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
                    .generateSecret(spec).getEncoded());
        } finally {
            spec.clearPassword();
        }
    }

    private static String storedHash(JsonObject entry, byte[] salt, String password) throws Exception {
        if (!entry.has("algorithm")) return legacyHash(salt, password);
        if (!"pbkdf2-sha256".equals(entry.get("algorithm").getAsString())) {
            throw new IllegalStateException("Unsupported password format");
        }
        int iterations = entry.has("iterations")
                ? entry.get("iterations").getAsInt() : PBKDF2_ITERATIONS;
        if (iterations < 100_000 || iterations > 2_000_000) {
            throw new IllegalStateException("Invalid password format");
        }
        return pbkdf2(salt, password, iterations);
    }

    private static void setSecureHash(JsonObject entry, byte[] salt, String password) throws Exception {
        entry.addProperty("algorithm", "pbkdf2-sha256");
        entry.addProperty("iterations", PBKDF2_ITERATIONS);
        entry.addProperty("hash", pbkdf2(salt, password, PBKDF2_ITERATIONS));
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private static String hex(byte[] out) {
        StringBuilder hex = new StringBuilder(out.length * 2);
        for (byte b : out) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }

    private static Path file() throws Exception {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("perplexity");
        Files.createDirectories(dir);
        return dir.resolve("accounts.json");
    }

    private static JsonObject read() throws Exception {
        Path path = file();
        if (!Files.isRegularFile(path)) {
            return new JsonObject();
        }
        String text = Files.readString(path, StandardCharsets.UTF_8).trim();
        if (text.isEmpty()) {
            return new JsonObject();
        }
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static void write(JsonObject root) throws Exception {
        Files.writeString(file(), GSON.toJson(root), StandardCharsets.UTF_8);
    }
}
