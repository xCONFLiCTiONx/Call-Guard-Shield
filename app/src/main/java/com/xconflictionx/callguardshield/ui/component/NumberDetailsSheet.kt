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
    isScannerMode: Boolean = false,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onNavigatePrevious: (() -> Unit)? = null,
    onNavigateNext: (() -> Unit)? = null
) {
    val geminiStageState = viewModel.geminiStage.collectAsState(initial = null)
    val geminiStage = geminiStageState.value
    
    val pendingResult by viewModel.pendingIntelResult.collectAsState()
    val isIdentifying by viewModel.isIdentifying.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    val groupedLogs by viewModel.groupedCallLogs.collectAsState()
    val currentGroup = groupedLogs.find { it.number == number }
    
    val isInBlacklist = currentGroup?.isInBlacklist ?: false
    val isPrefixMatch = currentGroup?.isPrefixMatch ?: false
    val isGlobalSpamMatch = currentGroup?.isGlobalSpamMatch ?: false
    val isInWhitelist = currentGroup?.isInWhitelist ?: false
    
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
            // Navigation Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(48.dp)) {
                    if (!isScannerMode && onNavigatePrevious != null) {
                        IconButton(onClick = onNavigatePrevious, modifier = Modifier.align(Alignment.Center)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Text(
                    text = if (isScannerMode) "AI Intelligence Center" else "Caller Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Navigation Right
                Box(modifier = Modifier.size(48.dp)) {
                    if (!isScannerMode && onNavigateNext != null) {
                        IconButton(onClick = onNavigateNext, modifier = Modifier.align(Alignment.Center)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.1f))

            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (label != null) {
                        Text(text = label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(text = number, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    val isBlockedInHistory = currentGroup?.isBlocked ?: false
                    
                    val (statusText, statusColor) = when {
                        isInWhitelist -> "🛡️ CURRENTLY ALLOWED" to Color(0xFF4CAF50)
                        isInBlacklist -> "🚫 CURRENTLY BLOCKED" to MaterialTheme.colorScheme.error
                        isPrefixMatch -> "🚫 BLOCKED BY PREFIX" to MaterialTheme.colorScheme.error
                        isGlobalSpamMatch -> "🚫 BLOCKED BY GLOBAL DB" to Color(0xFFFF5252)
                        isBlockedInHistory -> "🚫 BLOCKED BY FIREWALL" to MaterialTheme.colorScheme.error
                        else -> "⚖️ NO CUSTOM RULE" to Color.Gray
                    }
                    
                    Surface(color = statusColor.copy(alpha = 0.1f), shape = MaterialTheme.shapes.extraSmall, border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.2f))) {
                        Text(text = statusText, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.Bold)
                    }
                }
                
                FilledTonalIconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Number Settings")
                }
            }

            if (isThisNumberIdentifying || isIdentifying) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp), color = MaterialTheme.colorScheme.primary, trackColor = Color.Transparent)
                    Text(text = geminiStage ?: "Preparing AI scan...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
            }

            // Scrollable Content
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())
            ) {
                // Proposed Update Section (Scanner View)
                if (pendingResult != null && viewModel.isDataDifferent(pendingResult, intelResult)) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), shape = MaterialTheme.shapes.medium, modifier = Modifier.padding(vertical = 8.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.NewReleases, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Proposed Update", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            val pName = pendingResult?.manualLabel ?: pendingResult?.companyName ?: pendingResult?.ownerName ?: "Unknown"
                            Text("• Name: $pName", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Text("• Accuracy: ${pendingResult?.accuracy}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            val pRisk = if (pendingResult?.scam == true) "High" else if (pendingResult?.spam == true) "Medium" else "Low"
                            Text("• Risk: $pRisk", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (intelResult != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 12.dp))
                    Text(text = "Current Saved Details", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    val displayName = intelResult.manualLabel ?: intelResult.companyName ?: intelResult.ownerName ?: "Unknown"
                    DetailRow("Name", displayName)
                    DetailRow("Category", intelResult.category ?: "Unknown")
                    DetailRow("Accuracy", "${intelResult.accuracy}%")
                    val risk = when { intelResult.scam -> "HIGH - Scam Alert!"; intelResult.spam -> "MEDIUM - Spam Risk"; else -> "LOW - Safe/Legitimate" }
                    val riskColor = when { intelResult.scam -> MaterialTheme.colorScheme.error; intelResult.spam -> Color(0xFFFF9800); else -> Color(0xFF4CAF50) }
                    DetailRow("Risk Level", risk, valueColor = riskColor)
                    
                    intelResult.summary?.let { summary ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Summary", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = MaterialTheme.shapes.small) {
                            Text(text = summary, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else if (!isScannerMode) {
                    Text("No AI intelligence data available yet.", color = Color.Gray, modifier = Modifier.padding(vertical = 16.dp))
                }

                if (timeline.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(text = "Call History Timeline (x${timeline.size})", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    val df = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                    timeline.take(20).forEach { ts ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = df.format(Date(ts)), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Scanner Action Block (Only in Scanner Mode)
            if (isScannerMode) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val isDifferent = viewModel.isDataDifferent(pendingResult, intelResult)
                    if (pendingResult != null && isDifferent) {
                        Button(
                            onClick = { viewModel.applyPendingIntelUpdate() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Update Saved Details")
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.performInvestigation(number) }, modifier = Modifier.weight(1f), enabled = !isIdentifying) {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Fast Scan")
                        }
                        Button(onClick = { viewModel.performThoroughInvestigation(number) }, modifier = Modifier.weight(1f), enabled = !isIdentifying, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C), contentColor = Color.White)) {
                            Icon(Icons.AutoMirrored.Filled.ManageSearch, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Deep Scan")
                        }
                    }
                    Text(text = "Deep Scan performs multi-stage web research for maximum accuracy.", style = MaterialTheme.typography.labelSmall, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
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
