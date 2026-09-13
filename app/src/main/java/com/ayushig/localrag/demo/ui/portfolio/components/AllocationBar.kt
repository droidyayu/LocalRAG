package com.ayushig.localrag.demo.ui.portfolio.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ayushig.localrag.demo.core.Formatters
import com.ayushig.localrag.demo.domain.model.portfolio.CategorySummary
import com.ayushig.localrag.demo.ui.theme.LocalCategoryPalette

/**
 * One horizontal stacked bar showing each category's share of total value, with a legend.
 * Deliberately not a pie chart: shares are easier to compare along a common baseline.
 */
@Composable
fun AllocationBar(
    categories: List<CategorySummary>,
    modifier: Modifier = Modifier,
) {
    val total = categories.sumOf { it.currentValue }
    if (total <= 0.0) return
    val palette = LocalCategoryPalette.current.colors

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Allocation",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp)),
        ) {
            categories.forEachIndexed { index, summary ->
                Box(
                    modifier = Modifier
                        .weight((summary.currentValue / total).toFloat().coerceAtLeast(0.0001f))
                        .fillMaxHeight()
                        .background(palette[index % palette.size]),
                )
            }
        }
        FlowRow(
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            categories.forEachIndexed { index, summary ->
                LegendItem(
                    label = summary.category.displayName,
                    share = summary.currentValue / total * 100.0,
                    color = palette[index % palette.size],
                )
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, share: Double, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(
            text = "  $label ${Formatters.percent(share)}",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
