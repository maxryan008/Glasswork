package dev.maximus.glasswork.api;

import dev.maximus.glasswork.GlassworkMetrics;
import dev.maximus.glasswork.util.Log;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

/** Pure client-side quad store & frame queue. Thread-safe; snapshots are immutable. */
public final class GlassworkAPI {

    /* ===========================
       Public types
       =========================== */

    /** How to map the sprite to the quad. */
    public enum UVMode {
        /** Map the whole sprite across the entire quad. */
        STRETCH,
        /** Tile the sprite once per world unit along the quad's two in-plane axes. */
        TILE
    }

    /* ===========================
       Data
       =========================== */

    private static final Map<SectionPos, List<InjectedQuad>> QUADS = new ConcurrentHashMap<>();
    private static final Map<SectionPos, Integer> VER  = new ConcurrentHashMap<>();
    private static final Map<SectionPos, Integer> LAST = new ConcurrentHashMap<>();
    private static final Queue<InjectedQuad> FRAME    = new ConcurrentLinkedQueue<>();

    private GlassworkAPI() {}

    /* ===========================
       Existing API (unchanged)
       =========================== */

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

    /* ===========================
       NEW convenience builders
       =========================== */

    /**
     * Build and append one textured quad using a block texture id.
     *
     * @param section target section (required)
     * @param textureId atlas id like {@code minecraft:block/stone}
     * @param v1   world-space corner (must be coplanar, in order)
     * @param v2   world-space corner (must be coplanar, in order)
     * @param v3   world-space corner (must be coplanar, in order)
     * @param v4   world-space corner (must be coplanar, in order)
     * @param tintARGB ARGB color multiplier (0xAARRGGBB)
     * @param light    packed light
     * @param opacity  0..1, multiplies alpha channel
     * @param uvMode   STRETCH or TILE
     */
    public static void putBlockTexture(SectionPos section,
                                       ResourceLocation textureId,
                                       Vector3f v1, Vector3f v2, Vector3f v3, Vector3f v4,
                                       int tintARGB, int light, float opacity, UVMode uvMode) {
        if (section == null || textureId == null) return;
        TextureAtlasSprite sprite = resolveSprite(textureId);
        if (sprite == null) { Log.w("[api.putBlockTexture(id)] sprite not found: {}", textureId); return; }
        var quads = buildTexturedQuads(sprite, v1, v2, v3, v4, tintARGB, light, opacity, uvMode);
        _appendQuads(section, quads);
    }

    /**
     * Build and append one textured quad using a block's face sprite if available.
     * Accepts aliases for face: up/top, down/bottom, north/front, south/back, east, west.
     */
    public static void putBlockTexture(SectionPos section,
                                       Block block, String face,
                                       Vector3f v1, Vector3f v2, Vector3f v3, Vector3f v4,
                                       int tintARGB, int light, float opacity, UVMode uvMode) {
        if (section == null || block == null) return;
        TextureAtlasSprite sprite = resolveBlockFaceSprite(block, parseFace(face));
        if (sprite == null) {
            Log.w("[api.putBlockTexture(block)] face sprite missing for {} face={} -> particle", block, face);
            sprite = resolveParticleSprite(block);
            if (sprite == null) return;
        }
        var quads = buildTexturedQuads(sprite, v1, v2, v3, v4, tintARGB, light, opacity, uvMode);
        _appendQuads(section, quads);
    }

    /**
     * Build and append one textured quad using a fluid's sprite (still or flowing).
     * If Fabric FluidRenderHandler is present, uses it; otherwise falls back to vanilla water/lava ids.
     *
     * @param animated true → flowing sprite if available; false → still sprite
     */
    public static void putLiquidTexture(SectionPos section,
                                        Fluid fluid, boolean animated,
                                        Vector3f v1, Vector3f v2, Vector3f v3, Vector3f v4,
                                        int tintARGB, int light, float opacity, UVMode uvMode) {
        if (section == null || fluid == null) return;
        TextureAtlasSprite sprite = resolveFluidSprite(fluid, animated);
        if (sprite == null) return;
        var quads = buildTexturedQuads(sprite, v1, v2, v3, v4, tintARGB, light, opacity, uvMode);
        _appendQuads(section, quads);
    }

