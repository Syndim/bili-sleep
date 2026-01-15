package org.syndim.bilisleep.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import org.syndim.bilisleep.data.model.VideoSearchItem
import org.syndim.bilisleep.ui.screens.PlayerScreen
import org.syndim.bilisleep.ui.screens.SearchScreen

sealed class Screen(val route: String) {
    object Search : Screen("search")
    object Player : Screen("player")
}

@Composable
fun BiliSleepNavHost(
    navController: NavHostController,
    onPlayFromPlaylist: (List<VideoSearchItem>, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Search.route,
        modifier = modifier
    ) {
        composable(Screen.Search.route) {
            SearchScreen(
                onPlayFromPlaylist = { videos, startIndex ->
                    onPlayFromPlaylist(videos, startIndex)
                    navController.navigate(Screen.Player.route)
                }
            )
        }
        
        composable(Screen.Player.route) {
            PlayerScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
