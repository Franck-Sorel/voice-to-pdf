# Architecture

## 1. Big picture

Voice to PDF is a **single-APK, offline-first Android app**. All heavy work
happens on-device: audio capture, PCM decoding, Whisper inference, and PDF
rendering. The architecture is deliberately boring — small, layered, and easy
to test — because the hard problems here are *performance on 4 GB devices* and
*native integration*, not architectural novelty.

```mermaid
flowchart TB
    subgraph UI["UI layer (Jetpack Compose)"]
        Home["HomeScreen\n(record / import)"]
        Sessions["SessionsScreen\n(list)"]
        Session["SessionScreen\n(edit / export)"]
        Settings["SettingsScreen\n(model / language)"]
    end
    subgraph VM["ViewModels (Hilt)"]
        HomeVM["HomeViewModel"]
        SessionsVM["SessionsViewModel"]
        SessionVM["SessionViewModel"]
        SettingsVM["SettingsViewModel"]
    end
    subgraph Domain["Core domain (pure Kotlin, no Android)"]
        ifaces["Interfaces:\nAudioRecorder / Transcriber /\nPdfExporter / SessionRepository"]
        models["Models:\nSession / WhisperModel /\nSupportedLanguage / segments"]
        fmt["TranscriptFormatter\n(pause-based paragraphs)"]
    end
    subgraph Data["Data layer (Android impls)"]
        Room["Room DB (sessions)"]
        Prefs["Settings (SharedPreferences)"]
        Recorder["AndroidAudioRecorder\n(MediaRecorder)"]
        PCM["MediaCodecPcmDecoder"]
        Whisper["WhisperNative (JNI)\n+ WhisperTranscriber"]
        PDF["AndroidPdfExporter\n(PdfDocument)"]
    end
    subgraph Native["Native (built separately)"]
        libwhisper["libwhisper.so\n(whisper.cpp)"]
        model["GGML model file"]
    end

    Home --> HomeVM
    Sessions --> SessionsVM
    Session --> SessionVM
    Settings --> SettingsVM
    HomeVM --> Recorder
    HomeVM --> Whisper
    HomeVM --> Room
    HomeVM --> Prefs
    SessionsVM --> Room
    SessionVM --> Room
    SessionVM --> PDF
    SettingsVM --> Prefs
    Whisper --> PCM
    Whisper --> libwhisper
    libwhisper --> model
```

## 2. Layer rules

- **`core/` must not depend on Android.** It holds interfaces, models, and pure
  functions. This is what makes the paragraph-break logic (F7) unit-testable on
  the JVM (`TranscriptFormatterTest`).
- **`data/` depends on `core/`** and implements its interfaces using Android
  APIs.
- **`ui/` depends on `core/` and `data/`** through ViewModels. Screens never
  touch Room or MediaCodec directly.
- **Hilt** (`di/`) wires interfaces to implementations, so tests can swap in
  fakes.

Dependency direction is always inward: `ui → data → core`.

## 3. Data flow — the MVP 1 happy path

```
1. Record          MediaRecorder -> cacheDir/rec_<ts>.m4a (AAC)
2. Stop            AudioRecorder.stop() -> durationMs + file
3. Persist         Session(TRANSCRIBING) inserted into Room
4. Decode          MediaCodecPcmDecoder -> 16 kHz mono ShortArray
5. Infer           WhisperNative.whisper_transcribe(ctx, pcm)
6. Store           Session(READY, transcript)
7. Edit            SessionScreen OutlinedTextField (draft)
8. Export          PdfExporter.export(request, contentUri) -> A4 PDF
9. Share/Print     ACTION_CREATE_DOCUMENT / ACTION_SEND (MVP 1 F6)
```

Transcription is **post-hoc** (never real-time): the user stops recording,
then waits. Progress is reported as `processedMs / totalMs`.

### Import path (F1)

