package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.ChannelsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NavigationBarBg
import com.example.ui.theme.OnPrimaryText
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.example.ui.theme.SecondaryCyan
import com.example.viewmodel.IPTVViewModel

enum class NavigationTab { Channels, Settings }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: IPTVViewModel = viewModel()
                val isLandscape by viewModel.isManualLandscape.collectAsState()
                
                var activeTab by remember { mutableStateOf(NavigationTab.Channels) }

                Scaffold(
                    bottomBar = {
                        // Hide bottom navigation if the user manually activated landscape mode
                        // to enjoy true fullscreen cinema view!
                        if (!isLandscape) {
                            NavigationBar(
                                containerColor = NavigationBarBg,
                                tonalElevation = 8.dp,
                                modifier = Modifier
                                    .navigationBarsPadding()
                                    .testTag("app_navigation_bar")
                            ) {
                                NavigationBarItem(
                                    selected = activeTab == NavigationTab.Channels,
                                    onClick = { activeTab = NavigationTab.Channels },
                                    label = { Text("Channels") },
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.LiveTv,
                                            contentDescription = "Channels Page"
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = OnPrimaryText,
                                        selectedTextColor = TextWhite,
                                        unselectedIconColor = TextMuted,
                                        unselectedTextColor = TextMuted,
                                        indicatorColor = SecondaryCyan
                                    ),
                                    modifier = Modifier.testTag("nav_channels_tab")
                                )

                                NavigationBarItem(
                                    selected = activeTab == NavigationTab.Settings,
                                    onClick = { activeTab = NavigationTab.Settings },
                                    label = { Text("Settings") },
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Settings Page"
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = OnPrimaryText,
                                        selectedTextColor = TextWhite,
                                        unselectedIconColor = TextMuted,
                                        unselectedTextColor = TextMuted,
                                        indicatorColor = SecondaryCyan
                                    ),
                                    modifier = Modifier.testTag("nav_settings_tab")
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Box(
                        modifier = if (isLandscape) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        }
                    ) {
                        AnimatedContent(
                            targetState = activeTab,
                            transitionSpec = {
                                fadeIn() togetherWith fadeOut()
                            },
                            label = "screen_tab_switch"
                        ) { tab ->
                            when (tab) {
                                NavigationTab.Channels -> {
                                    ChannelsScreen(viewModel = viewModel)
                                }
                                NavigationTab.Settings -> {
                                    SettingsScreen(viewModel = viewModel)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
