package com.animehub.presentation.sites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.animehub.domain.model.SiteInfo
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SitesScreen(vm: SitesViewModel) {
    val sites by vm.sites.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val accepted by vm.disclaimerAccepted.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var showDisclaimer by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SiteInfo?>(null) }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            if (event.closeDialogs) {
                showAdd = false
                editing = null
                vm.resetDialogState()
            }
            event.message?.let { snackbar.showSnackbar(it) }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Fontes") }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    vm.resetDialogState()
                    if (accepted) showAdd = true else showDisclaimer = true
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Adicionar site") },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Text(
                        "Cole o endereço de um site e o app descobre sozinho a busca, as obras, os episódios e " +
                            "os vídeos. Se a mesma obra existir em mais de um site, você escolhe de onde assistir.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(sites, key = { it.id }) { site ->
                SiteCard(
                    site = site,
                    onToggle = { vm.setEnabled(site, it) },
                    onEdit = { vm.resetDialogState(); editing = site },
                    onTest = { vm.test(site) },
                    onDelete = { vm.delete(site) },
                )
            }
        }
    }

    if (showDisclaimer) {
        AlertDialog(
            onDismissRequest = { showDisclaimer = false },
            title = { Text("Antes de adicionar um site") },
            text = {
                Text(
                    "O AnimeHub não hospeda nem distribui conteúdo e não vem com sites prontos. " +
                        "Você é responsável por usar apenas sites e conteúdos que tem direito de acessar " +
                        "e por respeitar os termos de uso de cada site."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.acceptDisclaimer()
                    showDisclaimer = false
                    showAdd = true
                }) { Text("Entendi e aceito") }
            },
            dismissButton = { TextButton(onClick = { showDisclaimer = false }) { Text("Cancelar") } },
        )
    }

    if (showAdd) {
        AddSiteDialog(
            adding = ui.adding,
            error = ui.addError,
            onAdd = vm::add,
            onDismiss = { showAdd = false },
        )
    }

    editing?.let { site ->
        EditSiteDialog(
            site = site,
            testing = ui.testing,
            testResult = ui.testResult,
            error = ui.editError,
            onSave = vm::save,
            onTest = vm::test,
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun SiteCard(
    site: SiteInfo,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = if (site.enabled) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        site.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (site.enabled) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(site.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (site.builtIn) "Exemplo com conteúdo livre (CC BY)"
                    else site.baseUrl.toHttpUrlOrNull()?.host ?: site.baseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!site.builtIn) {
                    Text(
                        if (site.searchUrl != null) "Busca detectada" else "Busca local (sem URL de busca)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Switch(checked = site.enabled, onCheckedChange = onToggle)
            if (!site.builtIn) {
                Column {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Mais opções")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Editar") }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Testar") }, onClick = { menu = false; onTest() })
                        DropdownMenuItem(text = { Text("Remover") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        }
    }
}

@Composable
private fun AddSiteDialog(
    adding: Boolean,
    error: String?,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!adding) onDismiss() },
        title = { Text("Adicionar site") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Endereço do site") },
                    placeholder = { Text("https://exemplo.com") },
                    singleLine = true,
                    enabled = !adding,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Somente https. Endereços de rede local são bloqueados por segurança.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (adding) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Analisando o site…", style = MaterialTheme.typography.bodySmall)
                }
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(url) }, enabled = url.isNotBlank() && !adding) { Text("Adicionar") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !adding) { Text("Cancelar") } },
    )
}

@Composable
private fun EditSiteDialog(
    site: SiteInfo,
    testing: Boolean,
    testResult: String?,
    error: String?,
    onSave: (SiteInfo) -> Unit,
    onTest: (SiteInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(site.name) }
    var search by remember { mutableStateOf(site.searchUrl.orEmpty()) }
    var item by remember { mutableStateOf(site.itemSelector.orEmpty()) }
    var episode by remember { mutableStateOf(site.episodeSelector.orEmpty()) }

    fun current() = site.copy(
        name = name,
        searchUrl = search.ifBlank { null },
        itemSelector = item.ifBlank { null },
        episodeSelector = episode.ifBlank { null },
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configurar site") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    search, { search = it },
                    label = { Text("URL de busca") },
                    supportingText = { Text("Use {q} onde vai o termo. Ex.: https://site.com/?s={q}") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    item, { item = it },
                    label = { Text("Seletor CSS das obras (opcional)") },
                    supportingText = { Text("Vazio = detecção automática") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    episode, { episode = it },
                    label = { Text("Seletor CSS dos episódios (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (testing) LinearProgressIndicator(Modifier.fillMaxWidth())
                testResult?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { onTest(current()) }, enabled = !testing) { Text("Testar configuração") }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(current()) }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
