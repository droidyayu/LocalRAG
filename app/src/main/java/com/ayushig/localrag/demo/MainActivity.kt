package com.ayushig.localrag.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ayushig.localrag.demo.ui.navigation.AppNavHost
import com.ayushig.localrag.demo.ui.navigation.AssistantRoute
import com.ayushig.localrag.demo.ui.navigation.PortfolioRoute
import com.ayushig.localrag.demo.ui.navigation.ProfileRoute
import com.ayushig.localrag.demo.ui.theme.LocalRAGTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LocalRAGTheme {
                LocalRAGApp()
            }
        }
    }
}

@Composable
fun LocalRAGApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach { destination ->
                item(
                    icon = {
                        // ic_account_box is a 48dp asset while the others are 24dp; pin the size
                        // so every tab's label sits on the same baseline.
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
    ) {
        AppNavHost(navController = navController, modifier = Modifier.fillMaxSize())
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
    val icon: Int,
    val route: String,
) {
    PORTFOLIO("Portfolio", R.drawable.ic_wallet, PortfolioRoute.ROUTE),
    ASSISTANT("Assistant", R.drawable.ic_chat, AssistantRoute.ROUTE),
    PROFILE("Profile", R.drawable.ic_account_box, ProfileRoute.ROUTE),
}
