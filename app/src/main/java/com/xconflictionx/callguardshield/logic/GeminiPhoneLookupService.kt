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
    private val modelName: String
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
        Identify the owner, company, and reputation of phone numbers with zero hallucinations.
        
        TRUST RANKING (Priority):
        1. Official Organization Websites (.gov, .org, verified business domains).
        2. Telecom Carrier/ANAC announcement data (identify if it's a test line).
        3. Established Business Directories.
        4. User reports and spam databases (800notes, TrueCaller).
        
        RULES:
        - If sources disagree, do NOT guess. Mark identity as "Unverified" and explain the conflict.
        - Watch for spoofing: Is a real business number being used fraudulently?
        - Map confidence (0.0 to 1.0) strictly:
            0.9-1.0: Verified on official website.
            0.6-0.8: Multiple independent reputable sources agree.
            0.1-0.5: Conflicting or low-trust sources.
        - 800-444-4444 is an ANAC test line, NOT Bank of America.
    """.trimIndent()

    suspend fun lookup(phoneNumberVariations: Set<String>): PhoneLookupResult? = withContext(Dispatchers.IO) {
        val number = phoneNumberVariations.first()
        val region = PhoneHelper.getRegionForNumber(number) ?: "USA"
        
        val prompt = """
            Task: Investigate reputation and identity for: $number ($region).
            Targets: Check public directories, spam databases (800notes, who-called), and official brand sites.
            
            Output: Return a JSON object with fields: ownerName, companyName, category, confidence (0.0-1.0), summary, evidence (list), sources (list).
        """.trimIndent()

        executeSingleModelRequest(prompt, number)
    }

    suspend fun lookupDeep(number: String): PhoneLookupResult? = withContext(Dispatchers.IO) {
        val region = PhoneHelper.getRegionForNumber(number) ?: "USA"
        
        val prompt = """
            CRITICAL DEEP VERIFICATION: The number $number ($region) may be spoofed or misidentified.
            Cross-reference WhitePages, 800-notes, and the FCC database specifically.
            Identify if this is a "Hot Range" number often used for robocalls.
            
            Output: Return JSON with full schema including 'evidence' and 'summary' explaining why this deep scan is more reliable.
        """.trimIndent()

        executeSingleModelRequest(prompt, number)
    }

    private suspend fun executeSingleModelRequest(prompt: String, number: String): PhoneLookupResult? {
        // LOCK to exactly what is in settings. No fallbacks to restricted models.
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
                val result = Gson().fromJson(cleanJson, PhoneLookupResult::class.java)

                return result.copy(phoneNumber = number, lookupDate = System.currentTimeMillis())
            } catch (e: Exception) {
                lastError = e
                val errorBody = (e as? HttpException)?.response()?.errorBody()?.string()
                
                if (e is HttpException && e.code() == 429) {
                    if (useSearch) {
                        Log.w(TAG, "Search tool busy. Waiting 2.5s and retrying without search.")
                        delay(2500)
                        continue 
                    }
                }

                // If it's a 404 or a 429 without search, parse the REAL error message and throw it.
                if (!errorBody.isNullOrBlank()) {
                    try {
                        val errorJson = Gson().fromJson(errorBody, GeminiErrorResponse::class.java)
                        val msg = errorJson.error?.message
                        if (!msg.isNullOrBlank()) throw Exception(msg)
                    } catch (parseEx: Exception) {
                        // ignore and use the original error
                    }
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
