package com.vztechcore.vztune.ui.screens.medialist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.core.domain.search.SortOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaListSortSheet(
    currentSort: SortOrder,
    onSelect: (SortOrder) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141B30),
        contentColor = Color.White,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
            Text(
                "Ordenar por",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            SortOrder.entries.forEach { option ->
                Row(
                    Modifier.fillMaxWidth().clickable { onSelect(option) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = option == currentSort,
                        onClick = { onSelect(option) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = Color(0xFF60A5FA),
                            unselectedColor = Color.White.copy(alpha = 0.5f),
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        option.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        fontWeight = if (option == currentSort) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}