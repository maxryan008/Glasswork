# Glasswork

**Glasswork** is a small, **client-only** Fabric API for injecting **translucent quads** into Minecraft’s section renderer (MC **1.21.1**).  
It handles buffer injection, sorting/upload, and dirty-state bumps so your mod can simply say:

> “Render this translucent quad here, using this block or fluid texture, tiled or stretched.”

- Simple Java API: `GlassworkAPI`
- Textures by **block + face** or by **atlas id** (e.g. `minecraft:block/stone`)
- Fluids (still/flowing) with optional animation
- UV modes: `TILE` (repeat per world unit) or `STRETCH` (map once)
- Pure client; no networking or server entrypoints
- Optional client commands for debugging

---

## Requirements

- Minecraft **1.21.1**
- Fabric Loader **0.16.x** and Fabric API
- Java **21**

---

## Installation (Gradle / Fabric Loom)

Add the Maven and the dependency. Replace `<version>` with the latest available from the Maven index.

```gradle
repositories {
    maven { url = uri("https://maven.teamgalacticraft.org/") }
    mavenCentral()
}

dependencies {
    // Usual Fabric deps…
    modImplementation "net.fabricmc:fabric-loader:<loader_version>"
    modImplementation "net.fabricmc.fabric-api:fabric-api:<fabric_api_version>"

    // Glasswork API
    modImplementation "dev.maximus:glasswork:<version>"
}
```

If your project uses Loom’s `splitEnvironmentSourceSets()`, it’s fine to keep the dependency on the client side only:

```gradle
loom { splitEnvironmentSourceSets() }

dependencies {
    clientImplementation "dev.maximus:glasswork:<version>"   // compile on client
    modClientRuntimeOnly "dev.maximus:glasswork:<version>"   // runtime on client
}
```

If you want to declare a runtime dependency in your mod’s `fabric.mod.json` (optional, but recommended if your mod requires Glasswork at runtime):

```json
{
  "depends": {
    "fabricloader": ">=0.16.0",
    "fabric": "*",
    "minecraft": "1.21.1",
    "glasswork": "*"
  }
}
```

> Glasswork itself is client-only. Your mod can still be client+server; only the client will load Glasswork.

---

## Quick Start

All APIs live under `dev.maximus.glasswork.api.*`. The workhorse is `GlassworkAPI`.

### 1) Pick the section

```java
import dev.maximus.glasswork.api.GlassworkAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

SectionPos sec = GlassworkAPI.sectionFor(BlockPos.containing(x, y, z));
```

### 2) Place a quad with a **block texture** (block + face)

```java
import dev.maximus.glasswork.api.GlassworkAPI.UVMode;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3f;

// Define a vertical quad (bottom-left → bottom-right → top-right → top-left)
Vector3f v1 = new Vector3f(10, 64, 10);
Vector3f v2 = new Vector3f(12, 64, 10);
Vector3f v3 = new Vector3f(12, 66, 10);
Vector3f v4 = new Vector3f(10, 66, 10);

int   tintARGB = 0x80FFFFFF;  // semi-transparent white
int   light    = 0x00F000F0;  // fullbright
float opacity  = 1.0f;        // multiplies alpha channel
UVMode uv      = UVMode.TILE; // TILE or STRETCH

GlassworkAPI.putBlockTexture(
    sec, Blocks.GLASS, "north",
    v1, v2, v3, v4,
    tintARGB, light, opacity, uv
);
```

### 3) Use a **block texture by atlas id**

```java
import net.minecraft.resources.ResourceLocation;
import dev.maximus.glasswork.api.GlassworkAPI.UVMode;

ResourceLocation stone = ResourceLocation.parse("minecraft:block/stone");

GlassworkAPI.putBlockTexture(
    sec, stone,
    v1, v2, v3, v4,
    0xFFFFFFFF, 0x00F000F0, 1.0f, UVMode.STRETCH
);
```

