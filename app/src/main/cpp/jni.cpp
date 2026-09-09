// JNI bridge for the on-device STT runtime — whisper.cpp (docs/DECISIONS.md ADR-015).
//
// Implements com.sttapp.data.recognition.WhisperNative:
//   whisperInit(modelPath: String): Long                -> whisper_context* (opaque handle)
//   whisperTranscribe(ctx, pcm, sampleRate, threads, language): String  -> joined transcript
//   whisperRelease(ctx): Unit                           -> free the context
//
// Built by app/src/main/cpp/CMakeLists.txt against the whisper.cpp v1.9.3
// submodule. Kotlin calls these over JNI; native symbol names must match
// exactly (com/sttapp/... -> com_sttapp_...).

#include <jni.h>
#include <string>
#include <vector>

#include "whisper.h"   // from whisper.cpp/include (target `whisper` adds it)

// NOTE: whisper.cpp accepts ISO 639-1 codes and full names. Our Kotlin layer
// passes the ISO code (en/fr/pt/sw/ar), which maps to the multilingual model.

extern "C" {

// Load the GGML model file and return an opaque whisper_context* as jlong.
JNIEXPORT jlong JNICALL
Java_com_sttapp_data_recognition_WhisperNative_whisperInit(
    JNIEnv* env, jobject /*thiz*/, jstring jModelPath) {
  const char* modelPath = env->GetStringUTFChars(jModelPath, nullptr);
  whisper_context_params cparams = whisper_context_default_params();
  struct whisper_context* ctx =
      whisper_init_from_file_with_params_no_state(modelPath, cparams);
  env->ReleaseStringUTFChars(jModelPath, modelPath);
  return reinterpret_cast<jlong>(ctx);   // 0 on failure (whisper_init returns null)
}

// Transcribe 16-bit mono PCM ([-32768,32767]) at `sampleRate` Hz into text.
JNIEXPORT jstring JNICALL
Java_com_sttapp_data_recognition_WhisperNative_whisperTranscribe(
    JNIEnv* env, jobject /*thiz*/, jlong jCtx, jshortArray jPcm, jint jSampleRate,
    jint jThreads, jstring jLanguage) {
  struct whisper_context* ctx = reinterpret_cast<struct whisper_context*>(jCtx);
  if (ctx == nullptr) {
    return env->NewStringUTF("");   // model not loaded -> empty transcript
  }

  // whisper expects 16 kHz input; our pipeline guarantees jSampleRate == 16000
  // (PcmDecoder.iso). The parameter is part of the API contract but not variable here.
  (void)jSampleRate;

  const jsize n = env->GetArrayLength(jPcm);
  jshort* raw = env->GetShortArrayElements(jPcm, nullptr);
  std::vector<float> samples;
  samples.reserve(n);
  for (jsize i = 0; i < n; ++i) samples.push_back(static_cast<float>(raw[i]));
  env->ReleaseShortArrayElements(jPcm, raw, JNI_ABORT);

  if (samples.empty()) {
    return env->NewStringUTF("");
  }

  const char* lang = env->GetStringUTFChars(jLanguage, nullptr);

  whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
  params.n_threads = (int)jThreads;
  params.language = (lang != nullptr && lang[0] != '\0') ? lang : "en";

  // whisper_full returns 0 on success, non-zero on failure.
  const int ret = whisper_full(ctx, params, samples.data(), (int)samples.size());

  std::string text;
  if (ret == 0) {
    const int nSegments = whisper_full_n_segments(ctx);
    for (int i = 0; i < nSegments; ++i) {
      const char* seg = whisper_full_get_segment_text(ctx, i);
      if (seg != nullptr && seg[0] != '\0') {
        if (!text.empty()) text += " ";
        text += seg;
      }
    }
  }
  env->ReleaseStringUTFChars(jLanguage, lang);

  return env->NewStringUTF(text.c_str());
}

// Free the whisper_context* produced by whisperInit.
JNIEXPORT void JNICALL
Java_com_sttapp_data_recognition_WhisperNative_whisperRelease(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong jCtx) {
  struct whisper_context* ctx = reinterpret_cast<struct whisper_context*>(jCtx);
  if (ctx != nullptr) whisper_free(ctx);
}

}   // extern "C"
