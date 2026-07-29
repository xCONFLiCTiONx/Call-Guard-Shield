package com.xconflictionx.callguardshield.ui.screen

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.xconflictionx.callguardshield.ui.theme.CallGuardShieldTheme

@Composable
fun PermissionScreen(
    onRequestRole: () -> Unit,
    onContinue: () -> Unit
) {
    CallGuardShieldTheme(darkTheme = true, dynamicColor = false) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val scrollState = rememberScrollState()
        
        // State to track all permissions
        var contactsGranted by remember { mutableStateOf(hasPermission(context, android.Manifest.permission.READ_CONTACTS)) }
        
        var locationFineGranted by remember { mutableStateOf(hasPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)) }
        var locationBackgroundGranted by remember { 
            mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) hasPermission(context, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) else true) 
        }
        
        var phoneStateGranted by remember { mutableStateOf(hasPermission(context, android.Manifest.permission.READ_PHONE_STATE)) }
        var notificationsGranted by remember { mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) hasPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) else true) }
        var roleGranted by remember { mutableStateOf(checkCallScreeningRole(context)) }
        
        var roleAvailable by remember { mutableStateOf(true) }
        
        // Helper to launch app settings
        val openSettings = {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }

        // Re-check permissions when app comes to foreground
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    contactsGranted = hasPermission(context, android.Manifest.permission.READ_CONTACTS)
                    locationFineGranted = hasPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
                    locationBackgroundGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) hasPermission(context, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) else true
                    phoneStateGranted = hasPermission(context, android.Manifest.permission.READ_PHONE_STATE)
                    notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) hasPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) else true
                    roleGranted = checkCallScreeningRole(context)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            contactsGranted = result[android.Manifest.permission.READ_CONTACTS] ?: contactsGranted
            locationFineGranted = result[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: locationFineGranted
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                locationBackgroundGranted = result[android.Manifest.permission.ACCESS_BACKGROUND_LOCATION] ?: locationBackgroundGranted
            }
            phoneStateGranted = result[android.Manifest.permission.READ_PHONE_STATE] ?: phoneStateGranted
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationsGranted = result[android.Manifest.permission.POST_NOTIFICATIONS] ?: notificationsGranted
            }
            roleGranted = checkCallScreeningRole(context)
        }

        val basePermissionsGranted = contactsGranted && locationFineGranted && locationBackgroundGranted && phoneStateGranted && notificationsGranted
        val allGranted = basePermissionsGranted && (roleGranted || !roleAvailable)

        // Auto-trigger Call Screening role request when base permissions are granted
        LaunchedEffect(basePermissionsGranted, roleGranted) {
            if (basePermissionsGranted && roleAvailable && !roleGranted) {
                onRequestRole()
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Black,
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .navigationBarsPadding()
                ) {
                    if (!allGranted) {
                        Button(
                            onClick = {
                                val permissionsList = mutableListOf(
                                    android.Manifest.permission.READ_CONTACTS,
                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.READ_PHONE_STATE,
                                    android.Manifest.permission.READ_CALL_LOG,
                                    android.Manifest.permission.ANSWER_PHONE_CALLS,
                                    android.Manifest.permission.READ_PHONE_NUMBERS,
                                    android.Manifest.permission.CALL_PHONE
                                )
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionsList.add(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                                launcher.launch(permissionsList.toTypedArray())
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("Grant Core Permissions")
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Button(
                        onClick = onContinue,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (allGranted) Color(0xFF224422) else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (allGranted) Color.Green else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(if (allGranted) "CONTINUE TO APP" else "CONTINUE WITHOUT PERMISSIONS")
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    "Firewall Setup",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111122))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("System Diagnosis", fontWeight = FontWeight.Bold, color = Color.Cyan)
                        Text("Role Available: ${if (roleAvailable) "YES" else "NO"}", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                        Text("Silent Block Active: ${if (roleGranted) "YES" else "NO"}", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                    }
                }

                PermissionItem(
                    title = "Contacts", 
                    description = "Identify your friends.", 
                    granted = contactsGranted
                ) { 
                    if (contactsGranted) openSettings()
                    else launcher.launch(arrayOf(android.Manifest.permission.READ_CONTACTS))
                }

                PermissionItem(
                    title = "Phone", 
                    description = "Detect and manage calls.", 
                    granted = phoneStateGranted
                ) { 
                    if (phoneStateGranted) openSettings()
                    else launcher.launch(arrayOf(android.Manifest.permission.READ_PHONE_STATE, android.Manifest.permission.CALL_PHONE))
                }

                PermissionItem(
                    title = "Notifications", 
                    description = "Real-time alerts.", 
                    granted = notificationsGranted
                ) { 
                    if (notificationsGranted) openSettings()
                    else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        launcher.launch(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS))
                    }
                }

                val locationStatus = when {
                    locationFineGranted && locationBackgroundGranted -> true // Green
                    locationFineGranted -> false // Trigger Yellow warning
                    else -> false // Trigger Red denied
                }
                
                PermissionItem(
                    title = "Location", 
                    description = "Required for regional blocking.", 
                    granted = locationStatus,
                    warning = locationFineGranted && !locationBackgroundGranted
                ) { 
                    if (locationFineGranted && !locationBackgroundGranted) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            launcher.launch(arrayOf(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION))
                        }
                    } else if (locationStatus) {
                        openSettings()
                    } else {
                        launcher.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION))
                    }
                }
                
                if (roleAvailable) {
                    PermissionItem(
                        title = "Silent Blocking", 
                        description = "Enable native AI call screening.", 
                        granted = roleGranted
                    ) { if (basePermissionsGranted) onRequestRole() else launcher.launch(arrayOf(android.Manifest.permission.READ_PHONE_STATE)) }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun PermissionItem(
    title: String, 
    description: String, 
    granted: Boolean, 
    warning: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = when {
                granted -> Color(0xFF112211)
                warning -> Color(0xFF222211)
                else -> Color(0xFF221111)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (granted) Color.Green else if (warning) Color.Yellow else Color.Red
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, color = if (granted) Color.Green else if (warning) Color.Yellow else Color.Red)
                Text(description, style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
            }
        }
    }
}

fun checkCallScreeningRole(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        return roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
    }
    return true
}

fun hasPermission(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

fun isNotificationListenerEnabled(context: Context): Boolean {
    val pkgName = context.packageName
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat?.contains(pkgName) == true
}

fun checkAllPermissions(context: Context): Boolean {
    val contacts = hasPermission(context, android.Manifest.permission.READ_CONTACTS)
    val phone = hasPermission(context, android.Manifest.permission.READ_PHONE_STATE)
    val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        hasPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
    } else true
    
    val locationFine = hasPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
    val locationBg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        hasPermission(context, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    } else true
    
    return contacts && phone && notifications && locationFine && locationBg && checkCallScreeningRole(context)
}
