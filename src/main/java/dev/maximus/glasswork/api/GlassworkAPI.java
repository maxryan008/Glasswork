package dev.maximus.glasswork.api;

import dev.maximus.glasswork.GlassworkMetrics;
import dev.maximus.glasswork.net.GlassworkPackets;
import dev.maximus.glasswork.util.Log;
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

/**
 * Central public API for injecting and managing custom translucent quads.
 * <p>
 * Thread-safety:
 * <ul>
 *   <li>Backed by concurrent collections; methods are safe to call from render or server threads.</li>
 *   <li>Returned lists are <b>immutable snapshots</b> (via {@code List.copyOf})—do not mutate.</li>
 * </ul>
 *
 * Lifetime / ownership:
 * <ul>
 *   <li>Persistent quads are keyed by {@link SectionPos} and remain until {@link #removeAll(Level, SectionPos)} or
 *       {@link #_internalClearAll()} is called.</li>
 *   <li>Frame quads are one-shot; they are consumed by {@link #_drainFrameQuads()} once per frame on the client.</li>
 * </ul>
 *
 * Failure policy:
 * <ul>
 *   <li>Public entry points are lenient: nulls cause a warn log and a no-op (or a cleanup), not exceptions.</li>
 *   <li>Server broadcast helpers log recipients and sizes for easier debugging.</li>
 * </ul>
 */
public final class GlassworkAPI {

    /** Persistent user quads per section. */
    private static final Map<SectionPos, List<InjectedQuad>> QUADS = new ConcurrentHashMap<>();
    /** Monotonic generation counter per section (increments on changes). */
    private static final Map<SectionPos, Integer> VER = new ConcurrentHashMap<>();
    /** The last uploaded generation seen by the client per section. */
    private static final Map<SectionPos, Integer> LAST_UP = new ConcurrentHashMap<>();
    /** One-frame transient quads injected by game logic and consumed by the renderer. */
    private static final Queue<InjectedQuad> FRAME = new ConcurrentLinkedQueue<>();

    private GlassworkAPI() {}

    /* ===========================
       Public API (stable)
       =========================== */

    /**
     * Replace all persistent quads for a given section.
     * <p>
     * Behavior:
     * <ul>
     *   <li>If {@code quads} is null or empty, this is equivalent to {@link #removeAll(Level, SectionPos)}.</li>
     *   <li>Input is sanitized: any {@code null} entries are discarded; the remainder is stored as an immutable list.</li>
     *   <li>On change, the section's generation is bumped so clients know to re-upload.</li>
     * </ul>
     *
     * @param level   the level (currently unused; reserved for future hooks); may be {@code null}
     * @param section the section to replace data for; if {@code null}, logs a warning and no-ops
     * @param quads   the new set of quads; if {@code null} or empty, clears the section
     */
    public static void put(Level level, SectionPos section, Collection<InjectedQuad> quads) {
        if (section == null) {
            Log.w("[api.put] section=null (level={}) -> no-op", level);
            return;
        }
        if (quads == null || quads.isEmpty()) {
            Log.d("[api.put] empty input -> removeAll for section={}", section);
            removeAll(level, section);
            return;
        }

        // Sanitize: drop nulls
        int in = quads.size();
        ArrayList<InjectedQuad> clean = new ArrayList<>(in);
        for (InjectedQuad q : quads) if (q != null) clean.add(q);
        if (clean.isEmpty()) {
            Log.w("[api.put] all quads null -> removeAll for section={} (input={})", section, in);
            removeAll(level, section);
            return;
        }
        if (clean.size() != in) {
            Log.w("[api.put] discarded {} null quad(s) (kept={}) for section={}", (in - clean.size()), clean.size(), section);
        }

        QUADS.put(section, List.copyOf(clean));
        _bumpGeneration(section);
        Log.d("[api.put] stored={} quads section={} gen={}", clean.size(), section, VER.get(section));
    }

