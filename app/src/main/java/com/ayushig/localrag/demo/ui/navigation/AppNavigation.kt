package com.ayushig.localrag.demo.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ayushig.localrag.demo.ui.chat.ChatRoute
import com.ayushig.localrag.demo.ui.docs.DocsSearchRoute
import com.ayushig.localrag.demo.ui.portfolio.detail.CategoryDetailRoute as CategoryDetailScreenRoute
import com.ayushig.localrag.demo.ui.portfolio.home.PortfolioHomeRoute

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
            // The docs surface lives here until the demo app grows a real profile screen.
            DocsSearchRoute(modifier = Modifier.fillMaxSize())
        }
    }
}
