package com.xconflictionx.callguardshield.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: MainViewModel) {
    val logs by viewModel.groupedCallLogs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedNumberIntel by viewModel.selectedNumberIntel.collectAsState()
    val foregroundNumber by viewModel.foregroundNumber.collectAsState()
    val context = LocalContext.current
    
    var selectedIndex by remember { mutableIntStateOf(-1) }
    var showSettings by remember { mutableStateOf(false) }
    var showDetailsEditor by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var isScannerMode by remember { mutableStateOf(false) }

    val selectedEntry = if (selectedIndex in logs.indices) logs[selectedIndex] else null

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Call History") },
                    actions = {
                        if (logs.isNotEmpty() || searchQuery.isNotEmpty()) {
                            IconButton(onClick = { showClearDialog = true }) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All")
                            }
                        }
                    }
                )
                UniversalSearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.updateSearchQuery(it) }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (logs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No results found." else "No calls recorded yet.",
                        color = Color.Gray
                    )
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(logs) { index, entry ->
                    NumberItemCard(
                        headline = entry.headline,
                        subhead = if (entry.headline != entry.number) entry.number else null,
                        ownerName = entry.intel?.ownerName,
                        companyName = entry.intel?.companyName,
                        timestamp = entry.lastTimestamp,
                        isBlocked = entry.isBlocked,
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

            if (showClearDialog) {
                AlertDialog(
                    onDismissRequest = { showClearDialog = false },
                    title = { Text("Clear History") },
                    text = { Text("Are you sure you want to delete all call logs? This cannot be undone.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.clearHistory()
                                showClearDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Clear All")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Show Details Sheet (Primary View or Scanner View)
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
                            viewModel.fetchIntelForNumber(logs[selectedIndex].number)
                        }
                    } else null,
                    onNavigateNext = if (selectedIndex < logs.size - 1) {
                        {
                            selectedIndex++
                            isScannerMode = false
                            viewModel.fetchIntelForNumber(logs[selectedIndex].number)
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
                    onAddToWhitelist = { viewModel.addToWhitelist(selectedEntry.number, it) },
                    onAddToBlacklist = { viewModel.addToBlacklist(selectedEntry.number, it) },
                    onRemoveFromList = { 
                        viewModel.deleteNumberFromHistory(selectedEntry.number)
                        selectedIndex = -1
                    },
                    removeLabel = "Remove Entry",
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
