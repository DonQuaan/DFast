import argparse
import collections
import json
import math
import statistics
import sys
from pathlib import Path

for stream in (sys.stdin, sys.stdout, sys.stderr):
    stream.reconfigure(encoding="utf-8")

METRICS = (("averageFps", "avg FPS", 1), ("p99Ms", "p99 ms", -1), ("onePercentLowFps", "1% low FPS", 1))
MIN_RUNS = 5
CONFIDENCE = 0.95
POWER = 0.80


def continued_fraction(a, b, x):
    tiny = 1e-300
    c = 1.0
    d = 1.0 - (a + b) * x / (a + 1.0)
    d = 1.0 / (d if abs(d) > tiny else tiny)
    h = d
    for m in range(1, 300):
        m2 = 2 * m
        numerator = m * (b - m) * x / ((a + m2 - 1.0) * (a + m2))
        d = 1.0 + numerator * d
        d = 1.0 / (d if abs(d) > tiny else tiny)
        c = 1.0 + numerator / c
        c = c if abs(c) > tiny else tiny
        h *= d * c
        numerator = -(a + m) * (a + b + m) * x / ((a + m2) * (a + m2 + 1.0))
        d = 1.0 + numerator * d
        d = 1.0 / (d if abs(d) > tiny else tiny)
        c = 1.0 + numerator / c
        c = c if abs(c) > tiny else tiny
        step = d * c
        h *= step
        if abs(step - 1.0) < 1e-14:
            break
    return h


def regularized_beta(a, b, x):
    if x <= 0.0:
        return 0.0
    if x >= 1.0:
        return 1.0
    front = math.exp(math.lgamma(a + b) - math.lgamma(a) - math.lgamma(b) + a * math.log(x) + b * math.log1p(-x))
    if x < (a + 1.0) / (a + b + 2.0):
        return front * continued_fraction(a, b, x) / a
    return 1.0 - front * continued_fraction(b, a, 1.0 - x) / b


def t_cdf(t, df):
    tail = 0.5 * regularized_beta(df / 2.0, 0.5, df / (df + t * t))
    return 1.0 - tail if t > 0 else tail


def t_quantile(p, df):
    low, high = -1e4, 1e4
    for _ in range(200):
        mid = (low + high) / 2.0
        if t_cdf(mid, df) < p:
            low = mid
        else:
            high = mid
    return (low + high) / 2.0


def welch(a, b):
    va = statistics.variance(a) / len(a)
    vb = statistics.variance(b) / len(b)
    se = math.sqrt(va + vb)
    diff = statistics.fmean(b) - statistics.fmean(a)
    if se == 0.0:
        return diff, (diff, diff), 0.0, float("inf")
    df = (va + vb) ** 2 / (va ** 2 / (len(a) - 1) + vb ** 2 / (len(b) - 1))
    half = t_quantile(1.0 - (1.0 - CONFIDENCE) / 2.0, df) * se
    mde = (t_quantile(1.0 - (1.0 - CONFIDENCE) / 2.0, df) + t_quantile(POWER, df)) * se
    return diff, (diff - half, diff + half), mde, df


def signature(directory, data):
    system = json.loads((Path(directory) / "system.json").read_text(encoding="utf-8"))
    start = (data.get("environment") or {}).get("start") or {}
    return (start.get("power"), start.get("refreshRate"), start.get("fullscreen"),
            system.get("gl", {}).get("renderer"), system.get("options", {}).get("framebuffer"))


def load(directory):
    path = Path(directory) / "result.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    data["_signature"] = signature(directory, data)
    problems = []
    if not (data.get("environment") or {}).get("stable", False):
        problems.append(f"environment changed during the run or was not recorded: {data.get('environment')}")
    if data.get("status") != "ok":
        problems.append(f"status={data.get('status')} {data.get('failure')}")
    if data.get("worldCreated", True):
        problems.append("the run created the bench world, so it measured terrain generation")
    if not data.get("histogramCheck", {}).get("passed", False):
        problems.append("histogram check failed")
    if data.get("overflowed"):
        problems.append("frame buffer overflowed")
    return data, problems


def compare(group_a, group_b):
    rows = []
    for key, label, better in METRICS:
        a = [run[key] for run in group_a]
        b = [run[key] for run in group_b]
        mean_a = statistics.fmean(a)
        diff, (low, high), mde, df = welch(a, b)
        rows.append({
            "metric": key,
            "label": label,
            "meanA": mean_a,
            "meanB": statistics.fmean(b),
            "diff": diff,
            "relative": diff / mean_a if mean_a else float("nan"),
            "ci95": [low, high],
            "df": df,
            "significant": low > 0 or high < 0,
            "improved": diff * better > 0,
            "mde": mde,
        })
    return rows


def main():
    parser = argparse.ArgumentParser(
        description="Compare DFast bench runs with a Welch t confidence interval over run results.")
    parser.add_argument("--a", nargs="+", default=[], help="result directories of variant A")
    parser.add_argument("--b", nargs="+", default=[], help="result directories of variant B")
    parser.add_argument("--aa", nargs="+", default=[], help="interleaved runs of one variant, split alternately into A and B")
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()

    if args.aa:
        dirs_a, dirs_b = args.aa[0::2], args.aa[1::2]
    else:
        dirs_a, dirs_b = args.a, args.b
    if len(dirs_a) < MIN_RUNS or len(dirs_b) < MIN_RUNS:
        parser.error(f"each group needs at least {MIN_RUNS} runs")

    loaded = [[(directory, *load(directory)) for directory in dirs] for dirs in (dirs_a, dirs_b)]
    counts = collections.Counter(data["_signature"] for group in loaded for _, data, _ in group)
    majority = counts.most_common(1)[0][0]
    groups = []
    rejected = []
    for group in loaded:
        runs = []
        for directory, data, problems in group:
            if data["_signature"] != majority:
                problems.append(f"environment {data['_signature']} differs from the majority {majority}")
            if problems:
                rejected.append({"run": directory, "problems": problems})
            else:
                runs.append(data)
        groups.append(runs)
    if len(groups[0]) < MIN_RUNS or len(groups[1]) < MIN_RUNS:
        print(json.dumps({"rejected": rejected}, indent=2))
        sys.exit(f"fewer than {MIN_RUNS} valid runs in a group after rejecting unusable ones")

    rows = compare(groups[0], groups[1])
    report = {"mode": "A/A" if args.aa else "A/B", "runsA": len(groups[0]), "runsB": len(groups[1]),
              "environment": {"power": majority[0], "refreshRate": majority[1], "fullscreen": majority[2],
                              "renderer": majority[3], "framebuffer": majority[4]},
              "rejected": rejected, "metrics": rows}
    if args.json:
        print(json.dumps(report, indent=2))
        return
    print(f"{report['mode']}  runs A={report['runsA']} B={report['runsB']}  rejected={len(rejected)}  "
          f"environment={report['environment']}")
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
