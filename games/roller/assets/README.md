# Roller assets

Generated with xAI Grok Imagine (`grok-imagine-image`) on 2026-11-04 through the grok-image
skill in sprite mode: the skill appends "Single object only, centered, full view, no cropping, no
cast shadow, no text, no watermark, flat solid pure magenta background (#FF00FF) filling the
entire frame." to each prompt, keys out the magenta, trims and scales the longest side to the size
given, padding to a square. Ten prompts ran once each with `--n 2`, and one edit of the first
skating frame ran with `--n 2`: 22 images in all at $0.02 each ($0.44). The better of each pair
was kept. After keying, every kept sprite was cropped to its opaque bounding box, so its bottom
edge is the bottom of the art (the hero's wheels, the blob's base, the sign's foot); nothing else
was edited.

| File | Size | Use | Prompt |
|---|---|---|---|
| ninja_skate1.png | 118x128 | The hero rolling, first skating frame; faces right | 16-bit pixel art video game sprite, side view facing right, a young ninja in a black outfit with a yellow headband whose two tails flow behind his head, orange goggles pushed up on the forehead, skating on black inline rollerblades with yellow wheels, leaning forward in a speed skating pose with one arm swung forward and one back, bold dark outline, bright colors. |
| ninja_skate2.png | 118x128 | Second skating frame, an edit (`--edit`) of the first's download | The same ninja skater in the same pixel art style, same size and position, but the next frame of his skating stride: the other leg pushes back now and the arms are swapped, the left arm forward and the right arm back. |
| ninja_jump.png | 95x128 | The hero in the air | 16-bit pixel art video game sprite, side view facing right, a young ninja in a black outfit with a yellow belt and a yellow headband whose two tails flow behind his head, orange goggles pushed up on the forehead, black inline rollerblades with yellow wheels, jumping in mid-air with both knees tucked up high and one fist raised, bold dark outline, bright colors. |
| blob.png | 96x72 | The walking enemy, and its squashed remains | 16-bit pixel art video game enemy sprite, side view, a round squishy green slime blob monster sitting on the ground, two big white eyes with black pupils and an angry little mouth, glossy white highlight on top, bold dark outline, bright colors. |
| heli.png | 128x120 | The flying enemy; faces left | 16-bit pixel art video game enemy sprite, side view facing left, a small cute round green helicopter with one big angry eye in the cockpit window, a grey rotor blade on top, a short tail with a small tail rotor, two landing skids, bold dark outline, bright colors. |
| ring.png | 63x64 | Rings to collect, and the HUD's ring | 16-bit pixel art video game collectible, a shiny gold ring seen straight from the front, a thick round golden hoop with an empty hole in the middle, bright yellow highlight and orange shading, bold dark outline. |
| heart.png | 64x58 | Heart pickups, and the HUD's hearts | 16-bit pixel art video game health pickup, a plump bright red heart with a small white shine highlight in the top left, bold dark outline. |
| sign.png | 69x128 | The arrow sign at the end of the level | 16-bit pixel art video game sprite, side view, a round blue road sign with a big white arrow pointing right, mounted on top of a thin grey metal pole with a small square base, bold dark outline, bright colors. |
| poster_face.png | 142x192 | Billboard poster | 16-bit pixel art billboard poster, flat rectangle seen straight on, a black and white high contrast graphic of a cool skater's face wearing sunglasses, with a bold red vertical stripe along the left edge of the poster, no words. |
| poster_city.png | 141x192 | Billboard poster | 16-bit pixel art billboard poster, flat rectangle seen straight on, a black and white high contrast graphic of a city skyline under a big full moon, with a bold red vertical stripe along the right edge of the poster, no words. |
| plant.png | 67x96 | Potted plants on the roofs | 16-bit pixel art video game decoration, side view, a small potted plant, a round leafy green bush in an orange terracotta clay pot, bold dark outline, bright colors. |

The rest of the art is painted in code into an `Image` at startup (`Painting.kt`) and drawn with
nearest sampling: the brick buildings with their stone caps and windows, the steel ledges, the
billboards' lattice pylons, the air conditioners, the blocky clouds, the distant skyline, the sky
gradient and the impact star. The billboards' frames and catwalks are the engine's
`Assets.whitePixel`, tinted, and the dust and slime drops are `Assets.disc`. None of them is
loaded from a file.

## Sounds

`jump.wav`, `ring.wav`, `stomp.wav`, `hurt.wav`, `heart.wav`, `complete.wav` and `gameover.wav`
are synthesised by `tools/synth.py` (Python 3, standard library only). The script is
deterministic, so running it again rewrites byte-identical files. From the repository root:

```
python3 games/roller/tools/synth.py              # writes into games/roller/assets/
python3 games/roller/tools/synth.py --out <dir>  # writes somewhere else
```

| File | Length | Use |
|---|---|---|
| jump.wav | 160 ms | The hero jumps; a square wave chirping up from 300 Hz to 900 Hz |
| ring.wav | 300 ms | A ring is taken; B5 then E6, square waves, the second ringing out |
| stomp.wav | 220 ms | An enemy is stomped; a sine dropping from 220 Hz to 50 Hz under a burst of low noise |
| hurt.wav | 350 ms | The hero is hit or falls into a gap; a buzzy square falling from 520 Hz to 140 Hz with a wobble |
| heart.wav | 450 ms | A heart is picked up; C5, E5, G5, C6 rising quickly |
| complete.wav | 1600 ms | The level is complete; G4, C5, E5, G5, then a held E5, G5, C6 chord |
| gameover.wav | 1400 ms | Game over; E4, D#4, D4, then C#4 sinking to A3 |

All are mono, 16-bit, 44.1 kHz, normalised to a 0.9 peak with a 5 ms fade at both ends.

`music.ogg` (1:55.2, OGG Vorbis, stereo, 44.1 kHz, 1,288,490 bytes) is the looping version of
"Sideways City" by Zane Little Music, submitted 2022-12-11, <https://opengameart.org/content/sideways-city>.
Licence: CC0 1.0 Universal (public domain dedication),
<https://creativecommons.org/publicdomain/zero/1.0/>; the page's "License(s)" field reads "CC0"
and links to that deed, and its description reads "A cheerful and bustling city where nothing
really makes much sense." The file was downloaded on 2026-11-04 from
<https://opengameart.org/sites/default/files/sideways_city_loop.wav> (32-bit float WAV, SHA-256
`549309746ba2609d7d91d19f5757de6c86a987017e7d0f54aa2a97cb88f8521c`) and converted to OGG Vorbis
with SoX (`sox sideways_city_loop.wav -C 2 music.ogg`), because the engine streams music from
OGG only; it is not otherwise edited (SHA-256 of the OGG
`02c40e518c499a65c832b346806495ef5b8646631b8e978b012ccdec6d2a78d0`).
