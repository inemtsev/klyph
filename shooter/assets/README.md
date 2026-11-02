# Shooter assets

Generated with xAI Grok Imagine (`grok-imagine-image`) on 2026-10-31 through the grok-image
skill, keyed out from a magenta background and trimmed. `explosion.png` is a 6-frame strip of
128x128 cells built from one generated burst by scaling it up and fading it out.

| File | Size | Use |
|---|---|---|
| ship.png | 128x128 | Player ship, points up |
| enemy.png | 128x128 | Enemy ship, points down |
| bullet.png | 64x64 | Player bullet, vertical |
| explosion.png | 768x128 | Explosion animation, 6 frames of 128x128, left to right |

## Sounds

`shot.wav`, `explosion.wav`, `hit.wav` and `gameover.wav` are synthesised by `tools/synth.py`
(Python 3, standard library only). The script is deterministic, so running it again rewrites
byte-identical files. From the repository root:

```
python3 shooter/tools/synth.py              # writes into shooter/assets/
python3 shooter/tools/synth.py --out <dir>  # writes somewhere else
```

| File | Length | Use |
|---|---|---|
| shot.wav | 120 ms | Player fires; square-wave sweep from 1200 Hz down to 300 Hz |
| explosion.wav | 500 ms | Enemy destroyed; low-passed noise with a 60 Hz thump |
| hit.wav | 200 ms | Player is hit; two-tone sting, 440 Hz then 330 Hz |
| gameover.wav | 900 ms | Game over; three descending notes, 392, 311 and 262 Hz |

All are mono, 16-bit, 44.1 kHz, normalised to a 0.9 peak with a 5 ms fade at both ends.

`music.ogg` (46.8 s, OGG Vorbis, mono, 44.1 kHz) is the track "Mars" from "NES Shooter Music
(5 tracks, 3 jingles)" by SketchyLogic, <https://opengameart.org/content/nes-shooter-music-5-tracks-3-jingles>.
Licence: CC0 1.0 Universal (public domain dedication),
<https://creativecommons.org/publicdomain/zero/1.0/>; the page lists the licence as CC0. The file
is the original `Mars.ogg` from that page, not converted or edited.
