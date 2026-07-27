package com.xconflictionx.callguardshield.logic

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * AUTHORITATIVE TELECOM & SECURITY API SUITE
 */

interface TwilioLookupApi {
    @GET("v2/PhoneNumbers/{phoneNumber}")
    suspend fun lookup(
        @Header("Authorization") authHeader: String,
        @Path("phoneNumber") phoneNumber: String,
        @Query("Fields") fields: String = "identity_match,line_type_intelligence"
    ): TwilioResponse

    data class TwilioResponse(
        val valid: Boolean,
        @SerializedName("caller_name") val callerName: String?,
        @SerializedName("line_type_intelligence") val lineType: LineType?,
        @SerializedName("identity_match") val identityMatch: IdentityMatch?
    )

    data class LineType(val type: String?)
    data class IdentityMatch(@SerializedName("summary_score") val summaryScore: Int?)
}

interface AbstractPhoneApi {
    @GET("v1/")
    suspend fun validate(
        @Query("api_key") apiKey: String,
        @Query("phone") phoneNumber: String
    ): AbstractResponse

    data class AbstractResponse(
        val valid: Boolean,
        @SerializedName("phone_carrier") val carrier: Carrier?,
        @SerializedName("phone_location") val location: Location?
    )

    data class Carrier(val name: String?, @SerializedName("line_type") val type: String?)
    data class Location(@SerializedName("country_name") val country: String?)
}

interface IPQualityScoreApi {
    @GET("api/json/phone/{apiKey}/{phoneNumber}")
    suspend fun audit(
        @Path("apiKey") apiKey: String,
        @Path("phoneNumber") phoneNumber: String
    ): IPQSResponse

    data class IPQSResponse(
        val success: Boolean,
        @SerializedName("fraud_score") val fraudScore: Int?,
        @SerializedName("risky") val risky: Boolean?,
        @SerializedName("spammer") val spammer: Boolean?,
        @SerializedName("active") val active: Boolean?,
        val carrier: String?,
        @SerializedName("line_type") val lineType: String?
    )
}

object ApiClient {
    private val logger = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
    private val okHttpClient = OkHttpClient.Builder().addInterceptor(logger).build()

    val twilio: TwilioLookupApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://lookups.twilio.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TwilioLookupApi::class.java)
    }

    val abstract: AbstractPhoneApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://phonevalidation.abstractapi.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AbstractPhoneApi::class.java)
    }

    val ipqs: IPQualityScoreApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://www.ipqualityscore.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(IPQualityScoreApi::class.java)
    }
}
