package com.xconflictionx.callguardshield.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xconflictionx.callguardshield.data.entity.PhoneLookupResult

@Composable
fun PhoneLookupResultCard(
    result: PhoneLookupResult, 
    wasAutoApplied: Boolean = true,
    oldConfidence: Double? = null,
    onRefine: (String) -> Unit = {},
    onApply: (PhoneLookupResult) -> Unit = {}
) {
    val confidence = result.confidence ?: 0.0
    val displayConfidence = (confidence * 100).toInt().coerceIn(0, 100)
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    
    val (tierText, tierColor) = when {
        confidence >= 0.9 -> "Verified" to Color(0xFF4CAF50) // Green
        confidence >= 0.6 -> "Likely" to Color(0xFFFFC107)   // Amber/Yellow
        confidence >= 0.1 -> "Unverified" to Color.Gray
        else -> "Unknown" to Color(0xFFF44336)              // Red
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = {
                    val text = result.summary ?: ""
                    clipboardManager.setText(AnnotatedString(text))
                    android.widget.Toast.makeText(context, "Summary copied", android.widget.Toast.LENGTH_SHORT).show()
                })
            },
        colors = CardDefaults.cardColors(
            containerColor = if (!wasAutoApplied) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.05f) 
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = if (!wasAutoApplied) BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Intelligence Report", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        if (result.isCached) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = Color.Gray.copy(alpha = 0.2f),
                                shape = MaterialTheme.shapes.extraSmall
                            ) {
                                Text(
                                    "UP-TO-DATE",
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else if (wasAutoApplied && oldConfidence != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = Color.Green.copy(alpha = 0.1f),
                                shape = MaterialTheme.shapes.extraSmall
                            ) {
                                Text(
                                    "AUTO-UPDATED",
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Green,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Text(result.phoneNumber, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                
                Surface(
                    color = tierColor.copy(alpha = 0.2f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "$tierText ($displayConfidence%)",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = tierColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (!wasAutoApplied && oldConfidence != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = "⚠️ Conflict: New confidence ($displayConfidence%) is lower than current records (${(oldConfidence * 100).toInt()}%).",
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Body Info
            ResultRow("Identified As", result.ownerName ?: "Unknown")
            ResultRow("Company", result.companyName ?: "Unknown")
            ResultRow("Category", result.category ?: "Unknown")
            
            val status = when {
                result.scam -> "Confirmed Scam"
                result.spam -> "Potential Spam"
                result.debtCollector -> "Debt Collector"
                result.telemarketer -> "Telemarketer"
                confidence >= 0.9 -> "Authority Verified"
                else -> tierText
            }
            ResultRow("Status", status, color = if (result.scam || result.spam) MaterialTheme.colorScheme.error else tierColor)

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color.Gray.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(12.dp))

            Text("Investigation Summary", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(result.summary ?: "No details provided.", style = MaterialTheme.typography.bodyMedium)

            val evidence = result.evidence
            if (!evidence.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Verification Points", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    evidence.take(5).forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle, 
                                contentDescription = null, 
                                modifier = Modifier.size(14.dp), 
                                tint = tierColor.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(item, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (!result.lastVerified.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Last Verified: ${result.lastVerified}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            if (!wasAutoApplied) {
                var applied by remember { mutableStateOf(false) }
                Button(
                    onClick = { 
                        onApply(result)
                        applied = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !applied,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(if (applied) "Updated Successfully" else "Apply this update anyway")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (confidence < 0.8) {
                    OutlinedButton(
                        onClick = { onRefine(result.phoneNumber) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Verify Deeply", style = MaterialTheme.typography.labelSmall)
                    }
                }
                
                TextButton(
                    onClick = { onRefine(result.phoneNumber) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Wrong Info?", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}
