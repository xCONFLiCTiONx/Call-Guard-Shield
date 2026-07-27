package com.xconflictionx.callguardshield.logic

import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.regex.Pattern

object WebSearchHelper {

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val STANDARD_HEADERS = mapOf(
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.5",
        "Referer" to "https://www.google.com/"
    )

    data class SearchResult(val title: String, val description: String, val url: String)

    /**
     * Performs a tiered multi-engine search to identify phone numbers with improved resilience.
     * Primary Engine: Brave Search
     * Fallback Engine: DuckDuckGo Lite
     */
    fun performInvestigationSearch(number: String): String {
        val state = PhoneHelper.getRegionForNumber(number)
        val tiers = mutableListOf<String>()
        
        // Tier 1: Quoted number for exact matches
        tiers.add("\"$number\"")
        
        if (state != null) {
            tiers.add("$number $state")
        }
        tiers.add("$number phone owner")

        val allResults = mutableListOf<SearchResult>()
        
        for (tierQuery in tiers) {
            val tierResults = mutableListOf<SearchResult>()
            
            // Tier 1 logic: Primary Brave
            tierResults.addAll(fetchFromBrave(tierQuery))
            
            // Tier 1 fallback: DDG Lite if Brave returns sparse data
            if (tierResults.size < 3) {
                tierResults.addAll(fetchFromDDGLite(tierQuery))
            }

            allResults.addAll(tierResults)
            
            // Stop if any tier returns more than 3 high-quality results
            if (tierResults.count { isHighQuality(it, number) } > 3) break
            
            // Implement a small delay between tiers to avoid bot detection
            try { Thread.sleep(100) } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }

        val filteredResults = allResults.distinctBy { it.url }
            .filterNot { isGenericBloat(it, number) }

        if (filteredResults.isEmpty()) {
            return "No significant digital footprint found. Number is likely private or newly assigned."
        }

        // Sort: HQ first, then Contains Number, then others
        val sortedResults = filteredResults.sortedWith(
            compareByDescending<SearchResult> { isHighQuality(it, number) }
                .thenByDescending { containsNumber(it, number) }
        )

        val sb = StringBuilder("Web Intelligence Report for $number:\n\n")
        
        // Return at least 10 results total across sources if possible
        sortedResults.take(10).forEach { result ->
            android.util.Log.d("WebSearchHelper", "Found: ${result.title} | ${result.description}")
            sb.append("Source: ${result.title}\n")
            sb.append("Info: ${result.description}\n")
            sb.append("Link: ${result.url}\n\n")
        }

        return sb.toString()
    }

    /**
     * High-quality results often contain official or regulatory keywords and MUST contain the target number.
     */
    private fun isHighQuality(result: SearchResult, number: String): Boolean {
        if (!containsNumber(result, number)) return false
        
        val text = (result.title + " " + result.description).lowercase()
        val keywords = listOf(".gov", "official", "department", "verified", "police", "sheriff", "utility", "bank", "hospital", "clinic", "health", "medical")
        return keywords.any { text.contains(it) }
    }

    private fun containsNumber(result: SearchResult, number: String): Boolean {
        val text = (result.title + " " + result.description).lowercase()
        val raw = number.filter { it.isDigit() }
        
        if (text.contains(raw)) return true
        
        if (raw.length == 10) {
            val formatted = "${raw.substring(0, 3)}-${raw.substring(3, 6)}-${raw.substring(6)}"
            val parenthesized = "(${raw.substring(0, 3)}) ${raw.substring(3, 6)}-${raw.substring(6)}"
            val dots = "${raw.substring(0, 3)}.${raw.substring(3, 6)}.${raw.substring(6)}"
            
            if (text.contains(formatted) || text.contains(parenthesized) || text.contains(dots)) return true
        }
        
        return text.contains(number.lowercase())
    }

    private fun isGenericBloat(result: SearchResult, number: String): Boolean {
        val text = (result.title + " " + result.description).lowercase()
        val genericTerms = listOf("bank customer care", "toll free number", "customer care number", "contact us", "all bank", "customer support numbers")
        return genericTerms.any { text.contains(it) } && !containsNumber(result, number)
    }

    private fun fetchFromBrave(query: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://search.brave.com/search?q=$encodedQuery&source=web"
            
            val connection = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(10000)
            
            STANDARD_HEADERS.forEach { (k, v) -> connection.header(k, v) }
            
            val doc = connection.get()

            // 1. Try standard HTML parsing
            val snippets = doc.select(".snippet")
            for (snippet in snippets) {
                val title = snippet.select(".snippet-title, .title").text()
                val description = snippet.select(".snippet-description, .snippet-content, .snippet-text, .description, [data-testid=\"snippet-description\"]").text()
                var link = snippet.select("a").attr("href")
                
                if (link.startsWith("/")) link = "https://search.brave.com$link"

                if (title.isNotBlank()) {
                    results.add(SearchResult(title, description, link))
                }
            }

            // 2. SvelteKit/Next parsing: Regex extract from data blocks if HTML is missing
            if (results.isEmpty()) {
                val scripts = doc.select("script")
                for (script in scripts) {
                    val content = script.html()
                    if (content.contains("__next_f.push")) {
                        // Regex to find result objects with title, description, url
                        val pattern = Pattern.compile("\\{\\s*\"title\"\\s*:\\s*\"(.*?)\"\\s*,\\s*\"description\"\\s*:\\s*\"(.*?)\"\\s*,\\s*\"url\"\\s*:\\s*\"(.*?)\"\\s*\\}", Pattern.CASE_INSENSITIVE)
                        val matcher = pattern.matcher(content)
                        while (matcher.find()) {
                            val title = matcher.group(1)?.replace("\\\"", "\"") ?: ""
                            val desc = matcher.group(2)?.replace("\\\"", "\"") ?: ""
                            val link = matcher.group(3)?.replace("\\\"", "\"") ?: ""
                            if (title.isNotBlank()) {
                                results.add(SearchResult(title, desc, link))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WebSearchHelper", "Brave error: ${e.message}")
        }
        return results
    }

    private fun fetchFromDDGLite(query: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://duckduckgo.com/lite/?q=$encodedQuery"
            
            val connection = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .followRedirects(true)
                .timeout(10000)
            
            STANDARD_HEADERS.forEach { (k, v) -> connection.header(k, v) }
            
            val doc = connection.get()
            
            // DDG Lite result structure
            val resultLinks = doc.select("a.result-link")
            val resultSnippets = doc.select("td.result-snippet")
            
            for (i in 0 until resultLinks.size) {
                val linkEl = resultLinks.getOrNull(i)
                val snippetEl = resultSnippets.getOrNull(i)
                
                if (linkEl != null) {
                    val title = linkEl.text()
                    val link = linkEl.attr("href")
                    val description = snippetEl?.text() ?: ""
                    
                    if (title.isNotBlank()) {
                        results.add(SearchResult(title, description, link))
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WebSearchHelper", "DDG Lite error: ${e.message}")
        }
        return results
    }
}
