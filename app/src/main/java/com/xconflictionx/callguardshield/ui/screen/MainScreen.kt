package com.xconflictionx.callguardshield.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xconflictionx.callguardshield.ui.MainViewModel
import com.xconflictionx.callguardshield.ui.component.SecurityStatusSheet
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MainScreen(viewModel: MainViewModel, onNavigateToHistory: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val groupedLogs by viewModel.groupedCallLogs.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val blacklist by viewModel.blacklistFull.collectAsState()
    val whitelist by viewModel.whitelistFull.collectAsState()
    val globalSpamCount by viewModel.globalSpamCount.collectAsState()
    
    var showSecuritySheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val isPaused = settings.isPaused
        
        Text(
            text = "Firewall Status",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Surface(
            modifier = Modifier.size(200.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = if (isPaused) Color(0xFF442222) else Color(0xFF224422),
            onClick = { viewModel.togglePause() },
            shadowElevation = 8.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isPaused) "PAUSED" else "ACTIVE",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isPaused) Color.Red else Color.Green
                    )
                    Text(
                        text = if (isPaused) "Tap to resume" else "Tap to pause",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        
        // Firewall Activity Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            onClick = onNavigateToHistory
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val totalBlocked = groupedLogs.filter { it.isBlocked }.sumOf { it.count }
                    Text("Firewall Activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("$totalBlocked threats neutralized", style = MaterialTheme.typography.bodyMedium)
                }
                Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }

        // Protection Strength Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            onClick = { showSecuritySheet = true }
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val totalRules = blacklist.size + whitelist.size + globalSpamCount
                    val lastSync = settings.lastSyncTime
                    val locale = LocalConfiguration.current.locales[0]
                    val syncText = if (lastSync > 0) {
                        "Protection is up-to-date. (Last check: " + SimpleDateFormat("MMM dd, HH:mm", locale).format(Date(lastSync)) + ")"
                    } else "Auto-sync pending..."

                    Text("Protection Strength", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("$totalRules active filter rules", style = MaterialTheme.typography.bodyMedium)
                    Text(syncText, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                Icon(Icons.Default.Shield, contentDescription = null, tint = Color.Cyan)
            }
        }
        
        if (showSecuritySheet) {
            SecurityStatusSheet(
                viewModel = viewModel,
                onDismiss = { showSecuritySheet = false }
            )
        }

        if (isSyncing) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = "Syncing with global spam databases...",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
