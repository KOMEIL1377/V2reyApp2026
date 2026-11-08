package com.example

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ServerListScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StoreScreen
import com.example.ui.screens.SupportScreen
import com.example.ui.theme.BndBgDark
import com.example.ui.theme.BndNeonPurple
import com.example.ui.theme.BndOrchidTertiary
import com.example.ui.theme.BndPurpleSecondary
import com.example.ui.theme.BndSoftPurple
import com.example.ui.theme.BndSurfaceCard
import com.example.ui.theme.BndSurfaceDark
import com.example.ui.theme.BndSurfaceVariantDark
import com.example.ui.theme.BndTextMuted
import com.example.ui.theme.BndTextPrimary
import com.example.ui.theme.BndTextSecondary
import com.example.ui.theme.BndVioletPrimary
import com.example.ui.theme.V2reyBndTheme
import com.example.viewmodel.AppTab
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            V2reyBndTheme {
                val context = LocalContext.current
                val currentTab by viewModel.currentTab.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }

                // Launcher for Android system VPN Prepare dialog
                val vpnLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        viewModel.onVpnPermissionGranted(context)
                    } else {
                        Toast.makeText(context, "مجوز اتصال VPN تایید نشد", Toast.LENGTH_SHORT).show()
                    }
                }

                // Handle back button on secondary tabs
                BackHandler(enabled = currentTab != AppTab.DASHBOARD) {
                    viewModel.setTab(AppTab.DASHBOARD)
                }

                // Listen for UI notifications / toast events
                LaunchedEffect(Unit) {
                    viewModel.uiMessage.collectLatest { message ->
                        snackbarHostState.showSnackbar(message)
                    }
                }

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BndBgDark)
                        .windowInsetsPadding(WindowInsets.statusBars),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        V2reyBndBottomNavigationBar(
                            currentTab = currentTab,
                            onTabSelected = { viewModel.setTab(it) }
                        )
                    },
                    containerColor = BndBgDark
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = innerPadding.calculateBottomPadding())
                    ) {
                        AnimatedContent(
                            targetState = currentTab,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "tab_transition"
                        ) { tab ->
                            when (tab) {
                                AppTab.DASHBOARD -> DashboardScreen(
                                    viewModel = viewModel,
                                    onNavigateToServers = { viewModel.setTab(AppTab.SERVERS) },
                                    onRequestVpnPermission = { prepareIntent ->
                                        vpnLauncher.launch(prepareIntent)
                                    }
                                )
                                AppTab.SERVERS -> ServerListScreen(
                                    viewModel = viewModel,
                                    onServerSelected = {
                                        viewModel.setTab(AppTab.DASHBOARD)
                                    }
                                )
                                AppTab.STORE -> StoreScreen(
                                    viewModel = viewModel
                                )
                                AppTab.SUPPORT -> SupportScreen(
                                    viewModel = viewModel
                                )
                                AppTab.SETTINGS -> SettingsScreen(
                                    viewModel = viewModel
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun V2reyBndBottomNavigationBar(
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(BndSurfaceDark)
                .border(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(BndVioletPrimary.copy(alpha = 0.4f), BndOrchidTertiary.copy(alpha = 0.2f))
                    ),
                    RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppTab.values().forEach { tab ->
                val isSelected = tab == currentTab
                val (selectedIcon, unselectedIcon) = when (tab) {
                    AppTab.DASHBOARD -> Pair(Icons.Filled.PowerSettingsNew, Icons.Outlined.PowerSettingsNew)
                    AppTab.SERVERS -> Pair(Icons.Filled.Storage, Icons.Outlined.Storage)
                    AppTab.STORE -> Pair(Icons.Filled.ShoppingBag, Icons.Outlined.ShoppingBag)
                    AppTab.SUPPORT -> Pair(Icons.Filled.Headphones, Icons.Outlined.Headphones)
                    AppTab.SETTINGS -> Pair(Icons.Filled.Settings, Icons.Outlined.Settings)
                }

                Box(
                    modifier = Modifier
                        .testTag("nav_tab_${tab.name.lowercase()}")
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) BndVioletPrimary.copy(alpha = 0.35f) else Color.Transparent)
                        .clickable { onTabSelected(tab) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isSelected) selectedIcon else unselectedIcon,
                            contentDescription = tab.title,
                            tint = if (isSelected) BndOrchidTertiary else BndTextMuted,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.title,
                            color = if (isSelected) Color.White else BndTextMuted,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
