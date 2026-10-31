package com.vztechcore.vztune.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
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
    val tabs = AppTab.entries
    val currentOnSelect by rememberUpdatedState(onSelect)

    var hoveredTab by remember { mutableStateOf<AppTab?>(null) }
    var isPressed by remember { mutableStateOf(false) }

    // Whole bar scales slightly when pressed
    val barScale by animateFloatAsState(
        targetValue = if (isPressed) 1.04f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "barScale",
    )

    Box(
        modifier = modifier
            .padding(start = 28.dp, end = 28.dp, top = 12.dp, bottom = 24.dp)
            .graphicsLayer {
                scaleX = barScale
                scaleY = barScale
                // Allow children to draw outside the box (needed so the
                // selected marker can visually exceed the glass background)
                clip = false
            }
            .height(60.dp),
    ) {
        // Glass background (static, clipped)
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(glassShape)
                .background(BarFill)
                .border(BorderStroke(1.dp, BarBorder), glassShape),
        )

        // Interaction + tabs (unclipped, sits on top of glass)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(tabs) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isPressed = true

                        val tabWidth = size.width.toFloat() / tabs.size
                        fun tabAt(x: Float) =
                            tabs[(x / tabWidth).toInt().coerceIn(0, tabs.size - 1)]

                        var finalTab = tabAt(down.position.x)
                        hoveredTab = finalTab
                        down.consume()

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.changedToUpIgnoreConsumed()) break
                            finalTab = tabAt(change.position.x)
                            hoveredTab = finalTab
                            change.consume()
                        }

                        isPressed = false
                        hoveredTab = null
                        currentOnSelect(finalTab)
                    }
                }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val isHighlighted = if (hoveredTab != null) hoveredTab == tab else tab == selected
                val isBeingPressed = hoveredTab == tab && isPressed

                // Marker grows more when it's the one being touched
                val markerScale by animateFloatAsState(
                    targetValue = if (isBeingPressed) 1.28f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "markerScale-${tab.name}",
                )

                val contentColor =
                    if (isHighlighted) Color.White else Color.White.copy(alpha = 0.55f)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .graphicsLayer {
                            scaleX = markerScale
                            scaleY = markerScale
                            clip = false
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    // Selected marker
                    if (isHighlighted) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(glassShape)
                                .background(SelectedFill),
                        )
                    }

                    // Icon + label
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (isHighlighted) tab.selectedIcon else tab.unselectedIcon,
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
}