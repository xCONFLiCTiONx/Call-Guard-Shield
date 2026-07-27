# Implementation Plan - Smarter Multi-Source Intel Engine (No AI)

This plan transforms the investigation system into a professional-grade **Evidence Aggregator**. It will use web scraping to "proxy" data from major telecom layers (Twilio, Numlookup, etc.) and government registries to verify legitimate numbers alongside spam.

## User Review Required

> [!IMPORTANT]
> **Scraping as a Data Proxy**: To keep the app 100% free and avoid requiring you to sign up for 4 different API keys (Twilio, Numlookup, etc.), I will use **Jsoup** to scrape aggregated search results. This allows the app to "see" the carrier data, caller names, and reputation scores that these professional services publish on the open web.

## Proposed Changes

### 1. Advanced Web Scraper (WebSearchHelper.kt)

#### [MODIFY] [WebSearchHelper.kt](file:///C:/Users/Michael/Documents/AndroidStudioProjects/Call%20Guard%20Shield/app/src/main/java/com/xconflictionx/callguardshield/logic/WebSearchHelper.kt)
- **Implement Jsoup Engine**: Connect to `duckduckgo.com/html` to fetch full search results (not just "instant answers").
- **Four-Pronged Investigation**:
    1.  **Carrier Layer**: Search for `"[Number]" carrier line type info`. (Proxies Twilio/Numlookup data).
    2.  **Identity Layer**: Search for `"[Number]" owner identity official`. (Proxies CNAM/Identity data).
    3.  **Reputation Layer**: Search for `"[Number]" reputation score reports`. (Proxies ESPY/Spam database data).
    4.  **Government Layer**: Targeted `site:fcc.gov OR site:ftc.gov` search.
- **Deep Extraction**: Pull up to 15 high-quality snippets across all layers.

### 2. Weighted Reputation Engine (NumberIntelEngine.kt)

#### [MODIFY] [NumberIntelEngine.kt](file:///C:/Users/Michael/Documents/AndroidStudioProjects/Call%20Guard%20Shield/app/src/main/java/com/xconflictionx/callguardshield/logic/NumberIntelEngine.kt)
- **Scoring System**:
    - **TRUST (+)**: Official business markers, ".gov" snippets, Carrier verification (Verizon/AT&T/Google Voice).
    - **NEUTRAL (0)**: Private individual markers, Newly assigned VOIP.
    - **RISK (-)**: "Debt", "Scam", "Fraud", "FCC Complaint", "Robocall".
- **Dynamic Classification**:
    - If Score > 5: **"Verified Legitimate"**.
    - If Score < -5: **"Spam / High Risk"**.
    - If Score is 0 and is Google Voice: **"Verified VOIP (Private)"**.
- **Carrier/Line Extraction**: Explicitly detect and report line type (Mobile, Landline, VoIP).

### 3. Professional Report UI (ChatScreen.kt)

#### [MODIFY] [ChatScreen.kt](file:///C:/Users/Michael/Documents/AndroidStudioProjects/Call%20Guard%20Shield/app/src/main/java/com/xconflictionx/callguardshield/ui/screen/ChatScreen.kt)
- **Intel Breakdown**: Display the findings in a structured format:
    *   **Network Intel**: (Carrier, Line Type)
    *   **Government Intel**: (FCC/FTC data)
    *   **Web Identity**: (Found names, business listings)
    *   **Safety Score**: (Calculated risk)

## Verification Plan

### Manual Verification
- **Google Voice Test**: Verify a Google Voice number returns "Verified VOIP" and a Safe/Neutral score.
- **Business Test**: Verify a known business (e.g., a local bank) returns their official name and a "Safe" score.
- **Spam Test**: Verify a known robocaller returns a "High Risk" score with specific complaint counts.
