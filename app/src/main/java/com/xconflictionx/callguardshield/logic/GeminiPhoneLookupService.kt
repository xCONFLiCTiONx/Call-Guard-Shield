package com.xconflictionx.callguardshield.logic

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
import kotlin.time.Duration.Companion.milliseconds

class GeminiPhoneLookupService(
    private val apiKey: String,
    private val modelName: String,
    private val dao: com.xconflictionx.callguardshield.data.dao.CallGuardShieldDao,
    private val isDebugEnabled: Boolean = false
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
                
                models.asSequence()
                    .filter { it.contains("flash") || it.contains("pro") }
                    .filter { !it.contains("vision") && !it.contains("experimental") }
                    .sortedByDescending { it.contains("flash") }
                    .toList()
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
        
        CRITICAL RULES:
        - Do NOT guess or hallucinate names.
        - If you are not 100% certain of the owner/company name based on your data, return null.
        - NEVER default to common names like 'Bank of America', 'Telemarketer', or 'Spam' unless you have specific data for THIS exact number.
        - Accuracy scores must be honest. If uncertain, accuracy must be < 10.
        
        STRICT OUTPUT FORMAT:
        - Return ONLY a single JSON object.
        - 'ownerName' and 'companyName' MUST contain ONLY verified names or null.
        - 'accuracy' MUST be a whole number between 0 and 100.
        - 'evidence' MUST be a list of simple text strings.
        - 'sources' MUST be a list of simple text strings.
    """.trimIndent()

    suspend fun lookup(phoneNumberVariations: Set<String>, forceRefresh: Boolean = false): PhoneLookupResult? = withContext(Dispatchers.IO) {
        val number = phoneNumberVariations.first()
        
        if (!forceRefresh) {
            val cached = dao.getLookupResult(number)
            if (cached != null) {
                val age = System.currentTimeMillis() - cached.lookupDate
                if (age < (30L * 24 * 60 * 60 * 1000)) return@withContext cached.copy(isCached = true)
            }
        }

        // Fast Scan (No search) -> Deep Scan (Search) fallback for default lookup
        StatusManager.setGeminiStage("Fast Scan (High Speed)")
        val prompt = "Identify identity/reputation for: $number. Output JSON: ownerName, companyName, category, accuracy (0-100), summary, spam (bool), scam (bool), evidence (list), sources (list)."
        
        var result = executeSingleModelRequest(prompt, number, searchMode = false)
        
        if (result == null || result.accuracy < 85) {
            StatusManager.setGeminiStage("Deep Scan (Web Research)")
            result = executeSingleModelRequest(prompt, number, searchMode = true)
        }
        
        if (result != null && !forceRefresh) dao.insertLookupResult(result)
        return@withContext result
    }

    suspend fun lookupFast(number: String): PhoneLookupResult? = withContext(Dispatchers.IO) {
        StatusManager.setGeminiStage("Fast Scan (High Speed)")
        val prompt = "Identify identity/reputation for: $number. Output JSON: ownerName, companyName, category, accuracy (0-100), summary, spam (bool), scam (bool), evidence (list), sources (list)."
        val result = executeSingleModelRequest(prompt, number, searchMode = false)
        if (result != null) dao.insertLookupResult(result)
        result
    }

    suspend fun lookupDeep(number: String): PhoneLookupResult? = withContext(Dispatchers.IO) {
        StatusManager.setGeminiStage("Deep Scan (Web Research)")
        val prompt = "CRITICAL DEEP SEARCH: $number. Output JSON: ownerName, companyName, category, accuracy (0-100), summary, spam, scam, evidence, sources."
        executeSingleModelRequest(prompt, number, searchMode = true)
    }

    suspend fun lookupThorough(number: String): PhoneLookupResult? = withContext(Dispatchers.IO) {
        val results = mutableListOf<PhoneLookupResult>()
        val prompt = "THOROUGH INVESTIGATION: $number. Output JSON: ownerName, companyName, category, accuracy (0-100), summary, spam, scam, evidence, sources."
        
        repeat(3) { i ->
            StatusManager.setGeminiStage("Thorough Scan (Stage ${i + 1}/3)")
            executeSingleModelRequest(prompt, number, searchMode = true)?.let { results.add(it) }
            if (i < 2) delay(1000.milliseconds) // Small breather between heavy deep scans
        }
        
        if (results.isEmpty()) return@withContext null
        
        // Pick the one with highest accuracy
        val best = results.maxByOrNull { it.accuracy }
        if (best != null) {
            dao.insertLookupResult(best)
        }
        best
    }

    suspend fun lookupRealTime(number: String): PhoneLookupResult? = withContext(Dispatchers.IO) {
        val cached = dao.getLookupResult(number)
        if (cached != null) return@withContext cached.copy(isCached = true)
        
        val prompt = "Real-time Identify: $number. Output JSON (ownerName, companyName, accuracy (0-100), summary, spam, scam, debtCollector, telemarketer)."
        val result = executeSingleModelRequest(prompt, number, searchMode = false)
        if (result != null) {
            dao.insertLookupResult(result)
        }
        result
    }

    @Suppress("UNCHECKED_CAST")
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
                
                if (isDebugEnabled) {
                    ConsoleLogger.log("GEMINI_RAW", json, LogLevel.INFO)
                }

                return parseManualJson(json, number)

            } catch (e: Exception) {
                val code = (e as? HttpException)?.code()
                if (code == 429 && searchMode) {
                    delay(2500.milliseconds)
                    continue 
                }
                if (code == 503) {
                    delay(2000.milliseconds)
                    try {
                        val retryResponse = api.generateContent(url, request)
                        val retryJson = retryResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: continue
                        
                        if (isDebugEnabled) {
                            ConsoleLogger.log("GEMINI_RAW", retryJson, LogLevel.INFO)
                        }

                        return parseManualJson(retryJson, number)
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

    @Suppress("UNCHECKED_CAST")
    private fun parseManualJson(rawJson: String, number: String): PhoneLookupResult? {
        val cleanJson = rawJson.trim().removePrefix("```json").removeSuffix("```").trim()
        val gson = Gson()
        
        return try {
            val map = gson.fromJson(cleanJson, Map::class.java) as Map<String, Any?>
            
            // Deep Extraction logic for accuracy/confidence with support for multiple formats
            val possibleKeys = listOf("accuracy", "confidence", "confidence_score", "score", "fraud_score", "identity_match", "rating")
            var rawValue: Any? = null
            for (key in possibleKeys) {
                if (map.containsKey(key)) {
                    rawValue = map[key]
                    break
                }
            }
            
            val parsedAcc = when (rawValue) {
                is Number -> {
                    val v = rawValue.toDouble()
                    // If it's a decimal like 0.95, scale it to 95. If it's a whole number like 95, keep it.
                    if (v > 0.0 && v <= 1.0) (v * 100).toInt() else v.toInt()
                }
                is String -> {
                    // Handle formats like "90/100", "95%", "0.85", or "85"
                    val cleanStr = rawValue.trim().replace("%", "")
                    if (cleanStr.contains("/")) {
                        val parts = cleanStr.split("/")
                        val num = parts[0].toDoubleOrNull() ?: 0.0
                        val den = parts[1].toDoubleOrNull() ?: 100.0
                        ((num / den) * 100).toInt()
                    } else {
                        val v = cleanStr.toDoubleOrNull() ?: 0.0
                        if (v > 0.0 && v <= 1.0) (v * 100).toInt() else v.toInt()
                    }
                }
                else -> 0
            }.coerceIn(0, 100)

            PhoneLookupResult(
                phoneNumber = number,
                ownerName = map["ownerName"] as? String,
                companyName = map["companyName"] as? String,
                category = (map["category"] as? String) ?: "Unknown",
                accuracy = parsedAcc,
                spam = (map["spam"] as? Boolean) ?: (map["spam"]?.toString()?.toBoolean()) ?: false,
                scam = (map["scam"] as? Boolean) ?: (map["scam"]?.toString()?.toBoolean()) ?: false,
                debtCollector = (map["debtCollector"] as? Boolean) ?: (map["debtCollector"]?.toString()?.toBoolean()) ?: false,
                telemarketer = (map["telemarketer"] as? Boolean) ?: (map["telemarketer"]?.toString()?.toBoolean()) ?: false,
                summary = map["summary"] as? String,
                evidence = (map["evidence"] as? List<*>)?.filterIsInstance<String>(),
                sources = (map["sources"] as? List<*>)?.filterIsInstance<String>(),
                lookupDate = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            ConsoleLogger.log("PARSER", "Failed to parse manual JSON: ${e.message}", LogLevel.ERROR)
            null
        }
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
