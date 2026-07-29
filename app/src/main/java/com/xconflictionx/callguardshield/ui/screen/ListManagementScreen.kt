package com.xconflictionx.callguardshield.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xconflictionx.callguardshield.ui.MainViewModel
import com.xconflictionx.callguardshield.ui.component.NumberActionMenu
import com.xconflictionx.callguardshield.ui.component.NumberDetailsSheet
import com.xconflictionx.callguardshield.ui.component.EditNumberDetailsDialog
import com.xconflictionx.callguardshield.data.entity.BlacklistEntry
import com.xconflictionx.callguardshield.data.entity.WhitelistEntry

@Composable
fun ListManagementScreen(viewModel: MainViewModel) {
    val isIdentifying by viewModel.isIdentifying.collectAsState()
    val bulkProgress by viewModel.bulkProgress.collectAsState()
    val bulkNumber by viewModel.bulkNumber.collectAsState()
    val foregroundNumber by viewModel.foregroundNumber.collectAsState()
    val selectedNumberIntel by viewModel.selectedNumberIntel.collectAsState()
    var tabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Blacklist", "Whitelist")
    val context = LocalContext.current
    
    var pendingFileUri by remember { mutableStateOf<Uri?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showPasteDialog by remember { mutableStateOf(false) }
    var showAddSingleDialog by remember { mutableStateOf(false) }
    var exportData by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            pendingFileUri = uri
            showImportDialog = true
        }
    }

    val fileSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { stream ->
                stream.write(exportData.toByteArray())
            }
        }
    }

    Column {
        PrimaryTabRow(selectedTabIndex = tabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = tabIndex == index,
                    onClick = { tabIndex = index },
                    text = { Text(title) }
                )
            }
        }

        if (isIdentifying) {
            Column(modifier = Modifier.fillMaxWidth()) {
                val p = bulkProgress ?: 0f
                LinearProgressIndicator(
                    progress = p,
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                )
                bulkNumber?.let {
                    Text(
                        text = "Identifying: $it",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        
        // Action Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { showAddSingleDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add One")
                }

                TextButton(onClick = { showPasteDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Paste List")
                }

                TextButton(onClick = { filePicker.launch("text/plain") }) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Import")
                }
                
                Spacer(modifier = Modifier.weight(1f))

                IconButton(onClick = {
                    viewModel.performBulkInvestigation(tabIndex == 0)
                }) {
                    Icon(
                        Icons.Default.AutoAwesome, 
                        contentDescription = "Bulk Identify",
                        tint = if (isIdentifying) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }
            }
        }

        if (showAddSingleDialog) {
            AddSingleNumberDialog(
                isBlacklist = tabIndex == 0,
                onDismiss = { showAddSingleDialog = false },
                onConfirm = { num, label ->
                    if (tabIndex == 0) viewModel.addToBlacklist(num, label)
                    else viewModel.addToWhitelist(num, label)
                    showAddSingleDialog = false
                }
            )
        }

    if (showImportDialog && pendingFileUri != null) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Data") },
            text = { Text("Would you like to import this as a combined backup, or add to a specific list?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.importNumbers(pendingFileUri!!, true) { }
                    showImportDialog = false
                }) { Text("Auto-Detect / Backup") }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("Cancel") }
            }
        )
    }

        if (showPasteDialog) {
            PasteNumbersDialog(
                onDismiss = { showPasteDialog = false },
                onConfirm = { text, toBlacklist ->
                    viewModel.pasteNumbers(text, toBlacklist) { }
                    showPasteDialog = false
                }
            )
        }

        if (tabIndex == 0) {
            BlacklistTab(viewModel, selectedNumberIntel, foregroundNumber)
        } else {
            WhitelistTab(viewModel, selectedNumberIntel, foregroundNumber)
        }
    }
}

