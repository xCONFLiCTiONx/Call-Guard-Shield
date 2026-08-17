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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xconflictionx.callguardshield.ui.MainViewModel
import com.xconflictionx.callguardshield.ui.component.NumberActionMenu
import com.xconflictionx.callguardshield.ui.component.NumberDetailsSheet
import com.xconflictionx.callguardshield.ui.component.EditNumberDetailsDialog
import com.xconflictionx.callguardshield.ui.component.NumberItemCard
import com.xconflictionx.callguardshield.ui.component.UniversalSearchBar
import com.xconflictionx.callguardshield.data.entity.BlacklistEntry
import com.xconflictionx.callguardshield.data.entity.WhitelistEntry

@Composable
fun ListManagementScreen(viewModel: MainViewModel) {
    val isIdentifying by viewModel.isIdentifying.collectAsState()
    val bulkProgress by viewModel.bulkProgress.collectAsState()
    val bulkNumber by viewModel.bulkNumber.collectAsState()
    val foregroundNumber by viewModel.foregroundNumber.collectAsState()
    val selectedNumberIntel by viewModel.selectedNumberIntel.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    
    var tabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Blacklist", "Whitelist")
    val icons = listOf(Icons.Default.Block, Icons.Default.VerifiedUser)
    
    var pendingFileUri by remember { mutableStateOf<Uri?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showPasteDialog by remember { mutableStateOf(false) }
    var showAddSingleDialog by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            pendingFileUri = uri
            showImportDialog = true
        }
    }

    Column {
        PrimaryTabRow(selectedTabIndex = tabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = tabIndex == index,
                    onClick = { tabIndex = index },
                    text = { Text(title) },
                    icon = { Icon(icons[index], contentDescription = null) }
                )
            }
        }

        UniversalSearchBar(
            query = searchQuery,
            onQueryChange = { viewModel.updateSearchQuery(it) }
        )

        if (isIdentifying) {
            Column(modifier = Modifier.fillMaxWidth()) {
                val p = bulkProgress ?: 0f
                LinearProgressIndicator(
                    progress = { p },
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
                    viewModel.importNumbers(pendingFileUri!!) { }
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
            BlacklistTab(viewModel, selectedNumberIntel, foregroundNumber, searchQuery)
        } else {
            WhitelistTab(viewModel, selectedNumberIntel, foregroundNumber, searchQuery)
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
            TextButton(onClick = { onDismiss() }) { Text("Cancel") }
        }
    )
}