    /**
     * Remove all persistent user quads in a section and reset its bookkeeping.
     *
     * @param level   the level (unused; reserved); may be {@code null}
     * @param section the section to clear; if {@code null}, logs a warning and no-ops
     */
    public static void removeAll(Level level, SectionPos section) {
        if (section == null) {
            Log.w("[api.removeAll] section=null (level={}) -> no-op", level);
            return;
        }
        QUADS.remove(section);
        VER.remove(section);
        LAST_UP.remove(section);
        Log.d("[api.removeAll] cleared section={}", section);
    }

    /**
     * Server-side helper: broadcast a persistent {@code PUT} update for a section to all watching players.
     * <p>
     * Use this when the server owns the authoritative quad set and wants to push it to clients tracking
     * {@link ChunkPos} of the given section.
     *
     * @param level   server level (required)
     * @param section section to update; if {@code null}, logs a warning and no-ops
     * @param quads   quads to send; nulls are not allowed here (will be ignored if present)
     */
    public static void serverPut(ServerLevel level, SectionPos section, Collection<InjectedQuad> quads) {
        if (section == null) {
            Log.w("[api.serverPut] section=null -> no-op");
            return;
        }
        if (level == null) {
            Log.e("[api.serverPut] level=null for section={} -> no-op", section);
            return;
        }
        if (quads == null || quads.isEmpty()) {
            Log.w("[api.serverPut] empty quads for section={} -> consider serverRemoveAll instead", section);
        }

        // sanitize nulls to avoid codec surprises
        ArrayList<InjectedQuad> clean = new ArrayList<>(quads == null ? 0 : quads.size());
        if (quads != null) for (InjectedQuad q : quads) if (q != null) clean.add(q);

        var payload = new GlassworkPackets.PutQuads(section, List.copyOf(clean));
        var chunk = new ChunkPos(section.x(), section.z());
        int sent = 0;
        for (ServerPlayer p : PlayerLookup.tracking(level, chunk)) {
            try {
                ServerPlayNetworking.send(p, payload);
                sent++;
                GlassworkMetrics.recordServerPut(p.getGameProfile().getName(), clean.size()); // <--- add
            } catch (Throwable t) {
                Log.e(t, "[api.serverPut] failed to send PutQuads to {} section={}", p.getGameProfile().getName(), section);
            }
        }
        Log.i("[api.serverPut] section={} quads={} recipients={} (~{} B)", section, clean.size(), sent,
                GlassworkMetrics.estimateBytesForQuads(clean));
    }

    /**
     * Server-side helper: broadcast a persistent {@code REMOVE} for a section to all watching players.
     *
     * @param level   server level (required)
     * @param section section to clear; if {@code null}, logs a warning and no-ops
     */
    public static void serverRemoveAll(ServerLevel level, SectionPos section) {
        if (section == null) {
            Log.w("[api.serverRemoveAll] section=null -> no-op");
            return;
        }
        if (level == null) {
            Log.e("[api.serverRemoveAll] level=null for section={} -> no-op", section);
            return;
        }

        var payload = new GlassworkPackets.RemoveQuads(section);
        var chunk = new ChunkPos(section.x(), section.z());
        int sent = 0;
        for (ServerPlayer p : PlayerLookup.tracking(level, chunk)) {
            try {
                ServerPlayNetworking.send(p, payload);
                sent++;
                GlassworkMetrics.recordServerRemove(p.getGameProfile().getName()); // <--- add
            } catch (Throwable t) {
                Log.e(t, "[api.serverRemoveAll] failed to send RemoveQuads to {} section={}", p.getGameProfile().getName(), section);
            }
        }
        Log.i("[api.serverRemoveAll] section={} recipients={}", section, sent);
    }

    /**
     * Convenience utility: compute a {@link SectionPos} for a given block position.
     * <p>
     * This method never throws, but will log and return the origin section if {@code pos} is null.
     *
     * @param pos block position; if {@code null}, logs a warning and returns {@code SectionPos.of(0,0,0)}
     * @return the section containing {@code pos}, or the origin section if {@code pos} is null
     */
    public static SectionPos sectionFor(BlockPos pos) {
        if (pos == null) {
            Log.w("[api.sectionFor] pos=null -> returning origin section");
            return SectionPos.of(0, 0, 0);
        }
        return SectionPos.of(pos);
    }

