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

    public record PutQuads(SectionPos section, List<InjectedQuad> quads) implements CustomPacketPayload {
        public static final Type<PutQuads> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("glasswork", "put_quads"));
        public static final StreamCodec<FriendlyByteBuf, PutQuads> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeLong(p.section.asLong());
                    var list = p.quads;
                    buf.writeVarInt(list.size());
                    for (InjectedQuad q : list) writeQuad(buf, q);
                },
                buf -> {
                    var sec = SectionPos.of(buf.readLong());
                    int n = buf.readVarInt();
                    List<InjectedQuad> out = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) out.add(readQuad(buf));
                    return new PutQuads(sec, out);
                }
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record RemoveQuads(SectionPos section) implements CustomPacketPayload {
        public static final Type<RemoveQuads> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("glasswork", "remove_quads"));
        public static final StreamCodec<FriendlyByteBuf, RemoveQuads> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeLong(p.section.asLong()),
                buf -> new RemoveQuads(SectionPos.of(buf.readLong()))
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static void writeQuad(FriendlyByteBuf buf, InjectedQuad q) {
        writeV(buf, q.v1()); writeV(buf, q.v2()); writeV(buf, q.v3()); writeV(buf, q.v4());
    }
    private static InjectedQuad readQuad(FriendlyByteBuf buf) {
        QuadVertex v1 = readV(buf), v2 = readV(buf), v3 = readV(buf), v4 = readV(buf);
        return new InjectedQuad(v1, v2, v3, v4);
    }

    private static void writeV(FriendlyByteBuf b, QuadVertex v) {
        b.writeFloat(v.x()); b.writeFloat(v.y()); b.writeFloat(v.z());
        b.writeFloat(v.u()); b.writeFloat(v.v());
        b.writeInt(v.color());
        b.writeInt(v.light());
        b.writeShort((short) v.overlay());
        b.writeFloat(v.nx()); b.writeFloat(v.ny()); b.writeFloat(v.nz());
    }
    private static QuadVertex readV(FriendlyByteBuf b) {
        float x=b.readFloat(), y=b.readFloat(), z=b.readFloat();
        float u=b.readFloat(), v=b.readFloat();
        int color=b.readInt();
        int light=b.readInt();
        int overlay=b.readShort() & 0xFFFF;
        float nx=b.readFloat(), ny=b.readFloat(), nz=b.readFloat();
        return new QuadVertex(x,y,z, u,v, color, light, overlay, nx,ny,nz);
    }
}