@Composable
fun BlacklistTab(
    viewModel: MainViewModel, 
    selectedNumberIntel: com.xconflictionx.callguardshield.data.entity.PhoneLookupResult? = null,
    foregroundNumber: String? = null,
    searchQuery: String = ""
) {
    val blacklistFull by viewModel.blacklistFull.collectAsState()
    var selectedIndex by remember { mutableIntStateOf(-1) }
    var showSettings by remember { mutableStateOf(false) }
    var showDetailsEditor by remember { mutableStateOf(false) }
    var isScannerMode by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val selectedEntry = if (selectedIndex in blacklistFull.indices) blacklistFull[selectedIndex] else null

    Scaffold { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (blacklistFull.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No results found." else "Blacklist is empty.",
                        color = Color.Gray
                    )
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(blacklistFull) { index, entry ->
                    NumberItemCard(
                        headline = entry.headline,
                        subhead = if (entry.headline != entry.number) entry.number else null,
                        ownerName = entry.intel?.ownerName,
                        companyName = entry.intel?.companyName,
                        isBlocked = true,
                        timestamp = entry.lastTimestamp,
                        callerInfo = entry.formattedInfo,
                        onClick = { 
                            selectedIndex = index
                            showSettings = false
                            showDetailsEditor = false
                            isScannerMode = false
                            viewModel.fetchIntelForNumber(entry.number)
                        },
                        actionSlot = {
                            if (entry.count > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Calls: x${entry.count}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    )
                }
            }

            // Show Details Sheet
            if (!showSettings && !showDetailsEditor && selectedEntry != null) {
                NumberDetailsSheet(
                    viewModel = viewModel,
                    number = selectedEntry.number,
                    label = selectedEntry.headline,
                    intelResult = selectedNumberIntel,
                    isThisNumberIdentifying = foregroundNumber == selectedEntry.number,
                    isScannerMode = isScannerMode,
                    onDismiss = { selectedIndex = -1 },
                    onOpenSettings = { showSettings = true },
                    onNavigatePrevious = if (selectedIndex > 0) {
                        {
                            selectedIndex--
                            isScannerMode = false
                            viewModel.fetchIntelForNumber(blacklistFull[selectedIndex].number)
                        }
                    } else null,
                    onNavigateNext = if (selectedIndex < blacklistFull.size - 1) {
                        {
                            selectedIndex++
                            isScannerMode = false
                            viewModel.fetchIntelForNumber(blacklistFull[selectedIndex].number)
                        }
                    } else null
                )
            }

            // Show Settings Menu
            if (showSettings && selectedEntry != null) {
                NumberActionMenu(
                    viewModel = viewModel,
                    number = selectedEntry.number,
                    label = selectedEntry.headline,
                    intelResult = selectedNumberIntel,
                    onDismiss = { showSettings = false },
                    onOpenScanner = { isScannerMode = true },
                    onAddToWhitelist = { l -> viewModel.addToWhitelist(selectedEntry.number, l) },
                    onAddToBlacklist = { l -> viewModel.addToBlacklist(selectedEntry.number, l) },
                    onRemoveFromList = { 
                        if (blacklistFull.size > 1) {
                            val nextIdx = if (selectedIndex < blacklistFull.size - 1) selectedIndex else selectedIndex - 1
                            val nextEntry = if (selectedIndex < blacklistFull.size - 1) blacklistFull[selectedIndex + 1] else blacklistFull[selectedIndex - 1]
                            viewModel.fetchIntelForNumber(nextEntry.number)
                            selectedIndex = nextIdx
                        } else {
                            selectedIndex = -1
                        }
                        viewModel.removeFromBlacklist(BlacklistEntry(selectedEntry.number, selectedEntry.label))
                    },
                    onEditLabel = { 
                        showSettings = false
                        showDetailsEditor = true
                    },
                    onAddToContacts = { launchAddContactIntent(context, selectedEntry.number) },
                    onCall = { launchCallIntent(context, selectedEntry.number) }
                )
            }

            if (showDetailsEditor && selectedEntry != null) {
                EditNumberDetailsDialog(
                    number = selectedEntry.number,
                    initialIntel = selectedNumberIntel,
                    onDismiss = { 
                        showDetailsEditor = false
                        selectedIndex = -1
                    },
                    onConfirm = { oldNum, updatedIntel ->
                        viewModel.updateFullNumberDetails(oldNum, updatedIntel)
                        showDetailsEditor = false
                        selectedIndex = -1
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
    foregroundNumber: String? = null,
    searchQuery: String = ""
) {
    val whitelistFull by viewModel.whitelistFull.collectAsState()
    var selectedIndex by remember { mutableIntStateOf(-1) }
    var showSettings by remember { mutableStateOf(false) }
    var showDetailsEditor by remember { mutableStateOf(false) }
    var isScannerMode by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val selectedEntry = if (selectedIndex in whitelistFull.indices) whitelistFull[selectedIndex] else null

    Scaffold { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (whitelistFull.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No results found." else "Whitelist is empty.",
                        color = Color.Gray
                    )
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(whitelistFull) { index, entry ->
                    NumberItemCard(
                        headline = entry.headline,
                        subhead = if (entry.headline != entry.number) entry.number else null,
                        ownerName = entry.intel?.ownerName,
                        companyName = entry.intel?.companyName,
                        isBlocked = false,
                        timestamp = entry.lastTimestamp,
                        callerInfo = entry.formattedInfo,
                        onClick = { 
                            selectedIndex = index
                            showSettings = false
                            showDetailsEditor = false
                            isScannerMode = false
                            viewModel.fetchIntelForNumber(entry.number)
                        },
                        actionSlot = {
                            if (entry.count > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Calls: x${entry.count}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    )
                }
            }

            // Show Details Sheet
            if (!showSettings && !showDetailsEditor && selectedEntry != null) {
                NumberDetailsSheet(
                    viewModel = viewModel,
                    number = selectedEntry.number,
                    label = selectedEntry.headline,
                    intelResult = selectedNumberIntel,
                    isThisNumberIdentifying = foregroundNumber == selectedEntry.number,
                    isScannerMode = isScannerMode,
                    onDismiss = { selectedIndex = -1 },
                    onOpenSettings = { showSettings = true },
                    onNavigatePrevious = if (selectedIndex > 0) {
                        {
                            selectedIndex--
                            isScannerMode = false
                            viewModel.fetchIntelForNumber(whitelistFull[selectedIndex].number)
                        }
                    } else null,
                    onNavigateNext = if (selectedIndex < whitelistFull.size - 1) {
                        {
                            selectedIndex++
                            isScannerMode = false
                            viewModel.fetchIntelForNumber(whitelistFull[selectedIndex].number)
                        }
                    } else null
                )
            }

            // Show Settings Menu
            if (showSettings && selectedEntry != null) {
                NumberActionMenu(
                    viewModel = viewModel,
                    number = selectedEntry.number,
                    label = selectedEntry.headline,
                    intelResult = selectedNumberIntel,
                    onDismiss = { showSettings = false },
                    onOpenScanner = { isScannerMode = true },
                    onAddToWhitelist = { l -> viewModel.addToWhitelist(selectedEntry.number, l) },
                    onAddToBlacklist = { l -> viewModel.addToBlacklist(selectedEntry.number, l) },
                    onRemoveFromList = { 
                        if (whitelistFull.size > 1) {
                            val nextIdx = if (selectedIndex < whitelistFull.size - 1) selectedIndex else selectedIndex - 1
                            val nextEntry = if (selectedIndex < whitelistFull.size - 1) whitelistFull[selectedIndex + 1] else whitelistFull[selectedIndex - 1]
                            viewModel.fetchIntelForNumber(nextEntry.number)
                            selectedIndex = nextIdx
                        } else {
                            selectedIndex = -1
                        }
                        viewModel.removeFromWhitelist(WhitelistEntry(selectedEntry.number, selectedEntry.label))
                    },
                    onEditLabel = { 
                        showSettings = false
                        showDetailsEditor = true
                    },
                    onAddToContacts = { launchAddContactIntent(context, selectedEntry.number) },
                    onCall = { launchCallIntent(context, selectedEntry.number) }
                )
            }

            if (showDetailsEditor && selectedEntry != null) {
                EditNumberDetailsDialog(
                    number = selectedEntry.number,
                    initialIntel = selectedNumberIntel,
                    onDismiss = { 
                        showDetailsEditor = false
                        selectedIndex = -1
                    },
                    onConfirm = { oldNum, updatedIntel ->
                        viewModel.updateFullNumberDetails(oldNum, updatedIntel)
                        showDetailsEditor = false
                        selectedIndex = -1
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
        onDismissRequest = { onDismiss() },
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
            TextButton(onClick = { onDismiss() }) { Text("Cancel") }
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
