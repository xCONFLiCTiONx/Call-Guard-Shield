package com.xconflictionx.callguardshield.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.items
import com.xconflictionx.callguardshield.ui.ConsoleEntry
import com.xconflictionx.callguardshield.ui.LogLevel
import com.xconflictionx.callguardshield.ui.MainViewModel
import com.xconflictionx.callguardshield.logic.CryptoManager
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsState()
    val apiKeyStatus by viewModel.apiKeyStatus.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var showLocationRationale by remember { mutableStateOf(false) }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshLocationStatus()
        if (!granted && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // On Android 11+, the system might not show a dialog if they already said no once.
            // We guide them to settings as a secondary fallback.
            Toast.makeText(context, "Please enable 'Allow all the time' in settings.", Toast.LENGTH_LONG).show()
            viewModel.requestBackgroundLocation(context)
        }
    }

    // Refresh battery and location status when returning to settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshBatteryStatus()
                viewModel.refreshLocationStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Gemini Intel Engine",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            var tempKey by remember(apiKeyStatus) { 
                mutableStateOf(CryptoManager.getGeminiApiKey(context) ?: "") 
            }
            
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Gemini API Key", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Required for caller investigation and identification.", style = MaterialTheme.typography.bodySmall)
                    
                    OutlinedTextField(
                        value = tempKey,
                        onValueChange = { tempKey = it },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.saveGeminiKey(tempKey) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save")
                        }
                        OutlinedButton(
                            onClick = { viewModel.testGeminiKey(tempKey) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Test Key")
                        }
                    }

                    if (apiKeyStatus == "Connected") {
                        TextButton(
                            onClick = { viewModel.clearGeminiKey() },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("Clear Saved Key", color = MaterialTheme.colorScheme.error)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.Gray.copy(alpha = 0.1f))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Status: ", style = MaterialTheme.typography.bodySmall)
                        Text(
                            apiKeyStatus,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = when (apiKeyStatus) {
                                "Connected" -> Color.Green
                                "Testing..." -> Color.Yellow
                                "Missing API Key" -> Color.Gray
                                else -> Color.Red
                            }
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Model Selection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    
                    var expanded by remember { mutableStateOf(false) }
                    val currentModel = settings?.selectedGeminiModel ?: "gemini-2.5-flash"

                    Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        OutlinedTextField(
                            value = currentModel,
                            onValueChange = { },
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { expanded = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }
                        )
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            availableModels.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model) },
                                    onClick = {
                                        viewModel.updateSelectedModel(model)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Refresh models to see latest available.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        IconButton(onClick = { viewModel.refreshGeminiModels() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Cache Management",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Lookup History Cache", style = MaterialTheme.typography.bodyLarge)
                    Text("Results are stored locally for 30 days to save quota.", style = MaterialTheme.typography.bodySmall)
                    
                    Button(
                        onClick = { viewModel.clearLookupCache() },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                    ) {
                        Text("Clear Lookup Cache")
                    }
                }
            }
        }

        item {
            Text(
                "Background Protection",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            val isIgnoringBattery by viewModel.isIgnoringBatteryOptimizations.collectAsState()
            val isBackgroundLocationGranted by viewModel.backgroundLocationGranted.collectAsState()
            
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("System Optimization", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Ensure the app remains active and has necessary data for real-time protection.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Battery Section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Battery: " + if (isIgnoringBattery) "Unrestricted" else "Optimized (Restricted)",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isIgnoringBattery) Color.Green else Color.Yellow
                            )
                            Text(
                                "Prevents system from killing the firewall service.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        
                        if (!isIgnoringBattery) {
                            Button(onClick = { viewModel.requestIgnoreBatteryOptimizations(context) }) {
                                Text("Manage")
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.Gray.copy(alpha = 0.1f))

                    // Location Section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Location: " + if (isBackgroundLocationGranted) "All the time" else "While in use (Restricted)",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isBackgroundLocationGranted) Color.Green else Color.Yellow
                            )
                            Text(
                                "Required for region-based area code blocking.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        
                        if (!isBackgroundLocationGranted) {
                            Button(onClick = { 
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                    showLocationRationale = true 
                                } else {
                                    viewModel.requestBackgroundLocation(context)
                                }
                            }) {
                                Text("Manage")
                            }
                        }
                    }
                }
            }
        }

        item {
            if (showLocationRationale) {
                AlertDialog(
                    onDismissRequest = { showLocationRationale = false },
                    title = { Text("Location Background Access") },
                    text = { 
                        Text("To block spam based on your region while the app is closed, please select 'Allow all the time' on the next screen.")
                    },
                    confirmButton = {
                        Button(onClick = {
                            showLocationRationale = false
                            backgroundLocationLauncher.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }) {
                            Text("Continue")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLocationRationale = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val isGlobalEnabled = settings?.enabledDictionaries?.contains("global") ?: false
                    SettingToggle(
                        title = "Global Spam Database",
                        description = "Automatically block 2,000+ known spam numbers from FCC & community records.",
                        checked = isGlobalEnabled,
                        onCheckedChange = { viewModel.updateDictionaryEnabled("global", it) }
                    )

                    if (isGlobalEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.2f))
                        
                        val lastSync = settings?.lastSyncTime ?: 0L
                        val syncText = if (lastSync == 0L) "Never synced" else "Last sync: " + SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(lastSync))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(syncText, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            TextButton(onClick = { viewModel.forceSync() }) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sync Now", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Firewall Rules",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingToggle(
                        title = "Block Non-Contacts",
                        description = "Strict mode: Only people you know can reach you.",
                        checked = settings?.blockNonContacts ?: false,
                        onCheckedChange = { viewModel.updateSetting(blockNonContacts = it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.2f))
                    SettingToggle(
                        title = "Block Unknown",
                        description = "Drop calls with hidden or restricted ID.",
                        checked = settings?.blockUnknown ?: false,
                        onCheckedChange = { viewModel.updateSetting(blockUnknown = it) }
                    )
                }
            }
        }

        item {
            Text(
                "Technical Console",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            val consoleLogs by viewModel.consoleLogs.collectAsState()
            val clipboard = LocalClipboardManager.current
            
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Backend Activity", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Row {
                            TextButton(onClick = { 
                                val text = consoleLogs.joinToString("\n") { "[${it.formattedTime}] ${it.tag}: ${it.message}" }
                                if (text.isNotBlank()) {
                                    clipboard.setText(AnnotatedString(text))
                                    Toast.makeText(context, "Logs copied", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Text("Copy All", style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(onClick = { viewModel.clearConsole() }) {
                                Text("Clear", style = MaterialTheme.typography.labelSmall, color = Color.Red)
                            }
                        }
                    }
                    
                    Box(modifier = Modifier.height(200.dp).fillMaxWidth()) {
                        if (consoleLogs.isEmpty()) {
                            Text(
                                "No activity recorded.",
                                modifier = Modifier.align(Alignment.Center),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.DarkGray
                            )
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(consoleLogs) { entry ->
                                    Text(
                                        text = "[${entry.formattedTime}] ${entry.tag}: ${entry.message}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when (entry.level) {
                                            LogLevel.ERROR -> Color(0xFFFF5252)
                                            LogLevel.WARN -> Color(0xFFFFD740)
                                            else -> Color(0xFFB0BEC5)
                                        },
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Call Guard Shield", style = MaterialTheme.typography.titleSmall)
                Text("Version 1.0", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun SettingToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
