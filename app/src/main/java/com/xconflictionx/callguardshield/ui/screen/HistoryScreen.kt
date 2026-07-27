package com.xconflictionx.callguardshield.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
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
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: MainViewModel, onNavigateToChat: () -> Unit) {
    val logs by viewModel.callLogs.collectAsState()
    val context = LocalContext.current
    var selectedItem by remember { mutableStateOf<Pair<String, String?>?>(null) }
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
                            // Pass the best identity name to the popup menu
                            selectedItem = log.number to (log.callerName ?: log.callerId ?: "Unknown") 
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

            selectedItem?.let { (number, label) ->
                val logEntry = logs.find { it.number == number }
                NumberActionMenu(
                    number = number,
                    label = label,
                    onDismiss = { selectedItem = null },
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
                    onAddToContacts = { launchAddContactIntent(context, number) },
                    onCall = { launchCallIntent(context, number) }
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
                Column {
                    Text(
                        text = log.number,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    // Display system-provided Caller ID
                    if (!log.callerId.isNullOrBlank()) {
                        Text(
                            text = "Carrier ID: ${log.callerId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }

                    // Display AI or Contact verified identity
                    val verifiedName = log.callerName ?: if (log.isContact) "Verified Contact" else null
                    if (!verifiedName.isNullOrBlank()) {
                        Text(
                            text = "Identity: $verifiedName",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (log.isContact) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "Identity: Unknown",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            fontWeight = FontWeight.Normal
                        )
                    }

                    Text(
                        text = date,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                
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
            
            if (log.isBlocked) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Reason: ${log.reason}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            
            log.callerInfo?.let { 
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f), // Darker surface for better contrast
                    shape = MaterialTheme.shapes.small,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Gemini Intelligence Report",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant // High-contrast text
                        )
                    }
                }
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
