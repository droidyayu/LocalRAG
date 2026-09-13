package com.ayushig.localrag.demo.ui.portfolio.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.ayushig.localrag.demo.core.Formatters
import com.ayushig.localrag.demo.ui.theme.LocalFinanceColors

/** A gain or loss. Always shows the sign, so colour is never the only cue. */
@Composable
fun SignedAmount(
    amount: Double,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    Text(
        text = Formatters.signedCurrency(amount),
        color = LocalFinanceColors.current.forAmount(amount),
        style = style,
        modifier = modifier,
    )
}

@Composable
fun SignedPercent(
    value: Double,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    Text(
        text = Formatters.signedPercent(value),
        color = LocalFinanceColors.current.forAmount(value),
        style = style,
        modifier = modifier,
    )
}

/** "+$1,580.00 (+7.50%)" as one line, coloured by the amount. */
@Composable
fun SignedAmountAndPercent(
    amount: Double,
    percent: Double,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    Text(
        text = "${Formatters.signedCurrency(amount)} (${Formatters.signedPercent(percent)})",
        color = LocalFinanceColors.current.forAmount(amount),
        style = style,
        modifier = modifier,
    )
}
