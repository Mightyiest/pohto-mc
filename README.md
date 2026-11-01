# PohtoMC - URL Image Paintings

A Minecraft Fabric mod that lets players right-click any vanilla painting to display `.png` and `.jpg` images directly from a URL, complete with in-game size controls (1–16 blocks), aspect ratio preservation/stretching, multiplayer lock settings, and server-side disk caching.

Unmodded vanilla clients can still join the server without errors or desyncs!

---

## Features

- **Direct Image Display**: Render `.png` and `.jpg` images from web URLs on vanilla paintings (also supports direct image endpoints without file extensions).
- **Dynamic Sizing & Presets**: Customize width and height from 1x1 up to 16x16 blocks via an intuitive in-game GUI with quick aspect ratio presets (`1:1`, `4:3`, `16:9`, `2:1`).
- **Aspect Ratio Control**: Toggle between **Keep Aspect** (letterboxed/pillarboxed to fit) and **Stretch Fill**.
- **Multiplayer Edit Lock**: Creators can lock their paintings so only the owner (or server OPs) can modify or break them.
- **Vanilla Compatibility & Safe Drops**: Dropped items store painting dimensions and URLs inside vanilla `CustomData` components. When broken in Survival, items safely drop in front of the wall to prevent suffocating in blocks; in Creative, the custom item is granted to the player's inventory.
- **Server Disk Caching & SSRF Protection**: The server downloads and permanently caches images locally (`pohtomc_images/`), with built-in SSRF filtering and stream caps (max 10 MB) to prevent server memory exhaustion.
- **Client Smart Cache**: Asynchronously fetches and caches textures permanently with downscaling protection (max 2048x2048) and 15 MB payload limits.

---

## Project Structure

```text
pohto-mc/
├── build.gradle
├── gradle.properties
├── settings.gradle
├── LICENSE
├── README.md
└── src/
    └── main/
        ├── java/
        │   └── com/pohtomc/
        │       ├── PohtoMcMod.java                    # Common mod entrypoint & server networking
        │       ├── duck/
        │       │   └── UrlPaintingDataHolder.java     # Duck interface extending Painting entities
        │       ├── mixin/
        │       │   └── PaintingMixin.java             # Vanilla Painting entity mixin (data, AABB, drops)
        │       ├── network/                           # Fabric CustomPacketPayload network records
        │       │   ├── OpenUrlPaintingScreenPayload.java
        │       │   ├── RequestPaintingDataPayload.java
        │       │   ├── RequestPaintingImagePayload.java
        │       │   ├── SendPaintingImagePayload.java
        │       │   ├── SyncPaintingDataPayload.java
        │       │   └── UpdateUrlPaintingPayload.java
        │       └── client/
        │           ├── PohtoMcClient.java             # Client mod entrypoint & network handlers
        │           ├── duck/
        │           │   └── UrlPaintingRenderStateAccess.java # Duck interface for PaintingRenderState
        │           ├── gui/
        │           │   └── UrlPaintingScreen.java     # In-game interactive settings GUI
        │           ├── mixin/
        │           │   ├── PaintingRendererMixin.java # Modern SubmitNodeCollector quad renderer
        │           │   └── PaintingRenderStateMixin.java # Syncs duck fields into RenderState
        │           └── texture/
        │               └── UrlTextureCache.java       # Async downloader, disk & GPU texture cache
        └── resources/
            ├── fabric.mod.json
            ├── pohtomc.mixins.json
            ├── pohtomc.client.mixins.json
            └── assets/pohtomc/
                ├── icon.png
                └── lang/
                    └── en_us.json
```

---

## Core Systems Architecture

### 1. Networking Protocol
All packets utilize Minecraft's `CustomPacketPayload` and Fabric's `PayloadTypeRegistry`:

| Packet ID | Direction | Purpose |
| :--- | :--- | :--- |
| `pohtomc:open_screen` | Server $\rightarrow$ Client | Opens `UrlPaintingScreen` for the player with painting data |
| `pohtomc:sync_painting` | Server $\rightarrow$ Client | Syncs painting URL, dimensions, and lock status to tracking clients |
| `pohtomc:send_image` | Server $\rightarrow$ Client | Transmits server-cached image bytes if client cannot download URL |
| `pohtomc:update_painting`| Client $\rightarrow$ Server | Submits user changes (URL, dimensions, aspect ratio, lock) |
| `pohtomc:request_data` | Client $\rightarrow$ Server | Requests painting data when client loads an unsynced painting |
| `pohtomc:request_image`| Client $\rightarrow$ Server | Requests backup image bytes from server cache |

### 2. Entity & Rendering Pipeline
- **Vanilla Hooking**: Rather than registering a custom entity type (which breaks vanilla clients), PohtoMC attaches custom data to `net.minecraft.world.entity.decoration.painting.Painting` via `UrlPaintingDataHolder`.
- **Bounding Boxes**: `PaintingMixin` recalculates bounding boxes according to custom block widths and heights, aligning with wall orientation.
- **Rendering**: `PaintingRendererMixin` intercepts `PaintingRenderer.render` using Minecraft's modern `SubmitNodeCollector` pipeline. If a URL texture is loaded, it generates geometry mapped to the custom dimensions with proper block lighting from `LightCoordsUtil.getLightCoords`.

---

## Building from Source

### Prerequisites
- **Java Development Kit (JDK) 25**
- Gradle 8.x+ (or use standard Gradle wrapper)

### Build Commands
```bash
# Build the mod jar
gradle build

# Output jar location:
# build/libs/pohtomc-1.0.2.jar
```

---

## License
This project is licensed under the **CC0-1.0 Universal License** - see the [LICENSE](LICENSE) file for details.
