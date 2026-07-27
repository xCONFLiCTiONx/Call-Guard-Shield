package com.xconflictionx.callguardshield.logic

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface GeminiModelService {

    @GET("v1beta/models")
    suspend fun listModels(
        @Query("key") apiKey: String
    ): GeminiModelResponse

    companion object {
        fun create(): GeminiModelService {
            return Retrofit.Builder()
                .baseUrl("https://generativelanguage.googleapis.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(GeminiModelService::class.java)
        }
    }
}

data class GeminiModelResponse(
    @SerializedName("models") val models: List<GeminiModel>
)

data class GeminiModel(
    val name: String,
    val version: String,
    val displayName: String,
    val description: String,
    val supportedGenerationMethods: List<String>
)
