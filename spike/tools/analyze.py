"""Turns M3 spike CSV logs (one or more phones) into the tables for docs/spikes/M3-platform-findings.md.

Usage: python3 spike/tools/analyze.py docs/spikes/m3-logs/*.csv > /tmp/m3-tables.md

Go/no-go rule per audio tier (docs/03 §7), per device × target × usage:
  GO      every attempt started, and at least 90% of answered "did you hear it?" questions are clear/heard-poor
  PARTIAL started but 50–90% heard, or some attempts refused by the platform
  NO-GO   refused every time, or under 50% heard
"""

import csv
import statistics
import sys
from collections import defaultdict


def rows(paths):
    for path in paths:
        with open(path, newline="") as f:
            yield from csv.DictReader(f)


def device(r):
    return f"{r['manufacturer']} {r['model']} (SDK {r['sdk']})"


def detail(r):
    return dict(kv.split("=", 1) for kv in r["detail"].split(";") if "=" in kv)


def pct(values, q):
    s = sorted(values)
    return s[min(len(s) - 1, int(round(q * (len(s) - 1))))] if s else float("nan")


def fmt_s(ms):
    return "–" if ms != ms else (f"{ms / 1000:.0f} s" if abs(ms) < 120_000 else f"{ms / 60_000:.1f} min")


def table(header, body):
    out = ["| " + " | ".join(header) + " |", "|" + "---|" * len(header)]
    out += ["| " + " | ".join(str(c) for c in row) + " |" for row in body]
    return "\n".join(out) if body else "_No data yet._"


def lateness(all_rows):
    scheduled, fired = {}, {}
    for r in all_rows:
        key = (r["run_id"], r["series"], r["index"])
        if r["event"] == "alarm_scheduled":
            scheduled[key] = r
        elif r["event"] == "alarm_fired":
            fired[key] = r
    groups = defaultdict(lambda: {"late": [], "planned": 0, "idle": 0})
    for key, s in scheduled.items():
        method = detail(s).get("method", s["series"])
        g = groups[(device(s), s["target_sdk"], s["condition"], method)]
        g["planned"] += 1
        f = fired.get(key)
        if f:
            g["late"].append(int(f["lateness_ms"]))
            g["idle"] += f["device_idle"] == "true"
    body = []
    for (dev, target, cond, method), g in sorted(groups.items()):
        late = g["late"]
        body.append([
            dev, target, cond, method, f"{len(late)}/{g['planned']}",
            fmt_s(statistics.median(late)) if late else "–", fmt_s(pct(late, 0.9)), fmt_s(max(late) if late else float("nan")),
            g["idle"],
        ])
    return table(["Device", "Target", "Condition", "Method", "Fired/planned", "Median late", "p90 late", "Max late",
                  "Fired in Doze"], body)


def heard(all_rows, suffix, by_usage=False):
    """Answers per device × target (× usage for T2, whose question id ends in "-<usage>-t2")."""
    out = defaultdict(lambda: defaultdict(int))
    for r in all_rows:
        if r["event"] == "heard" and r["run_id"].endswith(suffix):
            key = (device(r), r["target_sdk"])
            if by_usage:
                key = (key, r["run_id"][: -len(suffix)].rsplit("-", 1)[-1])
            out[key][detail(r).get("answer", "?")] += 1
    return out


def verdict(started, refused, answers):
    n = sum(answers.values())
    ok = answers.get("clear", 0) + answers.get("heard-poor", 0)
    share = ok / n if n else None
    if started == 0 and refused > 0:
        return "NO-GO"
    if share is None:
        return "no answers yet"
    if refused == 0 and share >= 0.9:
        return "GO"
    return "PARTIAL" if share >= 0.5 else "NO-GO"


def answers_text(a):
    return ", ".join(f"{k} {v}" for k, v in sorted(a.items())) or "–"


