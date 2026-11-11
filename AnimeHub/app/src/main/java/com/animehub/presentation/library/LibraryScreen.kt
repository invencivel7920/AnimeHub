package com.animehub.presentation.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.animehub.domain.model.EntryRef
import com.animehub.domain.model.Work
import com.animehub.domain.repository.AnimeRepository
import com.animehub.presentation.common.EmptyState
import com.animehub.presentation.common.WorkCard
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class LibraryViewModel(repo: AnimeRepository) : ViewModel() {
    val favorites: StateFlow<List<Work>> =
        repo.favorites().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(vm: LibraryViewModel, onWorkClick: (List<EntryRef>) -> Unit) {
    val items by vm.favorites.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { TopAppBar(title = { Text("Biblioteca") }) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (items.isEmpty()) {
            EmptyState(
                title = "Sua biblioteca está vazia",
                message = "Toque no coração em uma obra para guardá-la aqui.",
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(120.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                items(items, key = { it.key }) { work ->
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
