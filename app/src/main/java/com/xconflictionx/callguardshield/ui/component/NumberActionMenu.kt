package com.xconflictionx.callguardshield.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberActionMenu(
    number: String,
    label: String? = null,
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
            
            ListItem(
                headlineContent = { Text("Identify Caller") },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null) },
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
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.2f))

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

enum class LabelDialogType { Blacklist, Whitelist }