    /**
     * Queue a one-frame quad to be rendered on the next client frame.
     * <p>
     * This does not affect persistent section quads; it is intended for transient effects.
     *
     * @param quad the quad to submit; if {@code null}, logs a warning and no-ops
     */
    public static void submitFrameQuad(InjectedQuad quad) {
        if (quad == null) {
            Log.w("[api.submitFrameQuad] quad=null -> no-op");
            return;
        }
        FRAME.add(quad);
        GlassworkMetrics.recordClientFrameSubmit();
    }

    /* ===========================
       INTERNAL (used by render/mixins)
       =========================== */

    /**
     * Retrieve the current immutable snapshot of persistent quads for a section.
     *
     * @param section the section key (must not be null)
     * @return immutable list; never {@code null} (empty if none exist)
     */
    public static List<InjectedQuad> _getQuads(SectionPos section) {
        if (section == null) return Collections.emptyList();
        return QUADS.getOrDefault(section, Collections.emptyList());
    }

    /**
     * Determine if a section needs (re)upload: returns {@code true} if its generation has changed
     * since the last successful upload and there is at least one quad present.
     *
     * @param section the section key
     * @return {@code true} if an upload is required
     */
    public static boolean _needsUpload(SectionPos section) {
        if (section == null) return false;
        return !Objects.equals(VER.get(section), LAST_UP.get(section)) && !_getQuads(section).isEmpty();
    }

    /**
     * Mark a section as "uploaded now" by copying its current generation into the last-uploaded map.
     * <p>
     * If the section has no generation yet, this records {@code 0}.
     *
     * @param section the section key
     */
    public static void _markUploaded(SectionPos section) {
        if (section == null) return;
        LAST_UP.put(section, VER.getOrDefault(section, 0));
    }

    /**
     * Force a re-upload next frame without touching persistent user data by bumping the section generation.
     *
     * @param section the section key
     */
    public static void _bumpGeneration(SectionPos section) {
        if (section == null) return;
        // Keep positive and avoid overflow wrap into negative
        VER.merge(section, 1, (a, b) -> (a + b) & 0x7fffffff);
        GlassworkMetrics.recordClientUploadTrigger();
    }

    /**
     * Mark a section "dirty" by forgetting its last upload stamp. Persistent quads (if any) are preserved.
     * <p>
     * The next call to {@link #_needsUpload(SectionPos)} will return true if quads are present.
     *
     * @param section the section key
     */
    public static void _clearSection(SectionPos section) {
        if (section == null) return;
        LAST_UP.remove(section);
    }

    /**
     * Drain and return all one-frame quads submitted since the previous drain.
     * <p>
     * The returned list is a fresh, mutable buffer; callers own it and may reuse it.
     *
     * @return list of quads; never {@code null} (empty when none)
     */
    public static List<InjectedQuad> _drainFrameQuads() {
        ArrayList<InjectedQuad> out = new ArrayList<>(FRAME.size());
        for (InjectedQuad q; (q = FRAME.poll()) != null; ) out.add(q);
        GlassworkMetrics.recordClientFrameDrain(out.size());
        return out;
    }

    /**
     * Global cleanup: clears all persistent and transient data structures.
     * <p>
     * Call on client disconnect or process shutdown. Safe to call multiple times.
     */
    public static void _internalClearAll() {
        QUADS.clear();
        VER.clear();
        LAST_UP.clear();
        FRAME.clear();
        Log.d("[api.clearAll] all maps/queues cleared");
    }

    public static java.util.Map<net.minecraft.core.SectionPos, java.util.List<dev.maximus.glasswork.api.InjectedQuad>> _debugSnapshot() {
        return java.util.Collections.unmodifiableMap(QUADS);
    }
}