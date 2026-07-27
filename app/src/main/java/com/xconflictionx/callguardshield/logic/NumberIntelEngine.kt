package com.xconflictionx.callguardshield.logic

object NumberIntelEngine {

    data class InvestigationData(
        val twilio: TwilioLookupApi.TwilioResponse?,
        val abstract: AbstractPhoneApi.AbstractResponse?,
        val ipqs: IPQualityScoreApi.IPQSResponse?
    )

    /**
     * Strictly raw data report as per specifications.
     */
    fun generateReport(
        number: String,
        data: InvestigationData
    ): String {
        val entity = data.twilio?.callerName ?: "Unknown Identity"
        val carrier = data.ipqs?.carrier ?: data.abstract?.carrier?.name ?: "Unknown"
        val lineType = data.ipqs?.lineType ?: data.twilio?.lineType?.type ?: data.abstract?.carrier?.type ?: "Unknown"
        val fraudScore = data.ipqs?.fraudScore ?: 0
        val identityMatch = data.twilio?.identityMatch?.summaryScore ?: 0
        
        val riskLevel = when {
            fraudScore >= 80 -> "HIGH RISK"
            fraudScore >= 50 -> "CAUTION"
            entity != "Unknown Identity" -> "VERIFIED"
            else -> "UNVERIFIED"
        }

        return """
            [TECHNICAL DATA REPORT: $number]
            ENTITY: $entity
            CARRIER: $carrier
            LINE_TYPE: $lineType
            FRAUD_SCORE: $fraudScore/100
            IDENTITY_MATCH: $identityMatch/100
            NETWORK_STATUS: ${if (data.ipqs?.active == true) "ACTIVE" else "INACTIVE"}
            SPAM_INDICATOR: ${if (data.ipqs?.spammer == true) "POSITIVE" else "NEGATIVE"}
            VERIFICATION_LEVEL: $riskLevel
        """.trimIndent()
    }
}
