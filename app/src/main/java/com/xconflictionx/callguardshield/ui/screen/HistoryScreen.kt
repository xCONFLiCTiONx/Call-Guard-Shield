package com.xconflictionx.callguardshield.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xconflictionx.callguardshield.data.entity.CallLogEntry
import com.xconflictionx.callguardshield.ui.MainViewModel
import com.xconflictionx.callguardshield.ui.component.NumberActionMenu
import com.xconflictionx.callguardshield.ui.component.NumberDetailsSheet
import com.xconflictionx.callguardshield.ui.component.EditNumberDetailsDialog
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: MainViewModel, onNavigateToChat: () -> Unit) {
    val logs by viewModel.callLogs.collectAsState()
    val selectedNumberIntel by viewModel.selectedNumberIntel.collectAsState()
    val foregroundNumber by viewModel.foregroundNumber.collectAsState()
    val context = LocalContext.current
    var selectedItem by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showDetailsEditor by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Call History") },
                actions = {
                    if (logs.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (logs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No calls recorded yet.", color = Color.Gray)
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(logs) { log ->
                    CallLogItem(
                        log = log,
                        onClick = { 
                            selectedItem = log.number to (log.callerName ?: log.callerId ?: "Unknown")
                            showSettings = false
                            // Load the full Gemini intel for this number
                            viewModel.fetchIntelForNumber(log.number)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = Color.Gray.copy(alpha = 0.2f))
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

            // Show Details Sheet first (primary view)
            if (!showSettings && selectedItem != null) {
                val (number, label) = selectedItem!!
                NumberDetailsSheet(
                    number = number,
                    label = label,
                    intelResult = selectedNumberIntel,
                    isThisNumberIdentifying = foregroundNumber == number,
                    onDismiss = { selectedItem = null },
                    onOpenSettings = { showSettings = true },
                    onIdentify = {
                        viewModel.performInvestigation(number)
                        onNavigateToChat()
                    }
                )
            }

            // Show Settings/Actions Menu (opened from Details Sheet)
            if (showSettings && selectedItem != null) {
                val (number, label) = selectedItem!!
                val logEntry = logs.find { it.number == number }
                NumberActionMenu(
                    number = number,
                    label = label,
                    onDismiss = { showSettings = false },
                    onIdentify = {
                        viewModel.setAutoQuery(number, label)
                        onNavigateToChat()
                    },
                    onAddToWhitelist = { viewModel.addToWhitelist(number, it) },
                    onAddToBlacklist = { viewModel.addToBlacklist(number, it) },
                    onRemoveFromList = { 
                        logEntry?.let { viewModel.deleteCallLogEntry(it) }
                    },
                    removeLabel = "Delete from History",
                    onEditLabel = {
                        showSettings = false
                        showDetailsEditor = true
                    },
                    onAddToContacts = { launchAddContactIntent(context, number) },
                    onCall = { launchCallIntent(context, number) }
                )
            }

            if (showDetailsEditor && selectedItem != null) {
                val (number, _) = selectedItem!!
                EditNumberDetailsDialog(
                    number = number,
                    initialIntel = selectedNumberIntel,
                    onDismiss = { 
                        showDetailsEditor = false
                        selectedItem = null
                    },
                    onConfirm = { oldNum, updatedIntel ->
                        viewModel.updateFullNumberDetails(oldNum, updatedIntel, null)
                        showDetailsEditor = false
                        selectedItem = null
                    }
                )
            }
        }
    }
}

@Composable
fun CallLogItem(
    log: CallLogEntry,
    onClick: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    val date = SimpleDateFormat("MMM dd, HH:mm", locale).format(Date(log.timestamp))
    
    val displayName = log.callerName ?: if (log.isContact) "Verified Contact" else null
    val headline = log.companyName ?: log.ownerName ?: displayName ?: log.number
    val showNumberInSub = headline != log.number

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (log.isBlocked) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f) 
                            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (headline == log.number) MaterialTheme.colorScheme.onSurface 
                                else MaterialTheme.colorScheme.primary
                    )
                    
                    if (showNumberInSub) {
                        Text(
                            text = log.number,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }

                    // Multi-line Identity details
                    if (log.ownerName != null || log.companyName != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        if (log.ownerName != null) {
                            Text(
                                text = "Name: ${log.ownerName}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (log.companyName != null) {
                            Text(
                                text = "Business: ${log.companyName}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (log.isContact) {
                        Text(
                            text = "Verified Contact",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Carrier ID
                    if (!log.callerId.isNullOrBlank() && log.callerId != log.number) {
                        Text(
                            text = "Carrier ID: ${log.callerId}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = date,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (log.isBlocked) {
                            Surface(
                                color = MaterialTheme.colorScheme.error,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "BLOCKED",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onError,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Surface(
                                color = Color.Green.copy(alpha = 0.2f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "ALLOWED",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Green,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = Color.Gray)
                    }
                }
            }
            
            // Technical Intel Footer (Risk/Accuracy)
            if (!log.callerInfo.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = log.callerInfo,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (log.isBlocked && !log.reason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Reason: ${log.reason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
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