package com.ayushig.localrag.demo

import com.ayushig.localrag.demo.ui.navigation.AssistantRoute
import com.ayushig.localrag.demo.ui.navigation.PortfolioRoute
import com.ayushig.localrag.demo.ui.navigation.RetrievalRoute
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The third tab is the retrieval-accuracy surface, not a profile screen.
 *
 * Loading AppDestinations needs no device: the entries are labels, drawable ids and route
 * strings, so this locks the tab wiring on the JVM.
 */
class RetrievalTabTest {

    @Test
    fun `retrieval tab replaces profile and points at the retrieval route`() {
        assertEquals(
            listOf(PortfolioRoute.ROUTE, AssistantRoute.ROUTE, RetrievalRoute.ROUTE),
            AppDestinations.entries.map { it.route },
        )
        val retrieval = AppDestinations.entries.single { it.route == RetrievalRoute.ROUTE }
        assertEquals("Retrieval", retrieval.label)
        assertEquals("retrieval", RetrievalRoute.ROUTE)
    }
}
