cmake_minimum_required(VERSION 3.22.1)
project(whisper_stt C CXX)

set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# --- whisper.cpp (submodule, pinned v1.9.3): only the static library, no examples ---
set(WHISPER_BUILD_IS_DEV  OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF  CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS   OFF  CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER  OFF  CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SDL2    OFF  CACHE BOOL "" FORCE)
set(WHISPER_COREML        OFF  CACHE BOOL "" FORCE)   # no Apple
set(WHISPER_METAL         OFF  CACHE BOOL "" FORCE)   # no GPU
set(WHISPER_BUILD_NUMBER  0    CACHE STRING "" FORCE)
set(WHISPER_BUILD_COMMIT  "v1.9.3" CACHE STRING "" FORCE)

# Force whisper.cpp to build `whisper`/`ggml` as STATIC so the only shared
# library produced here is our JNI bridge (libwhisper.so). Otherwise both
# would export libwhisper.so and collide.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

add_subdirectory(
    ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp
    ${CMAKE_CURRENT_BINARY_DIR}/whisper.cpp)

# --- App JNI bridge -----------------------------------------------------
# OUTPUT_NAME "whisper" -> libwhisper.so, matching System.loadLibrary("whisper")
# in app/src/main/java/com/sttapp/data/recognition/WhisperNative.kt.
add_library(whisper_jni SHARED jni.cpp)
set_target_properties(whisper_jni PROPERTIES OUTPUT_NAME whisper)

target_include_directories(whisper_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp/include)

target_link_libraries(whisper_jni PRIVATE whisper log)
