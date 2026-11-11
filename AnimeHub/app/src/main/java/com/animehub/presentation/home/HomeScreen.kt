package com.animehub.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.animehub.domain.model.ContinueItem
import com.animehub.domain.model.EntryRef
import com.animehub.presentation.common.EmptyState
import com.animehub.presentation.common.SectionTitle
import com.animehub.presentation.common.SkeletonGrid
import com.animehub.presentation.common.WorkCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onWorkClick: (List<EntryRef>) -> Unit,
    onOpenSites: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("AnimeHub") }) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQuery,
                placeholder = { Text("Buscar em todas as fontes") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = vm::clearQuery) {
                            Icon(Icons.Default.Close, contentDescription = "Limpar")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.submit() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (state.sites.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = state.selected == null,
                            onClick = { vm.select(null) },
                            label = { Text("Todas") },
                        )
                    }
                    items(state.sites, key = { it.id }) { site ->
                        FilterChip(
                            selected = state.selected == site.id,
                            onClick = { vm.select(site.id) },
                            label = { Text(site.name) },
                        )
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.noSites -> EmptyState(
                        title = "Nenhum site ativo",
                        message = "Adicione o endereço de um site na aba Fontes.",
                        actionLabel = "Ir para Fontes",
                        onAction = onOpenSites,
                    )
                    state.loading && state.items.isEmpty() -> SkeletonGrid()
                    state.error != null && state.items.isEmpty() -> EmptyState(
                        title = "Não foi possível carregar",
                        message = state.error,
                        actionLabel = "Tentar de novo",
                        onAction = vm::refresh,
                        isError = true,
                    )
                    state.items.isEmpty() -> EmptyState(
                        title = "Nada encontrado",
                        message = "Tente outro termo ou verifique a configuração do site na aba Fontes.",
                    )
                    else -> PullToRefreshBox(
                        isRefreshing = state.loading,
                        onRefresh = vm::refresh,
                    ) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(120.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            if (state.query.isBlank() && state.continueItems.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Column {
                                        SectionTitle("Continuar assistindo")
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            items(state.continueItems, key = { it.workKey + it.episodeKey }) { c ->
                                                ContinueCard(c) { onWorkClick(c.refs) }
                                            }
                                        }
                                    }
                                }
                            }
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SectionTitle(if (state.query.isBlank()) "Populares" else "Resultados")
                            }
                            items(
                                state.items,
                                key = { it.key },
                                contentType = { "work" },
                            ) { work ->
                                WorkCard(
                                    title = work.title,
                                    coverUrl = work.coverUrl,
                                    badge = if (work.entries.size > 1) "${work.entries.size} fontes" else null,
                                    onClick = { onWorkClick(work.refs) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueCard(item: ContinueItem, onClick: () -> Unit) {
    val progress = if (item.durationMs > 0) (item.positionMs.toFloat() / item.durationMs).coerceIn(0f, 1f) else 0f
    Card(onClick = onClick, modifier = Modifier.width(200.dp), shape = RoundedCornerShape(16.dp)) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                if (item.coverUrl != null) {
                    AsyncImage(
                        model = item.coverUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Column(Modifier.padding(8.dp)) {
                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Episódio ${item.episodeKey}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