def tiers(all_rows):
    t1, t2 = heard(all_rows, "-t1"), heard(all_rows, "-t2", by_usage=True)
    stats = defaultdict(lambda: defaultdict(int))
    usage_of = {}
    for r in all_rows:
        key = (device(r), r["target_sdk"])
        e = r["event"]
        if e == "t1_posted":
            stats[key]["t1_posted"] += 1
        elif e == "t1_failed":
            stats[key]["t1_failed"] += 1
        elif e == "t2_started":
            d = detail(r)
            usage_of[r["run_id"]] = d.get("usage", "?")
            stats[(key, d.get("usage", "?"))]["started"] += 1
            stats[(key, d.get("usage", "?"))][f"focus_{d.get('focus', '?')}"] += 1
        elif e in ("t2_start_failed", "fgs_failed") and detail(r).get("usage") != "monitor":
            stats[(key, detail(r).get("usage", "?"))]["refused"] += 1
        elif e == "t2_check":
            stats[(key, usage_of.get(r["run_id"], "?"))]["playing_" + detail(r).get("playing", "?")] += 1
    t1_body = [
        [dev, tgt, stats[(dev, tgt)]["t1_posted"], stats[(dev, tgt)]["t1_failed"], answers_text(a),
         verdict(stats[(dev, tgt)]["t1_posted"], stats[(dev, tgt)]["t1_failed"], a)]
        for (dev, tgt), a in sorted(t1.items())
    ]
    t2_keys = sorted({k for k in stats if isinstance(k[0], tuple)})
    t2_body = []
    for (dev, tgt), usage in t2_keys:
        s = stats[((dev, tgt), usage)]
        a = t2.get(((dev, tgt), usage), {})
        refused = s["refused"]
        t2_body.append([
            dev, tgt, usage, s["started"], refused, s["focus_granted"], s["focus_failed"], s["playing_true"],
            s["playing_false"], answers_text(a), verdict(s["started"], refused, a),
        ])
    return (
        table(["Device", "Target", "Posted", "Failed", "Heard?", "T1 verdict"], t1_body),
        table(["Device", "Target", "Usage", "Started", "Refused", "Focus granted", "Focus failed", "Playing after 1.5 s",
               "Not playing", "Heard?", "T2 verdict"], t2_body),
    )


def monitor(all_rows):
    body = []
    for r in all_rows:
        if r["event"] != "monitor_battery":
            continue
        d = detail(r)
        minutes, start = int(d.get("minutes", 0)), int(d.get("start_pct", -1))
        if minutes >= 60 and start >= 0 and r["charging"] == "false":
            drop = start - int(r["battery_pct"])
            body.append((device(r), r["target_sdk"], minutes, drop, d.get("playing", "?")))
    last = {}
    for dev, tgt, minutes, drop, playing in body:
        if (dev, tgt) not in last or minutes > last[(dev, tgt)][0]:
            last[(dev, tgt)] = (minutes, drop, playing)
    return table(
        ["Device", "Target", "Session length", "Battery used", "Per hour", "Still playing"],
        [[dev, tgt, f"{m / 60:.1f} h", f"{d} %", f"{d / (m / 60):.1f} %/h", p] for (dev, tgt), (m, d, p) in sorted(last.items())],
    )


def boots(all_rows):
    body = [
        [device(r), r["target_sdk"], r["wall_iso"], detail(r).get("missed", "?"), detail(r).get("rescheduled", "?")]
        for r in all_rows
        if r["event"] == "boot_completed"
    ]
    return table(["Device", "Target", "Boot", "Alarms missed while off", "Rescheduled"], body)


def ratings(all_rows):
    by_clip = defaultdict(list)
    for r in all_rows:
        if r["event"] == "rating":
            d = detail(r)
            by_clip[(device(r), d.get("clip", "?"))].append(int(d.get("stars", 0)))
    return table(["Device", "Clip", "Ratings", "Mean"],
                 [[dev, clip, " ".join(map(str, s)), f"{sum(s) / len(s):.1f}"] for (dev, clip), s in sorted(by_clip.items())])


def main(paths):
    all_rows = list(rows(paths))
    t1, t2 = tiers(all_rows)
    print(f"_Generated by spike/tools/analyze.py from {len(paths)} log file(s), {len(all_rows)} rows._\n")
    print("### Alarm lateness\n\n" + lateness(all_rows) + "\n")
    print("### T1 notification sound\n\n" + t1 + "\n")
    print("### T2 alarm-started playback\n\n" + t2 + "\n")
    print("### T3 monitor session battery\n\n" + monitor(all_rows) + "\n")
    print("### Reboot catch-up\n\n" + boots(all_rows) + "\n")
    print("### Clip ratings (T0, phone speaker)\n\n" + ratings(all_rows))


if __name__ == "__main__":
    main(sys.argv[1:])