`ACTION_OPEN_DOCUMENT` (Storage Access Framework) → the app copies the picked
file into `cacheDir` → the same transcribe pipeline runs. No storage
permission is needed because SAF grants per-file access.

## 4. Audio pipeline

```mermaid
flowchart LR
    SRC["MP3 / WAV / OGG / M4A"] --> EXT["MediaExtractor"]
    EXT --> MC["MediaCodec\ndecoder -> PCM16"]
    MC --> MIX["stereo -> mono\n(average channels)"]
    MIX --> RES["resample -> 16 kHz\n(TODO, see below)"]
    RES --> W["WhisperNative\ntranscribe(ctx, ShortArray)"]
```

- Our recorder already outputs 16 kHz mono AAC, so decode is a passthrough.
- Imported files may be 44.1 kHz stereo. Channel downmix is implemented;
  **sample-rate conversion to 16 kHz is a TODO** in `MediaCodecPcmDecoder`
  (it currently asserts the source is 16 kHz). The resampler is a
  self-contained ~50-line function (linear/SoX-style interpolation) and is the
  first natural implementation task.
- Whisper expects raw 16-bit little-endian mono PCM at 16 kHz.

## 5. Storage schema

Room database `voicetopdf.db`, single table:

```
sessions
  id            INTEGER PRIMARY KEY AUTOINCREMENT
  title         TEXT
  createdAtEpochMs  INTEGER
  status        TEXT  ('TRANSCRIBING' | 'READY' | 'ERROR')
  transcript    TEXT NULL
  audioPath     TEXT NULL     -- only if user opted to keep audio
  durationMs    INTEGER
  language      TEXT  (enum name)
  model         TEXT  (enum name)
```

Room schema JSON is exported to `app/schemas/` (append-only — see
`ksp { arg("room.schemaLocation", ...) }` in `app/build.gradle.kts`).

> Storage rule from the PRD: transcripts are plain text; audio is retained
> only if the user opts in. MVP 1 keeps a `cacheDir` copy and prunes it once a
> session is READY.

## 6. Threading model

| Work | Thread | How |
|------|--------|-----|
| UI | Main | Compose |
| Recording | Main (MediaRecorder does its own I/O) | `AudioRecorder` |
| PCM decode | Background | `Dispatchers.Default` via `withContext` |
| Whisper inference | Background, CPU | `Dispatchers.Default`; native call is blocking |
| Room | Main-safe | Suspend DAO + Flow |
| PDF render + disk write | Background | `Dispatchers.IO` |

`Transcriber.transcribe` is a **blocking native call**; implementations must
hop off the main thread. ViewModels launch in `viewModelScope`.

## 7. Offline-first design

- No permissions for network. No HTTP client dependency at all in MVP 1.
- Model files live in `filesDir/models/<id>.bin`. For MVP 1 they are bundled
  into `assets/` at build time; for MVP 2 (larger 1B/3B LLMs) they become a
  one-time optional download with a clear consent screen, then work offline.
- If the device has < 4 GB RAM at launch, show a "requires 4 GB" gate screen
  (risk mitigation).

## 8. MVP 2 extension points (no re-architecture needed)

- `core/model/` already separates `Session` (MVP 1) from future `StructuredDoc`.
- A new `StructuringEngine` interface sits next to `Transcriber`, with two
  implementations (on-device llama.cpp, optional cloud) behind the same
  interface — matches the hybrid decision in `docs/PRD.md §5.5`.
- MVP 2 can ship as a **separate APK or in-app module**; the single `:app`
  module today is fine to start, and can be split into `:app`, `:core`,
  `:recognition`, `:pdf` feature modules later without touching domain code.

## 9. Security & privacy

- Default path: **zero data leaves the device**.
- RECORD_AUDIO is the only runtime permission.
- Import/export use Storage Access Framework (scoped storage, no broad storage
  permission).
- The optional cloud "enhance" button (MVP 2) must show explicit "uses your
  data over the network" consent before any upload.
