package com.animehub.presentation.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.animehub.domain.model.Episode
import com.animehub.domain.model.episodeKey
import com.animehub.presentation.common.EmptyState
import com.animehub.presentation.common.SectionTitle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailsScreen(
    vm: DetailsViewModel,
    onBack: () -> Unit,
    onEpisodeClick: (DetailsState, Episode) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val favorite by vm.favorite.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var newestFirst by rememberSaveable { mutableStateOf(false) }

    // Lista ordenada só é recalculada quando os episódios ou a ordem mudam.
    val shownEpisodes = remember(state.episodes, newestFirst) {
        val asc = state.episodes.sortedBy { it.number }
        if (newestFirst) asc.asReversed() else asc
    }
    val firstEpisode = remember(state.episodes) { state.episodes.minByOrNull { it.number } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = vm::toggleFavorite, enabled = !state.loading && state.error == null) {
                        Icon(
                            if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (favorite) "Remover da biblioteca" else "Adicionar à biblioteca",
                            tint = if (favorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding)) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            state.error != null -> EmptyState(
                title = "Não foi possível abrir",
                message = state.error,
                isError = true,
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item(contentType = "header") {
                    Box(Modifier.fillMaxWidth().height(250.dp)) {
                        if (state.coverUrl != null) {
                            AsyncImage(
                                model = state.coverUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().alpha(0.35f),
                            )
                        }
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))
                            )
                        )
                        Row(
                            Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Box(
                                Modifier
                                    .width(110.dp)
                                    .height(165.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                if (state.coverUrl != null) {
                                    AsyncImage(
                                        model = state.coverUrl,
                                        contentDescription = state.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.padding(bottom = 4.dp)) {
                                Text(
                                    state.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${state.entries.size} " + if (state.entries.size == 1) "fonte" else "fontes",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                item(contentType = "play") {
                    Button(
                        onClick = { firstEpisode?.let { onEpisodeClick(state, it) } },
                        enabled = firstEpisode != null,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(firstEpisode?.let { "Assistir episódio ${it.number.episodeKey()}" } ?: "Assistir")
                    }
                }
                state.synopsis?.let { synopsis ->
                    item(contentType = "synopsis") {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            Text(
                                synopsis,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = if (expanded) Int.MAX_VALUE else 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(onClick = { expanded = !expanded }) {
                                Text(if (expanded) "Mostrar menos" else "Mostrar mais")
                            }
                        }
                    }
                }
                item(contentType = "sources") {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        SectionTitle("Assistir em")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.entries.forEach { entry ->
                                FilterChip(
                                    selected = entry.ref == state.selected,
                                    onClick = { vm.select(entry.ref) },
                                    label = {
                                        Text(entry.name + (entry.episodeCount?.let { " · $it ep" } ?: ""))
                                    },
                                )
                            }
                        }
                        if (state.searchingOthers) {
                            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Procurando em outras fontes…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                item(contentType = "episodes-header") {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        SectionTitle("Episódios")
                        TextButton(onClick = { newestFirst = !newestFirst }) {
                            Text(if (newestFirst) "Mais recentes primeiro" else "Mais antigos primeiro")
                        }
                    }
                }
                when {
                    state.loadingEpisodes -> item {
                        Box(Modifier.fillMaxWidth().padding(24.dp)) {
                            CircularProgressIndicator(Modifier.align(Alignment.Center))
                        }
                    }
                    state.episodesError != null -> item {
                        Text(
                            state.episodesError!!,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    else -> items(shownEpisodes, key = { it.id }, contentType = { "episode" }) { ep ->
                        ListItem(
                            leadingContent = {
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            ep.number.episodeKey(),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            },
                            headlineContent = { Text("Episódio ${ep.number.episodeKey()}") },
                            supportingContent = { Text(ep.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingContent = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                            modifier = Modifier.clickable { onEpisodeClick(state, ep) },
                        )
                    }
                }
            }
        }
    }
}
