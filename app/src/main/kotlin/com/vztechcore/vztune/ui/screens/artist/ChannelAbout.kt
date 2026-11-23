package com.vztechcore.vztune.ui.screens.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.data.youtube.ChannelImageLink
import com.vztechcore.vztune.ui.components.text.LinkifiedText
import com.vztechcore.vztune.ui.components.text.openUrlInBrowser

private val AboutLinkColor = Color(0xFF5AB0FF)

/**
 * Aba "Sobre" do canal: Descricao (com links clicaveis), Inscritos, Videos, Fotos, Banners e TAGS.
 * Fotos e Banners mostram o tamanho (72x72, 120x120...) e abrem o link direto da imagem.
 */
@Composable
internal fun ChannelAboutSection(state: ArtistUiState, modifier: Modifier = Modifier) {
    val about = state.about
    Column(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        AboutBlock(title = "Descrição") {
            val description = state.description?.takeIf { it.isNotBlank() }
            if (description != null) {
                LinkifiedText(
                    text = description,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                )
            } else {
                AboutPlain("Sem descrição")
            }
        }
        AboutBlock(title = "Inscritos") { AboutPlain(state.subscribers?.takeIf { it.isNotBlank() } ?: "Não informado") }
        state.videoCountText?.takeIf { it.isNotBlank() }?.let { count ->
            AboutBlock(title = "Vídeos") { AboutPlain(count) }
        }
        AboutBlock(title = "Fotos") { AboutImageLinks(about?.avatars.orEmpty(), "Nenhuma foto encontrada") }
        AboutBlock(title = "Banners") { AboutImageLinks(about?.banners.orEmpty(), "Nenhum banner encontrado") }
        AboutBlock(title = "TAGS") { AboutTags(about?.tags.orEmpty()) }
    }
}

@Composable
private fun AboutBlock(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = title + ":", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun AboutPlain(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutImageLinks(links: List<ChannelImageLink>, emptyText: String) {
    val context = LocalContext.current
    if (links.isEmpty()) {
        AboutPlain(emptyText)
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        links.forEach { link ->
            Text(
                text = link.label,
                style = MaterialTheme.typography.bodyMedium,
                color = AboutLinkColor,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { openUrlInBrowser(context, link.url) }.padding(vertical = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutTags(tags: List<String>) {
    if (tags.isEmpty()) {
        AboutPlain("Nenhuma tag encontrada")
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tags.forEach { tag ->
            Text(
                text = tag,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f)).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
