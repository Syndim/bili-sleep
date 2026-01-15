package org.syndim.bilisleep

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.syndim.bilisleep.service.MediaPlaybackService
import org.syndim.bilisleep.ui.components.MiniPlayer
import org.syndim.bilisleep.ui.navigation.BiliSleepNavHost
import org.syndim.bilisleep.ui.navigation.Screen
import org.syndim.bilisleep.ui.theme.BiliSleepTheme
import org.syndim.bilisleep.viewmodel.PlayerViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Permission result - notification will show if granted
        // We don't need to handle denial specially, audio will still play
    }
    
    // Track if we should navigate to player screen
    private var shouldOpenPlayer = mutableStateOf(false)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Request notification permission for Android 13+
        requestNotificationPermission()
        
        // Check if launched from notification
        handleIntent(intent)
        
        setContent {
            BiliSleepTheme {
                val navController = rememberNavController()
                val playerViewModel: PlayerViewModel = hiltViewModel()
                val playerState by playerViewModel.playerState.collectAsState()
                
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                
                // Handle navigation to player screen from notification
                val openPlayer by remember { shouldOpenPlayer }
                LaunchedEffect(openPlayer) {
                    if (openPlayer && playerState.currentItem != null) {
                        navController.navigate(Screen.Player.route) {
                            // Avoid multiple copies of player screen
                            launchSingleTop = true
                        }
                        shouldOpenPlayer.value = false
                    }
                }
                
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        // Show mini player when not on player screen and something is playing
                        if (currentRoute != Screen.Player.route && playerState.currentItem != null) {
                            MiniPlayer(
                                playerState = playerState,
                                onPlayPauseClick = playerViewModel::togglePlayPause,
                                onNextClick = playerViewModel::playNext,
                                onClick = {
                                    navController.navigate(Screen.Player.route)
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        BiliSleepNavHost(
                            navController = navController,
                            onPlayFromPlaylist = { videos, startIndex ->
                                playerViewModel.playPlaylist(videos, startIndex)
                            }
                        )
                    }
                }
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
    
    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(MediaPlaybackService.EXTRA_OPEN_PLAYER, false) == true) {
            shouldOpenPlayer.value = true
        }
    }
    
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted
                }
                else -> {
                    // Request the permission
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
}
