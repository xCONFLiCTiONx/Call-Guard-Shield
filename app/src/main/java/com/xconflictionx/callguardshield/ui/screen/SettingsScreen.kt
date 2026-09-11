package com.xconflictionx.callguardshield.ui.screen

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.xconflictionx.callguardshield.data.repository.AppTheme
import com.xconflictionx.callguardshield.logic.CryptoManager
import com.xconflictionx.callguardshield.ui.LogLevel
import com.xconflictionx.callguardshield.ui.MainViewModel
import com.xconflictionx.callguardshield.ui.component.SecurityStatusSheet
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

private val ActionBlue = Color(0xFF1565C0)

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateToPrivacy: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val apiKeyStatus by viewModel.apiKeyStatus.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val driveError by viewModel.driveError.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var showSecurityStatus by remember { mutableStateOf(false) }
    var exportData by remember { mutableStateOf("") }
    var isConnectingGoogle by remember { mutableStateOf(false) }

    if (showSecurityStatus) {
        SecurityStatusSheet(
            viewModel = viewModel,
            onDismiss = { showSecurityStatus = false }
        )
    }

    val fileSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openFileDescriptor(it, "wt")?.use { descriptor ->
                    FileOutputStream(descriptor.fileDescriptor).use { stream ->
                        stream.write(exportData.toByteArray())
                    }
                }
                Toast.makeText(context, "Backup successfully saved!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error saving backup: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.importNumbers(it) {
                Toast.makeText(context, "Restore complete!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isConnectingGoogle = false
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                account?.let {
                    viewModel.connectGoogleAccount(it.email ?: "Unknown Account")
                    viewModel.clearDriveError()
                    Toast.makeText(context, "Connected as ${it.email}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: ApiException) {
                viewModel.logToConsole("GOOGLE", "Sign-in failed: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshBatteryStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Status and Outlook ---
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (settings.isPaused) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                                    else Color(0xFF1B5E20).copy(alpha = 0.2f)
                ),
                onClick = { viewModel.togglePause() }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (settings.isPaused) "Status: PAUSED" else "Status: ACTIVE",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (settings.isPaused) MaterialTheme.colorScheme.error else Color.Green
                        )
                        Text(
                            text = if (settings.isPaused) "Shields are currently down." else "Firewall is protecting you.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = !settings.isPaused,
                        onCheckedChange = { viewModel.togglePause() }
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                onClick = { showSecurityStatus = true }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Protection Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("View detailed firewall metrics and outlook.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }
        }

        // --- GOOGLE SYNC ---
        item {
            val isDriveConnected by viewModel.isGoogleDriveConnected.collectAsState()
            val isBackupActive by viewModel.isBackupActive.collectAsState()
            
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("☁️ Sync to Google Account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text("Securely backup your settings and history to your personal cloud storage.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (driveError == "API_DISABLED") {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.padding(bottom = 12.dp)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("ACTION REQUIRED", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                Text("Google Drive API is disabled in your cloud project. This is why sync is failing.", style = MaterialTheme.typography.bodySmall)
                                Button(
                                    onClick = { 
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://console.developers.google.com/apis/api/drive.googleapis.com/overview?project=38581640934"))
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Enable Drive API Now")
                                }
                            }
                        }
                    }
                    
                    if (isDriveConnected) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CloudDone, contentDescription = null, tint = Color.Green, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "Connected", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.Green)
                                    Text(text = settings.googleAccountEmail ?: "Active Account", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                                }
                                TextButton(
                                    onClick = { 
                                        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                                        val client = GoogleSignIn.getClient(context, gso)
                                        client.signOut().addOnCompleteListener { viewModel.disconnectGoogleDrive() }
                                    }
                                ) {
                                    Text("Sign Out", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.triggerAutoBackup() }, 
                                modifier = Modifier.weight(1f), 
                                colors = ButtonDefaults.buttonColors(containerColor = ActionBlue, contentColor = Color.White)
                            ) {
                                Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sync Now", style = MaterialTheme.typography.labelSmall)
                            }
                            Button(
                                onClick = { viewModel.triggerCloudRestore() }, 
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = ActionBlue, contentColor = Color.White)
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Restore", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (isBackupActive) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp).padding(top = 4.dp), color = Color.Green, trackColor = Color.Transparent)
                    } else {
                        Button(
                            onClick = { 
                                isConnectingGoogle = true
                                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail().requestScopes(Scope("https://www.googleapis.com/auth/drive.file")).build()
                                val client = GoogleSignIn.getClient(context, gso)
                                client.signOut().addOnCompleteListener { googleSignInLauncher.launch(client.signInIntent) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isConnectingGoogle,
                            colors = ButtonDefaults.buttonColors(containerColor = ActionBlue, contentColor = Color.White)
                        ) {
                            if (isConnectingGoogle) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp)); Text("Connecting...")
                            } else Text("Sign in with Google")
                        }
                    }
                }
            }
        }

        // --- THEME SELECTOR ---
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("App Theme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    
                    var expanded by remember { mutableStateOf(false) }
                    val currentTheme = settings.theme

                    Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        OutlinedTextField(
                            value = currentTheme.name.lowercase().replaceFirstChar { it.uppercase() },
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
                            AppTheme.entries.forEach { theme ->
                                DropdownMenuItem(
                                    text = { Text(theme.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                    onClick = {
                                        viewModel.updateTheme(theme)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 1. Security Zones
        item {
            Text("Security Zones", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🛡️ Call Protection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    SettingToggle(
                        title = "Enable Whitelist",
                        description = "Enforce 'Always Allow' rules from your custom manual whitelist.",
                        checked = settings.whitelistEnabled,
                        onCheckedChange = { viewModel.updateWhitelistEnabled(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.1f))

                    SettingToggle(
                        title = "Allow Only from Contacts",
                        description = "Strict mode: Only people in your address book can reach you.",
                        checked = settings.allowOnlyContacts,
                        onCheckedChange = { viewModel.updateSetting(allowOnlyContacts = it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.1f))

                    SettingToggle(
                        title = "Enable Blacklist",
                        description = "Enforce blocking rules from your custom manual blacklist.",
                        checked = settings.blacklistEnabled,
                        onCheckedChange = { viewModel.updateBlacklistEnabled(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.1f))
                    
                    SettingToggle(
                        title = "Block Unknown ID",
                        description = "Reject calls with hidden or restricted numbers.",
                        checked = settings.blockUnknown,
                        onCheckedChange = { viewModel.updateSetting(blockUnknown = it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.1f))

                    SettingToggle(
                        title = "Block International Calls",
                        description = "Reject all incoming international numbers.",
                        checked = settings.blockInternational,
                        onCheckedChange = { viewModel.updateSetting(blockInternational = it) }
                    )
                }
            }
        }

        // 2. Global Spam Database
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val isGlobalEnabled = settings.enabledDictionaries.contains("global")
                    SettingToggle(
                        title = "Global Spam Database",
                        description = "Automatically block 2,000+ known spam numbers from FCC & community records.",
                        checked = isGlobalEnabled,
                        onCheckedChange = { viewModel.updateDictionaryEnabled("global", it) }
                    )

                    if (isGlobalEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.2f))
                        val lastSync = settings.lastSyncTime
                        val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                        
                        val lastSyncText = if (lastSync == 0L) "Never synced" else "Last sync: " + dateFormat.format(Date(lastSync))
                        val nextSyncText = if (lastSync == 0L) "Next sync: Pending" 
                                           else "Next sync: " + dateFormat.format(Date(lastSync + 30L * 24 * 60 * 60 * 1000))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(lastSyncText, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                Text(nextSyncText, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            Button(
                                onClick = { viewModel.forceSync() },
                                colors = ButtonDefaults.buttonColors(containerColor = ActionBlue, contentColor = Color.White)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sync Now", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        // 3. System Optimization
        item {
            Text("Background Protection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        item {
            val isIgnoringBattery by viewModel.isIgnoringBatteryOptimizations.collectAsState()
            
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("System Optimization", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Ensure the app remains active and has necessary data for real-time protection.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Battery: " + if (isIgnoringBattery) "Unrestricted" else "Optimized (Restricted)", style = MaterialTheme.typography.bodyLarge, color = if (isIgnoringBattery) Color.Green else Color.Yellow)
                            Text("Prevents system from killing the firewall service.", style = MaterialTheme.typography.bodySmall)
                        }
                        if (!isIgnoringBattery) {
                            Button(
                                onClick = { viewModel.requestIgnoreBatteryOptimizations(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = ActionBlue, contentColor = Color.White)
                            ) { 
                                Text("Manage") 
                            }
                        }
                    }
                }
            }
        }

        // 5. Gemini Intel Engine
        item {
            Text("Gemini Intel Engine", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }

        item {
            var tempKey by remember(apiKeyStatus) { mutableStateOf(CryptoManager.getGeminiApiKey(context) ?: "") }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Gemini API Key", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text("Required for caller investigation and identification.", style = MaterialTheme.typography.bodySmall)
                    
                    OutlinedTextField(value = tempKey, onValueChange = { tempKey = it }, label = { Text("API Key") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.saveGeminiKey(tempKey.trim()) }, 
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = ActionBlue, contentColor = Color.White)
                        ) { 
                            Text("Save") 
                        }
                        Button(
                            onClick = { viewModel.testGeminiKey() }, 
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = ActionBlue, contentColor = Color.White)
                        ) { 
                            Text("Test Key") 
                        }
                    }
                    if (apiKeyStatus == "Connected") {
                        TextButton(onClick = { viewModel.clearGeminiKey() }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Clear Saved Key", color = MaterialTheme.colorScheme.error) }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.Gray.copy(alpha = 0.1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Status: ", style = MaterialTheme.typography.bodySmall)
                        Text(apiKeyStatus, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = if (apiKeyStatus == "Connected") Color.Green else if (apiKeyStatus == "Testing...") Color.Yellow else if (apiKeyStatus == "Missing API Key") Color.Gray else Color.Red)
                    }
                }
            }
        }

        if (apiKeyStatus == "Connected") {
            item { Text("AI Protection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingToggle(title = "Real-Time Gemini Filtering", description = "Verify unknown callers with AI before the phone rings. (Requires internet)", checked = settings.aiRealTimeBlocking, onCheckedChange = { viewModel.updateAiRealTimeBlocking(it) })
                        if (settings.aiRealTimeBlocking) {
                            Column(modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp)) {
                                Text(text = "Blocking Accuracy: ${settings.aiBlockingAccuracy}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(text = "Only block if Gemini is at least this sure about the result.", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                Slider(value = settings.aiBlockingAccuracy.toFloat(), onValueChange = { val snapped = ((it / 10f).roundToInt() * 10); viewModel.updateAiBlockingAccuracy(snapped) }, valueRange = 0f..100f, steps = 9, modifier = Modifier.padding(top = 4.dp))
                                val (warningText, warningColor) = when {
                                    settings.aiBlockingAccuracy >= 50 -> "Optimal" to Color.Green
                                    settings.aiBlockingAccuracy >= 30 -> "Lower accuracy can give unexpected results." to Color(0xFFAAFF88)
                                    settings.aiBlockingAccuracy >= 20 -> "Lower accuracy can give unexpected results." to Color.Yellow
                                    else -> "Lower accuracy can give unexpected results." to Color.Red
                                }
                                Text(text = warningText, style = MaterialTheme.typography.labelSmall, color = warningColor, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.2f))
                            SettingToggle(title = "Block Debt Collectors", description = "Auto-reject calls identified as debt collection services.", checked = settings.blockDebtCollectors, onCheckedChange = { viewModel.updateBlockDebtCollectors(it) })
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.2f))
                            SettingToggle(title = "Block Telemarketers", description = "Auto-reject verified marketing and sales calls.", checked = settings.blockTelemarketers, onCheckedChange = { viewModel.updateBlockTelemarketers(it) })
                        }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Model Selection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        var expanded by remember { mutableStateOf(false) }
                        val currentModel = settings.selectedGeminiModel
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            OutlinedTextField(value = currentModel, onValueChange = { }, readOnly = true, modifier = Modifier.fillMaxWidth(), trailingIcon = { IconButton(onClick = { expanded = true }) { Icon(Icons.Default.ArrowDropDown, contentDescription = null) } })
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.fillMaxWidth(0.8f)) {
                                availableModels.forEach { model -> DropdownMenuItem(text = { Text(model) }, onClick = { viewModel.updateSelectedModel(model); expanded = false }) }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Refresh models to see latest available.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            IconButton(onClick = { viewModel.refreshGeminiModels() }) { Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(20.dp)) }
                        }
                    }
                }
            }
        }

        // 5. Data Management
        item {
            Text("Data Management", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Manual Backup & Restore", style = MaterialTheme.typography.bodyLarge)
                    Text("Save to a .bak file on your device storage.", style = MaterialTheme.typography.bodySmall)
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.getFullBackupData { data -> exportData = data; fileSaver.launch("Call_Guard_Shield.bak") } }, 
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = ActionBlue, contentColor = Color.White)
                        ) { 
                            Text("Create Backup") 
                        }
                        Button(
                            onClick = { filePicker.launch("*/*") }, 
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = ActionBlue, contentColor = Color.White)
                        ) { 
                            Text("Restore") 
                        }
                    }
                }
            }
        }

        // 7. Technical Console
        item { Text("Technical Console", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Backend Activity", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Debug", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Switch(
                                checked = settings.debugEnabled,
                                onCheckedChange = { viewModel.updateDebugEnabled(it) },
                                modifier = Modifier.scale(0.6f).padding(horizontal = 4.dp)
                            )
                            TextButton(onClick = { 
                                val text = consoleLogs.joinToString("\n") { "[${it.formattedTime}] ${it.tag}: ${it.message}" }
                                if (text.isNotBlank()) { clipboard.setText(AnnotatedString(text)); Toast.makeText(context, "Logs copied", Toast.LENGTH_SHORT).show() }
                            }) { Text("Copy All", style = MaterialTheme.typography.labelSmall) }
                            TextButton(onClick = { viewModel.clearConsole() }) { Text("Clear", style = MaterialTheme.typography.labelSmall, color = Color.Red) }
                        }
                    }
                    Box(modifier = Modifier.height(200.dp).fillMaxWidth()) {
                        if (consoleLogs.isEmpty()) {
                            Text("No activity recorded.", modifier = Modifier.align(Alignment.Center), style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
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
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Cache Management", style = MaterialTheme.typography.bodyLarge)
                    Text("Local caller identification results (30 days).", style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = { viewModel.clearLookupCache() }, 
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp), 
                        colors = ButtonDefaults.buttonColors(containerColor = ActionBlue, contentColor = Color.White)
                    ) { 
                        Text("Clear Lookup Cache") 
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TextButton(onClick = onNavigateToPrivacy) {
                    Text("Privacy Policy", color = ActionBlue)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Call Guard Shield", style = MaterialTheme.typography.titleSmall)
                Text("Version 1.0", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
        item { Spacer(modifier = Modifier.height(100.dp)) }
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
