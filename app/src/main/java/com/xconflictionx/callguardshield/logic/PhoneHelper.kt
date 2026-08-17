package com.xconflictionx.callguardshield.logic

object PhoneHelper {
    /**
     * Normalizes a phone number to strict E.164 format (+CountryCodeDigits).
     * Primarily focuses on US (+1) but handles other countries if provided with +.
     */
    fun normalizeToE164(number: String?): String {
        if (number.isNullOrEmpty()) return ""
        
        // Check if this is an alphanumeric Sender ID (e.g., "GOOGLE", "BANK")
        if (number.any { it.isLetter() }) {
            return number.trim()
        }
        
        // Remove all non-numeric characters (except +)
        val clean = number.filter { it.isDigit() || it == '+' }
        
        return if (clean.startsWith("+")) {
            clean
        } else if (clean.length == 10) {
            // Assume US if 10 digits
            "+1$clean"
        } else if (clean.length == 11 && clean.startsWith("1")) {
            // Assume US if 11 digits starting with 1
            "+$clean"
        } else {
            // Fallback: just prepend + if missing, or return as is if too short/long
            if (clean.isEmpty()) "" else if (clean.startsWith("+")) clean else "+$clean"
        }
    }

    /**
     * Extracts digits and + for database storage and matching.
     */
    fun cleanForStorage(pattern: String?): String {
        if (pattern.isNullOrEmpty()) return ""
        return pattern.filter { it.isDigit() || it == '+' || it == '*' }
    }

    /**
     * Returns the US State for a given number based on area code.
     */
    fun getRegionForNumber(number: String): String? {
        val digits = number.filter { it.isDigit() }
        val areaCode = when {
            digits.length == 10 -> digits.substring(0, 3)
            digits.length == 11 && digits.startsWith("1") -> digits.substring(1, 4)
            else -> return null
        }

        return when (areaCode) {
            "870", "501", "479" -> "Arkansas"
            "417" -> "Missouri"
            "901" -> "Tennessee"
            "318" -> "Louisiana"
            "918", "405", "580" -> "Oklahoma"
            "214", "972", "469", "817", "903" -> "Texas"
            else -> null
        }
    }

    /**
     * Checks if a number matches a pattern (prefix or full match).
     * Strips all formatting and +1 prefix before comparing.
     */
    fun isMatch(number: String, pattern: String): Boolean {
        val n = cleanForComparison(number)
        val p = cleanForComparison(pattern).removeSuffix("*").removeSuffix("%")
        if (n.isEmpty() || p.isEmpty()) return false
        return n.startsWith(p)
    }

    /**
     * Checks if a number is an exact match for a pattern.
     */
    fun isExactMatch(number: String, pattern: String): Boolean {
        val n = cleanForComparison(number)
        val p = cleanForComparison(pattern).removeSuffix("*").removeSuffix("%")
        return n == p
    }

    private fun cleanForComparison(input: String): String {
        // Remove all non-digits
        val digits = input.filter { it.isDigit() }
        
        // Robust US prefix handling: if it's 11 digits starting with 1, it's definitely +1 prefix.
        // If it's shorter, but starts with 1, we still strip it for comparison if it's likely a prefix.
        // Area codes in the US (NANP) never start with 1.
        return if (digits.startsWith("1") && digits.length >= 4) {
            digits.substring(1)
        } else {
            digits
        }
    }
}
