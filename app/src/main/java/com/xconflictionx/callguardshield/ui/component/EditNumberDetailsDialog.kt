package com.xconflictionx.callguardshield.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xconflictionx.callguardshield.data.entity.PhoneLookupResult

@Composable
fun EditNumberDetailsDialog(
    number: String,
    initialIntel: PhoneLookupResult?,
    onDismiss: () -> Unit,
    onConfirm: (PhoneLookupResult) -> Unit
) {
    var ownerName by remember { mutableStateOf(initialIntel?.ownerName ?: "") }
    var companyName by remember { mutableStateOf(initialIntel?.companyName ?: "") }
    var category by remember { mutableStateOf(initialIntel?.category ?: "Unknown") }
    var summary by remember { mutableStateOf(initialIntel?.summary ?: "") }
    var confidence by remember { mutableStateOf(initialIntel?.confidence?.toString() ?: "0.8") }
    
    var isSpam by remember { mutableStateOf(initialIntel?.spam ?: false) }
    var isScam by remember { mutableStateOf(initialIntel?.scam ?: false) }
    var isDebt by remember { mutableStateOf(initialIntel?.debtCollector ?: false) }
    var isTelemarketer by remember { mutableStateOf(initialIntel?.telemarketer ?: false) }
    
    var evidence by remember { mutableStateOf(initialIntel?.evidence?.joinToString(", ") ?: "") }
    var sources by remember { mutableStateOf(initialIntel?.sources?.joinToString(", ") ?: "") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Edit Intelligence: $number",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = ownerName,
                        onValueChange = { ownerName = it },
                        label = { Text("Owner Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = companyName,
                        onValueChange = { companyName = it },
                        label = { Text("Company Name") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category (e.g. Marketing, Government)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = confidence,
                        onValueChange = { confidence = it },
                        label = { Text("Confidence (0.0 to 1.0)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text("Risk Flags", style = MaterialTheme.typography.titleSmall)
                    
                    RiskToggle("Spam Alert", isSpam) { isSpam = it }
                    RiskToggle("Scam Alert", isScam) { isScam = it }
                    RiskToggle("Debt Collector", isDebt) { isDebt = it }
                    RiskToggle("Telemarketer", isTelemarketer) { isTelemarketer = it }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    OutlinedTextField(
                        value = summary,
                        onValueChange = { summary = it },
                        label = { Text("Summary / Verification Logic") },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        minLines = 3
                    )

                    OutlinedTextField(
                        value = evidence,
                        onValueChange = { evidence = it },
                        label = { Text("Evidence (Comma separated)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = sources,
                        onValueChange = { sources = it },
                        label = { Text("Sources (URLs/Names, comma separated)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val result = PhoneLookupResult(
                            phoneNumber = number,
                            ownerName = ownerName.ifBlank { null },
                            companyName = companyName.ifBlank { null },
                            category = category.ifBlank { "Unknown" },
                            confidence = confidence.toDoubleOrNull() ?: 0.5,
                            spam = isSpam,
                            scam = isScam,
                            debtCollector = isDebt,
                            telemarketer = isTelemarketer,
                            summary = summary.ifBlank { null },
                            evidence = evidence.split(",").map { it.trim() }.filter { it.isNotBlank() },
                            sources = sources.split(",").map { it.trim() }.filter { it.isNotBlank() },
                            lastVerified = "Manual Edit",
                            lookupDate = System.currentTimeMillis()
                        )
                        onConfirm(result)
                    }) {
                        Text("Save Details")
                    }
                }
            }
        }
    }
}

@Composable
fun RiskToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
