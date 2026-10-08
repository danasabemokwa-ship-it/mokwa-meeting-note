package com.meetnotes.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.HttpException
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

// ------------------------------------------------------------------ OpenAI

interface OpenAiApi {
    @Multipart
    @POST("v1/audio/transcriptions")
    suspend fun transcribe(
        @Header("Authorization") bearer: String,
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody,
        @Part("language") language: RequestBody?,
        /** Optional hint text: names, spellings and context (e.g. Nigerian English). */
        @Part("prompt") prompt: RequestBody?,
        @Part("response_format") responseFormat: RequestBody,
    ): OpenAiTranscription

    @POST("v1/chat/completions")
    suspend fun chat(
        @Header("Authorization") bearer: String,
        @Body body: OpenAiChatRequest,
    ): OpenAiChatResponse
}

@Serializable data class OpenAiTranscription(val text: String = "")

@Serializable
data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    @SerialName("response_format") val responseFormat: OpenAiResponseFormat = OpenAiResponseFormat(),
    val temperature: Double = 0.2,
)

@Serializable data class OpenAiMessage(val role: String, val content: String? = null)
@Serializable data class OpenAiResponseFormat(val type: String = "json_object")
@Serializable data class OpenAiChatResponse(val choices: List<OpenAiChoice> = emptyList())
@Serializable data class OpenAiChoice(val message: OpenAiMessage? = null)

// ------------------------------------------------------------------ Google Gemini

interface GeminiApi {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generate(
        @Path("model") model: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body body: GeminiRequest,
    ): GeminiResponse
}

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    @SerialName("systemInstruction") val systemInstruction: GeminiContent? = null,
    @SerialName("generationConfig") val generationConfig: GeminiGenerationConfig? = null,
)

@Serializable data class GeminiContent(val role: String? = null, val parts: List<GeminiPart> = emptyList())

@Serializable
data class GeminiPart(
    val text: String? = null,
    @SerialName("inline_data") val inlineData: GeminiInlineData? = null,
)

@Serializable
data class GeminiInlineData(
    @SerialName("mime_type") val mimeType: String,
    val data: String,
)

@Serializable
data class GeminiGenerationConfig(
    val temperature: Double = 0.2,
    @SerialName("responseMimeType") val responseMimeType: String? = null,
)

@Serializable data class GeminiResponse(val candidates: List<GeminiCandidate> = emptyList())
@Serializable data class GeminiCandidate(val content: GeminiContent? = null)

fun GeminiResponse.text(): String =
    candidates.firstOrNull()?.content?.parts.orEmpty().mapNotNull { it.text }.joinToString("")

// ------------------------------------------------------------------ Anthropic

interface AnthropicApi {
    @POST("v1/messages")
    suspend fun messages(
        @Header("x-api-key") apiKey: String,
        @Header("anthropic-version") version: String,
        @Body body: AnthropicRequest,
    ): AnthropicResponse
}

@Serializable
data class AnthropicRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int = 4096,
    val system: String,
    val messages: List<AnthropicMessage>,
)

@Serializable data class AnthropicMessage(val role: String, val content: String)
@Serializable data class AnthropicResponse(val content: List<AnthropicBlock> = emptyList())
@Serializable data class AnthropicBlock(val type: String = "", val text: String? = null)

// ------------------------------------------------------------------ errors

class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Turns HTTP errors into readable messages (network IOExceptions pass through unchanged). */
suspend fun <T> apiCall(provider: String, block: suspend () -> T): T = try {
    block()
} catch (e: HttpException) {
    val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()?.take(300)
    val hint = when (e.code()) {
        401, 403 -> "check your $provider API key in Settings"
        404 -> "check the model name in Settings"
        413 -> "the audio file is too large for this service"
        429 -> "rate limit or quota reached — try again later"
        else -> null
    }
    throw ApiException(
        buildString {
            append("$provider error ${e.code()}")
            if (hint != null) append(" – ").append(hint)
            if (!body.isNullOrBlank()) append("\n").append(body)
        },
        e,
    )
}
