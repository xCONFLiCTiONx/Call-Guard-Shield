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
            
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("System Optimization", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Ensure the app remains active in the background for real-time protection.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (isIgnoringBattery) "Unrestricted" else "Optimized (Restricted)",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isIgnoringBattery) Color.Green else Color.Yellow
                            )
                            Text(
                                if (isIgnoringBattery) "App is running at full priority." else "System may pause the app to save power.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        
                        if (!isIgnoringBattery) {
                            Button(onClick = { viewModel.requestIgnoreBatteryOptimizations(context) }) {
                                Text("Manage")
                            }
                        } else {
                            IconButton(onClick = { viewModel.refreshBatteryStatus() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh Status")
                            }
                        }
                    }
                }
            }
        }

        item {
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
