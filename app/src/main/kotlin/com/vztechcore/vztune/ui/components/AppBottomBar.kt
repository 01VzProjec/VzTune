package com.vztechcore.vztune.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vztechcore.vztune.ui.navigation.AppTab

private val BarFill = Color.White.copy(alpha = 0.18f)
private val BarBorder = Color.White.copy(alpha = 0.22f)
private val SelectedFill = Color.White.copy(alpha = 0.28f)

@Composable
fun AppBottomBar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val glassShape = RoundedCornerShape(50)

    Row(
        modifier = modifier
            .padding(horizontal = 28.dp, vertical = 14.dp)
            .clip(glassShape)
            .background(BarFill)
            .border(BorderStroke(1.dp, BarBorder), glassShape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppTab.entries.forEach { tab ->
            val isSelected = tab == selected
            val contentColor = if (isSelected) Color.White else Color.White.copy(alpha = 0.55f)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(glassShape)
                    .background(if (isSelected) SelectedFill else Color.Transparent)
                    .clickable { onSelect(tab) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tab.label,
                        tint = contentColor,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = tab.label,
                        fontSize = 10.sp,
                        color = contentColor,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}