    /* ===========================
       INTERNAL (render/mixins)
       =========================== */

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

    /* ===========================
       Helpers
       =========================== */

    /** Append a quad to the section (preserves existing quads). */
    private static void _appendQuad(SectionPos section, InjectedQuad q) {
        if (q == null) return;
        QUADS.merge(section, List.of(q), (oldL, one) -> {
            ArrayList<InjectedQuad> merged = new ArrayList<>(oldL.size() + 1);
            merged.addAll(oldL);
            merged.addAll(one);
            return List.copyOf(merged);
        });
        _bumpGeneration(section);
    }

    /** Append multiple quads to the section. */
    private static void _appendQuads(SectionPos section, List<InjectedQuad> add) {
        if (add == null || add.isEmpty()) return;
        QUADS.merge(section, List.copyOf(add), (oldL, more) -> {
            ArrayList<InjectedQuad> merged = new ArrayList<>(oldL.size() + more.size());
            merged.addAll(oldL);
            merged.addAll(more);
            return List.copyOf(merged);
        });
        _bumpGeneration(section);
    }

    private static TextureAtlasSprite resolveSprite(ResourceLocation id) {
        var mc = Minecraft.getInstance();
        if (mc == null) return null;
        Function<ResourceLocation, TextureAtlasSprite> atlas = mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
        return atlas.apply(id);
    }

    private static TextureAtlasSprite resolveParticleSprite(Block block) {
        var mc = Minecraft.getInstance();
        if (mc == null) return null;
        BlockModelShaper shaper = mc.getBlockRenderer().getBlockModelShaper();
        BakedModel model = shaper.getBlockModel(block.defaultBlockState());
        return model.getParticleIcon();
    }

    private static TextureAtlasSprite resolveBlockFaceSprite(Block block, Direction face) {
        try {
            var mc = Minecraft.getInstance();
            if (mc == null) return null;
            BlockModelShaper shaper = mc.getBlockRenderer().getBlockModelShaper();
            BakedModel model = shaper.getBlockModel(block.defaultBlockState());
            // getQuads(state, face, rand) – try face-specific first
            List<BakedQuad> quads = model.getQuads(block.defaultBlockState(), face, RandomSource.create());
            if (!quads.isEmpty()) return quads.getFirst().getSprite();
            // fall back to particle
            return model.getParticleIcon();
        } catch (Throwable t) {
            Log.d("[api.resolveBlockFaceSprite] failed: {}", t.getMessage());
            return null;
        }
    }

    private static Direction parseFace(String s) {
        if (s == null) return Direction.NORTH;
        String n = s.trim().toLowerCase(Locale.ROOT);
        return switch (n) {
            case "up", "top", "u", "y+", "positive_y"    -> Direction.UP;
            case "down", "bottom", "d", "y-", "negative_y" -> Direction.DOWN;
            case "north", "front", "n", "z-", "negative_z" -> Direction.NORTH;
            case "south", "back", "s", "z+", "positive_z"  -> Direction.SOUTH;
            case "west", "w", "x-", "negative_x"          -> Direction.WEST;
            case "east", "e", "x+", "positive_x"          -> Direction.EAST;
            default -> Direction.NORTH;
        };
    }

