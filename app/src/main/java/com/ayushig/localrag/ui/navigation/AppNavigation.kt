package com.ayushig.localrag.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ayushig.localrag.ui.chat.ChatRoute
import com.ayushig.localrag.ui.portfolio.detail.CategoryDetailRoute as CategoryDetailScreenRoute
import com.ayushig.localrag.ui.portfolio.home.PortfolioHomeRoute

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = PortfolioRoute.ROUTE,
        modifier = modifier,
    ) {
        composable(PortfolioRoute.ROUTE) {
            PortfolioHomeRoute(
                onCategoryClick = { category ->
                    navController.navigate(CategoryDetailRoute.build(category))
                },
            )
        }

        composable(
            route = CategoryDetailRoute.ROUTE,
            arguments = listOf(
                navArgument(CategoryDetailRoute.ARG_CATEGORY) { type = NavType.StringType },
            ),
        ) {
            CategoryDetailScreenRoute(onBack = { navController.popBackStack() })
        }

        composable(AssistantRoute.ROUTE) {
            ChatRoute(modifier = Modifier.fillMaxSize())
        }

        composable(ProfileRoute.ROUTE) {
            Placeholder("Profile")
        }
    }
}

@Composable
private fun Placeholder(label: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "$label coming later", style = MaterialTheme.typography.bodyMedium)
    }
}
