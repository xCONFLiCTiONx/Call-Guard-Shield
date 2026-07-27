package com.xconflictionx.callguardshield.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xconflictionx.callguardshield.data.entity.PhoneLookupResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberDetailsSheet(
    number: String,
    label: String? = null,
    intelResult: PhoneLookupResult? = null,
    isIdentifying: Boolean = false,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onIdentify: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Header with number and settings button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (label!= null) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = number,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Gemini manual scan button or loading indicator
                    if (isIdentifying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(4.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        IconButton(
                            onClick = onIdentify
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh, 
                                contentDescription = "Gemini Individual Scan",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    FilledTonalIconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Number Settings")
                    }
                }
            }

            // Intel Details Section
            if (intelResult!= null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Gemini Intelligence Details",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Name (Owner/Company)
                    val displayName = intelResult.companyName?: intelResult.ownerName?: "Unknown"
                    DetailRow("Name", displayName)

                    // Category
                    DetailRow("Category", intelResult.category?: "Unknown")

                    // Confidence
                    val confidenceText = intelResult.confidence?.let { "${(it * 100).toInt()}%" }?: "N/A"
                    DetailRow("Confidence", confidenceText)

                    // Risk Assessment
                    val risk = when {
                        intelResult.scam -> "HIGH - Scam Alert!"
                        intelResult.spam -> "MEDIUM - Spam Risk"
                        else -> "LOW - Safe/Legitimate"
                    }
                    val riskColor = when {
                        intelResult.scam -> MaterialTheme.colorScheme.error
                        intelResult.spam -> Color(0xFFFF9800)
                        else -> Color(0xFF4CAF50)
                    }
                    DetailRow("Risk Level", risk, valueColor = riskColor)

                    // Flags
                    val flags = buildList {
                        if (intelResult.debtCollector) add("Debt Collector")
                        if (intelResult.telemarketer) add("Telemarketer")
                    }
                    if (flags.isNotEmpty()) {
                        DetailRow("Flags", flags.joinToString(", "))
                    }

                    // Summary
                    intelResult.summary?.let { summary ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Summary",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = summary,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Evidence
                    intelResult.evidence?.takeIf { it.isNotEmpty() }?.let { evidenceList ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Evidence",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        evidenceList.forEach { evidence ->
                            Text(
                                text = "• $evidence",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                            )
                        }
                    }

                    // Sources
                    intelResult.sources?.takeIf { it.isNotEmpty() }?.let { sourceList ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Sources",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        sourceList.forEach { source ->
                            Text(
                                text = "• $source",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                            )
                        }
                    }

                    // Last Verified
                    intelResult.lastVerified?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        DetailRow("Last Verified", it)
                    }

                    // Cached indicator
                    if (intelResult.isCached) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "📦 Data sourced from local cache (last 30 days)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            } else {
                // No intel data available
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "No Gemini intelligence data available for this number yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Use the settings button above to identify this caller.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor,
            fontWeight = if (valueColor!= MaterialTheme.colorScheme.onSurfaceVariant) FontWeight.Bold else FontWeight.Normal
        )
    }
}