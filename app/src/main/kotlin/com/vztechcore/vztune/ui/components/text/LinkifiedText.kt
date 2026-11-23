package com.vztechcore.vztune.ui.components.text

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.ui.components.rows.SheetOptionRow
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.ContentCopy
import com.vztechcore.vztune.ui.icons.actions.OpenInNew

private const val LINK_TAG = "vz_link"

// Cores: e-mail e @usuario = azul claro, telefone/WhatsApp = verde, site = azul mais escuro.
private val EmailNickColor = Color(0xFF5AB0FF)
private val PhoneColor = Color(0xFF3DDC84)
private val UrlColor = Color(0xFF2F6BFF)

private enum class LinkKind { EMAIL, NICK, PHONE, URL }

private data class LinkSpan(val start: Int, val end: Int, val kind: LinkKind, val value: String)

private val UrlRegex = Regex("(?:https?://|www\\.)[^\\s<>\"']+", RegexOption.IGNORE_CASE)
private val EmailRegex = Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9\\-]+(?:\\.[A-Za-z0-9\\-]+)+")
// (xx) xxxxx-xxxx | (xx)xxxxx-xxxx | (xx)x xxxx-xxxx | (37)9 9935-6789 | opcional +55
private val PhoneRegex = Regex("(?:\\+55\\s?)?\\(\\d{2}\\)\\s?(?:\\d\\s)?\\d{4,5}-\\d{4}")
// @nick so vale na MESMA palavra: o espaco termina o link. Nao pega o @ de dentro de e-mail.
private val NickRegex = Regex("(?<![A-Za-z0-9._%+\\-@])@[A-Za-z0-9_.\\-]{2,}")

private fun findLinks(text: String): List<LinkSpan> {
    val candidates = mutableListOf<Pair<LinkSpan, Int>>()
    UrlRegex.findAll(text).forEach { m ->
        val trimmed = m.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '"', '\'')
        if (trimmed.length > 4) candidates.add(LinkSpan(m.range.first, m.range.first + trimmed.length, LinkKind.URL, trimmed) to 0)
    }
    EmailRegex.findAll(text).forEach { m ->
        candidates.add(LinkSpan(m.range.first, m.range.last + 1, LinkKind.EMAIL, m.value) to 1)
    }
    PhoneRegex.findAll(text).forEach { m ->
        candidates.add(LinkSpan(m.range.first, m.range.last + 1, LinkKind.PHONE, m.value) to 2)
    }
    NickRegex.findAll(text).forEach { m ->
        val trimmed = m.value.trimEnd('.', '-')
        if (trimmed.length >= 3) candidates.add(LinkSpan(m.range.first, m.range.first + trimmed.length, LinkKind.NICK, trimmed) to 3)
    }
    val sorted = candidates.sortedWith(compareBy({ it.first.start }, { it.second }))
    val out = mutableListOf<LinkSpan>()
    var lastEnd = 0
    for ((span, _) in sorted) {
        if (span.start >= lastEnd) {
            out.add(span)
            lastEnd = span.end
        }
    }
    return out
}

private fun buildLinkedString(text: String): AnnotatedString {
    val links = findLinks(text)
    return buildAnnotatedString {
        var cursor = 0
        for (link in links) {
            if (link.start > cursor) append(text.substring(cursor, link.start))
            val style = when (link.kind) {
                LinkKind.EMAIL, LinkKind.NICK -> SpanStyle(color = EmailNickColor)
                LinkKind.PHONE -> SpanStyle(color = PhoneColor)
                LinkKind.URL -> SpanStyle(color = UrlColor, textDecoration = TextDecoration.Underline)
            }
            val startIndex = length
            withStyle(style) { append(text.substring(link.start, link.end)) }
            addStringAnnotation(LINK_TAG, link.kind.name + "|" + link.value, startIndex, length)
            cursor = link.end
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}

/** Copia [value] pra area de transferencia e avisa com um Toast. */
fun copyToClipboard(context: Context, value: String, message: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("VzTune", value))
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

/** Abre o link no navegador principal do usuario. */
fun openUrlInBrowser(context: Context, url: String) {
    val full = if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) url else "https://$url"
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(full)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Nenhum navegador encontrado", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Texto com links clicaveis:
 * - e-mail (azul claro) e @usuario (azul claro): clicou, copia.
 * - telefone (xx) xxxxx-xxxx (verde): clicou, copia como numero de WhatsApp.
 * - site (azul escuro): clicou, sobe menu "Copiar" / "Abrir no navegador".
 * Toque fora dos links chama [onPlainClick].
 */
@Composable
fun LinkifiedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = Color.White.copy(alpha = 0.7f),
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onPlainClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val annotated = remember(text) { buildLinkedString(text) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var sheetUrl by remember { mutableStateOf<String?>(null) }
    val currentPlainClick by rememberUpdatedState(onPlainClick)

    sheetUrl?.let { url ->
        UrlActionsSheet(
            url = url,
            onCopy = { copyToClipboard(context, url, "Link copiado") },
            onOpen = { openUrlInBrowser(context, url) },
            onDismiss = { sheetUrl = null },
        )
    }

    Text(
        text = annotated,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = { layout = it },
        modifier = modifier.pointerInput(annotated) {
            detectTapGestures { position ->
                val offset = layout?.getOffsetForPosition(position)
                val hit = offset?.let { annotated.getStringAnnotations(LINK_TAG, it, it).firstOrNull() }
                if (hit == null) {
                    currentPlainClick?.invoke()
                } else {
                    val kind = hit.item.substringBefore("|")
                    val value = hit.item.substringAfter("|")
                    when (kind) {
                        LinkKind.EMAIL.name -> copyToClipboard(context, value, "E-mail copiado")
                        LinkKind.NICK.name -> copyToClipboard(context, value, "Usuário copiado")
                        LinkKind.PHONE.name -> copyToClipboard(context, value, "Número de WhatsApp copiado")
                        LinkKind.URL.name -> sheetUrl = value
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UrlActionsSheet(
    url: String,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF121829),
        contentColor = Color.White,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = UrlColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            SheetOptionRow(
                icon = VzIcons.ContentCopy,
                label = "Copiar",
                onClick = {
                    onCopy()
                    onDismiss()
                },
            )
            SheetOptionRow(
                icon = VzIcons.OpenInNew,
                label = "Abrir no navegador",
                onClick = {
                    onOpen()
                    onDismiss()
                },
            )
        }
    }
}
