package com.screening.dashboard.data

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

class VoiceSession {

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private var audioRecord: AudioRecord? = null
    private val pcmBuffer = ByteArrayOutputStream()

    @Volatile var isRecording = false
        private set

    fun startRecording() {
        if (isRecording) return
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufSize = maxOf(minBuf, 8192)
        audioRecord = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufSize)
        } catch (_: Exception) { return }
        pcmBuffer.reset()
        audioRecord?.startRecording()
        isRecording = true
    }

    /** Reads audio in a loop until stopRecording() is called. Call on IO dispatcher. */
    suspend fun collectAudio() = withContext(Dispatchers.IO) {
        val chunk = ByteArray(4096)
        while (isActive && isRecording) {
            val read = audioRecord?.read(chunk, 0, chunk.size) ?: break
            if (read > 0) pcmBuffer.write(chunk, 0, read)
        }
    }

    fun stopRecording() {
        isRecording = false
        try { audioRecord?.stop(); audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
    }

    /** Convert buffered PCM to WAV and send to Groq Whisper. Returns transcript or "". */
    suspend fun transcribe(): String = withContext(Dispatchers.IO) {
        val pcm = pcmBuffer.toByteArray()
        if (pcm.isEmpty()) return@withContext ""
        val wav = pcmToWav(pcm)
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", AiConfig.GROQ_MODEL)
            .addFormDataPart("response_format", "json")
            .addFormDataPart("file", "audio.wav", wav.toRequestBody("audio/wav".toMediaType()))
            .build()
        val req = Request.Builder()
            .url("https://api.groq.com/openai/v1/audio/transcriptions")
            .header("Authorization", "Bearer ${AiConfig.GROQ_API_KEY}")
            .post(requestBody)
            .build()
        try {
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: return@withContext ""
            JSONObject(body).optString("text", "")
        } catch (_: Exception) { "" }
    }

    private fun pcmToWav(pcm: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(44 + pcm.size)
        fun int32(v: Int) = out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array())
        fun int16(v: Int) = out.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v.toShort()).array())
        out.write("RIFF".toByteArray())
        int32(pcm.size + 36)   // file size - 8
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        int32(16)              // fmt chunk size
        int16(1)               // PCM
        int16(1)               // mono
        int32(SAMPLE_RATE)
        int32(SAMPLE_RATE * 2) // byte rate (16-bit mono)
        int16(2)               // block align
        int16(16)              // bits per sample
        out.write("data".toByteArray())
        int32(pcm.size)
        out.write(pcm)
        return out.toByteArray()
    }
}

