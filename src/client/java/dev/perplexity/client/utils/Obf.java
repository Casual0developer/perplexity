package dev.perplexity.client.utils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Compile-time string hiding for cheat-sensitive literals. Stored forms are XOR-masked
 * Base64 with no searchable plaintext. Callers must decode transiently (use the value
 * within the same frame) and never store the result: decoded text on the heap is
 * unavoidable while displayed, but nothing lingers past it.
 */
public final class Obf {
    private static final byte[] KEY = {
        (byte) 0x5E, (byte) 0x3A, (byte) 0x77, (byte) 0x11, (byte) 0x4D, (byte) 0x02,
        (byte) 0x6B, (byte) 0x39, (byte) 0x0C, (byte) 0x55, (byte) 0x2F, (byte) 0x71,
        (byte) 0x1A, (byte) 0x7D, (byte) 0x09, (byte) 0x33
    };

    private Obf() {
    }

    public static String s(String encoded) {
        byte[] bytes = Base64.getDecoder().decode(encoded);
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] ^= KEY[i % KEY.length];
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
