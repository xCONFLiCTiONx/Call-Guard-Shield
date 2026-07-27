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
            
            Output: Return a JSON object with these EXACT fields:
            {
              "ownerName": "string",
              "companyName": "string",
              "category": "string (Personal, Business, Scam, Telemarketer)",
              "confidence": decimal (0.0 to 1.0),
              "spam": boolean,
              "scam": boolean,
              "debtCollector": boolean,
              "telemarketer": boolean,
              "summary": "Detailed verification logic and findings",
              "evidence": ["Point 1", "Point 2", "Point 3"],
              "sources": ["source1.com", "source2.com"],
              "lastVerified": "Date string"
            }
            Use "Unknown" for missing strings. Ensure 'evidence' has at least 2 technical data points.
        """.trimIndent()

        rotateModelsAndExecute(prompt, number)
    }

    suspend fun lookupDeep(number: String): PhoneLookupResult? = withContext(Dispatchers.IO) {
        val region = PhoneHelper.getRegionForNumber(number) ?: "USA"
        
        val prompt = """
            CRITICAL DEEP VERIFICATION: The number $number ($region) may be spoofed or misidentified.
            Cross-reference WhitePages, 800-notes, and the FCC database specifically.
            Identify if this is a "Hot Range" number often used for robocalls.
            
            Output: Return JSON with the full schema including detailed 'evidence' and 'summary' explaining why this deep scan is more reliable.
        """.trimIndent()

        rotateModelsAndExecute(prompt, number)
    }

    private suspend fun rotateModelsAndExecute(prompt: String, number: String): PhoneLookupResult? {
        val modelsToTry = listOf(modelName, "gemini-flash-latest", "gemini-2.0-flash", "gemini-pro-latest")
            .filter { it.isNotBlank() }.distinct()

        var lastError: Exception? = null

        for (target in modelsToTry) {
            val searchModes = listOf(true, false)
            for (useSearch in searchModes) {
                try {
                    val request = GeminiRequest(
                        contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
                        systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemInstructionText))),
                        tools = if (useSearch) listOf(GeminiTool(googleSearchRetrieval = emptyMap())) else null,
                        generationConfig = GeminiGenerationConfig(responseMimeType = "application/json")
                    )

                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$target:generateContent?key=$apiKey"
                    val response = api.generateContent(url, request)
                    val json = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: continue
                    
                    val cleanJson = json.trim().removePrefix("```json").removeSuffix("```").trim()
                    val result = Gson().fromJson(cleanJson, PhoneLookupResult::class.java)

                    return result.copy(phoneNumber = number, lookupDate = System.currentTimeMillis())
                } catch (e: Exception) {
                    lastError = e
                    if (e is HttpException && e.code() == 429) {
                        Log.w(TAG, "Quota hit on $target. Waiting 2.5s...")
                        delay(2500)
                        if (useSearch) continue 
                    }
                    if (e is HttpException && e.code() == 404) break 
                    break 
                }
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
}
