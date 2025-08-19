package dev.maximus.glasswork.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Public API for submitting translucent quads into world sections.
 * Call from the client thread.
 */
public final class GlassworkAPI {
    private GlassworkAPI() {}

    // Per-section persistent quads
    private static final Map<SectionPos, List<InjectedQuad>> QUADS = new ConcurrentHashMap<>();
    private static final Map<SectionPos, Integer> VER = new ConcurrentHashMap<>();
    private static final Map<SectionPos, Integer> LAST_UP = new ConcurrentHashMap<>();

    // Per-frame world-space overlay (optional future use)
    private static final Queue<InjectedQuad> FRAME = new ConcurrentLinkedQueue<>();

    /** Replace quads for a section. Pass empty/null to remove. */
    public static void put(Level level, SectionPos section, Collection<InjectedQuad> quads) {
        if (section == null) return;
        if (quads == null || quads.isEmpty()) {
            removeAll(level, section);
            return;
        }
        QUADS.put(section, List.copyOf(quads));
        VER.merge(section, 1, (a, b) -> (a + b) & 0x7fffffff);
    }

    /** Remove all custom quads in a section. */
    public static void removeAll(Level level, SectionPos section) {
        QUADS.remove(section);
        VER.remove(section);
        LAST_UP.remove(section);
    }

    /** Convenience. */
    public static SectionPos sectionFor(BlockPos pos) {
        return SectionPos.of(pos);
    }

    // ---------- INTERNAL (used by mixins) ----------

    public static List<InjectedQuad> _getQuads(SectionPos section) {
        return QUADS.getOrDefault(section, Collections.emptyList());
    }

    public static boolean _needsUpload(SectionPos section) {
        return !Objects.equals(VER.get(section), LAST_UP.get(section));
    }

    public static void _markUploaded(SectionPos section) {
        LAST_UP.put(section, VER.getOrDefault(section, 0));
    }

    /** Called when a section gets dirty/unloaded. */
    public static void _clearSection(SectionPos section) {
        removeAll(null, section);
    }

    /** Submit a world-space quad for just this frame (optional overlay path). */
    public static void submitFrameQuad(InjectedQuad quad) {
        if (quad != null) FRAME.add(quad);
    }

    public static List<InjectedQuad> _drainFrameQuads() {
        ArrayList<InjectedQuad> out = new ArrayList<>(FRAME.size());
        for (InjectedQuad q; (q = FRAME.poll()) != null; ) out.add(q);
        return out;
    }

    /** Clear all API storage (disconnect/shutdown). */
    public static void _internalClearAll() {
        QUADS.clear(); VER.clear(); LAST_UP.clear(); FRAME.clear();
    }
}