package com.sttapp.data.recognition

import java.io.File

/**
 * Decodes a compressed audio file (MP3/WAV/OGG/AAC/M4A) into raw 16-bit
 * little-endian signed PCM at [targetSampleRateHz] (Whisper expects 16 kHz
 * mono). Runs on the calling thread; callers hop to a background dispatcher.
 */
interface PcmDecoder {

    fun decodeToPcm16Mono(audioFile: File, targetSampleRateHz: Int = 16_000): ShortArray
}
