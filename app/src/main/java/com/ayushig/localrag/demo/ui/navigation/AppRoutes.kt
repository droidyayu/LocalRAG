package com.ayushig.localrag.demo.ui.navigation

import com.ayushig.localrag.demo.domain.model.portfolio.AssetCategory

/**
 * Route names in one place, so the NavHost and the ViewModels reading arguments cannot drift
 * apart.
 */
object PortfolioRoute {
    const val ROUTE = "portfolio"
}

object CategoryDetailRoute {
    const val ARG_CATEGORY = "category"
    const val ROUTE = "portfolio/{$ARG_CATEGORY}"

    fun build(category: AssetCategory): String = "portfolio/${category.name}"
}

object AssistantRoute {
    const val ROUTE = "assistant"
}

object ProfileRoute {
    const val ROUTE = "profile"
}
