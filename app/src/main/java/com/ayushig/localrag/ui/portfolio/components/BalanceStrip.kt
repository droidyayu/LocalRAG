package com.ayushig.localrag.ui.portfolio.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ayushig.localrag.core.Formatters

/** Available balance, equity and free margin as three compact stats. */
@Composable
fun BalanceStrip(
    availableBalance: Double,
    equity: Double,
    freeMargin: Double,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Stat("Available", availableBalance, Modifier.weight(1f))
            Stat("Equity", equity, Modifier.weight(1f))
            Stat("Free margin", freeMargin, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(label: String, value: Double, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = Formatters.currency(value),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}
