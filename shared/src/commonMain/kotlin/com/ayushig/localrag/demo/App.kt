package com.ayushig.localrag.demo

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ayushig.localrag.demo.resources.Res
import com.ayushig.localrag.demo.resources.ic_chat
import com.ayushig.localrag.demo.resources.ic_search
import com.ayushig.localrag.demo.resources.ic_wallet
import com.ayushig.localrag.demo.ui.navigation.AppNavHost
import com.ayushig.localrag.demo.ui.navigation.AssistantRoute
import com.ayushig.localrag.demo.ui.navigation.PortfolioRoute
import com.ayushig.localrag.demo.ui.navigation.RetrievalRoute
import com.ayushig.localrag.demo.ui.theme.LocalRAGTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun App() {
    LocalRAGTheme {
        LocalRAGApp()
    }
}

@Composable
fun LocalRAGApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppDestinations.entries.forEach { destination ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                painter = painterResource(destination.icon),
                                contentDescription = destination.label,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        label = { Text(destination.label) },
                        // The detail screen sits under the portfolio route, so the Portfolio tab
                        // stays selected while a category is open.
                        selected = currentDestination?.hierarchy?.any { navDestination ->
                            navDestination.route?.startsWith(destination.route) == true
                        } == true,
                        onClick = { navController.switchTab(destination.route) },
                    )
                }
            }
        },
    ) { padding ->
        AppNavHost(navController = navController, modifier = Modifier.fillMaxSize().padding(padding))
    }
}

/**
 * Tab switching keeps each tab's own back stack and state, so the assistant's loaded engine and
 * transcript survive a trip to the portfolio and back.
 */
private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

enum class AppDestinations(
    val label: String,
    val icon: DrawableResource,
    val route: String,
) {
    PORTFOLIO("Portfolio", Res.drawable.ic_wallet, PortfolioRoute.ROUTE),
    ASSISTANT("Assistant", Res.drawable.ic_chat, AssistantRoute.ROUTE),
    RETRIEVAL("Retrieval", Res.drawable.ic_search, RetrievalRoute.ROUTE),
}
