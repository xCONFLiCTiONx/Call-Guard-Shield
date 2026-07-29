package com.xconflictionx.callguardshield.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xconflictionx.callguardshield.data.entity.PhoneLookupResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberActionMenu(
    number: String,
    label: String? = null,
    intelResult: PhoneLookupResult? = null,
    onDismiss: () -> Unit,
    onIdentify: () -> Unit,
    onAddToWhitelist: (String?) -> Unit,
    onAddToBlacklist: (String?) -> Unit,
    onRemoveFromList: () -> Unit,
    onAddToContacts: () -> Unit,
    onCall: () -> Unit,
    onEditLabel: (() -> Unit)? = null,
    removeLabel: String = "Remove from List"
) {
    val clipboardManager = LocalClipboardManager.current
    var showLabelDialog by remember { mutableStateOf<LabelDialogType?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Header: Number and Label
            Column(modifier = Modifier.padding(16.dp)) {
                if (label != null) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = number,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            // Action Items
            ListItem(
                headlineContent = { Text("Identify Caller") },
                leadingContent = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.clickable { 
                    onIdentify()
                    onDismiss()
                }
            )

            ListItem(
                headlineContent = { Text("Call Number") },
                leadingContent = { Icon(Icons.Default.Phone, contentDescription = null) },
                modifier = Modifier.clickable { 
                    onCall()
                    onDismiss()
                }
            )

            if (onEditLabel != null) {
                ListItem(
                    headlineContent = { Text("Edit Label") },
                    leadingContent = { Icon(Icons.Default.Edit, contentDescription = null) },
                    modifier = Modifier.clickable { 
                        onEditLabel()
                        onDismiss()
                    }
                )
            }

            ListItem(
                headlineContent = { Text(removeLabel, color = MaterialTheme.colorScheme.error) },
                leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable { 
                    onRemoveFromList()
                    onDismiss()
                }
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.2f))

            ListItem(
                headlineContent = { Text("Add to/Move to Blacklist") },
                leadingContent = { Icon(Icons.Default.Block, contentDescription = null) },
                modifier = Modifier.clickable { 
                    showLabelDialog = LabelDialogType.Blacklist
                }
            )
            
            ListItem(
                headlineContent = { Text("Add to/Move to Whitelist") },
                leadingContent = { Icon(Icons.Default.Check, contentDescription = null) },
                modifier = Modifier.clickable { 
                    showLabelDialog = LabelDialogType.Whitelist
                }
            )

            ListItem(
                headlineContent = { Text("Add to Contacts") },
                leadingContent = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                modifier = Modifier.clickable { 
                    onAddToContacts()
                    onDismiss()
                }
            )
            
            ListItem(
                headlineContent = { Text("Copy Number") },
                leadingContent = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                modifier = Modifier.clickable { 
                    clipboardManager.setText(AnnotatedString(number))
                    onDismiss()
                }
            )

            // Gemini Intelligence Details Section
            if (intelResult != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Gemini Intelligence Details",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Owner/Company
                    val displayName = intelResult.companyName ?: intelResult.ownerName ?: "Unknown"
                    DetailRow("Name", displayName)
                    
                    // Category
                    DetailRow("Category", intelResult.category ?: "Unknown")
                    
                    // Confidence
                    val confidenceText = intelResult.confidence?.let { "${(it * 100).toInt()}%" } ?: "N/A"
                    DetailRow("Confidence", confidenceText)
                    
                    // Risk Assessment
                    val risk = when {
                        intelResult.scam -> "HIGH - Scam"
                        intelResult.spam -> "MEDIUM - Spam"
                        else -> "LOW - Legitimate"
                    }
                    DetailRow("Risk Level", risk)
                    
                    // Flags
                    val flags = buildList {
                        if (intelResult.debtCollector) add("Debt Collector")
                        if (intelResult.telemarketer) add("Telemarketer")
                    }
                    if (flags.isNotEmpty()) {
                        DetailRow("Flags", flags.joinToString(", "))
                    }

                    // Summary
                    intelResult.summary?.let {
                        Spacer(modifier = Modifier.height(8.dp))
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
                                text = it,
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Evidence
                    intelResult.evidence?.takeIf { it.isNotEmpty() }?.let { evidenceList ->
                        Spacer(modifier = Modifier.height(8.dp))
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
                        Spacer(modifier = Modifier.height(8.dp))
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
                }
            }
        }
    }

    if (showLabelDialog != null) {
        var labelInput by remember { mutableStateOf(label ?: "") }
        AlertDialog(
            onDismissRequest = { showLabelDialog = null },
            title = { Text("Add Label") },
            text = {
                OutlinedTextField(
                    value = labelInput,
                    onValueChange = { labelInput = it },
                    label = { Text("Name (e.g., DHS, Spam)") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val finalLabel = labelInput.trim().ifEmpty { null }
                    if (showLabelDialog == LabelDialogType.Blacklist) onAddToBlacklist(finalLabel)
                    else onAddToWhitelist(finalLabel)
                    showLabelDialog = null
                    onDismiss()
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (showLabelDialog == LabelDialogType.Blacklist) onAddToBlacklist(null)
                    else onAddToWhitelist(null)
                    showLabelDialog = null
                    onDismiss()
                }) { Text("Skip") }
            }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
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
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

enum class LabelDialogType { Blacklist, Whitelist }