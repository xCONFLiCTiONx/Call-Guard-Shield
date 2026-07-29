package com.xconflictionx.callguardshield.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xconflictionx.callguardshield.data.entity.PhoneLookupResult
import com.xconflictionx.callguardshield.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberActionMenu(
    viewModel: MainViewModel,
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
    
    val pendingResult by viewModel.pendingIntelResult.collectAsState()

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
                supportingContent = { Text("Triggers a Fast Scan for immediate results.") },
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

            ListItem(
                headlineContent = { Text("Edit Details") },
                supportingContent = { Text("Manually set the label and risk level.") },
                leadingContent = { Icon(Icons.Default.Edit, contentDescription = null) },
                modifier = Modifier.clickable { 
                    onEditLabel?.invoke()
                    onDismiss()
                }
            )

            // Update Details Button (Always Visible)
            val isDifferent = viewModel.isDataDifferent(pendingResult, intelResult)
            ListItem(
                headlineContent = { 
                    Text(
                        text = if (pendingResult == null) "No scan results available"
                               else if (isDifferent) "Update Saved Details" 
                               else "Information is up-to-date",
                        fontWeight = FontWeight.Bold,
                        color = if (pendingResult != null && isDifferent) MaterialTheme.colorScheme.primary else Color.Gray
                    ) 
                },
                supportingContent = { Text("Apply the latest Gemini research to this number.") },
                leadingContent = { 
                    Icon(
                        Icons.Default.CloudUpload, 
                        contentDescription = null,
                        tint = if (pendingResult != null && isDifferent) MaterialTheme.colorScheme.primary else Color.Gray
                    ) 
                },
                modifier = Modifier.clickable(enabled = pendingResult != null && isDifferent) {
                    viewModel.applyPendingIntelUpdate()
                    onDismiss()
                }
            )

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

            // Gemini Intelligence Details Section (Summary View)
            if (intelResult != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Current Saved Intelligence",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val displayName = intelResult.companyName ?: intelResult.ownerName ?: "Unknown"
                    DetailRow("Name", displayName)
                    DetailRow("Category", intelResult.category ?: "Unknown")
                    DetailRow("Accuracy", "${intelResult.accuracy}%")
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
