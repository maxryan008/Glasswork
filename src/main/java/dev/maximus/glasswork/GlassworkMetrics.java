package dev.maximus.glasswork;

import dev.maximus.glasswork.api.InjectedQuad;
import net.minecraft.core.SectionPos;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Centralized counters for Glasswork. All methods are thread-safe.
 * Keep it simple: only increment where events happen; compute derived
 * numbers on demand in commands.
 */
public final class GlassworkMetrics {
    private GlassworkMetrics() {}

    // --- Server-side counters ---
    private static final LongAdder packetsPutSent = new LongAdder();
    private static final LongAdder packetsRemoveSent = new LongAdder();
    private static final LongAdder quadsSent = new LongAdder();
    private static final Map<String, LongAdder> perPlayerPackets = new ConcurrentHashMap<>();
    private static final Map<String, LongAdder> perPlayerQuads = new ConcurrentHashMap<>();

    // --- Client-side counters ---
    private static final LongAdder frameQuadsSubmitted = new LongAdder();
    private static final LongAdder frameDrainCalls = new LongAdder();
    private static final LongAdder uploadsTriggered = new LongAdder();

    public static void recordServerPut(String playerName, int quadsCount) {
        packetsPutSent.increment();
        quadsSent.add(quadsCount);
        perPlayerPackets.computeIfAbsent(playerName, k -> new LongAdder()).increment();
        perPlayerQuads.computeIfAbsent(playerName, k -> new LongAdder()).add(quadsCount);
    }

    public static void recordServerRemove(String playerName) {
        packetsRemoveSent.increment();
        perPlayerPackets.computeIfAbsent(playerName, k -> new LongAdder()).increment();
    }

    public static void recordClientFrameSubmit() { frameQuadsSubmitted.increment(); }
    public static void recordClientFrameDrain(int drained) { frameDrainCalls.increment(); }

    /** Called when the client marks a section for (re)upload. */
    public static void recordClientUploadTrigger() { uploadsTriggered.increment(); }

    // --- Snapshots for commands ---
    public static long totalPacketsPut() { return packetsPutSent.sum(); }
    public static long totalPacketsRemove() { return packetsRemoveSent.sum(); }
    public static long totalQuadsSent() { return quadsSent.sum(); }
    public static Map<String, Long> perPlayerPacketSnapshot() {
        return perPlayerPackets.entrySet().stream().collect(
                java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue().sum())
        );
    }
    public static Map<String, Long> perPlayerQuadSnapshot() {
        return perPlayerQuads.entrySet().stream().collect(
                java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue().sum())
        );
    }

    public static long clientFrameSubmits() { return frameQuadsSubmitted.sum(); }
    public static long clientFrameDrains() { return frameDrainCalls.sum(); }
    public static long clientUploadsTriggered() { return uploadsTriggered.sum(); }

    // Convenience estimators
    public static long estimateBytesForQuads(List<InjectedQuad> quads) {
        // ~52 bytes/vertex * 4 vertices; add ~10% overhead
        return Math.round(quads.size() * 4 * 52 * 1.1);
    }

    // Optional: count currently tracked sections on client; caller supplies maps.
    public static int countNonEmptySections(Map<SectionPos, List<InjectedQuad>> quadsBySection) {
        int c = 0;
        for (var e : quadsBySection.entrySet()) if (!e.getValue().isEmpty()) c++;
        return c;
    }

    // Client-side mesh counters
    private static final java.util.concurrent.atomic.LongAdder meshStores = new java.util.concurrent.atomic.LongAdder();
    private static final java.util.concurrent.atomic.LongAdder meshReplaces = new java.util.concurrent.atomic.LongAdder();
    private static final java.util.concurrent.atomic.LongAdder meshRemoves = new java.util.concurrent.atomic.LongAdder();
    private static final java.util.concurrent.atomic.LongAdder meshMerges = new java.util.concurrent.atomic.LongAdder();
    private static final java.util.concurrent.atomic.LongAdder meshBytesIn = new java.util.concurrent.atomic.LongAdder();
    private static final java.util.concurrent.atomic.LongAdder meshBytesOut = new java.util.concurrent.atomic.LongAdder();
    private static final java.util.concurrent.atomic.LongAdder meshErrors = new java.util.concurrent.atomic.LongAdder();

    public static void recordClientMeshStore(long srcBytes, long outBytes) {
        meshStores.increment(); meshBytesIn.add(srcBytes); meshBytesOut.add(outBytes);
    }
    public static void recordClientMeshReplace(long srcBytes, long outBytes) {
        meshReplaces.increment(); meshBytesIn.add(srcBytes); meshBytesOut.add(outBytes);
    }
    public static void recordClientMeshRemove(long freedBytes, long count) {
        meshRemoves.add(count); /* freedBytes is informational; not tracked separately here */
    }
    public static void recordClientMeshMerge(long aBytes, long bBytes, long outBytes) {
        meshMerges.increment(); meshBytesIn.add(aBytes + bBytes); meshBytesOut.add(outBytes);
    }
    public static void recordClientMeshMergeError(String reason) {
        meshErrors.increment();
    }

    // Expose snapshots for /gwc stats
    public static long clientMeshStores()   { return meshStores.sum(); }
    public static long clientMeshReplaces() { return meshReplaces.sum(); }
    public static long clientMeshRemoves()  { return meshRemoves.sum(); }
    public static long clientMeshMerges()   { return meshMerges.sum(); }
    public static long clientMeshBytesIn()  { return meshBytesIn.sum(); }
    public static long clientMeshBytesOut() { return meshBytesOut.sum(); }
    public static long clientMeshErrors()   { return meshErrors.sum(); }
}