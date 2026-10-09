package dev.perplexity.client.utils.rotation;

public enum RotationPriority {
    LOW(0),
    NORMAL(100),
    HIGH(200),
    CRITICAL(300),
    SAFE_ANCHOR(1_000);

    private final int value;

    RotationPriority(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