    /**
     * Try Fabric's FluidRenderHandlerRegistry for modded fluids; otherwise fall back to vanilla ids.
     * animated=true chooses the "flowing" sprite when available; false chooses "still".
     */
    private static TextureAtlasSprite resolveFluidSprite(Fluid fluid, boolean animated) {
        var mc = Minecraft.getInstance();
        if (mc == null) return null;

        // Try Fabric registry if present
        try {
            if (FabricLoader.getInstance().isModLoaded("fabric-rendering-fluids-v1")) {
                // Use reflection to avoid a hard compile dep in this file
                Class<?> regClass = Class.forName("net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry");
                Object registry = regClass.getField("INSTANCE").get(null);
                var get = regClass.getMethod("get", Fluid.class).invoke(registry, fluid);
                if (get != null) {
                    // getFluidSprites(Level, BlockPos, FluidState)
                    var method = get.getClass().getMethod("getFluidSprites",
                            net.minecraft.world.level.BlockAndTintGetter.class,
                            BlockPos.class,
                            net.minecraft.world.level.material.FluidState.class);
                    var level = mc.level;
                    var pos = BlockPos.ZERO;
                    var state = fluid.defaultFluidState();
                    TextureAtlasSprite[] sprites = (TextureAtlasSprite[]) method.invoke(get, level, pos, state);
                    if (sprites != null && sprites.length > 0) {
                        // Vanilla ordering: [still, flowing]
                        int idx = animated && sprites.length > 1 ? 1 : 0;
                        return sprites[Math.min(idx, sprites.length - 1)];
                    }
                }
            }
        } catch (Throwable t) {
            // ignore and fall through to vanilla fallback
            Log.t("[api.resolveFluidSprite] Fabric handler not available: {}", t.getMessage());
        }

        // Vanilla fallback for common fluids
        ResourceLocation id;
        String ns = "minecraft";
        if (fluid.toString().contains("lava")) {
            id = ResourceLocation.fromNamespaceAndPath(ns, animated ? "block/lava_flow" : "block/lava_still");
        } else {
            // default to water
            id = ResourceLocation.fromNamespaceAndPath(ns, animated ? "block/water_flow" : "block/water_still");
        }
        return resolveSprite(id);
    }

    // Build one-or-many quads depending on UVMode and orientation.
    private static List<InjectedQuad> buildTexturedQuads(TextureAtlasSprite sprite,
                                                         Vector3f p1, Vector3f p2, Vector3f p3, Vector3f p4,
                                                         int tintARGB, int light, float opacity, UVMode mode) {
        if (mode == UVMode.TILE) {
            List<InjectedQuad> tiled = tryTileAxisAlignedToQuads(sprite, p1, p2, p3, p4, tintARGB, light, opacity);
            if (tiled != null) return tiled; // true 1×1 tiling, no bleed
            // Fallback: UV-wrap (works for arbitrary quads; minimal bleed)
            return List.of(buildTexturedQuad(sprite, p1, p2, p3, p4, tintARGB, light, opacity, /*wrap*/true));
        }
        // STRETCH: single quad, no wrap
        return List.of(buildTexturedQuad(sprite, p1, p2, p3, p4, tintARGB, light, opacity, /*wrap*/false));
    }

