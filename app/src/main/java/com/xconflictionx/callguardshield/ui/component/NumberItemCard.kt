package com.xconflictionx.callguardshield.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NumberItemCard(
    headline: String,
    subhead: String?,
    ownerName: String? = null,
    companyName: String? = null,
    callerId: String? = null,
    timestamp: Long? = null,
    isBlocked: Boolean,
    isContact: Boolean = false,
    callerInfo: String? = null,
    reason: String? = null,
    onClick: () -> Unit,
    actionSlot: @Composable (() -> Unit)? = null
) {
    val locale = LocalConfiguration.current.locales[0]
    val date = timestamp?.takeIf { it > 0 }?.let { SimpleDateFormat("MMM dd, HH:mm", locale).format(Date(it)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isBlocked) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f) 
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
                        color = if (subhead == null || headline == subhead) MaterialTheme.colorScheme.onSurface 
                                else MaterialTheme.colorScheme.primary
                    )
                    
                    if (subhead != null && headline != subhead) {
                        Text(
                            text = subhead,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }

                    // Details
                    if (ownerName != null || companyName != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        if (ownerName != null) {
                            Text(
                                text = "Name: $ownerName",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (companyName != null) {
                            Text(
                                text = "Business: $companyName",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (isContact) {
                        Text(
                            text = "Verified Contact",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!callerId.isNullOrBlank() && callerId != subhead) {
                        Text(
                            text = "Carrier ID: $callerId",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }

                    if (date != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = date,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isBlocked) {
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
            
            if (!callerInfo.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = callerInfo,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isBlocked && !reason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Reason: $reason",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (actionSlot != null) {
                actionSlot()
            }
        }
    }
}