### 4) Use a **fluid sprite** (still/flowing)

```java
import net.minecraft.world.level.material.Fluids;
import dev.maximus.glasswork.api.GlassworkAPI.UVMode;

GlassworkAPI.putLiquidTexture(
    sec, Fluids.WATER, true,   // animated=true selects the flowing sprite when available
    v1, v2, v3, v4,
    0x80FFFFFF, 0x00F000F0, 1.0f, UVMode.TILE
);
```

### 5) Clearing (optional)

```java
// Clear all persistent quads in a section:
GlassworkAPI.removeAll(sec);
```

---

## UVs and Tiling

- `STRETCH` maps the entire sprite once across your quad (0..1 on U and V).
- `TILE` repeats one full sprite per **world unit** along the quad’s in-plane axes.
  - Axis-aligned vertical quads are automatically subdivided into 1×1 tiles, each with its own 0..1 UVs to avoid atlas bleeding.
  - Non-axis-aligned quads use UV wrapping so tiles repeat without bleeding.

**Vertex order matters**: pass corners in order **bottom-left → bottom-right → top-right → top-left** (coplanar).  
The normal is computed from edges `(v1→v2) × (v1→v4)`.

**Lighting**: pass a packed light (`0x00F000F0` is a convenient fullbright), or use a value from your context.

---

## Client commands (development)

When running in dev, Glasswork exposes client-side helpers:

```
/gwc stats
/gwc clear section
/gwc clear all
```

These are optional and only act on the client.

---

## API Surface (helpers)

Common helper signatures:

```java
// Block texture by atlas id (e.g., "minecraft:block/stone")
static void putBlockTexture(
    SectionPos section, net.minecraft.resources.ResourceLocation textureId,
    org.joml.Vector3f v1, org.joml.Vector3f v2, org.joml.Vector3f v3, org.joml.Vector3f v4,
    int tintARGB, int light, float opacity,
    GlassworkAPI.UVMode uvMode
);

// Block + face ("north","south","east","west","up","down", plus aliases)
static void putBlockTexture(
    SectionPos section, net.minecraft.world.level.block.Block block, String face,
    org.joml.Vector3f v1, org.joml.Vector3f v2, org.joml.Vector3f v3, org.joml.Vector3f v4,
    int tintARGB, int light, float opacity,
    GlassworkAPI.UVMode uvMode
);

// Fluid (animated=true picks "flowing" when present, false picks "still")
static void putLiquidTexture(
    SectionPos section, net.minecraft.world.level.material.Fluid fluid, boolean animated,
    org.joml.Vector3f v1, org.joml.Vector3f v2, org.joml.Vector3f v3, org.joml.Vector3f v4,
    int tintARGB, int light, float opacity,
    GlassworkAPI.UVMode uvMode
);

// Low-level (replace all persistent quads in a section)
static void put(SectionPos section, java.util.Collection<InjectedQuad> quads);

// Remove all persistent quads from a section
static void removeAll(SectionPos section);
```

---

## Troubleshooting

- **Nothing renders**
  - Ensure vertices are coplanar and ordered correctly.
  - Ensure you’re running on the **client** and the dependency is available at client runtime.
  - Try fullbright lighting `0x00F000F0` to rule out lighting issues.
- **Tiling looks misaligned**
  - Prefer axis-aligned vertical quads for perfect per-block tiling, or ensure your quad spans an integer number of world units.
- **“Class path entries reference missing files … /build/classes/java/main”**
  - If your mod is also client-only with Loom’s `splitEnvironmentSourceSets()`, ensure your `mods {}` block only includes the `client` source set, or create an empty `build/classes/java/main` before `runClient`.

---

## Links

- GitHub: https://github.com/maxryan008/Glasswork
- Maven:  https://maven.teamgalacticraft.org/dev/maximus/glasswork/

---

## License

See `LICENSE` (All-Rights-Reserved).
