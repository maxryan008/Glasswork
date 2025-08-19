package dev.maximus.glasswork.api;

public record QuadVertex(
        float x, float y, float z,
        float u, float v,
        int color, int light, int overlay,
        float nx, float ny, float nz
) {}