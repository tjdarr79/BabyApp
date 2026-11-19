"""Synthesizes the spike's placeholder sounds (no recordings, no licences needed).

These are test signals for alarm/notification/playback audibility, not candidate baby audio. Real candidate
clips from docs/08 are imported on the phone (the app's "Import clip" button) and are never committed.

Usage: python3 spike/tools/make_clips.py spike/app/src/main/res/raw
"""

import math
import random
import struct
import sys
import wave

RATE = 22050
OUT = sys.argv[1]


def write(name, samples):
    peak = max(1e-9, max(abs(s) for s in samples))
    with wave.open(f"{OUT}/{name}.wav", "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(32000 * s / peak * 0.9)) for s in samples))


def cry_burst(seconds, f0, rng):
    """One 'waah': rising-then-falling pitch, harmonics, vibrato, breath noise, soft attack and release."""
    n = int(seconds * RATE)
    out, phase = [], 0.0
    for i in range(n):
        x = i / n
        contour = 1.0 + 0.18 * math.sin(math.pi * x) - 0.08 * x
        vibrato = 1.0 + 0.012 * math.sin(2 * math.pi * 6.5 * i / RATE)
        phase += 2 * math.pi * f0 * contour * vibrato / RATE
        tone = sum(math.sin(k * phase) / (k ** 1.3) for k in range(1, 7))
        env = min(1.0, x / 0.08) * min(1.0, (1 - x) / 0.15)
        out.append(env * (tone + 0.15 * rng.uniform(-1, 1)))
    return out


def silence(seconds):
    return [0.0] * int(seconds * RATE)


def cry(seconds, rng):
    out = []
    while len(out) < seconds * RATE:
        out += cry_burst(rng.uniform(0.8, 1.3), rng.uniform(420, 560), rng) + silence(rng.uniform(0.25, 0.45))
    return out[: int(seconds * RATE)]


def ambient(seconds, rng):
    """Soft brown noise with matching start and end, for a looping 'sleeping baby' monitor bed."""
    out, level = [], 0.0
    for _ in range(int(seconds * RATE)):
        level = 0.995 * level + 0.02 * rng.uniform(-1, 1)
        out.append(level)
    fade = int(0.5 * RATE)
    for i in range(fade):
        g = i / fade
        out[i] *= g
        out[-1 - i] *= g
    return out


rng = random.Random(7)
write("cry_short", cry(3.0, rng))
write("cry_loop", cry(12.0, rng))
write("ambient_loop", ambient(8.0, rng))
print("wrote cry_short, cry_loop, ambient_loop to", OUT)
