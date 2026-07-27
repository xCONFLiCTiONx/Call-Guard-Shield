package com.xconflictionx.callguardshield.logic

object AreaCodeManager {
    private val areaCodeToState = mapOf(
        "479" to "Arkansas",
        "501" to "Arkansas",
        "870" to "Arkansas",
        "205" to "Alabama",
        "251" to "Alabama",
        "256" to "Alabama",
        "334" to "Alabama",
        "938" to "Alabama",
        "907" to "Alaska",
        "480" to "Arizona",
        "520" to "Arizona",
        "602" to "Arizona",
        "623" to "Arizona",
        "928" to "Arizona",
        "209" to "California",
        "213" to "California",
        "310" to "California",
        "323" to "California",
        "408" to "California",
        "415" to "California",
        "510" to "California",
        "530" to "California",
        "559" to "California",
        "562" to "California",
        "619" to "California",
        "626" to "California",
        "650" to "California",
        "661" to "California",
        "707" to "California",
        "714" to "California",
        "760" to "California",
        "805" to "California",
        "818" to "California",
        "831" to "California",
        "858" to "California",
        "909" to "California",
        "916" to "California",
        "925" to "California",
        "949" to "California",
        "951" to "California",
        // ... (This would be much larger in a real app, I'll focus on the logic)
        "614" to "Ohio"
    )

    fun getStateForAreaCode(areaCode: String): String? {
        return areaCodeToState[areaCode]
    }
}
