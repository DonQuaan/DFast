# DFast

A performance companion mod for Minecraft (Fabric). It does not replace Sodium or Lithium. It measures frame times, runs a repeatable benchmark, gives launcher advice matched to your Java version and RAM, and leaves render-ahead control to Sodium when Sodium is installed. Further modules are added one version at a time, and each one that is on by default has to show a measured benefit first.

## Features

| Feature | Details |
|---|---|
| Frame-time HUD | Press **F9** (rebindable in Controls). Shows FPS averaged over the last 5 seconds, the last frame time and p99 frame time over 5 seconds, and the 1% and 0.1% lows over 60 seconds, plus which mod owns render-ahead. Frames are only counted in a world with no menu open and the window focused, so pauses and alt-tab never show up as stutter, while real hitches during play do. Hidden with F1 and while the F3 screen is open. |
| Bench Mode | Start the game with `-Ddfast.bench=short` (or `ci`, `long`) to run a repeatable benchmark: DFast opens or creates a fixed-seed world, flies the same camera path every time, records every frame and writes `dfast/bench/<run>/result.json`, `system.json` and `frametimes.txt`, then quits. See below. |
| Render-ahead ownership | With Sodium installed, DFast runs no fence loop of its own and leaves the CPU render-ahead limit to Sodium. Without Sodium its experimental limiter stays **off** unless you set `renderQueueLimiter=true` in `config/dfast-latency.properties`. It has not been benchmarked yet. |
| JVM advice | Reads your Java version and RAM and writes matching launcher arguments to `dfast/dfast.log`: G1 or Generational ZGC, a heap size, and Compact Object Headers only on Java versions that accept the flag. DFast never edits your launcher. |

Diagnostics go to `.minecraft/dfast/dfast.log`; `latest.log` gets a single line.

## Bench Mode

Add a JVM argument in your launcher, for example `-Ddfast.bench=short`.

| Preset | Warmup | Measured |
|---|---|---|
| `ci` | 5 s | 10 s |
| `short` | 10 s | 30 s |
| `long` | 20 s | 120 s |

Presets accept overrides: `-Ddfast.bench=short,seed=42,duration=60,radius=128,height=150,pitch=20`.

- The world is `dfast-bench-<seed>`, peaceful, with time, weather and mob spawning frozen. The camera orbits the spawn point in spectator mode, driven by game ticks, so the path is the same at any FPS.
- The run is uncapped: VSync off, unlimited FPS, no pause when the window loses focus. Your own settings are restored afterwards and are never written to `options.txt`.
- The first run creates the world and generates its chunks. Do not compare it with later runs.
- `result.json` reports average FPS, p50/p90/p99/p99.9 frame time, max frame time and the 1% and 0.1% lows, plus a `histogramCheck` that compares the HUD's statistics with the exact values for the same frames.
- `system.json` records the OS, CPU threads, RAM, Java, `-X` JVM flags, OpenGL renderer and driver, video settings and the mod list. It contains no file paths or user names.

To compare two setups, run each several times in alternation and use `tools/bench_compare.py` from this repository:

```sh
python tools/bench_compare.py --a run1 run3 run5 --b run2 run4 run6
```

It reports the difference with a 95% bootstrap confidence interval and the smallest difference those runs could detect.

## Requirements

- Minecraft **1.21.1**, Fabric Loader 0.19.3+, Fabric API 0.116.13+
- Java 21+ (Java 25 recommended)

## Install

Download `dfast-<version>.jar` from [Releases](https://github.com/DonQuaan/DFast/releases) and put it in your `mods` folder.

## Build

```sh
./gradlew build
```

The jar is written to `build/libs/`. `./gradlew runBench -Pdfast.bench=ci` runs Bench Mode in a development client; add `-Pdfast.runJava=25` to run the game on another installed JDK.

## Development

DFast is developed with AI assistance (Claude). Every change is reviewed and tested before release. CI builds and unit-tests every commit, then runs Bench Mode in a real client on Linux with Java 21 and 25, alone and with Sodium 0.6.13 and 0.8.13.

For automated testing, `-Ddfast.ctl=true` (`./gradlew runClient -Pdfast.ctl`) lets a script drive a development client through files: each JSON line appended to `dfast/ctl/in.jsonl` (`{"id":1,"cmd":"dump"}`, `screenshot` or `quit`) gets a JSON reply in `dfast/ctl/out.jsonl`. It is off unless that property is set.

## License

PolyForm Noncommercial 1.0.0 with the DonQuaan Addendum: source-available and non-commercial. Credit **DonQuaan (yangdawn) — https://github.com/DonQuaan/DFast**, no commercial use, and no use by organisations with more than 3 members. See [LICENSE.md](LICENSE.md).
