package com.worldmates.messenger.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * Node.js Voice Transcription API
 *
 * Endpoint (port 449):
 *   POST /api/node/voice/transcribe  — AI voice-to-text (PRO only, OpenAI Whisper)
 */
interface NodeVoiceApi {

    /**
     * Transcribe a voice message to text (PRO users only).
     * @param url absolute URL of the voice file (uploaded to the server)
     */
    @FormUrlEncoded
    @POST("api/node/voice/transcribe")
    suspend fun transcribe(
        @Field("url") url: String,
    ): VoiceTranscriptResponse
}

// ─── DTOs ─────────────────────────────────────────────────────────────────────

data class VoiceTranscriptResponse(
    @SerializedName("api_status")    val apiStatus:    Int = 0,
    @SerializedName("transcript")    val transcript:   String = "",
    @SerializedName("language")      val language:     String = "uk",
    @SerializedName("error_message") val errorMessage: String? = null,
)
