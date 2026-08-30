package com.financetracker.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** The palette accounts and categories pick from - deliberately small, and all chart-legible. */
val PALETTE: List<Int> = listOf(
    0xFF4C8DFF, 0xFF3FBF8F, 0xFFE8A33D, 0xFFE85E7A, 0xFF9B7BE8,
    0xFF5EC8D8, 0xFFE0C24E, 0xFFD98BC8, 0xFF62B8E8, 0xFFB88A5E,
    0xFF8A94A6, 0xFF4CAF7D
).map { it.toInt() }

@Composable
fun ColorPickerRow(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PALETTE.forEach { argb ->
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .border(
                        width = if (argb == selected) 3.dp else 0.dp,
                        color = Color.White,
                        shape = CircleShape
                    )
                    .clickable { onSelect(argb) }
            )
        }
    }
}
