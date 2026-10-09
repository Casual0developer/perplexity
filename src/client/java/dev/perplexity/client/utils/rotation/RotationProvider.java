package dev.perplexity.client.utils.rotation;

@FunctionalInterface
public interface RotationProvider {
    Rotation at(float partialTicks);
}
