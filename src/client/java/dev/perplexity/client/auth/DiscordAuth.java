package dev.perplexity.client.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Properties;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Discord-backed accounts for this client only. Records are PBKDF2 hashes, never reversible
 * passwords: anyone reading the channel sees salts and hashes they cannot turn back into
 * logins. The bot token lives in a gitignored local {@code discord.properties}, never in
 * source, because anything shipped in the mod is extractable.
 *
 * <p>Residual risks, stated plainly: an extracted token can vandalize the channel and
 * download hashes for offline cracking, so user passwords must still be unique throwaways
 * (the login screen says so), and Discord may rate-limit heavy automation.</p>
 */
public final class DiscordAuth {
    private static final String RECORD_PREFIX = "PERPLEXITY-ACCOUNT";
    private static final String CREDENTIAL_SCHEME = "pbkdf2-sha256";
    private static final int PBKDF2_ITERATIONS = 600_000;
    private static final int KEY_BITS = 256;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static String botToken;
    private static String channelId;
    private static boolean loaded;

    private DiscordAuth() {
    }

    /** True when a token and channel are configured locally. */
    public static synchronized boolean configured() {
        load();
        return botToken != null && channelId != null;
    }

    /**
     * Remote continue: signs up when the email is new, verifies when it exists.
     * Returns the account identity on success, an error message on definitive failure,
     * and throws on transport problems (caller treats those as offline).
     */
    public static AuthResult continueWith(String email, String password) throws Exception {
        load();
        String key = AuthValidation.normalizeEmail(email);
        String emailError = AuthValidation.emailError(key);
        if (emailError != null) return AuthResult.fail(emailError);
        JsonObject existing = findRecord(key);
        if (existing == null) {
            String passwordError = AuthValidation.newPasswordError(password);
            if (passwordError != null) return AuthResult.fail(passwordError);
            String id = java.util.UUID.randomUUID().toString();
            byte[] salt = new SecureRandom().generateSeed(16);
            String credential = CREDENTIAL_SCHEME + "$" + PBKDF2_ITERATIONS + "$"
                    + Base64.getEncoder().encodeToString(salt) + "$" + hash(salt, password);
            String record = "```\n"
                    + "Account Number: \"" + id + "\"\n"
                    + "Account Email: \"" + key + "\"\n"
                    + "Account Password: \"" + credential + "\"\n"
                    + "```";
            postMessage(record);
            return AuthResult.ok(key, id);
        }
        String passwordError = AuthValidation.existingPasswordError(password);
        if (passwordError != null) return AuthResult.fail(passwordError);
        byte[] salt = Base64.getDecoder().decode(existing.get("salt").getAsString());
        String expected = existing.get("hash").getAsString();
        String actual = hash(salt, password);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8))) {
            return AuthResult.fail("Wrong password");
        }
        String id = existing.has("id") ? existing.get("id").getAsString() : null;
        return AuthResult.ok(key, id);
    }

    private static String hash(byte[] salt, String password) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS);
        try {
            byte[] out = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            StringBuilder hex = new StringBuilder(out.length * 2);
            for (byte b : out) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } finally {
            spec.clearPassword();
        }
    }

    /** Pages channel history for this client's account records. */
    private static JsonObject findRecord(String email) throws Exception {
        String before = null;
        while (true) {
            String url = "https://discord.com/api/v10/channels/" + channelId
                    + "/messages?limit=100" + (before == null ? "" : "&before=" + before);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bot " + botToken)
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new IllegalStateException("Discord rejected the token");
            }
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Discord error " + response.statusCode());
            }
            JsonArray messages = JsonParser.parseString(response.body()).getAsJsonArray();
            if (messages.isEmpty()) {
                return null;
            }
            for (JsonElement element : messages) {
                JsonObject message = element.getAsJsonObject();
                before = message.get("id").getAsString();
                if (!message.has("content")) {
                    continue;
                }
                JsonObject record = parseRecord(message.get("content").getAsString());
                if (record != null && email.equals(record.get("email").getAsString())) {
                    return record;
                }
            }
            if (messages.size() < 100) {
                return null;
            }
        }
    }

    private static JsonObject parseRecord(String content) {
        if (content == null) {
            return null;
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstLine = trimmed.indexOf('\n');
            int closingFence = trimmed.lastIndexOf("```");
            if (firstLine >= 0 && closingFence > firstLine) {
                trimmed = trimmed.substring(firstLine + 1, closingFence).trim();
            }
        }
        if (trimmed.startsWith("Account Number:")) {
            return parseReadableRecord(trimmed);
        }
        if (!trimmed.startsWith(RECORD_PREFIX + " ")) {
            return null;
        }
        JsonObject record = new JsonObject();
        for (String part : trimmed.substring(RECORD_PREFIX.length() + 1).split(" ")) {
            int equals = part.indexOf('=');
            if (equals > 0) {
                record.addProperty(part.substring(0, equals), part.substring(equals + 1));
            }
        }
        if (!record.has("email") || !record.has("salt") || !record.has("hash")) {
            return null;
        }
        return record;
    }

    /** Reads the human-friendly Discord layout while keeping the verifier's internal field names. */
    private static JsonObject parseReadableRecord(String content) {
        String id = null;
        String email = null;
        String credential = null;
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("Account Number:")) {
                id = quotedValue(trimmed, "Account Number:");
            } else if (trimmed.startsWith("Account Email:")) {
                email = quotedValue(trimmed, "Account Email:");
            } else if (trimmed.startsWith("Account Password:")) {
                credential = quotedValue(trimmed, "Account Password:");
            }
        }
        if (id == null || email == null || credential == null) {
            return null;
        }
        String[] parts = credential.split("\\$", -1);
        if (parts.length != 4 || !CREDENTIAL_SCHEME.equals(parts[0])) {
            return null;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            if (iterations != PBKDF2_ITERATIONS) {
                return null;
            }
            Base64.getDecoder().decode(parts[2]);
            if (parts[3].length() != KEY_BITS / 4) {
                return null;
            }
        } catch (IllegalArgumentException invalid) {
            return null;
        }
        JsonObject record = new JsonObject();
        record.addProperty("id", id);
        record.addProperty("email", email);
        record.addProperty("salt", parts[2]);
        record.addProperty("hash", parts[3]);
        return record;
    }

    private static String quotedValue(String line, String label) {
        String value = line.substring(label.length()).trim();
        if (value.length() < 2 || value.charAt(0) != '"'
                || value.charAt(value.length() - 1) != '"') {
            return null;
        }
        return value.substring(1, value.length() - 1);
    }

    private static void postMessage(String content) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("content", content);
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("https://discord.com/api/v10/channels/" + channelId + "/messages"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bot " + botToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("Discord error " + response.statusCode());
        }
    }

    private static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try (java.io.InputStream input =
                     DiscordAuth.class.getClassLoader().getResourceAsStream("discord.properties")) {
            if (input == null) {
                return;
            }
            Properties properties = new Properties();
            properties.load(input);
            String token = properties.getProperty("botToken", "").trim();
            String channel = properties.getProperty("channelId", "").trim();
            if (!token.isEmpty() && !channel.isEmpty()) {
                botToken = token;
                channelId = channel;
            }
        } catch (Exception ignored) {
        }
    }
}
