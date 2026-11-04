#!/usr/bin/env python3
"""Synthesises the roller game's sound effects as mono 16-bit 44.1 kHz WAV files.

Usage: python3 games/roller/tools/synth.py [--out DIR]

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


def square(phase, duty=0.5):
    """A band-limited enough square for short game blips: +1 for the duty share of each cycle."""
    return 1.0 if (phase % 1.0) < duty else -1.0


def lowpass(samples, cutoff):
    """One-pole low-pass, to take the fizz off square waves and noise."""
    a = 1 - math.exp(-2 * math.pi * cutoff / RATE)
    y = 0.0
    out = []
    for x in samples:
        y += a * (x - y)
        out.append(y)
    return out


def sweep(start, end, seconds, duty, decay, curve=1.0):
    """A square wave gliding from start to end Hz over seconds, dying away at decay per second."""
    n = int(RATE * seconds)
    out, phase = [], 0.0
    for i in range(n):
        t = i / n
        freq = start * (end / start) ** (t ** curve)
        phase += freq / RATE
        env = math.exp(-decay * i / RATE) * min(1.0, i / (RATE * 0.002))
        out.append(square(phase, duty) * env)
    return lowpass(out, 6000.0)


def jump(rng):
    # A quick rising chirp, 160 ms: the classic platformer hop.
    return sweep(300.0, 900.0, 0.16, duty=0.25, decay=10.0, curve=0.6)


def note(freq, seconds, duty=0.5, decay=8.0, vibrato=0.0):
    n = int(RATE * seconds)
    out, phase = [], 0.0
    for i in range(n):
        s = i / RATE
        f = freq * (1 + vibrato * math.sin(2 * math.pi * 6 * s))
        phase += f / RATE
        env = math.exp(-decay * s) * min(1.0, s / 0.003)
        out.append(square(phase, duty) * env)
    return out


def join(*parts):
    out = []
    for p in parts:
        out.extend(p)
    return out


def mix(layers, total):
    """Mixes (start seconds, samples, gain) layers into one buffer total seconds long."""
    out = [0.0] * int(RATE * total)
    for start, samples, gain in layers:
        offset = int(RATE * start)
        for i, s in enumerate(samples):
            if offset + i < len(out):
                out[offset + i] += s * gain
    return out


def ring(rng):
    # A ring: two bright notes, B5 then E6, the second ringing out, 300 ms.
    return lowpass(mix([(0.0, note(987.77, 0.07, duty=0.5, decay=4.0), 1.0), (0.06, note(1318.5, 0.24, duty=0.5, decay=9.0), 1.0)], 0.3), 7000.0)


def stomp(rng):
    # A squelchy stomp: a pitch drop from 220 Hz to 50 Hz under a short burst of low noise, 220 ms.
    n = int(RATE * 0.22)
    out, phase, noise = [], 0.0, 0.0
    for i in range(n):
        t = i / n
        freq = 220.0 * (50.0 / 220.0) ** t
        phase += freq / RATE
        noise += 0.25 * ((rng.random() * 2 - 1) - noise)
        env = math.exp(-5.0 * t) * min(1.0, i / (RATE * 0.002))
        out.append((math.sin(2 * math.pi * phase) * 0.9 + square(phase, 0.3) * 0.25 + noise * 0.9 * math.exp(-12 * t)) * env)
    return lowpass(out, 3000.0)


def hurt(rng):
    # Hit: a buzzy fall from 520 Hz to 140 Hz with a wobble, 350 ms.
    n = int(RATE * 0.35)
    out, phase = [], 0.0
    for i in range(n):
        t = i / n
        freq = 520.0 * (140.0 / 520.0) ** t * (1 + 0.08 * math.sin(2 * math.pi * 30 * i / RATE))
        phase += freq / RATE
        env = math.exp(-3.5 * t) * min(1.0, i / (RATE * 0.002))
        out.append(square(phase, 0.4) * env)
    return lowpass(out, 3500.0)


def heart(rng):
    # A heart picked up: C5 E5 G5 C6 quick and rising, 450 ms.
    notes = [(0.00, 523.25), (0.06, 659.25), (0.12, 783.99), (0.18, 1046.5)]
    layers = [(start, note(freq, 0.25 if freq < 1000 else 0.27, duty=0.25, decay=7.0), 1.0) for start, freq in notes]
    return lowpass(mix(layers, 0.45), 6000.0)


def complete(rng):
    # Level complete: G4 C5 E5 G5, a beat, then a held E5 G5 C6 chord with a little vibrato, 1.6 s.
    run = [(0.00, 392.00), (0.12, 523.25), (0.24, 659.25), (0.36, 783.99)]
    layers = [(start, note(freq, 0.2, duty=0.5, decay=6.0), 0.8) for start, freq in run]
    for freq in (659.25, 783.99, 1046.5):
        layers.append((0.6, note(freq, 1.0, duty=0.5, decay=2.2, vibrato=0.006), 0.55))
    return lowpass(mix(layers, 1.6), 5000.0)


def gameover(rng):
    # Game over: E4 D#4 D4, then a long C#4 sinking, 1.4 s.
    layers = [
        (0.00, note(329.63, 0.3, duty=0.5, decay=3.0), 1.0),
        (0.30, note(311.13, 0.3, duty=0.5, decay=3.0), 1.0),
        (0.60, note(293.66, 0.3, duty=0.5, decay=3.0), 1.0),
        (0.90, sweep(277.18, 220.0, 0.5, duty=0.5, decay=2.5), 1.0),
    ]
    return lowpass(mix(layers, 1.4), 3000.0)


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
    sounds = (
        ("jump", jump),
        ("ring", ring),
        ("stomp", stomp),
        ("hurt", hurt),
        ("heart", heart),
        ("complete", complete),
        ("gameover", gameover),
    )
    for name, make in sounds:
        samples = finish(make(random.Random(1234)))
        path = args.out / f"{name}.wav"
        write(path, samples)
        print(f"{path.name}: {len(samples) / RATE * 1000:.0f} ms, {path.stat().st_size} bytes")


if __name__ == "__main__":
    main()
