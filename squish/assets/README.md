# Squish assets

Generated with xAI Grok Imagine (`grok-imagine-image`) on 2026-11-03 through the grok-image
skill in sprite mode: the skill appends "Single object only, centered, full view, no cropping, no
cast shadow, no text, no watermark, flat solid pure magenta background (#FF00FF) filling the
entire frame." to each prompt, keys out the magenta, trims and scales the longest side to the size
given. Each prompt ran once with `--n 2`, twelve generations in all at $0.02 each ($0.24), and
the better of each pair was kept. `ladybug.png` was turned 180 degrees after keying so that every
bug faces up like the others; nothing else was edited.

| File | Size | Use | Prompt |
|---|---|---|---|
| ant.png | 128x128 | Small, fast bug, faces up | Top-down view of a cute friendly cartoon ant facing up, bright orange body made of three round segments, big white eyes on the head, two curly antennae, six thin legs, thick dark outline, bold flat colors, children's picture book style. |
| ladybug.png | 128x128 | Middle bug, faces up | Top-down view of a cute friendly cartoon ladybug facing up, bright red shell with big round black spots, black head with two big white eyes, six short black legs, thick dark outline, bold flat colors, children's picture book style. |
| caterpillar.png | 128x128 | Big, slow bug, faces up | Top-down view of a cute friendly cartoon caterpillar facing up, a straight vertical line of five round bright lime-green body segments with yellow dots, round head at the top with big white eyes and tiny antennae, little stubby feet on both sides, thick dark outline, bold flat colors, children's picture book style. |
| cake.png | 256x256 | The cake in the middle of the picnic | Top-down view of a round birthday cake seen from directly above, white frosting with rainbow sprinkles, a ring of red strawberries around the edge, three yellow candles in the middle, thick dark outline, bold flat colors, children's picture book style. |
| watermelon.png | 192x192 | The two watermelon slices | A big juicy slice of watermelon lying flat, bright red flesh with black seeds, green and light-green striped rind, thick dark outline, bold flat colors, children's picture book style. |
| swatter.png | 192x192 | The cursor; its head's middle is at 46% across and 23% down | A cartoon fly swatter, bright yellow square mesh head with a dark grid pattern at the top, straight blue handle going down to the bottom right, thick dark outline, bold flat colors, children's picture book style. |

The splat under a squished bug, the droplets and crumbs, and the gingham blanket are drawn in code
(`Game.kt` and `Screens.kt`), not loaded from files.

## Sounds

`squish.wav`, `munch.wav`, `start.wav` and `end.wav` are synthesised by `tools/synth.py`
(Python 3, standard library only). The script is deterministic, so running it again rewrites
byte-identical files. From the repository root:

```
python3 squish/tools/synth.py              # writes into squish/assets/
python3 squish/tools/synth.py --out <dir>  # writes somewhere else
```

| File | Length | Use |
|---|---|---|
| squish.wav | 180 ms | A bug is squished; noise through a band-pass sweeping from 2.4 kHz down to 250 Hz over a sine dropping from 420 Hz to 70 Hz |
| munch.wav | 310 ms | A bug takes a bite; three crunchy noise bursts over a 110 Hz thump, each softer |
| start.wav | 800 ms | A round starts; bell tones C5, E5, G5, C6 rising |
| end.wav | 1400 ms | A round ends; G4, C5, E5, then a held C major chord |

All are mono, 16-bit, 44.1 kHz, normalised to a 0.9 peak with a 5 ms fade at both ends.

`music.ogg` (21.3 s, OGG Vorbis, stereo, 44.1 kHz, 436,390 bytes) is "Upbeat Loop.ogg" from
"Upbeat short Music Loop Vorbis oog" by BenjaTheMaker, submitted 2016-03-02,
<https://opengameart.org/node/54530>. Licence: CC0 1.0 Universal (public domain dedication),
<https://creativecommons.org/publicdomain/zero/1.0/>. The page's "License(s)" field reads "CC0"
and links to that deed, and lists the file as "Upbeat Loop.ogg 436.4 Kb"; its description reads
"This is a short upbeat loop." The file was downloaded on 2026-11-03 from
<https://opengameart.org/sites/default/files/Upbeat%20Loop_2.ogg> and is not converted or edited
(SHA-256 `a9d5b3d0e4695b02a85e03bcdf9a60beb847a12908d07c8ff41edc2f36b8bd66`).
