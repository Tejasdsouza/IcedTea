# IcedTea

**IcedTea** is an advanced Minecraft client optimization mod for Fabric, focused on multi-threaded occlusion culling, entity and particle management, and overall rendering performance. Designed for Minecraft 1.20.1–1.20.4.

---

## Features

- **Multi-threaded Occlusion Culling:**
  Efficiently determines visible chunks using parallel processing.

- **Entity & Block Entity Culling:**
  Skips rendering of distant or occluded entities and block entities, with support for LOD and caching.

- **Particle Culling:**
  Removes excess or distant particles, prioritizing important types.

- **Biome-aware & Density-based Optimization:**
  Adapts culling aggressiveness and checks based on biome and entity density.

- **Performance Overlay:**
  Toggleable HUD showing FPS, frame time, and culling statistics.

- **Highly Configurable:**
  All major features and thresholds can be adjusted via in-game commands or `icedtea.json`.

- **Debug Visualization:**
  Optional debug mode for visualizing culling decisions.

- **Efficient Caching:**
  LRU chunk visibility cache and short-term render cache for fast lookups.

- **Thread Pool Management:**
  Custom thread pool for parallel tasks, configurable thread count.

---

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for your Minecraft version.
2. Download the latest IcedTea release from [GitHub Releases](https://github.com/Tejasdsouza/IcedTea/releases).
3. Place the `icedtea-x.x.x.jar` file in your `mods` folder.
4. Launch Minecraft.

---

## How to Build

1. Ensure you have [JDK 17+](https://adoptium.net/) installed.
2. Clone the repository:
   ```sh
   git clone https://github.com/Tejasdsouza/IcedTea.git
   cd IcedTea
   ```
3. Build the mod using Gradle:
   ```sh
   ./gradlew build
   ```
4. The built `.jar` file will be located in the `build/libs/` directory.

---

## Usage

### Commands

- `/icedtea enable` – Enable the mod
- `/icedtea disable` – Disable the mod
- `/icedtea stats` – Toggle performance HUD
- `/icedtea debug` – Toggle debug visualization
- `/icedtea reload` – Reload configuration
- `/icedtea set <option> <value>` – Change config option (see `/icedtea help`)
- `/icedtea help` – List all options and usage

### Configuration

Edit `config/icedtea.json` or use `/icedtea set <option> <value>` in-game.
Options include culling toggles, distances, cache sizes, thread count, and more.

---

## Compatibility

- **Minecraft:** 1.20.1 – 1.20.4
- **Fabric Loader:** 0.15.0+
- **Java:** 17+

---

## Disclaimer

This mod is under active development and may contain bugs or unintended behavior. Please report issues on [GitHub Issues](https://github.com/Tejasdsouza/IcedTea/issues).

## Contributing

Pull requests and issue reports are welcome.
See [GitHub Issues](https://github.com/Tejasdsouza/IcedTea/issues) for bug reports and feature requests.

---

## License

This project is licensed under the [MIT License](LICENSE).

---

## Credits

Developed by [Tejas Dsouza](https://tejasdsouza7.space).

---
