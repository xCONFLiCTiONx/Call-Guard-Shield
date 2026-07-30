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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xconflictionx.callguardshield.data.entity.PhoneLookupResult
import com.xconflictionx.callguardshield.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

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
    onIdentify: () -> Unit,
    onNavigatePrevious: (() -> Unit)? = null,
    onNavigateNext: (() -> Unit)? = null
) {
    val geminiStageState = viewModel.geminiStage.collectAsState(initial = null)
    val geminiStage = geminiStageState.value
    
    val pendingResult by viewModel.pendingIntelResult.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    val groupedLogs by viewModel.groupedCallLogs.collectAsState()
    val currentGroup = groupedLogs.find { it.number == number }
    val timeline = currentGroup?.allTimestamps ?: emptyList()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        contentWindowInsets = { WindowInsets(0) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            // Navigation Row at the very top
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button (Left)
                Box(modifier = Modifier.size(48.dp)) {
                    if (onNavigatePrevious != null) {
                        IconButton(
                            onClick = onNavigatePrevious,
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack, 
                                contentDescription = "Previous",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Text(
                    text = "Caller Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Next Button (Right)
                Box(modifier = Modifier.size(48.dp)) {
                    if (onNavigateNext != null) {
                        IconButton(
                            onClick = onNavigateNext,
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward, 
                                contentDescription = "Next",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.1f))

            // Header with number and settings button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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
                // Proposed Update Section (Comparison)
                if (pendingResult != null && viewModel.isDataDifferent(pendingResult, intelResult)) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.NewReleases, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Proposed Update", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            val pName = pendingResult?.manualLabel ?: pendingResult?.companyName ?: pendingResult?.ownerName ?: "Unknown"
                            val pAcc = "${pendingResult?.accuracy ?: 0}%"
                            val pRisk = if (pendingResult?.scam == true) "High" else if (pendingResult?.spam == true) "Medium" else "Low"
                            
                            Text("Gemini found new information:", style = MaterialTheme.typography.bodySmall)
                            Text("• Name: $pName", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Text("• Accuracy: $pAcc", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Text("• Risk: $pRisk", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (intelResult != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Current Saved Details",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val displayName = intelResult.manualLabel ?: intelResult.companyName ?: intelResult.ownerName ?: "Unknown"
                    DetailRow("Name", displayName)
                    DetailRow("Category", intelResult.category ?: "Unknown")
                    
                    val accuracyText = "${intelResult.accuracy}%"
                    DetailRow("Accuracy", accuracyText)

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

                    if (intelResult.isCached) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("📦 Cached result", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                } else {
                    Text("No AI intelligence data available yet.", color = Color.Gray, modifier = Modifier.padding(vertical = 16.dp))
                }

                // Call Timeline Section
                if (timeline.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Call History Timeline (x${timeline.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val locale = LocalConfiguration.current.locales[0]
                    val df = SimpleDateFormat("MMM dd, HH:mm", locale)
                    
                    timeline.take(20).forEach { ts ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = df.format(Date(ts)), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Bottom Actions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Persistent Update Details Button
                val isDifferent = viewModel.isDataDifferent(pendingResult, intelResult)
                Button(
                    onClick = { viewModel.applyPendingIntelUpdate() },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    enabled = pendingResult != null && isDifferent,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        disabledContentColor = Color.Gray
                    )
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (pendingResult == null) "No scan results yet"
                        else if (isDifferent) "Update Saved Details" 
                        else "Information matches current records"
                    )
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
