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
