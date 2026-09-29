import argparse
import json
import random
import statistics
import sys
from pathlib import Path

for stream in (sys.stdin, sys.stdout, sys.stderr):
    stream.reconfigure(encoding="utf-8")

METRICS = (("averageFps", "avg FPS", 1), ("p99Ms", "p99 ms", -1), ("onePercentLowFps", "1% low FPS", 1))
Z_ALPHA = 1.959964
Z_POWER = 0.841621


def load(directory):
    path = Path(directory) / "result.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    problems = []
    if data.get("status") != "ok":
        problems.append(f"status={data.get('status')} {data.get('failure')}")
    if not data.get("histogramCheck", {}).get("passed", False):
        problems.append("histogram check failed")
    if data.get("overflowed"):
        problems.append("frame buffer overflowed")
    return data, problems


def bootstrap_ci(a, b, iterations, rng):
    diffs = []
    for _ in range(iterations):
        sample_a = [rng.choice(a) for _ in a]
        sample_b = [rng.choice(b) for _ in b]
        diffs.append(statistics.fmean(sample_b) - statistics.fmean(sample_a))
    diffs.sort()
    return diffs[int(0.025 * iterations)], diffs[int(0.975 * iterations) - 1]


def mde(a, b):
    if len(a) < 2 or len(b) < 2:
        return float("nan")
    pooled = ((len(a) - 1) * statistics.variance(a) + (len(b) - 1) * statistics.variance(b)) / (len(a) + len(b) - 2)
    return (Z_ALPHA + Z_POWER) * (pooled * (1 / len(a) + 1 / len(b))) ** 0.5


def compare(group_a, group_b, iterations, seed):
    rng = random.Random(seed)
    rows = []
    for key, label, better in METRICS:
        a = [run[key] for run in group_a]
        b = [run[key] for run in group_b]
        mean_a = statistics.fmean(a)
        mean_b = statistics.fmean(b)
        low, high = bootstrap_ci(a, b, iterations, rng)
        rows.append({
            "metric": key,
            "label": label,
            "meanA": mean_a,
            "meanB": mean_b,
            "diff": mean_b - mean_a,
            "relative": (mean_b - mean_a) / mean_a if mean_a else float("nan"),
            "ci95": [low, high],
            "significant": low > 0 or high < 0,
            "improved": (mean_b - mean_a) * better > 0,
            "mde": mde(a, b),
        })
    return rows


def main():
    parser = argparse.ArgumentParser(description="Compare DFast bench runs with a bootstrap confidence interval.")
    parser.add_argument("--a", nargs="+", default=[], help="result directories of variant A")
    parser.add_argument("--b", nargs="+", default=[], help="result directories of variant B")
    parser.add_argument("--aa", nargs="+", default=[], help="interleaved runs of one variant, split alternately into A and B")
    parser.add_argument("--iterations", type=int, default=10000)
    parser.add_argument("--seed", type=int, default=0)
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()

    if args.aa:
        dirs_a, dirs_b = args.aa[0::2], args.aa[1::2]
    else:
        dirs_a, dirs_b = args.a, args.b
    if len(dirs_a) < 2 or len(dirs_b) < 2:
        parser.error("each group needs at least 2 runs")

    groups = []
    rejected = []
    for dirs in (dirs_a, dirs_b):
        runs = []
        for directory in dirs:
            data, problems = load(directory)
            if problems:
                rejected.append({"run": directory, "problems": problems})
            else:
                runs.append(data)
        groups.append(runs)
    if len(groups[0]) < 2 or len(groups[1]) < 2:
        print(json.dumps({"rejected": rejected}, indent=2))
        sys.exit("not enough valid runs after rejecting failed ones")

    rows = compare(groups[0], groups[1], args.iterations, args.seed)
    report = {"mode": "A/A" if args.aa else "A/B", "runsA": len(groups[0]), "runsB": len(groups[1]),
              "rejected": rejected, "metrics": rows}
    if args.json:
        print(json.dumps(report, indent=2))
        return
    print(f"{report['mode']}  runs A={report['runsA']} B={report['runsB']}  rejected={len(rejected)}")
    for row in rows:
        verdict = "significant" if row["significant"] else "no detectable difference"
        if row["significant"]:
            verdict += ", better" if row["improved"] else ", worse"
        print(f"  {row['label']:<11} A={row['meanA']:.3f} B={row['meanB']:.3f} "
              f"diff={row['diff']:+.3f} ({row['relative']:+.2%}) "
              f"CI95=[{row['ci95'][0]:+.3f}, {row['ci95'][1]:+.3f}] MDE={row['mde']:.3f}  {verdict}")
    for item in rejected:
        print(f"  rejected {item['run']}: {'; '.join(item['problems'])}")


if __name__ == "__main__":
    main()
