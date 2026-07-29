package com.xconflictionx.callguardshield.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xconflictionx.callguardshield.data.entity.CallLogEntry
import com.xconflictionx.callguardshield.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityStatusSheet(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val suggestions by viewModel.securitySuggestions.collectAsState()
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "Security Outlook",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Overview of your current firewall posture.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            // 1. Active Shields
            item {
                Text("Active Shields", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShieldStatusItem("AI Real-Time Filtering", settings.aiRealTimeBlocking)
                    ShieldStatusItem("Manual Blacklist Enforced", settings.blacklistEnabled)
                    ShieldStatusItem("Manual Whitelist Enforced", settings.whitelistEnabled)
                    ShieldStatusItem("Unknown ID Blocking", settings.blockUnknown)
                    ShieldStatusItem("Strict Contact-Only Mode", settings.allowOnlyContacts)
                    ShieldStatusItem("Regional Out-of-State Blocking", settings.blockOutOfState)
                    ShieldStatusItem("International Blocking", settings.blockInternational)
                }
            }

            // 2. Hardening Suggestions
            val recommendations = buildList {
                if (!settings.aiRealTimeBlocking) add("Enable AI Filtering to verify unknown callers with Gemini.")
                if (!settings.blacklistEnabled) add("Enable your Blacklist to enforce your manual block rules.")
                if (!settings.whitelistEnabled) add("Enable your Whitelist to ensure important numbers always ring.")
                if (!settings.blockUnknown) add("Enable 'Block Unknown' to reject restricted/private numbers.")
                if (!settings.blockInternational) add("Enable International Blocking to stop overseas spam.")
                if (settings.enabledDictionaries.isEmpty()) add("Enable 'Global Spam Database' for 2,000+ community rules.")
            }

            if (recommendations.isNotEmpty()) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.2f))
                    Text("Protection Recommendations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Yellow)
                    Spacer(modifier = Modifier.height(8.dp))
                    recommendations.forEach { rec ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(rec, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // 3. Recent Threat Suggestions
            if (suggestions.isNotEmpty()) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.2f))
                    Text("Recent Threats Found", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Text("These callers were flagged by Gemini but were allowed through.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }

                items(suggestions) { threat ->
                    ThreatSuggestionItem(
                        threat = threat,
                        onBlock = {
                            viewModel.addToBlacklist(threat.number, threat.callerName ?: threat.ownerName ?: threat.companyName ?: "Spam")
                            onDismiss()
                        }
                    )
                }
            }
            
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun ShieldStatusItem(label: String, isActive: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isActive) "ACTIVE" else "DISABLED",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isActive) Color.Green else Color.Gray
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = if (isActive) Color.Green else Color.Gray,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun ThreatSuggestionItem(
    threat: CallLogEntry,
    onBlock: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = threat.callerName ?: threat.number,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = threat.callerInfo ?: "High Risk Caller",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Button(
                onClick = onBlock,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Text("BLOCK", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
