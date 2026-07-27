# Implementation Plan - Improve Intelligence Gathering Accuracy

The user reported that the system failed to identify a known business number (870-425-6011 - Baxter County DHS). Investigation revealed that the DuckDuckGo scraping URL was outdated and the entity extraction logic was too restrictive. This plan improves the intelligence engine to be more accurate and region-aware.

## Proposed Changes

### [app](file:///C:/Users/Michael/Documents/AndroidStudioProjects/Call%20Guard%20Shield/app)

#### [MODIFY] [PhoneHelper.kt](file:///C:/Users/Michael/Documents/AndroidStudioProjects/Call%20Guard%20Shield/app/src/main/java/com/xconflictionx/callguardshield/logic/PhoneHelper.kt)
- Add `getRegionForNumber(number: String): String?` to map area codes to US States (starting with Arkansas codes like 870, 501, 479).

#### [MODIFY] [WebSearchHelper.kt](file:///C:/Users/Michael/Documents/AndroidStudioProjects/Call%20Guard%20Shield/app/src/main/java/com/xconflictionx/callguardshield/logic/WebSearchHelper.kt)
- Update search URL to `https://html.duckduckgo.com/html/` for reliable non-JS scraping.
- Improve `fetchSnippets` to select `.result` blocks and extract titles/snippets together.
- Detect the region (State) using `PhoneHelper` and include it in the "Identity Layer" query to find local businesses/offices.

#### [MODIFY] [NumberIntelEngine.kt](file:///C:/Users/Michael/Documents/AndroidStudioProjects/Call%20Guard%20Shield/app/src/main/java/com/xconflictionx/callguardshield/logic/NumberIntelEngine.kt)
- Update `extractEntity` to:
    - Search both titles AND snippets for business patterns.
    - Handle cases where the number is prefixed in the title.
    - Be less restrictive with character limits if the content looks like a business name.
- Enhance `calculateTrustScore` with keywords for social services, human services, and DHS to correctly identify government entities.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:compileDebugKotlin` to ensure syntax is correct.

### Manual Verification
- Deploy the app.
- Search for "8704256011" in the Chat/Intel screen.
- Verify that the report correctly identifies "Baxter County Department of Human Services" (or similar) and shows a positive safety score.
