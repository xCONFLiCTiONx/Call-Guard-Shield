package com.xconflictionx.callguardshield.logic

object NumberNormalizer {
    /**
     * Converts a phone number into all common formats for search.
     * Example: +18005551234, 18005551234, 8005551234, 800-555-1234, (800) 555-1234
     */
    fun getVariations(number: String): Set<String> {
        val digits = number.filter { it.isDigit() }
        val variations = mutableSetOf<String>()
        
        variations.add("+$digits")
        variations.add(digits)
        
        val tenDigits = if (digits.length == 11 && digits.startsWith("1")) {
            digits.substring(1)
        } else if (digits.length == 10) {
            digits
        } else {
            null
        }

        tenDigits?.let {
            variations.add(it)
            val area = it.substring(0, 3)
            val mid = it.substring(3, 6)
            val last = it.substring(6)
            
            variations.add("$area-$mid-$last")
            variations.add("($area) $mid-$last")
        }
        
        return variations
    }
}