    /** If the quad is a vertical axis-aligned rectangle (constant X or Z), split into 1×1 world tiles. */
    private static List<InjectedQuad> tryTileAxisAlignedToQuads(TextureAtlasSprite sprite,
                                                                Vector3f p1, Vector3f p2, Vector3f p3, Vector3f p4,
                                                                int tintARGB, int light, float opacity) {
        final float eps = 1e-4f;
        boolean constZ = Math.abs(p1.z - p2.z) < eps && Math.abs(p1.z - p3.z) < eps && Math.abs(p1.z - p4.z) < eps;
        boolean constX = Math.abs(p1.x - p2.x) < eps && Math.abs(p1.x - p3.x) < eps && Math.abs(p1.x - p4.x) < eps;
        if (!constZ && !constX) return null;

        float u0 = sprite.getU0(), u1 = sprite.getU1();
        float v0 = sprite.getV0(), v1 = sprite.getV1();

        ArrayList<InjectedQuad> out = new ArrayList<>();

        if (constZ) {
            float z = p1.z;
            float minX = Math.min(Math.min(p1.x, p2.x), Math.min(p3.x, p4.x));
            float maxX = Math.max(Math.max(p1.x, p2.x), Math.max(p3.x, p4.x));
            float minY = Math.min(Math.min(p1.y, p2.y), Math.min(p3.y, p4.y));
            float maxY = Math.max(Math.max(p1.y, p2.y), Math.max(p3.y, p4.y));

            int xStart = (int)Math.floor(minX);
            int xEnd   = (int)Math.ceil(maxX);
            int yStart = (int)Math.floor(minY);
            int yEnd   = (int)Math.ceil(maxY);

            for (int xi = xStart; xi < xEnd; xi++) {
                float xA = Math.max(minX, xi);
                float xB = Math.min(maxX, xi + 1f);
                if (xB - xA <= eps) continue;
                for (int yi = yStart; yi < yEnd; yi++) {
                    float yA = Math.max(minY, yi);
                    float yB = Math.min(maxY, yi + 1f);
                    if (yB - yA <= eps) continue;

                    // Tile vertices (bottom-left → bottom-right → top-right → top-left)
                    Vector3f q1p = new Vector3f(xA, yA, z);
                    Vector3f q2p = new Vector3f(xB, yA, z);
                    Vector3f q3p = new Vector3f(xB, yB, z);
                    Vector3f q4p = new Vector3f(xA, yB, z);

                    // Full 0..1 sprite per tile:
                    QuadVertex q1 = new QuadVertex(q1p.x, q1p.y, q1p.z, u0, v0, tintARGB, light, 0, 0, 0, (z >= 0 ? 1 : -1));
                    QuadVertex q2 = new QuadVertex(q2p.x, q2p.y, q2p.z, u1, v0, tintARGB, light, 0, 0, 0, (z >= 0 ? 1 : -1));
                    QuadVertex q3 = new QuadVertex(q3p.x, q3p.y, q3p.z, u1, v1, tintARGB, light, 0, 0, 0, (z >= 0 ? 1 : -1));
                    QuadVertex q4 = new QuadVertex(q4p.x, q4p.y, q4p.z, u0, v1, tintARGB, light, 0, 0, 0, (z >= 0 ? 1 : -1));
                    out.add(new InjectedQuad(q1, q2, q3, q4));
                }
            }
            return out;
        } else { // constX
            float x = p1.x;
            float minZ = Math.min(Math.min(p1.z, p2.z), Math.min(p3.z, p4.z));
            float maxZ = Math.max(Math.max(p1.z, p2.z), Math.max(p3.z, p4.z));
            float minY = Math.min(Math.min(p1.y, p2.y), Math.min(p3.y, p4.y));
            float maxY = Math.max(Math.max(p1.y, p2.y), Math.max(p3.y, p4.y));

            int zStart = (int)Math.floor(minZ);
            int zEnd   = (int)Math.ceil(maxZ);
            int yStart = (int)Math.floor(minY);
            int yEnd   = (int)Math.ceil(maxY);

            for (int zi = zStart; zi < zEnd; zi++) {
                float zA = Math.max(minZ, zi);
                float zB = Math.min(maxZ, zi + 1f);
                if (zB - zA <= eps) continue;
                for (int yi = yStart; yi < yEnd; yi++) {
                    float yA = Math.max(minY, yi);
                    float yB = Math.min(maxY, yi + 1f);
                    if (yB - yA <= eps) continue;

                    Vector3f q1p = new Vector3f(x, yA, zA);
                    Vector3f q2p = new Vector3f(x, yA, zB);
                    Vector3f q3p = new Vector3f(x, yB, zB);
                    Vector3f q4p = new Vector3f(x, yB, zA);

                    QuadVertex q1 = new QuadVertex(q1p.x, q1p.y, q1p.z, u0, v0, tintARGB, light, 0, (x >= 0 ? 1 : -1), 0, 0);
                    QuadVertex q2 = new QuadVertex(q2p.x, q2p.y, q2p.z, u1, v0, tintARGB, light, 0, (x >= 0 ? 1 : -1), 0, 0);
                    QuadVertex q3 = new QuadVertex(q3p.x, q3p.y, q3p.z, u1, v1, tintARGB, light, 0, (x >= 0 ? 1 : -1), 0, 0);
                    QuadVertex q4 = new QuadVertex(q4p.x, q4p.y, q4p.z, u0, v1, tintARGB, light, 0, (x >= 0 ? 1 : -1), 0, 0);
                    out.add(new InjectedQuad(q1, q2, q3, q4));
                }
            }
            return out;
        }
    }

