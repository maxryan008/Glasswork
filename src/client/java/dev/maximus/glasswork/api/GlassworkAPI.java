package dev.maximus.glasswork.api;

import dev.maximus.glasswork.GlassworkMetrics;
import dev.maximus.glasswork.util.Log;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Pure client-side quad store & frame queue. Thread-safe; snapshots are immutable. */
public final class GlassworkAPI {

    private static final Map<SectionPos, List<InjectedQuad>> QUADS = new ConcurrentHashMap<>();
    private static final Map<SectionPos, Integer> VER  = new ConcurrentHashMap<>();
    private static final Map<SectionPos, Integer> LAST = new ConcurrentHashMap<>();
    private static final Queue<InjectedQuad> FRAME    = new ConcurrentLinkedQueue<>();

    private GlassworkAPI() {}

    /** Replace all persistent quads for a section (null/empty → clear). */
    public static void put(SectionPos section, Collection<InjectedQuad> quads) {
        if (section == null) {
            Log.w("[api.put] section=null -> no-op");
            return;
        }
        if (quads == null || quads.isEmpty()) {
            removeAll(section);
            return;
        }
        int in = quads.size();
        ArrayList<InjectedQuad> clean = new ArrayList<>(in);
        for (InjectedQuad q : quads) if (q != null) clean.add(q);
        if (clean.isEmpty()) {
            removeAll(section);
            return;
        }
        if (clean.size() != in) {
            Log.w("[api.put] discarded {} null quad(s) (kept={}) for section={}", (in - clean.size()), clean.size(), section);
        }
        QUADS.put(section, List.copyOf(clean));
        _bumpGeneration(section);
    }

    /** Clear persistent quads for a section. */
    public static void removeAll(SectionPos section) {
        if (section == null) return;
        QUADS.remove(section);
        VER.remove(section);
        LAST.remove(section);
        Log.d("[api.removeAll] cleared section={}", section);
    }

    /** Convert a block position to its section. */
    public static SectionPos sectionFor(BlockPos pos) {
        return (pos == null) ? SectionPos.of(0, 0, 0) : SectionPos.of(pos);
    }

    /** Submit a one-frame quad to render next frame. */
    public static void submitFrameQuad(InjectedQuad quad) {
        if (quad == null) return;
        FRAME.add(quad);
        GlassworkMetrics.recordClientFrameSubmit();
    }

    /* ===== INTERNAL (client renderer) ===== */

    public static List<InjectedQuad> _getQuads(SectionPos section) {
        if (section == null) return Collections.emptyList();
        return QUADS.getOrDefault(section, Collections.emptyList());
    }

    public static boolean _needsUpload(SectionPos section) {
        if (section == null) return false;
        return !Objects.equals(VER.get(section), LAST.get(section)) && !_getQuads(section).isEmpty();
    }

    public static void _markUploaded(SectionPos section) {
        if (section == null) return;
        LAST.put(section, VER.getOrDefault(section, 0));
    }

    public static void _bumpGeneration(SectionPos section) {
        if (section == null) return;
        VER.merge(section, 1, (a, b) -> (a + b) & 0x7fffffff);
        GlassworkMetrics.recordClientUploadTrigger();
    }

    public static void _clearSection(SectionPos section) {
        if (section == null) return;
        LAST.remove(section);
    }

    public static List<InjectedQuad> _drainFrameQuads() {
        ArrayList<InjectedQuad> out = new ArrayList<>(FRAME.size());
        for (InjectedQuad q; (q = FRAME.poll()) != null; ) out.add(q);
        GlassworkMetrics.recordClientFrameDrain(out.size());
        return out;
    }

    public static Map<SectionPos, List<InjectedQuad>> _debugSnapshot() {
        return java.util.Collections.unmodifiableMap(QUADS);
    }

    public static void _internalClearAll() {
        QUADS.clear(); VER.clear(); LAST.clear(); FRAME.clear();
        Log.d("[api.clearAll] all maps/queues cleared");
    }
}