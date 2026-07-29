package com.xconflictionx.callguardshield.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xconflictionx.callguardshield.data.entity.PhoneLookupResult
import com.xconflictionx.callguardshield.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberDetailsSheet(
    viewModel: MainViewModel,
    number: String,
    label: String? = null,
    intelResult: PhoneLookupResult? = null,
    isThisNumberIdentifying: Boolean = false,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onIdentify: () -> Unit
) {
    val geminiStageState = viewModel.geminiStage.collectAsState(initial = null)
    val geminiStage = geminiStageState.value
    
    val pendingResult by viewModel.pendingIntelResult.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
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
                    if (label != null) {
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
                    IconButton(
                        onClick = onIdentify,
                        enabled = !isThisNumberIdentifying
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome, 
                            contentDescription = "Fast Scan",
                            tint = if (isThisNumberIdentifying) Color.Gray else MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    FilledTonalIconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Number Settings")
                    }
                }
            }

            if (isThisNumberIdentifying) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent
                    )
                    Text(
                        text = geminiStage ?: "Preparing AI scan...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            // Intel Details Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (intelResult != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Gemini Intelligence Details",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val displayName = intelResult.companyName ?: intelResult.ownerName ?: "Unknown"
                    DetailRow("Name", displayName)
                    DetailRow("Category", intelResult.category ?: "Unknown")
                    
                    val confidenceText = intelResult.confidence?.let { "${(it * 100).toInt()}%" } ?: "N/A"
                    DetailRow("Confidence", confidenceText)

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

                    val flags = buildList {
                        if (intelResult.debtCollector) add("Debt Collector")
                        if (intelResult.telemarketer) add("Telemarketer")
                    }
                    if (flags.isNotEmpty()) {
                        DetailRow("Flags", flags.joinToString(", "))
                    }

                    intelResult.summary?.let { summary ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Summary", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = MaterialTheme.shapes.small) {
                            Text(text = summary, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    intelResult.evidence?.takeIf { it.isNotEmpty() }?.let { evidenceList ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Evidence", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        evidenceList.forEach { evidence ->
                            Text("• $evidence", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp, top = 2.dp))
                        }
                    }

                    intelResult.sources?.takeIf { it.isNotEmpty() }?.let { sourceList ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Sources", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        sourceList.forEach { source ->
                            Text("• $source", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp, top = 2.dp))
                        }
                    }

                    if (intelResult.isCached) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("📦 Cached result", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                } else {
                    Text("No AI intelligence data available yet.", color = Color.Gray, modifier = Modifier.padding(vertical = 16.dp))
                }
            }

            // Bottom Actions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (pendingResult != null) {
                    Button(
                        onClick = { viewModel.applyPendingIntelUpdate() },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Update Details")
                    }
                }

                Button(
                    onClick = { viewModel.performThoroughInvestigation(number) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isThisNumberIdentifying,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFB71C1C), // Dark Red
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.ManageSearch, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Deep Scan")
                }
                Text(
                    text = "Do a deep scan of this number for higher accuracy.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(text = "$label: ", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        Text(text = value, style = MaterialTheme.typography.bodySmall, color = valueColor)
    }
}
