package dev.maximus.glasswork;

import dev.maximus.glasswork.api.InjectedQuad;
import net.minecraft.core.SectionPos;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

public final class GlassworkMetrics {
    private GlassworkMetrics() {}

    // Frame / upload counters
    private static final LongAdder frameQuadsSubmitted = new LongAdder();
    private static final LongAdder frameDrainCalls     = new LongAdder();
    private static final LongAdder uploadsTriggered    = new LongAdder();

    public static void recordClientFrameSubmit()   { frameQuadsSubmitted.increment(); }
    public static void recordClientFrameDrain(int drained) { frameDrainCalls.increment(); }
    public static void recordClientUploadTrigger() { uploadsTriggered.increment(); }

    public static long clientFrameSubmits()     { return frameQuadsSubmitted.sum(); }
    public static long clientFrameDrains()      { return frameDrainCalls.sum(); }
    public static long clientUploadsTriggered() { return uploadsTriggered.sum(); }

    // Mesh counters
    private static final LongAdder meshStores   = new LongAdder();
    private static final LongAdder meshReplaces = new LongAdder();
    private static final LongAdder meshRemoves  = new LongAdder();
    private static final LongAdder meshMerges   = new LongAdder();
    private static final LongAdder meshBytesIn  = new LongAdder();
    private static final LongAdder meshBytesOut = new LongAdder();
    private static final LongAdder meshErrors   = new LongAdder();

    public static void recordClientMeshStore(long srcBytes, long outBytes) {
        meshStores.increment(); meshBytesIn.add(srcBytes); meshBytesOut.add(outBytes);
    }
    public static void recordClientMeshReplace(long srcBytes, long outBytes) {
        meshReplaces.increment(); meshBytesIn.add(srcBytes); meshBytesOut.add(outBytes);
    }
    public static void recordClientMeshRemove(long freedBytes, long count) {
        meshRemoves.add(count);
    }
    public static void recordClientMeshMerge(long aBytes, long bBytes, long outBytes) {
        meshMerges.increment(); meshBytesIn.add(aBytes + bBytes); meshBytesOut.add(outBytes);
    }
    public static void recordClientMeshMergeError(String reason) {
        meshErrors.increment();
    }

    public static long clientMeshStores()   { return meshStores.sum(); }
    public static long clientMeshReplaces() { return meshReplaces.sum(); }
    public static long clientMeshRemoves()  { return meshRemoves.sum(); }
    public static long clientMeshMerges()   { return meshMerges.sum(); }
    public static long clientMeshBytesIn()  { return meshBytesIn.sum(); }
    public static long clientMeshBytesOut() { return meshBytesOut.sum(); }
    public static long clientMeshErrors()   { return meshErrors.sum(); }

    // Helpers
    public static long estimateBytesForQuads(List<InjectedQuad> quads) {
        return Math.round(quads.size() * 4 * 52 * 1.1);
    }

    public static int countNonEmptySections(Map<SectionPos, List<InjectedQuad>> quadsBySection) {
        int c = 0;
        for (var e : quadsBySection.entrySet()) if (!e.getValue().isEmpty()) c++;
        return c;
    }
}