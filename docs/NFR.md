# Non-Functional Requirements & Measurement

These targets come from `docs/PRD.md`. Each has: the **target**, **how we
measure it**, and **when it's checked** (CI, release gate, or manual
benchmark).

## 1. Transcription speed

| Target | Measurement |
|--------|-------------|
| ≤ 1.5× real-time on a 4 GB RAM device (30 min audio → ≤ 45 min) | Instrumented benchmark: transcribe a fixed 10-min mono 16 kHz WAV with `base` model on a real device; record wall time. Store results in `docs/benchmarks/`. |

- Benchmarked **per model size** (tiny / base / small) and per ABI (arm64-v8a
  is the target; armeabi-v7a optional).
- Run at every release on a reference device list (see `docs/TESTING.md`).

## 2. APK size ≤ 80 MB

| Target | Measurement |
|--------|-------------|
| ≤ 80 MB | `ls -lh app/build/outputs/apk/release/app-release.apk` in CI; fail the release job if exceeded. |

Budget breakdown (indicative):

| Component | Size |
|-----------|------|
| App code + Compose | ~12 MB |
| libwhisper.so (arm64-v8a) | ~5 MB |
| Whisper `base` GGML model (bundled default) | ~74 MB |
| **Total** | **~91 MB** ⚠️ |

> ⚠️ Bundling `base` (~74 MB) exceeds the 80 MB target. **Decision needed at
> implementation time:** bundle `tiny` (~39 MB) to hit the budget and offer
> `base`/`small` as an optional one-time download, or accept a larger APK and
> ship `base`. The scaffold defaults the user-facing setting to `BASE` but the
> bundled model, if any, must be chosen to satisfy this NFR.

## 3. Battery ≤ 15% per session

| Target | Measurement |
|--------|-------------|
| ≤ 15% drain per 30-min record + transcribe session | Manual benchmark: fully charge, run one record+transcribe cycle, measure delta; on reference devices. Log CPU busy-time via `adb shell dumpsys batterystats`. |

Optimizations if exceeded:
- Cap Whisper threads to `min(cpuCount, 4)` (already in `WhisperTranscriber`).
- Screen-off tolerant background via a foreground service (post-scaffold task).
- Do not poll progress faster than needed; keep UI light during inference.

## 4. Storage

| Target | Measurement |
|--------|-------------|
| Transcripts plain text; audio opt-in only | Code review + instrumented test asserting `cacheDir` is pruned after READY. |

## 5. Offline (airplane mode)

| Target | Measurement |
|--------|-------------|
| 100% of P0 works in airplane mode | Manual test matrix: full happy path in airplane mode on reference devices. CI static check: no `uses-permission android:name="android.permission.INTERNET"` and no network client dependency in `build.gradle.kts`. |

## 6. Memory (4 GB devices)

| Target | Measurement |
|--------|-------------|
| No OOM on ≥ 4 GB RAM devices | RAM gate at launch (< 4 GB → "requires 4 GB" screen). Instrumented test on low-RAM emulator (`adb shell setprop dalvik.vm.heapsize ...`). Monitor `android.util.MemoryInfo`. |

Whisper `base` on 4 GB devices is expected to peak ~600–900 MB RSS during
inference; `small` may exceed it — hence the model-size warning in settings.

## 7. Min Android & device reach

| Target | Measurement |
|--------|-------------|
| minSdk 26 (Android 8.0) | Enforced by `app/build.gradle.kts` (`minSdk = 26`). |
| Mid-range 4 GB RAM | Manual matrix below. |

## 8. Stability / crash-free

| Target | Measurement |
|--------|-------------|
| ≥ 70% of sessions complete without crash | Play Console crash-free users + session-completion funnel metric (local analytics event, opt-in, crash-safe). |
| Rating ≥ 4.2 | Play Store reviews. |

---

## Reference device matrix (manual benchmark)

| Device | OS | RAM | Notes |
|--------|----|-----|-------|
| Pixel 6a / mid-range reference | 13+ | 6 GB | Performance reference |
| Low-end Android Go device | 10 | 2 GB | Must show RAM gate, never crash |
| Old flagship (e.g. Galaxy S8-class) | 8 | 4 GB | Core perf target |
| Budget 2023 device | 12 | 4 GB | Core perf target |

## When each gate runs

| Gate | Frequency |
|------|-----------|
| Unit tests + lint + assembleDebug | Every commit (CI) |
| APK size check | Every commit (CI, release config) |
| No-INTERNET static check | Every commit (CI) |
| Transcription speed + battery benchmarks | Every release |
| Manual offline happy path | Every release |
