package com.animehub

import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.animehub.domain.model.AppSettings
import com.animehub.domain.model.EntryRef
import com.animehub.presentation.details.DetailsScreen
import com.animehub.presentation.details.DetailsViewModel
import com.animehub.presentation.home.HomeScreen
import com.animehub.presentation.home.HomeViewModel
import com.animehub.presentation.library.LibraryScreen
import com.animehub.presentation.library.LibraryViewModel
import com.animehub.presentation.player.PlayerArgs
import com.animehub.presentation.player.PlayerScreen
import com.animehub.presentation.player.PlayerViewModel
import com.animehub.presentation.settings.SettingsScreen
import com.animehub.presentation.settings.SettingsViewModel
import com.animehub.presentation.sites.SitesScreen
import com.animehub.presentation.sites.SitesViewModel
import com.animehub.presentation.theme.AnimeHubTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as AnimeHubApp).container
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())

            // Segurança: impede capturas de tela/gravação quando o usuário ativa a opção.
            LaunchedEffect(settings.secureScreen) {
                if (settings.secureScreen) {
                    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            AnimeHubTheme(settings) { AnimeHubNav(container) }
        }
    }
}

private const val HOME = "home"
private const val LIBRARY = "library"
private const val SITES = "sites"
private const val SETTINGS = "settings"
private const val DETAILS = "details/{refs}"
private const val PLAYER = "player/{refs}/{sourceId}/{episodeId}/{number}/{title}?cover={cover}"

private fun detailsRoute(refs: List<EntryRef>) = "details/" + Uri.encode(EntryRef.encodeList(refs))

private fun playerRoute(
    refs: List<EntryRef>, sourceId: String, episodeId: String, number: String, title: String, cover: String?,
): String = "player/" + listOf(EntryRef.encodeList(refs), sourceId, episodeId, number, title)
    .joinToString("/") { Uri.encode(it) } + (cover?.let { "?cover=" + Uri.encode(it) } ?: "")

private inline fun <reified T : ViewModel> vmFactory(crossinline create: () -> T): ViewModelProvider.Factory =
    viewModelFactory { initializer { create() } }

@Composable
private fun AnimeHubNav(container: AppContainer) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBottomBar = route == HOME || route == LIBRARY || route == SITES || route == SETTINGS

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = route == HOME, onClick = { nav.navigateTop(HOME) },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) }, label = { Text("Início") },
                    )
                    NavigationBarItem(
                        selected = route == LIBRARY, onClick = { nav.navigateTop(LIBRARY) },
                        icon = { Icon(Icons.Default.Favorite, contentDescription = null) }, label = { Text("Biblioteca") },
                    )
                    NavigationBarItem(
                        selected = route == SITES, onClick = { nav.navigateTop(SITES) },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) }, label = { Text("Fontes") },
                    )
                    NavigationBarItem(
                        selected = route == SETTINGS, onClick = { nav.navigateTop(SETTINGS) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) }, label = { Text("Ajustes") },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = HOME, modifier = Modifier.padding(padding)) {
            composable(HOME) {
                val vm: HomeViewModel = viewModel(factory = vmFactory { HomeViewModel(container.repository, container.siteRepository) })
                HomeScreen(
                    vm,
                    onWorkClick = { refs -> nav.navigate(detailsRoute(refs)) },
                    onOpenSites = { nav.navigateTop(SITES) },
                )
            }
            composable(LIBRARY) {
                val vm: LibraryViewModel = viewModel(factory = vmFactory { LibraryViewModel(container.repository) })
                LibraryScreen(vm, onWorkClick = { refs -> nav.navigate(detailsRoute(refs)) })
            }
            composable(SITES) {
                val vm: SitesViewModel = viewModel(factory = vmFactory { SitesViewModel(container.siteRepository, container.settings) })
                SitesScreen(vm)
            }
            composable(SETTINGS) {
                val vm: SettingsViewModel = viewModel(factory = vmFactory { SettingsViewModel(container.settings) })
                SettingsScreen(vm)
            }
            composable(DETAILS, arguments = listOf(navArgument("refs") { type = NavType.StringType })) { entry ->
                val refs = EntryRef.decodeList(entry.arguments?.getString("refs").orEmpty())
                val vm: DetailsViewModel = viewModel(
                    factory = vmFactory { DetailsViewModel(container.repository, container.settings, refs) }
                )
                DetailsScreen(
                    vm,
                    onBack = { nav.popBackStack() },
                    onEpisodeClick = { state, episode ->
                        val selected = state.selected ?: return@DetailsScreen
                        nav.navigate(
                            playerRoute(
                                refs = state.entries.map { it.ref },
                                sourceId = selected.sourceId,
                                episodeId = episode.id,
                                number = episode.number.toString(),
                                title = state.title,
                                cover = state.coverUrl,
                            )
                        )
                    },
                )
            }
            composable(
                PLAYER,
                arguments = listOf(
                    navArgument("refs") { type = NavType.StringType },
                    navArgument("sourceId") { type = NavType.StringType },
                    navArgument("episodeId") { type = NavType.StringType },
                    navArgument("number") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType },
                    navArgument("cover") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                val a = entry.arguments
                val args = PlayerArgs(
                    refs = EntryRef.decodeList(a?.getString("refs").orEmpty()),
                    title = a?.getString("title").orEmpty(),
                    coverUrl = a?.getString("cover"),
                    startSourceId = a?.getString("sourceId").orEmpty(),
                    startEpisodeId = a?.getString("episodeId").orEmpty(),
                    startNumber = a?.getString("number")?.toFloatOrNull() ?: 0f,
                )
                val vm: PlayerViewModel = viewModel(
                    factory = vmFactory { PlayerViewModel(container.repository, container.settings, args) }
                )
                PlayerScreen(vm, container.httpClient, onBack = { nav.popBackStack() })
            }
        }
    }
}

private fun NavController.navigateTop(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
