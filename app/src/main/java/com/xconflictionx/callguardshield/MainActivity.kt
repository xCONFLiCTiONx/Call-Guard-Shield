package com.xconflictionx.callguardshield

import android.app.role.RoleManager
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
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.xconflictionx.callguardshield.ui.MainViewModel
import com.xconflictionx.callguardshield.ui.screen.*
import com.xconflictionx.callguardshield.ui.theme.CallGuardShieldTheme

class MainActivity : ComponentActivity() {
    private val roleRequestLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            CallGuardShieldTheme {
                var showMainApp by remember { mutableStateOf(checkAllPermissions(context)) }
                
                if (!showMainApp) {
                    PermissionScreen(
                        onRequestRole = { requestCallScreeningRole() },
                        onContinue = { showMainApp = true }
                    )
                } else {
                    MainApp()
                }
            }
        }
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
fun MainApp() {
    val navController = rememberNavController()
    val viewModel: MainViewModel = viewModel()
    var selectedItem by remember { mutableIntStateOf(0) }
    val items = listOf("Home", "History", "Lists", "Chat", "Settings")
    val icons = listOf(
        Icons.Default.Home, 
        Icons.Default.History, 
        Icons.AutoMirrored.Filled.List, 
        Icons.AutoMirrored.Filled.Message,
        Icons.Default.Settings
    )

    // Centralized navigation logic
    val navTo: (String, Int) -> Unit = { route, index ->
        if (selectedItem == index) {
            // If already on this tab, pop to the root of the tab to "reset" it
            navController.popBackStack(route, inclusive = false)
        } else {
            selectedItem = index
            if (index == 3) viewModel.clearChat()
            navController.navigate(route) {
                popUpTo("home") { saveState = true }
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
                        onClick = { navTo(item.lowercase(), index) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            val navigateToChat = { navTo("chat", 3) }

            composable("home") { 
                MainScreen(
                    viewModel = viewModel, 
                    onNavigateToHistory = { navTo("history", 1) },
                    onNavigateToGlobalSpam = {
                        navController.navigate("global_spam_list")
                    }
                ) 
            }
            composable("history") { HistoryScreen(viewModel, onNavigateToChat = navigateToChat) }
            composable("lists") { ListManagementScreen(viewModel, onNavigateToChat = navigateToChat) }
            composable("chat") { ChatScreen(viewModel) }
            composable("settings") { SettingsScreen(viewModel) }
            composable("global_spam_list") { 
                GlobalSpamListScreen(viewModel, onNavigateBack = { navController.popBackStack() }) 
            }
        }
    }
}
