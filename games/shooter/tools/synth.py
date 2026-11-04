#!/usr/bin/env python3
"""Synthesises the shooter's sound effects as mono 16-bit 44.1 kHz WAV files.

Usage: python3 games/shooter/tools/synth.py [--out DIR]

The output is deterministic: a fixed seed and no clock, so two runs write identical bytes.
"""
import argparse
import math
import random
import struct
import wave
from pathlib import Path

RATE = 44100
PEAK = 0.9
FADE_SECONDS = 0.005


def square(phase):
    return 1.0 if (phase % 1.0) < 0.5 else -1.0


def shot(rng):
    n = int(RATE * 0.12)
    out, phase = [], 0.0
    for i in range(n):
        t = i / n
        freq = 1200.0 * (300.0 / 1200.0) ** t
        phase += freq / RATE
        env = math.exp(-5.0 * t)
        noise = (rng.random() * 2 - 1) * 0.3 * math.exp(-60.0 * t)
        out.append(square(phase) * 0.6 * env + noise)
    return out


def explosion(rng):
    n = int(RATE * 0.5)
    out, low = [], 0.0
    for i in range(n):
        t = i / n
        seconds = i / RATE
        alpha = 0.5 * (0.02 / 0.5) ** t
        low += alpha * ((rng.random() * 2 - 1) - low)
        noise = low * math.exp(-3.0 * t)
        thump = math.sin(2 * math.pi * 60.0 * seconds) * math.exp(-seconds / 0.06) if seconds < 0.2 else 0.0
        out.append(noise * 2.5 + thump * 0.8)
    return out


def tone(freq, count, decay):
    return [square(freq * i / RATE) * 0.5 * math.exp(-decay * i / count) for i in range(count)]


def hit(rng):
    half = int(RATE * 0.1)
    return tone(440.0, half, 4.0) + tone(330.0, half, 5.0)


def gameover(rng):
    count = int(RATE * 0.3)
    out = []
    for freq in (392.0, 311.0, 262.0):
        out += [
            (math.sin(2 * math.pi * freq * i / RATE) + 0.3 * square(freq * i / RATE))
            * math.exp(-3.5 * i / count)
            for i in range(count)
        ]
    return out


def finish(samples):
    fade = int(RATE * FADE_SECONDS)
    for i in range(fade):
        gain = i / fade
        samples[i] *= gain
        samples[-1 - i] *= gain
    scale = PEAK / max(abs(s) for s in samples)
    return [s * scale for s in samples]


def write(path, samples):
    with wave.open(str(path), "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(RATE)
        f.writeframes(b"".join(struct.pack("<h", int(round(s * 32767))) for s in samples))


def main():
    default = Path(__file__).resolve().parent.parent / "assets"
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, default=default, help="output folder")
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    for name, make in (("shot", shot), ("explosion", explosion), ("hit", hit), ("gameover", gameover)):
        samples = finish(make(random.Random(1234)))
        path = args.out / f"{name}.wav"
        write(path, samples)
        print(f"{path.name}: {len(samples) / RATE * 1000:.0f} ms, {path.stat().st_size} bytes")


if __name__ == "__main__":
    main()
