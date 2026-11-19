package com.vztechcore.vztune.ui.components.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.Close
import com.vztechcore.vztune.ui.icons.navigation.Search

/**
 * UNICA barra de pesquisa dos overlays.
 * Antes: SearchInputBar (Library, fechar na DIREITA, campo 48dp) + barra inline
 * da MediaList (fechar na ESQUERDA, campo 44dp) copiadas.
 * Agora as duas usam esta, so muda [closeOnStart]:
 * - false = fechar na direita (Library)
 * - true  = fechar na esquerda (MediaList / Album / Playlist)
 * Visual de cada uma preservado: campo 48dp, fundo 0.12, borda 0.18, icone 20dp.
 */
@Composable
fun VzSearchInputBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    placeholder: String,
    focusRequester: FocusRequester,
    onSearch: () -> Unit,
    closeOnStart: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (closeOnStart) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape).clickable { onClose() }, contentAlignment = Alignment.Center) {
                Icon(VzIcons.Close, "Fechar busca", tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(8.dp))
        }
        Row(
            Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)).border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50)).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(VzIcons.Search, null, tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) { Text(placeholder, color = Color.White.copy(alpha = 0.45f), fontSize = 15.sp) }
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                Box(Modifier.size(24.dp).clip(CircleShape).clickable { onQueryChange("") }, contentAlignment = Alignment.Center) {
                    Icon(VzIcons.Close, "Limpar", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                }
            }
        }
        if (!closeOnStart) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape).clickable { onClose() }, contentAlignment = Alignment.Center) {
                Icon(VzIcons.Close, "Fechar busca", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}
