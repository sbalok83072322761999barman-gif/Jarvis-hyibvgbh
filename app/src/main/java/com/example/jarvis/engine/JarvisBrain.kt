package com.example.jarvis.engine

import com.example.BuildConfig
import com.example.jarvis.model.ActionType
import com.example.jarvis.model.ContactInfo
import com.example.jarvis.model.ExecutionPlan
import com.example.jarvis.model.PlannedAction
import com.example.jarvis.model.RiskLevel
import com.example.jarvis.model.SessionContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@Serializable
data class GeminiGenerateRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null
)

@Serializable
data class GeminiContent(
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiPart(
    val text: String
)

@Serializable
data class GeminiGenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Float? = 0.2f
)

@Serializable
data class GeminiGenerateResponse(
    val candidates: List<GeminiCandidate> = emptyList()
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null
)

interface GeminiRestService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generatePlan(
        @Query("key") apiKey: String,
        @Body request: GeminiGenerateRequest
    ): GeminiGenerateResponse
}

class JarvisBrain(
    private val localParser: NaturalLanguageParser = NaturalLanguageParser()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val geminiService: GeminiRestService by lazy {
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GeminiRestService::class.java)
    }

    fun isCloudApiKeyConfigured(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotBlank() && key != "MY_GEMINI_API_KEY" && !key.startsWith("YOUR_")
    }

    suspend fun planCommand(
        rawCommand: String,
        session: SessionContext,
        cloudAiEnabled: Boolean,
        confirmMediumRisk: Boolean,
        agentModeEnabled: Boolean,
        memorySummary: String,
        contactResolver: suspend (String) -> List<ContactInfo>
    ): ExecutionPlan = withContext(Dispatchers.IO) {
        // 1. Always run fast deterministic local NLU first so offline commands & disambiguation rules are 100% reliable
        val localPlan = localParser.parseCommandSuspend(
            rawInput = rawCommand,
            session = session,
            confirmMediumRisk = confirmMediumRisk,
            agentModeEnabled = agentModeEnabled,
            contactResolver = contactResolver
        )

        // If local parser matched a concrete device action or disambiguation, use it immediately for low latency,
        // unless it fell back to conversational AI or Agent Mode research and Cloud AI is available
        val isConversationalFallback = localPlan.steps.size == 1 &&
            localPlan.steps.first().actionType == ActionType.ANSWER_CONVERSATION &&
            localPlan.steps.first().value != "AWAITING_WHATSAPP_MESSAGE"

        if (!cloudAiEnabled || !isCloudApiKeyConfigured() || (!isConversationalFallback && !localPlan.isAgentModePlan)) {
            return@withContext localPlan
        }

        // 2. Call Gemini 3.5 Flash for advanced reasoning / conversational or open-ended agent planning
        return@withContext try {
            val systemPrompt = """
                You are JARVIS, an intelligent, fast, witty, and safety-first Android AI Agent.
                You understand English, Hindi, and Hinglish naturally.
                Session Context: lastContact=${session.lastContactName ?: "none"}, lastApp=${session.lastAppName ?: "none"}, lastMessage=${session.lastMessageText ?: "none"}.
                User Memory: $memorySummary
                Respond with a JSON object matching ExecutionPlan:
                {
                  "originalCommand": "...",
                  "detectedLanguage": "Hinglish",
                  "intentSummary": "...",
                  "spokenResponse": "Concise natural response in user's language",
                  "riskLevel": "LOW" | "MEDIUM" | "HIGH",
                  "requiresConfirmation": false,
                  "confirmationPrompt": "",
                  "steps": [
                    {
                      "stepNumber": 1,
                      "actionType": "LAUNCH_APP" | "SET_VOLUME" | "SET_BRIGHTNESS" | "TOGGLE_FLASHLIGHT" | "NAVIGATE_BACK" | "NAVIGATE_HOME" | "SCROLL_SCREEN" | "CLICK_ELEMENT" | "TYPE_TEXT" | "WHATSAPP_SEND_MESSAGE" | "BROWSER_SEARCH" | "READ_NOTIFICATIONS" | "READ_DEVICE_TELEMETRY" | "SET_TIMER" | "CREATE_REMINDER" | "FILE_SEARCH" | "AGENT_RESEARCH" | "ANSWER_CONVERSATION",
                      "title": "Human readable step title",
                      "target": "Target app/element/recipient",
                      "value": "Value or parameter",
                      "expectedVerification": "How to verify completion"
                    }
                  ]
                }
            """.trimIndent()

            val request = GeminiGenerateRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = rawCommand)))),
                systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt))),
                generationConfig = GeminiGenerationConfig(
                    responseMimeType = "application/json",
                    temperature = 0.2f
                )
            )

            val response = geminiService.generatePlan(BuildConfig.GEMINI_API_KEY, request)
            val rawJson = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!rawJson.isNullOrBlank()) {
                val parsed = json.decodeFromString<ExecutionPlan>(rawJson)
                parsed.copy(
                    originalCommand = rawCommand,
                    sourceEngine = "Gemini 3.5 Flash + Action Planner"
                )
            } else {
                localPlan
            }
        } catch (e: Exception) {
            localPlan
        }
    }
}
