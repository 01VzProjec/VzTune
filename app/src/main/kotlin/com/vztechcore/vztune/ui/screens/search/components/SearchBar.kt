package com.vztechcore.vztune.ui.screens.search.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vztechcore.vztune.core.domain.search.SearchFilter

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    selectedFilter: SearchFilter,
    onFilterSelect: (SearchFilter) -> Unit,
    showFilters: Boolean,
    modifier: Modifier = Modifier,
) {
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(query, selection = TextRange(query.length)))
    }

    LaunchedEffect(query) {
        if (query != textFieldValue.text) {
            textFieldValue = TextFieldValue(text = query, selection = TextRange(query.length))
        }
    }

    Column(modifier = modifier) {
        Box(
            Modifier
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp),
        ) {
            val pillShape = RoundedCornerShape(50)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(pillShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), pillShape)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        textFieldValue = newValue
                        onQueryChange(newValue.text)
                    },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    modifier = Modifier.weight(1f).onFocusChanged { onFocusChanged(it.isFocused) },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (textFieldValue.text.isEmpty()) {
                                Text("Buscar música, artista, álbum...", color = Color.White.copy(alpha = 0.4f), fontSize = 16.sp)
                            }
                            inner()
                        }
                    },
                )
                if (textFieldValue.text.isNotEmpty()) {
                    IconButton(onClick = {
                        textFieldValue = TextFieldValue("")
                        onQueryChange("")
                    }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Close, "Limpar", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        if (showFilters) {
            Spacer(Modifier.height(12.dp))
            FilterChipRow(selected = selectedFilter, onSelect = onFilterSelect)
        }
    }
}

@Composable
private fun FilterChipRow(selected: SearchFilter, onSelect: (SearchFilter) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(SearchFilter.entries.toList(), key = { it.name }) { filter ->
            val isSelected = filter == selected
            val bg = if (isSelected) Color.White else Color.White.copy(alpha = 0.08f)
            val fg = if (isSelected) Color.Black else Color.White.copy(alpha = 0.75f)
            Box(Modifier.clip(RoundedCornerShape(50)).background(bg).clickable { onSelect(filter) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(filter.label, style = MaterialTheme.typography.labelMedium, color = fg, fontWeight = FontWeight.Bold)
            }
        }
    }
}
