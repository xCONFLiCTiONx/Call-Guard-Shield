package com.xconflictionx.callguardshield.logic

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.xconflictionx.callguardshield.data.entity.PhoneLookupResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
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
    private val TAG = "GEMINI_LOG"

    private interface GeminiApiService {
        @POST
        suspend fun generateContent(
            @Url url: String,
            @Body request: GeminiRequest
        ): GeminiResponse
    }

    private val api: GeminiApiService by lazy {
        val logger = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        val client = OkHttpClient.Builder()
            .addInterceptor(logger)
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
        - DO NOT include generic info like "Spam Caller", "High Risk", or confidence scores in the name fields.
        - If the name is unknown, use null.
        - 'evidence' MUST be a list of simple text strings.
        - 'sources' MUST be a list of simple text strings.
        - DO NOT nest objects or complex structures inside lists.
        
        TRUST RANKING (Priority):
        1. Official Organization Websites (.gov, .org, verified business domains).
        2. Telecom Carrier/ANAC announcement data (identify if it's a test line).
        3. Established Business Directories.
        4. User reports and spam databases (800notes, TrueCaller).
        
        CRITICAL IDENTIFICATION RULES:
        - Prioritize identifying Public Entities: Government offices (City Hall, Police), Schools, and Hospitals.
        - If sources disagree, do NOT guess. Mark identity as "Unverified" and explain the conflict.
        - ANOMALY: 800-444-4444 is an ANAC test line. If you see it associated with a bank, report the conflict but identify it as a test line.
        
        CONFIDENCE MAPPING:
        0.9-1.0: Verified on official website.
        0.6-0.8: Multiple independent sources agree.
        0.1-0.5: Conflicting or low-trust sources.
    """.trimIndent()

    suspend fun lookup(phoneNumberVariations: Set<String>, forceRefresh: Boolean = false): PhoneLookupResult? = withContext(Dispatchers.IO) {
        val number = phoneNumberVariations.first()
        
        // 1. Check Cache First (Unless forced)
        if (!forceRefresh) {
            try {
                val cached = dao.getLookupResult(number)
                if (cached != null) {
                    val age = System.currentTimeMillis() - cached.lookupDate
                    val thirtyDays = 30L * 24 * 60 * 60 * 1000
                    if (age < thirtyDays) {
                        Log.d(TAG, "Using cached result for $number")
                        return@withContext cached.copy(isCached = true)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cache read failed: ${e.message}")
            }
        } else {
            Log.i(TAG, "Forced refresh requested for $number")
        }

        val region = PhoneHelper.getRegionForNumber(number) ?: "USA"
        
        val prompt = """
            Task: Investigate reputation and identity for: $number ($region).
            Targets: Check public directories, spam databases (800notes, who-called), and official brand sites.
            
            Output: Return a JSON object with these fields:
            ownerName (string), companyName (string), category (string), confidence (0.0 to 1.0), summary (string), spam (bool), scam (bool), evidence (list of strings), sources (list of strings).
            Note: Ensure 'summary' explains your verification logic.
        """.trimIndent()

        val result = executeSingleModelRequest(prompt, number)
        
        // 2. Save to Cache on Success (Only if not a manual forced refresh)
        // If it's a forced refresh, we let the caller (ViewModel) decide whether to update
        if (result != null && !forceRefresh) {
            try {
                dao.insertLookupResult(result)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save cache: ${e.message}")
            }
        }
        
        return@withContext result
    }

    suspend fun lookupDeep(number: String): PhoneLookupResult? = withContext(Dispatchers.IO) {
        val region = PhoneHelper.getRegionForNumber(number) ?: "USA"
        
        val prompt = """
            CRITICAL DEEP VERIFICATION: $number ($region).
            The previous scan might be wrong. 
            Perform an exhaustive search: Cross-reference WhitePages, 800-notes, and official state/local directories.
            Provide a definitive identification if possible, or a detailed conflict report.
            
            Output: Return JSON including 'evidence' (list of strings) and a 'summary' (string) of why this identification is reliable.
        """.trimIndent()

        executeSingleModelRequest(prompt, number)
    }

    private suspend fun executeSingleModelRequest(prompt: String, number: String): PhoneLookupResult? {
        val target = if (modelName.isBlank()) "gemini-flash-latest" else modelName
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$target:generateContent?key=$apiKey"
        
        val searchModes = listOf(true, false)
        var lastError: Exception? = null

        for (useSearch in searchModes) {
            try {
                Log.d(TAG, "Executing Request: model=$target, search=$useSearch")
                val request = GeminiRequest(
                    contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
                    systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemInstructionText))),
                    tools = if (useSearch) listOf(GeminiTool(googleSearchRetrieval = emptyMap())) else null,
                    generationConfig = GeminiGenerationConfig(responseMimeType = "application/json")
                )

                val response = api.generateContent(url, request)
                val json = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: continue
                
                val cleanJson = json.trim().removePrefix("```json").removeSuffix("```").trim()
                
                return try {
                    val result = Gson().fromJson(cleanJson, PhoneLookupResult::class.java)
                    result.copy(phoneNumber = number, lookupDate = System.currentTimeMillis())
                } catch (e: Exception) {
                    Log.e(TAG, "JSON Parsing failed for $number: ${e.message}")
                    // Fallback: If evidence/sources are the problem, try to extract them manually or return partial
                    throw Exception("Intelligence data format error. Gemini returned complex data types. Please try again.")
                }
            } catch (e: Exception) {
                lastError = e
                val errorBody = (e as? HttpException)?.response()?.errorBody()?.string()
                
                if (e is HttpException && e.code() == 429 && useSearch) {
                    Log.w(TAG, "Search tool busy. Waiting 2.5s...")
                    delay(2500)
                    continue 
                }

                if (!errorBody.isNullOrBlank()) {
                    try {
                        val errorJson = Gson().fromJson(errorBody, GeminiErrorResponse::class.java)
                        val msg = errorJson.error?.message
                        if (!msg.isNullOrBlank()) throw Exception(msg)
                    } catch (parseEx: Exception) { }
                }
                break 
            }
        }
        if (lastError != null) throw lastError
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

    data class GeminiErrorResponse(val error: GeminiErrorDetail?)
    data class GeminiErrorDetail(val code: Int, val message: String, val status: String)
}
