# stb_image

Vendored single-header image decoder from https://github.com/nothings/stb.

- File: `stb_image.h`, unmodified.
- Version: `stb_image - v2.30`.
- Upstream commit: `003501f6d7a8668ce8b6d875e34dd97ad0d5d5b5` (master as of 2026-10-31).
- SHA-256: `594c2fe35d49488b4382dbfaec8f98366defca819d916ac95becf3e75f4200b3`.
- Licence: dual, at the user's choice: MIT, or public domain (Unlicense). The full text is at the
  end of the header.

The `stb` cinterop (`../../cinterop/stb.def`) compiles the implementation into the interop stubs
with `STBI_ONLY_PNG`, `STBI_ONLY_JPEG` and `STBI_NO_STDIO`, so no separate library is built or
linked.

To update, download `stb_image.h` from a pinned commit and refresh the lines above.

# stb_vorbis

Vendored single-file Ogg Vorbis decoder from the same repository.

- File: `stb_vorbis.c`, unmodified. It is both the header and the implementation.
- Version: `stb_vorbis - v1.22`.
- Upstream commit: `003501f6d7a8668ce8b6d875e34dd97ad0d5d5b5`, the same as `stb_image.h`.
- SHA-256: `4c7cb2ff1f7011e9d67950446b7eb9ca044f2e464d76bfbb0b84dd2e23e65636`.
- Licence: dual, at the user's choice: MIT, or public domain (Unlicense). The full text is at the
  end of the file.

The `vorbis` cinterop (`../../cinterop/vorbis.def`) generates its bindings from the file with
`STB_VORBIS_HEADER_ONLY` defined, then includes it again in the def's C section without that macro,
so the implementation is compiled into the interop stubs. Both passes define `STB_VORBIS_NO_STDIO`
and `STB_VORBIS_NO_PUSHDATA_API`: klyph decodes from memory with the pull API only.

To update, download `stb_vorbis.c` from a pinned commit and refresh the lines above.
