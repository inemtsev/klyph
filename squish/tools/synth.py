#!/usr/bin/env python3
"""Synthesises the squish game's sound effects as mono 16-bit 44.1 kHz WAV files.

Usage: python3 squish/tools/synth.py [--out DIR]

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


def resonant(samples, cutoffs, q):
    """Runs samples through a state-variable band-pass whose cutoff follows cutoffs, one per sample."""
    low = band = 0.0
    out = []
    for x, cutoff in zip(samples, cutoffs):
        f = 2 * math.sin(math.pi * cutoff / RATE)
        high = x - low - q * band
        band += f * high
        low += f * band
        out.append(band)
    return out


def squish(rng):
    # A wet splat: noise through a band-pass sweeping down from 2.4 kHz to 250 Hz, over a sine
    # that drops from 420 Hz to 70 Hz, both dying away in about 180 ms.
    n = int(RATE * 0.18)
    noise = [rng.random() * 2 - 1 for _ in range(n)]
    cutoffs = [2400.0 * (250.0 / 2400.0) ** (i / n) for i in range(n)]
    wet = resonant(noise, cutoffs, q=0.35)
    out, phase = [], 0.0
    for i in range(n):
        t = i / n
        freq = 420.0 * (70.0 / 420.0) ** t
        phase += freq / RATE
        env = math.exp(-6.0 * t) * min(1.0, i / (RATE * 0.003))
        out.append((wet[i] * 1.6 + math.sin(2 * math.pi * phase) * 0.7) * env)
    return out


def chomp(rng, n):
    """One bite: a short crunchy noise burst over a low thump."""
    noise = [rng.random() * 2 - 1 for _ in range(n)]
    crunch = resonant(noise, [1800.0] * n, q=0.8)
    out = []
    for i in range(n):
        t = i / n
        seconds = i / RATE
        env = math.exp(-9.0 * t)
        thump = math.sin(2 * math.pi * 110.0 * seconds) * math.exp(-seconds / 0.03)
        out.append(crunch[i] * env * 1.2 + thump * 0.6)
    return out


def munch(rng):
    # Three quick bites, the last one softest, about 340 ms in all.
    gap = int(RATE * 0.11)
    bite = int(RATE * 0.09)
    out = [0.0] * (gap * 2 + bite)
    for k, gain in enumerate((1.0, 0.85, 0.6)):
        for i, s in enumerate(chomp(rng, bite)):
            out[k * gap + i] += s * gain
    return out


def bell(freq, count, decay):
    """A soft bell: a sine with a little of its octave and twelfth, struck and ringing out."""
    out = []
    for i in range(count):
        seconds = i / RATE
        env = math.exp(-decay * seconds) * min(1.0, seconds / 0.004)
        out.append(
            (
                math.sin(2 * math.pi * freq * seconds)
                + 0.35 * math.sin(2 * math.pi * 2 * freq * seconds)
                + 0.12 * math.sin(2 * math.pi * 3 * freq * seconds)
            )
            * env
        )
    return out


def sequence(notes, total):
    """Mixes bells into one buffer: notes holds (start seconds, frequency, length seconds, decay)."""
    out = [0.0] * int(RATE * total)
    for start, freq, length, decay in notes:
        offset = int(RATE * start)
        for i, s in enumerate(bell(freq, int(RATE * length), decay)):
            if offset + i < len(out):
                out[offset + i] += s
    return out


def start(rng):
    # Round start: C5 E5 G5 C6 rising, 90 ms apart, the last note ringing.
    notes = [(0.00, 523.25, 0.3, 9.0), (0.09, 659.25, 0.3, 9.0), (0.18, 783.99, 0.3, 9.0), (0.27, 1046.5, 0.5, 6.0)]
    return sequence(notes, 0.8)


def end(rng):
    # Round end: a happy little fanfare, G4 C5 E5, a pause, then G5 and C6 held as a chord.
    notes = [
        (0.00, 392.00, 0.25, 8.0),
        (0.14, 523.25, 0.25, 8.0),
        (0.28, 659.25, 0.25, 8.0),
        (0.50, 783.99, 0.9, 3.5),
        (0.50, 1046.5, 0.9, 3.5),
        (0.50, 523.25, 0.9, 3.5),
    ]
    return sequence(notes, 1.4)


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
    for name, make in (("squish", squish), ("munch", munch), ("start", start), ("end", end)):
        samples = finish(make(random.Random(1234)))
        path = args.out / f"{name}.wav"
        write(path, samples)
        print(f"{path.name}: {len(samples) / RATE * 1000:.0f} ms, {path.stat().st_size} bytes")


if __name__ == "__main__":
    main()
