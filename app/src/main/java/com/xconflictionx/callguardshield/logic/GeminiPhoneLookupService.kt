package com.xconflictionx.callguardshield.logic

import android.content.Context
import com.google.gson.Gson
import com.xconflictionx.callguardshield.data.entity.PhoneLookupResult
import com.xconflictionx.callguardshield.ui.LogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

class GeminiPhoneLookupService(
    private val context: Context,
    private val apiKey: String,
    private val modelName: String,
    private val dao: com.xconflictionx.callguardshield.data.dao.CallGuardShieldDao
) {
    companion object {
        suspend fun testApiKey(apiKey: String): Boolean {
            val helloSuccess = try {
                val service = createApiService()
                val url = "https://generativelanguage.googleapis.com/v1/models/gemini-1.5-flash:generateContent?key=$apiKey"
                val request = GeminiRequest(
                    contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = "Hello"))))
                )
                service.generateContent(url, request)
                true
            } catch (e: Exception) {
                false
            }

            if (helloSuccess) {
                return true
            }

            return try {
                val service = createApiService()
                val modelResponse = service.listModels(apiKey)
                val availableModels = modelResponse.models?.map { it.name.removePrefix("models/") } ?: emptyList()
                availableModels.isNotEmpty()
            } catch (e: Exception) {
                false
            }
        }

        suspend fun fetchAvailableModels(apiKey: String): List<String> {
            return try {
                val service = createApiService()
                val response = service.listModels(apiKey)
                val models = response.models?.map { it.name.removePrefix("models/") } ?: emptyList()
                
                models.filter { it.contains("flash") || it.contains("pro") }
                    .filter { !it.contains("vision") && !it.contains("experimental") }
                    .sortedByDescending { it.contains("flash") }
            } catch (e: Exception) {
                emptyList()
            }
        }

        private fun createApiService(): GeminiApiService {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl("https://generativelanguage.googleapis.com/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(GeminiApiService::class.java)
        }
    }

    private interface GeminiApiService {
        @POST
        suspend fun generateContent(
            @Url url: String,
            @Body request: GeminiRequest
        ): GeminiResponse

        @GET("v1/models")
        suspend fun listModels(
            @Query("key") apiKey: String
        ): GeminiModelListResponse
    }

    private val api: GeminiApiService by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeminiApiService::class.java)
    }

    private val systemInstructionText = """
        You are a highly accurate phone number intelligence expert. 
        Your goal is to identify caller owners and reputations with zero hallucinations.
        
        STRICT OUTPUT FORMAT:
        - Return ONLY a single JSON object.
        - 'ownerName' and 'companyName' MUST contain ONLY the verified names. 
        - If unknown, use null.
        - 'evidence' MUST be a list of simple text strings.
        - 'sources' MUST be a list of simple text strings.
    """.trimIndent()

    suspend fun lookup(phoneNumberVariations: Set<String>, forceRefresh: Boolean = false): PhoneLookupResult? = withContext(Dispatchers.IO) {
        val number = phoneNumberVariations.first()
        
        if (!forceRefresh) {
            val cached = dao.getLookupResult(number)
            if (cached != null) {
                val age = System.currentTimeMillis() - cached.lookupDate
                if (age < 30L * 24 * 60 * 60 * 1000) return@withContext cached.copy(isCached = true)
            }
        }

        // Fast Scan (No search) -> Deep Scan (Search) fallback for default lookup
        StatusManager.setGeminiStage("Fast Scan (High Speed)")
        val prompt = "Identify identity/reputation for: $number. Output JSON: ownerName, companyName, category, confidence (0-1), summary, spam (bool), scam (bool), evidence (list), sources (list)."
        
        var result = executeSingleModelRequest(prompt, number, searchMode = false)
        
        if (result == null || (result.confidence ?: 0.0) < 0.85) {
            StatusManager.setGeminiStage("Deep Scan (Web Research)")
            result = executeSingleModelRequest(prompt, number, searchMode = true)
        }
        
        if (result != null && !forceRefresh) dao.insertLookupResult(result)
        return@withContext result
    }

    suspend fun lookupFast(number: String): PhoneLookupResult? = withContext(Dispatchers.IO) {
        StatusManager.setGeminiStage("Fast Scan (High Speed)")
        val prompt = "Identify identity/reputation for: $number. Output JSON: ownerName, companyName, category, confidence (0-1), summary, spam (bool), scam (bool), evidence (list), sources (list)."
        val result = executeSingleModelRequest(prompt, number, searchMode = false)
        if (result != null) dao.insertLookupResult(result)
        result
    }

    suspend fun lookupDeep(number: String): PhoneLookupResult? = withContext(Dispatchers.IO) {
        StatusManager.setGeminiStage("Deep Scan (Web Research)")
        val prompt = "CRITICAL DEEP SEARCH: $number. Output JSON: ownerName, companyName, category, confidence, summary, spam, scam, evidence, sources."
        executeSingleModelRequest(prompt, number, searchMode = true)
    }

    suspend fun lookupThorough(number: String): PhoneLookupResult? = withContext(Dispatchers.IO) {
        val results = mutableListOf<PhoneLookupResult>()
        val prompt = "THOROUGH INVESTIGATION: $number. Output JSON: ownerName, companyName, category, confidence, summary, spam, scam, evidence, sources."
        
        repeat(3) { i ->
            StatusManager.setGeminiStage("Thorough Scan (Stage ${i + 1}/3)")
            executeSingleModelRequest(prompt, number, searchMode = true)?.let { results.add(it) }
            if (i < 2) delay(1000) // Small breather between heavy deep scans
        }
        
        if (results.isEmpty()) return@withContext null
        
        // Pick the one with highest confidence
        val best = results.maxByOrNull { it.confidence ?: 0.0 }
        if (best != null) {
            dao.insertLookupResult(best)
        }
        best
    }

    suspend fun lookupRealTime(number: String): PhoneLookupResult? = withContext(Dispatchers.IO) {
        val cached = dao.getLookupResult(number)
        if (cached != null) return@withContext cached.copy(isCached = true)
        
        val prompt = "Real-time Identify: $number. Output JSON (ownerName, companyName, confidence, summary, spam, scam, debtCollector, telemarketer)."
        executeSingleModelRequest(prompt, number, searchMode = false)
    }

    private suspend fun executeSingleModelRequest(prompt: String, number: String, searchMode: Boolean): PhoneLookupResult? {
        val cleanModelName = modelName.removePrefix("models/").ifBlank { "gemini-3.1-flash-lite" }
        val versions = listOf("v1", "v1beta")

        for (version in versions) {
            val url = "https://generativelanguage.googleapis.com/$version/models/$cleanModelName:generateContent?key=$apiKey"
            val request = GeminiRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
                systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemInstructionText))),
                tools = if (searchMode) listOf(GeminiTool(googleSearchRetrieval = emptyMap())) else null,
                generationConfig = GeminiGenerationConfig(responseMimeType = "application/json")
            )

            try {
                val response = api.generateContent(url, request)
                val json = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: continue
                val cleanJson = json.trim().removePrefix("```json").removeSuffix("```").trim()
                
                val result = try {
                    Gson().fromJson(cleanJson, PhoneLookupResult::class.java)
                } catch (e: Exception) {
                    null
                } ?: continue

                return result.copy(phoneNumber = number, lookupDate = System.currentTimeMillis())

            } catch (e: Exception) {
                val code = (e as? HttpException)?.code()
                if (code == 429 && searchMode) {
                    delay(2500)
                    continue 
                }
                if (code == 503) {
                    delay(2000)
                    try {
                        val retryResponse = api.generateContent(url, request)
                        val retryJson = retryResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: continue
                        val retryCleanJson = retryJson.trim().removePrefix("```json").removeSuffix("```").trim()
                        val retryResult = Gson().fromJson(retryCleanJson, PhoneLookupResult::class.java)
                        return retryResult.copy(phoneNumber = number, lookupDate = System.currentTimeMillis())
                    } catch (re: Exception) {
                        break 
                    }
                }
                if (code == 404) continue 
                
                val body = (e as? HttpException)?.response()?.errorBody()?.string()
                val errorMsg = if (code != null) "API Error $code: $body" else "Network error: ${e.message}"
                ConsoleLogger.log("GEMINI", "Request failed: $errorMsg", LogLevel.ERROR)
                break 
            }
        }
        return null
    }

    data class GeminiRequest(
        val contents: List<GeminiContent>,
        val systemInstruction: GeminiContent? = null,
        val tools: List<GeminiTool>? = null,
        val generationConfig: GeminiGenerationConfig? = null
    )
    data class GeminiContent(val parts: List<GeminiPart>)
    data class GeminiPart(val text: String)
    data class GeminiTool(val googleSearchRetrieval: Map<String, Any>? = null)
    data class GeminiGenerationConfig(val responseMimeType: String)
    data class GeminiResponse(val candidates: List<GeminiCandidate>?)
    data class GeminiCandidate(val content: GeminiContent?)

    data class GeminiModelListResponse(val models: List<GeminiModelDetail>?)
    data class GeminiModelDetail(val name: String, val version: String, val displayName: String)
}
