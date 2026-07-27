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
        var locationGranted by remember { mutableStateOf(hasPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)) }
        var phoneStateGranted by remember { mutableStateOf(hasPermission(context, android.Manifest.permission.READ_PHONE_STATE)) }
        var smsGranted by remember { mutableStateOf(hasPermission(context, android.Manifest.permission.RECEIVE_SMS)) }
        var roleGranted by remember { mutableStateOf(checkCallScreeningRole(context)) }
        
        var roleAvailable by remember { mutableStateOf(true) }
        
        // Check role availability
        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(RoleManager::class.java)
                roleAvailable = roleManager?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) == true
            }
        }

        // Re-check permissions when app comes to foreground
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    contactsGranted = hasPermission(context, android.Manifest.permission.READ_CONTACTS)
                    locationGranted = hasPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
                    phoneStateGranted = hasPermission(context, android.Manifest.permission.READ_PHONE_STATE)
                    smsGranted = hasPermission(context, android.Manifest.permission.RECEIVE_SMS)
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
            locationGranted = result[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: locationGranted
            phoneStateGranted = result[android.Manifest.permission.READ_PHONE_STATE] ?: phoneStateGranted
            smsGranted = result[android.Manifest.permission.RECEIVE_SMS] ?: smsGranted
            roleGranted = checkCallScreeningRole(context)
        }

        val basePermissionsGranted = contactsGranted && locationGranted && phoneStateGranted && smsGranted
        val allGranted = basePermissionsGranted && (roleGranted || !roleAvailable)

        Scaffold(
            modifier = Modifier.fillMaxSize(),
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
                                launcher.launch(
                                    arrayOf(
                                        android.Manifest.permission.READ_CONTACTS,
                                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                                        android.Manifest.permission.READ_PHONE_STATE,
                                        android.Manifest.permission.RECEIVE_SMS,
                                        android.Manifest.permission.READ_CALL_LOG,
                                        android.Manifest.permission.ANSWER_PHONE_CALLS,
                                        android.Manifest.permission.READ_PHONE_NUMBERS,
                                        android.Manifest.permission.CALL_PHONE
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("Grant Permissions")
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Button(
                            onClick = { onRequestRole() },
                            enabled = basePermissionsGranted && roleAvailable && !roleGranted,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (basePermissionsGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text("Enable Silent Blocking")
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
                        Text("Role Available: ${if (roleAvailable) "YES" else "NO"}", style = MaterialTheme.typography.bodySmall)
                        Text("Role Held: ${if (roleGranted) "YES" else "NO"}", style = MaterialTheme.typography.bodySmall)
                        Text("Samsung Model: S23 FE detected", style = MaterialTheme.typography.bodySmall)
                    }
                }

                PermissionItem("Contacts", "Identify your friends.", contactsGranted) { openAppSettings(context) }
                PermissionItem("Location", "Block out-of-state area codes.", locationGranted) { openAppSettings(context) }
                PermissionItem("Phone State", "Detect incoming calls.", phoneStateGranted) { openAppSettings(context) }
                PermissionItem("SMS", "Filter spam messages.", smsGranted) { openAppSettings(context) }
                
                if (roleAvailable) {
                    PermissionItem(
                        title = "Silent Blocking", 
                        description = "Sets the app as your Spam & Call ID provider.", 
                        granted = roleGranted
                    ) { if (basePermissionsGranted) onRequestRole() else openAppSettings(context) }
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
                imageVector = when {
                    granted -> Icons.Default.CheckCircle
                    else -> Icons.Default.Warning
                },
                contentDescription = null,
                tint = when {
                    granted -> Color.Green
                    warning -> Color.Yellow
                    else -> Color.Red
                }
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                val textColor = when {
                    granted -> Color.Green
                    warning -> Color.Yellow
                    else -> Color.Red
                }
                Text(title, fontWeight = FontWeight.Bold, color = textColor)
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

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    context.startActivity(intent)
}

fun hasPermission(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

fun checkAllPermissions(context: Context): Boolean {
    val roleAvailable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        roleManager?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) == true
    } else false

    val basePermissions = hasPermission(context, android.Manifest.permission.READ_CONTACTS) &&
            hasPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) &&
            hasPermission(context, android.Manifest.permission.READ_PHONE_STATE) &&
            hasPermission(context, android.Manifest.permission.RECEIVE_SMS)
    
    return if (roleAvailable) {
        basePermissions && checkCallScreeningRole(context)
    } else {
        basePermissions
    }
}
