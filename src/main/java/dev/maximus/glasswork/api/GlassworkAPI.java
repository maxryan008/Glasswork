package dev.maximus.glasswork.api;

import dev.maximus.glasswork.net.GlassworkPackets;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class GlassworkAPI {
    private GlassworkAPI() {}

    private static final Map<SectionPos, List<InjectedQuad>> QUADS = new ConcurrentHashMap<>();

    private static final Map<SectionPos, Integer> VER = new ConcurrentHashMap<>();

    private static final Map<SectionPos, Integer> LAST_UP = new ConcurrentHashMap<>();

    private static final Queue<InjectedQuad> FRAME = new ConcurrentLinkedQueue<>();

    /* ===========================
       Public API (stable)
       =========================== */

    /** Replace quads for a section (persistent until removeAll). */
    public static void put(Level level, SectionPos section, Collection<InjectedQuad> quads) {
        if (section == null) return;
        if (quads == null || quads.isEmpty()) {
            removeAll(level, section);
            return;
        }
        QUADS.put(section, List.copyOf(quads));
        _bumpGeneration(section);
    }

    /** Remove all user quads in a section. */
    public static void removeAll(Level level, SectionPos section) {
        if (section == null) return;
        QUADS.remove(section);
        VER.remove(section);
        LAST_UP.remove(section);
    }

    /** Server helper: broadcast PUT to clients tracking this section's chunk. */
    public static void serverPut(ServerLevel level, SectionPos section, Collection<InjectedQuad> quads) {
        if (section == null) return;
        var payload = new GlassworkPackets.PutQuads(section, List.copyOf(quads));
        var chunk = new ChunkPos(section.x(), section.z());
        for (ServerPlayer p : PlayerLookup.tracking(level, chunk)) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    /** Server helper: broadcast REMOVE to clients tracking this section's chunk. */
    public static void serverRemoveAll(ServerLevel level, SectionPos section) {
        if (section == null) return;
        var payload = new GlassworkPackets.RemoveQuads(section);
        var chunk = new ChunkPos(section.x(), section.z());
        for (ServerPlayer p : PlayerLookup.tracking(level, chunk)) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    /** Convenience to compute section from block pos. */
    public static SectionPos sectionFor(BlockPos pos) {
        return SectionPos.of(pos);
    }

    public static void submitFrameQuad(InjectedQuad quad) {
        if (quad != null) FRAME.add(quad);
    }

    /* ===========================
       INTERNAL (used by mixins)
       =========================== */

    /** Get current persistent quads (never null). */
    public static List<InjectedQuad> _getQuads(SectionPos section) {
        return QUADS.getOrDefault(section, Collections.emptyList());
    }

    /** Has this section changed (or been bumped) since last upload? */
    public static boolean _needsUpload(SectionPos section) {
        return !Objects.equals(VER.get(section), LAST_UP.get(section)) && !_getQuads(section).isEmpty();
    }

    /** Stamp "uploaded now". */
    public static void _markUploaded(SectionPos section) {
        LAST_UP.put(section, VER.getOrDefault(section, 0));
    }

    /** Force a re-upload next frame without touching user data. */
    public static void _bumpGeneration(SectionPos section) {
        VER.merge(section, 1, (a, b) -> (a + b) & 0x7fffffff);
    }

    /**
     * On section dirty: DO NOT remove user quads.
     * Only forget last upload stamp so we re-upload as soon as possible.
     */
    public static void _clearSection(SectionPos section) {
        LAST_UP.remove(section);
    }

    public static List<InjectedQuad> _drainFrameQuads() {
        ArrayList<InjectedQuad> out = new ArrayList<>(FRAME.size());
        for (InjectedQuad q; (q = FRAME.poll()) != null; ) out.add(q);
        return out;
    }

    /** Global cleanup on disconnect/exit. */
    public static void _internalClearAll() {
        QUADS.clear();
        VER.clear();
        LAST_UP.clear();
        FRAME.clear();
    }
}