    /** Build one InjectedQuad with UVs derived from the sprite and mode. */
    /** Build one quad; if wrap=true, UVs are wrapped with frac() to avoid atlas bleeding. */
    private static InjectedQuad buildTexturedQuad(TextureAtlasSprite sprite,
                                                  Vector3f p1, Vector3f p2, Vector3f p3, Vector3f p4,
                                                  int tintARGB, int light, float opacity, boolean wrap) {
        int a = (tintARGB >>> 24) & 0xFF, r = (tintARGB >>> 16) & 0xFF, g = (tintARGB >>> 8) & 0xFF, b = (tintARGB) & 0xFF;
        int alpha = Math.max(0, Math.min(255, Math.round(a * Math.max(0f, Math.min(1f, opacity)))));
        int argb = (alpha << 24) | (r << 16) | (g << 8) | b;

        Vector3f u = new Vector3f(p2).sub(p1);
        Vector3f v = new Vector3f(p4).sub(p1);
        Vector3f n = new Vector3f(u).cross(v).normalize();

        float uLen = u.length(), vLen = v.length();
        float invULen = uLen != 0 ? 1f / uLen : 0f;
        float invVLen = vLen != 0 ? 1f / vLen : 0f;

        float[] U = new float[]{0f, uLen, uLen, 0f};
        float[] V = new float[]{0f, 0f,   vLen, vLen};

        // Normalize to 0..1 (STRETCH)
        for (int i = 0; i < 4; i++) { U[i] *= invULen; V[i] *= invVLen; }

        if (wrap) {
            for (int i = 0; i < 4; i++) { U[i] = frac(U[i]); V[i] = frac(V[i]); }
        }

        float u0 = sprite.getU0(), u1 = sprite.getU1();
        float v0 = sprite.getV0(), v1 = sprite.getV1();
        float du = u1 - u0, dv = v1 - v0;

        float uu1 = u0 + du * U[0], vv1 = v0 + dv * V[0];
        float uu2 = u0 + du * U[1], vv2 = v0 + dv * V[1];
        float uu3 = u0 + du * U[2], vv3 = v0 + dv * V[2];
        float uu4 = u0 + du * U[3], vv4 = v0 + dv * V[3];

        QuadVertex q1 = new QuadVertex(p1.x, p1.y, p1.z, uu1, vv1, argb, light, 0, n.x, n.y, n.z);
        QuadVertex q2 = new QuadVertex(p2.x, p2.y, p2.z, uu2, vv2, argb, light, 0, n.x, n.y, n.z);
        QuadVertex q3 = new QuadVertex(p3.x, p3.y, p3.z, uu3, vv3, argb, light, 0, n.x, n.y, n.z);
        QuadVertex q4 = new QuadVertex(p4.x, p4.y, p4.z, uu4, vv4, argb, light, 0, n.x, n.y, n.z);
        return new InjectedQuad(q1, q2, q3, q4);
    }

    private static float frac(float x) {
        float f = x - (float)Math.floor(x);
        return (f >= 0.99999994f) ? 0f : f; // avoid hitting 1.0 due to fp noise
    }
}