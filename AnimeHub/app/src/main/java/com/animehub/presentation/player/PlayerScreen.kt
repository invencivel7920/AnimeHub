package com.animehub.presentation.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.app.DownloadManager
import android.os.Environment
import android.widget.Toast
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.animehub.domain.model.episodeKey
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient

private fun subtitleMime(url: String) = when {
    url.substringBefore('?').endsWith(".srt", ignoreCase = true) -> MimeTypes.APPLICATION_SUBRIP
    else -> MimeTypes.TEXT_VTT
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(vm: PlayerViewModel, httpClient: OkHttpClient, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var selectedSubtitle by remember { mutableStateOf<String?>(null) }

    // O player usa o mesmo cliente seguro (HTTPS, DNS que bloqueia rede local) e envia o Referer da página.
    val dataSourceFactory = remember { OkHttpDataSource.Factory(httpClient) }
    val player = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
    }

    // Em paisagem, esconde as barras do sistema para assistir em tela cheia.
    DisposableEffect(landscape) {
        val window = context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (landscape && controller != null) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) vm.onEnded()
            }

            override fun onPlayerError(error: PlaybackException) {
                vm.onPlaybackError(error.errorCodeName, player.currentPosition)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            vm.saveProgress(player.currentPosition, player.duration)
            player.release()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.pause() }

    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            if (player.isPlaying) vm.saveProgress(player.currentPosition, player.duration)
        }
    }

    LaunchedEffect(state.selected) {
        val video = state.selected ?: return@LaunchedEffect
        dataSourceFactory.setDefaultRequestProperties(video.headers)
        val item = MediaItem.Builder()
            .setUri(video.url)
            .setSubtitleConfigurations(
                video.subtitles.map {
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(it.url))
                        .setMimeType(subtitleMime(it.url))
                        .setLanguage(it.language)
                        .setSelectionFlags(0)
                        .build()
                }
            )
            .build()
        selectedSubtitle = null
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        player.setMediaItem(item, state.startPositionMs)
        player.prepare()
        player.playWhenReady = true
    }

    @Composable
    fun VideoSurface(modifier: Modifier) {
        Box(modifier.background(Color.Black)) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        keepScreenOn = true // não apaga a tela durante o episódio
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            if (landscape && state.error != null) {
                Text(
                    state.error!!,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            }
        }
    }

    if (landscape) {
        VideoSurface(Modifier.fillMaxSize())
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        vm.title + (state.episode?.let { " · Ep ${it.number.episodeKey()}" } ?: ""),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            VideoSurface(Modifier.fillMaxWidth().aspectRatio(16f / 9f))

            Column(Modifier.padding(horizontal = 16.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(state.episode?.let { "Episódio ${it.number.episodeKey()}" } ?: "Reprodução", style = MaterialTheme.typography.titleLarge)
                    Text(state.episode?.title?.takeIf { it.isNotBlank() } ?: vm.title,
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = vm::previous, enabled = state.hasPrev, modifier = Modifier.weight(1f)) { Text("Anterior") }
                    Button(onClick = vm::next, enabled = state.hasNext, modifier = Modifier.weight(1f)) { Text("Próximo") }
                }
                state.error?.let {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(it, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(14.dp))
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Configurações de reprodução", style = MaterialTheme.typography.titleMedium)
                        if (state.sources.isNotEmpty()) {
                            Text("Servidor", style = MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.sources.forEach { source ->
                                    FilterChip(selected = source.sourceId == state.sourceId,
                                        onClick = { vm.switchSource(source.sourceId, player.currentPosition) }, label = { Text(source.name) })
                                }
                            }
                        }
                        if (state.videos.isNotEmpty()) {
                            Divider()
                            Text("Qualidade", style = MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.videos.forEach { video ->
                                    FilterChip(selected = video == state.selected, onClick = { vm.select(video, player.currentPosition) },
                                        label = { Text(video.quality.ifBlank { "Automática" }) })
                                }
                            }
                        }
                        val subtitleOptions = state.selected?.subtitles.orEmpty()
                        Divider()
                        Text("Idioma das legendas", style = MaterialTheme.typography.labelLarge)
                        if (subtitleOptions.isEmpty()) {
                            Text("Nenhuma legenda encontrada para este vídeo.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = selectedSubtitle == null, onClick = {
                                    selectedSubtitle = null
                                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                                }, label = { Text("Desativadas") })
                                subtitleOptions.forEach { subtitle ->
                                    val language = subtitle.language.ifBlank { "Desconhecida" }
                                    FilterChip(selected = selectedSubtitle == subtitle.url, onClick = {
                                        selectedSubtitle = subtitle.url
                                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                            .setPreferredTextLanguage(language).build()
                                    }, label = { Text(language.uppercase()) })
                                }
                            }
                        }
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Assistir offline", style = MaterialTheme.typography.titleMedium)
                        Text("O download funciona quando a fonte oferece um arquivo direto (MP4, MKV ou WebM). Streams HLS/DASH podem não ser baixáveis como arquivo único.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = {
                            val video = state.selected ?: return@Button
                            val cleanUrl = video.url.substringBefore('?').lowercase()
                            if (cleanUrl.endsWith(".m3u8") || cleanUrl.endsWith(".mpd")) {
                                Toast.makeText(context, "Esta fonte usa streaming HLS/DASH; não há um arquivo único para baixar.", Toast.LENGTH_LONG).show()
                            } else if (Uri.parse(video.url).scheme != "https") {
                                Toast.makeText(context, "Por segurança, só é permitido baixar arquivos por HTTPS.", Toast.LENGTH_LONG).show()
                            } else try {
                                val request = DownloadManager.Request(Uri.parse(video.url))
                                    .setTitle("${vm.title} · Ep ${state.episode?.number?.episodeKey() ?: ""}")
                                    .setDescription("AnimeHub · ${video.quality}")
                                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                    .setAllowedOverMetered(true).setAllowedOverRoaming(false)
                                video.headers.forEach { (name, value) -> request.addRequestHeader(name, value) }
                                val ext = when {
                                    cleanUrl.endsWith(".mkv") -> "mkv"
                                    cleanUrl.endsWith(".webm") -> "webm"
                                    else -> "mp4"
                                }
                                val safeTitle = vm.title.replace(Regex("[^A-Za-z0-9._-]"), "_").take(45)
                                val ep = state.episode?.number?.episodeKey()?.replace('.', '_') ?: "video"
                                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "AnimeHub_${safeTitle}_E${ep}.$ext")
                                (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                                Toast.makeText(context, "Download iniciado. Acompanhe nas notificações.", Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Não foi possível iniciar: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }, enabled = state.selected != null && !state.loading, modifier = Modifier.fillMaxWidth()) {
                            Text("Baixar episódio")
                        }
                    }
                }
                Text("Gire o celular para assistir em tela cheia.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
