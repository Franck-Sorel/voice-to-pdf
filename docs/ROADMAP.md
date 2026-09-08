# Roadmap

## MVP 1 — Voice to PDF (foundation)

### M0 — Scaffold (current state ✅)

- [x] Project structure, Gradle toolchain (AGP 9.4 / Kotlin 2.3 / Gradle 9.7)
- [x] Domain model, interfaces, Room layer, PDF exporter, Compose UI skeleton
- [x] Documentation (PRD, architecture, decisions, NFR, testing)
- [ ] First successful `./gradlew assembleDebug` on a real machine

**Definition of done:** project opens in Android Studio, builds, unit tests pass.

### M1 — Core flow end-to-end

- [ ] Build & bundle `libwhisper.so` + a model (see `whisper/README.md`) — pick
  bundle strategy per `docs/NFR.md §2` (tiny bundled vs base bundled)
- [ ] Implement 16 kHz resampling in `MediaCodecPcmDecoder`
- [ ] Implement `MediaMetadataRetriever` duration for progress
- [ ] Wire record → transcribe → READY on a real device
- [ ] Export PDF to `Downloads/` and share via intent (F6)
- [ ] RAM gate screen (< 4 GB)

**Definition of done:** full happy path works offline in airplane mode on a
4 GB device; transcription ≤ 1.5× real-time for `base`.

### M2 — Polish, robustness, release

- [ ] Foreground service + notification for long transcriptions
- [ ] Auto-prune `cacheDir` audio after READY (ADR-007)
- [ ] Model-size trade-off warning dialog; optional model download
- [ ] CI: lint + test + APK-size gate + no-INTERNET check
- [ ] Crash reporting (opt-in, offline-friendly) + local completion funnel
- [ ] Beta on Play (internal track) → open test → production

**Definition of done:** NFR gates in `docs/NFR.md` pass; ≥ 70% session
completion in beta telemetry.

---

## MVP 2 — AI-enhanced structuring (future)

### M3 — Structured output (P0)

- [ ] `StructuringEngine` interface (on-device llama.cpp first)
- [ ] Markdown prompt (PRD §5.6), parser → heading/bullet model
- [ ] Diff view with per-section accept/reject (F7)
- [ ] Structured PDF export (F8) reusing `PdfExporter` with heading styles

### M4 — Quality & optional cloud

- [ ] Speaker diarization labels (F4)
- [ ] Title suggestion (F5), "polish" mode (F6)
- [ ] Optional "Enhance with Cloud" button with explicit consent (hybrid)
- [ ] Presentation mode (F9, P2)

**Definition of done:** ≥ 60% "Accept All" rate; on-device ≤ 60 s for
2,000 words on 6 GB device; cloud opt-in < 20%.

---

## MVP 3+ (ideas, not committed)

- Summaries, flashcards, quiz generation (from MVP 2 structured docs)
- Multi-document projects
- iOS/tablet

---

## Dependencies & ownership

- MVP 1 must be fully usable without MVP 2 (PRD §6).
- MVP 2 is a separate APK or in-app module, updateable independently.
- Native whisper build and model bundling are on the critical path for M1 —
  start early; a university partnership can host CI for native builds
  (PRD risk).
