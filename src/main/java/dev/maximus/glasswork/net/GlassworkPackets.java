package dev.maximus.glasswork.net;

import dev.maximus.glasswork.api.InjectedQuad;
import dev.maximus.glasswork.api.QuadVertex;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public final class GlassworkPackets {
    private GlassworkPackets() {}

    // --- Safety caps (tune if needed) ---
    // Quads are 4 vertices each; each vertex ~ (10 floats + 3 ints) => ~ (40 + 12) bytes ~= 52 bytes/vertex.
    // Very rough estimate: 4 * 52 = ~208 bytes/quad. Add overhead and round up generously.
    private static final int MAX_QUADS_PER_PACKET = 8_192;         // ~1.6 MB worst-case; adjust to your needs
    private static final int MAX_PACKET_BYTES_EST = 2 * 1024 * 1024; // 2 MB estimated cap

    // ----------------- Low-level IO -----------------

    private static void writeQuad(FriendlyByteBuf buf, InjectedQuad q) {
        writeV(buf, q.v1());
        writeV(buf, q.v2());
        writeV(buf, q.v3());
        writeV(buf, q.v4());
    }

    private static InjectedQuad readQuad(FriendlyByteBuf buf) {
        QuadVertex v1 = readV(buf);
        QuadVertex v2 = readV(buf);
        QuadVertex v3 = readV(buf);
        QuadVertex v4 = readV(buf);
        return new InjectedQuad(v1, v2, v3, v4);
    }

    private static void writeV(FriendlyByteBuf b, QuadVertex v) {
        b.writeFloat(v.x());
        b.writeFloat(v.y());
        b.writeFloat(v.z());
        b.writeFloat(v.u());
        b.writeFloat(v.v());
        b.writeInt(v.color());
        b.writeInt(v.light());
        // overlay is typically 0..0xFFFF; preserve full range (and keep wire compat)
        b.writeShort((short) v.overlay());
        b.writeFloat(v.nx());
        b.writeFloat(v.ny());
        b.writeFloat(v.nz());
    }

    private static QuadVertex readV(FriendlyByteBuf b) {
        float x = b.readFloat();
        float y = b.readFloat();
        float z = b.readFloat();
        float u = b.readFloat();
        float v = b.readFloat();
        int color = b.readInt();
        int light = b.readInt();
        int overlay = b.readShort() & 0xFFFF;
        float nx = b.readFloat();
        float ny = b.readFloat();
        float nz = b.readFloat();

        // Fast sanity checks (avoid generating exceptions in hot paths later).
        if (bad(x) | bad(y) | bad(z) | bad(u) | bad(v) | bad(nx) | bad(ny) | bad(nz)) {
            throw decodeError("Vertex contains NaN/Inf or invalid normal");
        }
        // Optional: very loose normal magnitude check (skip sqrt for speed)
        float nLenSq = nx * nx + ny * ny + nz * nz;
        if (!(nLenSq > 0.001f && nLenSq < 4.0f)) { // allow a bit of slop
            throw decodeError("Vertex normal has unreasonable magnitude: len^2=" + nLenSq);
        }

        return new QuadVertex(x, y, z, u, v, color, light, overlay, nx, ny, nz);
    }

    private static boolean bad(float f) {
        return Float.isNaN(f) || Float.isInfinite(f);
    }

    private static IllegalArgumentException decodeError(String msg) {
        return new IllegalArgumentException("[glasswork:net] Decode error: " + msg);
    }

    // ----------------- Payloads -----------------

    public record PutQuads(SectionPos section, List<InjectedQuad> quads) implements CustomPacketPayload {
        public static final Type<PutQuads> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath("glasswork", "put_quads"));

        public static final StreamCodec<FriendlyByteBuf, PutQuads> CODEC = CustomPacketPayload.codec(
                // ENCODE
                (p, buf) -> {
                    final long startIdx = buf.writerIndex();
                    buf.writeLong(p.section.asLong());
                    final List<InjectedQuad> list = p.quads;
                    final int n = list.size();

                    // Hard guard to avoid giant packets
                    if (n < 0 || n > MAX_QUADS_PER_PACKET) {
                        throw new IllegalArgumentException("[glasswork:net] PutQuads encode: quad count out of bounds: " + n);
                    }
                    buf.writeVarInt(n);
                    for (int i = 0; i < n; i++) {
                        writeQuad(buf, list.get(i));
                    }
                    final int bytes = buf.writerIndex() - (int) startIdx;
                    if (bytes > MAX_PACKET_BYTES_EST) {
                        throw new IllegalArgumentException("[glasswork:net] PutQuads encode: estimated packet too large: " + bytes + " bytes");
                    }
                },
                // DECODE
                buf -> {
                    final long secLong = buf.readLong();
                    final SectionPos sec = SectionPos.of(secLong);

                    final int n = buf.readVarInt();
                    if (n < 0 || n > MAX_QUADS_PER_PACKET) {
                        throw decodeError("PutQuads quad count out of bounds: " + n);
                    }

                    final ArrayList<InjectedQuad> out = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        out.add(readQuad(buf));
                    }
                    // Immutable to discourage accidental mutation downstream
                    return new PutQuads(sec, List.copyOf(out));
                }
        );

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record RemoveQuads(SectionPos section) implements CustomPacketPayload {
        public static final Type<RemoveQuads> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath("glasswork", "remove_quads"));

        public static final StreamCodec<FriendlyByteBuf, RemoveQuads> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeLong(p.section.asLong()),
                buf -> new RemoveQuads(SectionPos.of(buf.readLong()))
        );

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}