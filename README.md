# DFast

A performance companion mod for Minecraft (Fabric). It does not replace Sodium or Lithium. This beta ships a frame-time HUD and launcher advice matched to your Java version and RAM, and it leaves render-ahead control to Sodium when Sodium is installed. Further modules are added one version at a time.

## What 0.2.0-beta.3 does

| Feature | Details |
|---|---|
| Frame-time HUD | Press **F9** (rebindable in Controls). Shows average FPS over the last 2048 frames, the last frame time and the 1% low. Hidden with F1 and while the F3 screen is open. |
| Render-ahead ownership | With Sodium installed, DFast runs no fence loop of its own and leaves the CPU render-ahead limit to Sodium. Without Sodium its experimental limiter stays **off** unless you set `renderQueueLimiter=true` in `config/dfast-latency.properties`. It has not been benchmarked yet. |
| JVM advice | Reads your Java version and RAM and writes matching launcher arguments to `dfast/dfast.log`: G1 or Generational ZGC, a heap size, and Compact Object Headers only on Java versions that accept the flag. DFast never edits your launcher. |

Diagnostics go to `.minecraft/dfast/dfast.log`; `latest.log` gets a single line.

## Requirements

- Minecraft **1.21.1**, Fabric Loader 0.19.3+, Fabric API 0.116.13+
- Java 21+ (Java 25 recommended)

## Install

Download `dfast-<version>.jar` from [Releases](https://github.com/DonQuaan/DFast/releases) and put it in your `mods` folder.

## Build

```sh
./gradlew build
```

The jar is written to `build/libs/`.

## Development

DFast is developed with AI assistance (Claude). Every change is reviewed and tested before release.

## License

PolyForm Noncommercial 1.0.0 with the DonQuaan Addendum: source-available and non-commercial. Credit **DonQuaan (yangdawn) — https://github.com/DonQuaan/DFast**, no commercial use, and no use by organisations with more than 3 members. See [LICENSE.md](LICENSE.md).