@Composable
fun AddSingleNumberDialog(isBlacklist: Boolean, onDismiss: () -> Unit, onConfirm: (String, String?) -> Unit) {
    var number by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isBlacklist) "Block Number" else "Allow Number") },
        text = {
            Column {
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text("Phone Number") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (Optional)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            Button(onClick = { if (number.isNotBlank()) onConfirm(number, label.ifEmpty { null }) }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun PasteNumbersDialog(onDismiss: () -> Unit, onConfirm: (String, Boolean) -> Unit) {
    var text by remember { mutableStateOf("") }
    var toBlacklist by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste Numbers") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Format: Label: Number (one per line)", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().height(200.dp).padding(top = 8.dp),
                    placeholder = { Text("Spam: +1800...") }
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    RadioButton(selected = toBlacklist, onClick = { toBlacklist = true })
                    Text("Blacklist", modifier = Modifier.clickable { toBlacklist = true })
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(selected = !toBlacklist, onClick = { toBlacklist = false })
                    Text("Whitelist", modifier = Modifier.clickable { toBlacklist = false })
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(text, toBlacklist) }) { Text("Import") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun BlacklistTab(
    viewModel: MainViewModel, 
    selectedNumberIntel: com.xconflictionx.callguardshield.data.entity.PhoneLookupResult? = null,
    foregroundNumber: String? = null
) {
    val blacklist by viewModel.blacklist.collectAsState()
    var selectedItem by remember { mutableStateOf<Triple<String, String?, Boolean>?>(null) } 
    var showSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (blacklist.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Blacklist is empty.", color = Color.Gray)
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(blacklist) { entry ->
                    ListItem(
                        headlineContent = { Text(entry.label ?: entry.pattern) },
                        supportingContent = { 
                            if (entry.label != null) Text(entry.pattern)
                        },
                        modifier = Modifier.clickable { 
                            selectedItem = Triple(entry.pattern, entry.label, false)
                            showSettings = false
                            viewModel.fetchIntelForNumber(entry.pattern)
                        }
                    )
                }
            }

            // Show Details Sheet first (primary view)
            if (!showSettings && selectedItem != null && !selectedItem!!.third) {
                val (number, label, _) = selectedItem!!
                NumberDetailsSheet(
                    viewModel = viewModel,
                    number = number,
                    label = label,
                    intelResult = selectedNumberIntel,
                    isThisNumberIdentifying = foregroundNumber == number,
                    onDismiss = { selectedItem = null },
                    onOpenSettings = { showSettings = true },
                    onIdentify = {
                        viewModel.performInvestigation(number)
                    }
                )
            }

            // Show Settings/Actions Menu (opened from Details Sheet)
            if (showSettings && selectedItem != null && !selectedItem!!.third) {
                val (number, label, _) = selectedItem!!
                NumberActionMenu(
                    number = number,
                    label = label,
                    onDismiss = { showSettings = false },
                    onIdentify = {
                        viewModel.performInvestigation(number)
                    },
                    onAddToWhitelist = { l -> viewModel.addToWhitelist(number, l) },
                    onAddToBlacklist = { l -> viewModel.addToBlacklist(number, l) },
                    onRemoveFromList = { 
                        blacklist.find { it.pattern == number }?.let { viewModel.removeFromBlacklist(it) }
                    },
                    onEditLabel = { 
                        showSettings = false
                        selectedItem = Triple(number, label, true) 
                    },
                    onAddToContacts = { launchAddContactIntent(context, number) },
                    onCall = { launchCallIntent(context, number) }
                )
            }

            // Edit Label dialog (separate from settings)
            if (selectedItem?.third == true) {
                val (number, _, _) = selectedItem!!
                EditNumberDetailsDialog(
                    number = number,
                    initialIntel = selectedNumberIntel,
                    onDismiss = { selectedItem = null },
                    onConfirm = { oldNum, updatedIntel ->
                        viewModel.updateFullNumberDetails(oldNum, updatedIntel, null)
                        selectedItem = null
                    }
                )
            }
        }
    }
}

@Composable
fun WhitelistTab(
    viewModel: MainViewModel, 
    selectedNumberIntel: com.xconflictionx.callguardshield.data.entity.PhoneLookupResult? = null,
    foregroundNumber: String? = null
) {
    val whitelist by viewModel.whitelist.collectAsState()
    var selectedItem by remember { mutableStateOf<Triple<String, String?, Boolean>?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (whitelist.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Whitelist is empty.", color = Color.Gray)
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(whitelist) { entry ->
                    ListItem(
                        headlineContent = { Text(entry.label ?: entry.number) },
                        supportingContent = { 
                            if (entry.label != null) Text(entry.number)
                        },
                        modifier = Modifier.clickable { 
                            selectedItem = Triple(entry.number, entry.label, false)
                            showSettings = false
                            viewModel.fetchIntelForNumber(entry.number)
                        }
                    )
                }
            }

            // Show Details Sheet first (primary view)
            if (!showSettings && selectedItem != null && !selectedItem!!.third) {
                val (number, label, _) = selectedItem!!
                NumberDetailsSheet(
                    viewModel = viewModel,
                    number = number,
                    label = label,
                    intelResult = selectedNumberIntel,
                    isThisNumberIdentifying = foregroundNumber == number,
                    onDismiss = { selectedItem = null },
                    onOpenSettings = { showSettings = true },
                    onIdentify = {
                        viewModel.performInvestigation(number)
                    }
                )
            }

            // Show Settings/Actions Menu (opened from Details Sheet)
            if (showSettings && selectedItem != null && !selectedItem!!.third) {
                val (number, label, _) = selectedItem!!
                NumberActionMenu(
                    number = number,
                    label = label,
                    onDismiss = { showSettings = false },
                    onIdentify = {
                        viewModel.performInvestigation(number)
                    },
                    onAddToWhitelist = { l -> viewModel.addToWhitelist(number, l) },
                    onAddToBlacklist = { l -> viewModel.addToBlacklist(number, l) },
                    onRemoveFromList = { 
                        whitelist.find { it.number == number }?.let { viewModel.removeFromWhitelist(it) }
                    },
                    onEditLabel = { 
                        showSettings = false
                        selectedItem = Triple(number, label, true) 
                    },
                    onAddToContacts = { launchAddContactIntent(context, number) },
                    onCall = { launchCallIntent(context, number) }
                )
            }

            // Edit Label dialog (separate from settings)
            if (selectedItem?.third == true) {
                val (number, _, _) = selectedItem!!
                EditNumberDetailsDialog(
                    number = number,
                    initialIntel = selectedNumberIntel,
                    onDismiss = { selectedItem = null },
                    onConfirm = { oldNum, updatedIntel ->
                        viewModel.updateFullNumberDetails(oldNum, updatedIntel, null)
                        selectedItem = null
                    }
                )
            }
        }
    }
}

@Composable
fun EditLabelDialog(initialLabel: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initialLabel) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Label") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(text) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun launchAddContactIntent(context: Context, number: String) {
    val intent = Intent(Intent.ACTION_INSERT).apply {
        type = ContactsContract.Contacts.CONTENT_TYPE
        putExtra(ContactsContract.Intents.Insert.PHONE, number)
    }
    context.startActivity(intent)
}

private fun launchCallIntent(context: Context, number: String) {
    val intent = Intent(Intent.ACTION_DIAL).apply {
        data = Uri.parse("tel:$number")
    }
    context.startActivity(intent)
}