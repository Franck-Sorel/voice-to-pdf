package com.sttapp.data.recognition

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PCM decoder built on [MediaExtractor] + [MediaCodec]. No external
 * dependencies; works for any container/format the platform can decode
 * (MP3, AAC, OGG/Vorbis, WAV, M4A).
 *
 * Gotcha: [MediaCodec] always outputs at the source sample rate. Downsampling
 * to 16 kHz from e.g. 44.1 kHz is NOT implemented yet — see
 * docs/ARCHITECTURE.md#audio-decoding for the plan. For now sources already
 * at 16 kHz (our recorder output) pass through.
 */
@Singleton
class MediaCodecPcmDecoder @Inject constructor() : PcmDecoder {

    override fun decodeToPcm16Mono(audioFile: File, targetSampleRateHz: Int): ShortArray {
        val extractor = MediaExtractor()
        extractor.setDataSource(audioFile.absolutePath)
        try {
            val trackIndex = findAudioTrack(extractor)
            val sourceFormat = extractor.getTrackFormat(trackIndex)

            val sourceRate = sourceFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            check(sourceRate == targetSampleRateHz) {
                "Sample-rate conversion $sourceRate Hz -> $targetSampleRateHz Hz not implemented"
            }

            val channelCount = sourceFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            extractor.selectTrack(trackIndex)

            // Ask the decoder for 16-bit PCM output.
            sourceFormat.setInteger(
                MediaFormat.KEY_PCM_ENCODING,
                AudioFormat.ENCODING_PCM_16BIT,
            )

            val decoder = MediaCodec.createDecoderByType(
                sourceFormat.getString(MediaFormat.KEY_MIME)
                    ?: error("No MIME type on audio track"),
            )
            decoder.configure(sourceFormat, null, null, 0)
            decoder.start()

            val pcmBytes = drainDecoder(decoder, extractor)
            decoder.stop()
            decoder.release()

            return toMonoPcm16(pcmBytes, channelCount)
        } finally {
            extractor.release()
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int {
        for (index in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
            if (mime != null && mime.startsWith("audio/")) return index
        }
        error("No audio track found in ${extractor}")
    }

    private fun drainDecoder(decoder: MediaCodec, extractor: MediaExtractor): ByteArray {
        val out = ByteArrayOutputStream()
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val inputIndex = decoder.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputIndex)
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(
                            inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(
                            inputIndex, 0, sampleSize, extractor.sampleTime, 0,
                        )
                        extractor.advance()
                    }
                }
            }

            when (val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 10_000)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                else -> {
                    if (bufferInfo.size > 0) {
                        val outputBuffer = decoder.getOutputBuffer(outputIndex)
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        val chunk = ByteArray(bufferInfo.size)
                        outputBuffer.get(chunk)
                        out.write(chunk)
                    }
                    decoder.releaseOutputBuffer(outputIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                }
            }
        }

        return out.toByteArray()
    }

    /** Interleaved 16-bit PCM -> mono by averaging channels. */
    private fun toMonoPcm16(bytes: ByteArray, channelCount: Int): ShortArray {
        val frameCount = bytes.size / (2 * channelCount)
        val mono = ShortArray(frameCount)
        var byteOffset = 0
        for (frame in 0 until frameCount) {
            var sum = 0L
            for (channel in 0 until channelCount) {
                val low = bytes[byteOffset++].toInt() and 0xFF
                val high = bytes[byteOffset++].toInt()
                sum += ((low or (high shl 8)).toShort()).toLong()
            }
            mono[frame] = (sum / channelCount).toShort()
        }
        return mono
    }
}
