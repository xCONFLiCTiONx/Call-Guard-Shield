package com.xconflictionx.callguardshield

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.xconflictionx.callguardshield.ui.MainViewModel
import com.xconflictionx.callguardshield.ui.screen.*
import com.xconflictionx.callguardshield.ui.theme.CallGuardShieldTheme

class MainActivity : ComponentActivity() {
    private val roleRequestLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    private val intentState = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        intentState.value = intent
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val viewModel: MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            
            CallGuardShieldTheme {
                var showMainApp by remember { mutableStateOf(checkAllPermissions(context)) }
                
                if (!showMainApp) {
                    PermissionScreen(
                        onRequestRole = { requestCallScreeningRole() },
                        onContinue = { 
                            showMainApp = true
                        }
                    )
                } else {
                    MainApp(viewModel, intentState.value)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentState.value = intent
    }

    private fun requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) == true &&
                !roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
            ) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                roleRequestLauncher.launch(intent)
            }
        }
    }
}

@Composable
fun MainApp(viewModel: MainViewModel, initialIntent: Intent? = null) {
    val navController = rememberNavController()
    val context = LocalContext.current

    // Derive selected item from navigation state
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "history"
    
    val items = listOf("History", "Lists", "Settings")
    val routes = listOf("history", "lists", "settings")
    val icons = listOf(
        Icons.Default.History, 
        Icons.AutoMirrored.Filled.List, 
        Icons.Default.Settings
    )

    val selectedItem = remember(currentRoute) {
        val index = routes.indexOf(currentRoute)
        if (index != -1) index else 0
    }

    // Global UI Event Observer
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is com.xconflictionx.callguardshield.ui.UiEvent.ShowToast -> {
                    android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Handle App Shortcut and Notification Intents
    LaunchedEffect(initialIntent) {
        initialIntent?.let { intent ->
            if (intent.getStringExtra("shortcut") == "history") {
                navController.navigate("history") {
                    popUpTo("history") { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    val navTo: (String, Int) -> Unit = { route, _ ->
        if (currentRoute != route) {
            navController.navigate(route) {
                popUpTo("history") { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = { Icon(icons[index], contentDescription = item) },
                        label = { Text(item) },
                        selected = selectedItem == index,
                        onClick = { navTo(routes[index], index) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "history",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("history") { HistoryScreen(viewModel) }
            composable("lists") { ListManagementScreen(viewModel) }
            composable("settings") { SettingsScreen(viewModel) }
        }
    }
}
