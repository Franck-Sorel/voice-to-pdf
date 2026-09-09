# Third-party notices

This project is distributed under the **MIT License** (see `LICENSE`). It
depends on the following third-party works; each is redistributed under its
own license and must be shipped together with any binary that includes it.

## Runtime / libraries
| Component | License | Notes |
|-----------|---------|-------|
| [whisper.cpp](https://github.com/ggml-org/whisper.cpp) (STT runtime) | MIT | Bundled as `libwhisper.so`; a copy of the MIT license is placed at `app/src/main/assets/licenses/whisper.cpp.license` at build time. |
| AndroidX / Jetpack Compose / Room / Hilt / kotlinx | Apache-2.0 / MIT | Transitive via Gradle; standard AndroidX notices apply. |
| Android `PdfDocument` | Apache-2.0 (platform) | Part of the Android framework; no bundling required. |

## Models (data artifacts — MIT)
| Model | License | Usage |
|-------|---------|-------|
| `ggml-base-q8_0.bin` (Whisper base, INT8) | MIT | Default bundled STT model (~78 MB) |
| `ggml-tiny-q8_0.bin` (Whisper tiny, INT8) | MIT | Low-RAM / optional STT model (~41.5 MB) |

Whisper models are MIT-licensed (OpenAI Whisper weights, redistributed by the
whisper.cpp project).

## Deferred components (not yet bundled; add notices here when introduced)
- Piper TTS voices (MIT) — MVP 3
- llama.cpp (MIT) + Phi-3-mini / Gemma-2-2B GGUF (model terms vary; verify before bundling) — MVP 2/3

## Obligations
- Keep the *entire* MIT/Apache license text with the distribution.
- Do **not** remove this notice from a distributed APK.
- Verify license texts are in `app/src/main/assets/licenses/` before each
  release build (see `docs/SETUP.md`).
