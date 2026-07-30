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
    number: String,
    label: String? = null,
    intelResult: PhoneLookupResult? = null,
    onDismiss: () -> Unit,
    onOpenScanner: () -> Unit,
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
    
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentWindowInsets = { WindowInsets(0) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
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

            // Standard Actions
            ListItem(
                headlineContent = { Text("Look up number with AI") },
                supportingContent = { Text("Research identity and reputation with Gemini.") },
                leadingContent = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable { 
                    onOpenScanner()
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
        var labelInput by remember { mutableStateOf(if (label == number) "" else (label ?: "")) }
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
