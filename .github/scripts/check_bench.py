import json
import sys
from pathlib import Path

for stream in (sys.stdin, sys.stdout, sys.stderr):
    stream.reconfigure(encoding="utf-8")

MIN_FRAMES = 50


def main():
    root = Path(sys.argv[1] if len(sys.argv) > 1 else "run/dfast/bench")
    runs = sorted(path for path in root.glob("*/result.json"))
    if len(runs) != 1:
        sys.exit(f"expected exactly one bench result under {root}, found {len(runs)}")
    result = json.loads(runs[0].read_text(encoding="utf-8"))
    system = json.loads((runs[0].parent / "system.json").read_text(encoding="utf-8"))
    check = result.get("histogramCheck", {})
    problems = []
    if result.get("status") != "ok":
        problems.append(f"status {result.get('status')}: {result.get('failure')}")
    if result.get("frames", 0) < MIN_FRAMES:
        problems.append(f"only {result.get('frames', 0)} frames recorded, need {MIN_FRAMES}")
    if not check.get("passed", False):
        problems.append(f"histogram check failed, max relative error {check.get('maxRelativeError')}")
    if result.get("overflowed"):
        problems.append("frame buffer overflowed")
    print(f"{runs[0].parent.name}: {result.get('frames')} frames, {result.get('averageFps', 0):.1f} avg FPS, "
          f"p99 {result.get('p99Ms', 0):.2f} ms, histogram error {check.get('maxRelativeError', float('nan')):.5f}, "
          f"GL {system.get('gl', {}).get('renderer')}, java {system.get('java')}")
    print("mods: " + ", ".join(mod for mod in system.get("mods", []) if not mod.startswith("fabric-")))
    if problems:
        sys.exit("bench check failed: " + "; ".join(problems))


if __name__ == "__main__":
    main()
