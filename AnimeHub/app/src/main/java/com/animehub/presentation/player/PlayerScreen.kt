package com.animehub.presentation.player

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.core.content.ContextCompat
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
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.animehub.data.download.Downloader
import com.animehub.domain.download.DownloadPlanner
import com.animehub.domain.model.episodeKey
import com.animehub.presentation.common.SectionTitle
import com.animehub.presentation.common.languageLabel
import com.animehub.presentation.common.languageMatches
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient

private fun subtitleMime(url: String) = when {
    url.substringBefore('?').endsWith(".srt", ignoreCase = true) -> MimeTypes.APPLICATION_SUBRIP
    else -> MimeTypes.TEXT_VTT
}

private fun speedLabel(speed: Float): String =
    if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x"

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
    var audioLanguages by remember { mutableStateOf<List<String>>(emptyList()) }

    val enqueueDownload = {
        val video = state.selected
        if (video == null) {
            Toast.makeText(context, "Nenhum vídeo selecionado.", Toast.LENGTH_LONG).show()
        } else if (!DownloadPlanner.isDirectFile(video.url)) {
            Toast.makeText(
                context,
                "Esta fonte usa streaming HLS/DASH ou um link que não é arquivo direto. " +
                    "Escolha outra qualidade ou use a fonte que oferece MP4.",
                Toast.LENGTH_LONG,
            ).show()
        } else {
            val episodeKey = state.episode?.number?.episodeKey() ?: "video"
            val fileName = DownloadPlanner.fileName(
                vm.title, episodeKey, DownloadPlanner.extension(video.url),
            )
            val id = Downloader.enqueue(
                context = context.applicationContext,
                url = video.url,
                headers = video.headers,
                fileName = fileName,
                title = "${vm.title} · Ep $episodeKey",
                description = "AnimeHub · ${video.quality}",
                wifiOnly = state.downloadWifiOnly,
            )
            if (id != null) {
                Toast.makeText(context, "Download iniciado. Acompanhe pelo app Downloads ou pelas notificações.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Não foi possível iniciar o download.", Toast.LENGTH_LONG).show()
            }
        }
    }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) enqueueDownload()
    }
    val startDownload = {
        val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        else enqueueDownload()
    }

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

            override fun onTracksChanged(tracks: Tracks) {
                audioLanguages = tracks.groups
                    .filter { it.type == C.TRACK_TYPE_AUDIO }
                    .flatMap { group ->
                        (0 until group.length).mapNotNull { index ->
                            group.getTrackFormat(index).language
                        }
                    }
                    .filter { it.isNotBlank() && !it.equals("und", ignoreCase = true) }
                    .distinct()
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
        audioLanguages = emptyList()
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
        player.setMediaItem(item, state.startPositionMs)
        player.prepare()
        player.playWhenReady = true
    }

    LaunchedEffect(state.selected, state.preferredAudioLanguage, state.preferredSubtitleLanguage) {
        val builder = player.trackSelectionParameters.buildUpon()
        val audio = state.preferredAudioLanguage
        if (audio.isNullOrBlank()) builder.setPreferredAudioLanguage(null)
        else builder.setPreferredAudioLanguage(audio)
        val subtitle = state.preferredSubtitleLanguage
        val availableSubtitles = state.selected?.subtitles.orEmpty().map { it.language }
        if (subtitle.isNullOrBlank() || availableSubtitles.none { languageMatches(subtitle, it) }) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLanguage(subtitle)
        }
        player.trackSelectionParameters = builder.build()
    }

    LaunchedEffect(state.playbackSpeed) {
        player.setPlaybackSpeed(state.playbackSpeed)
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

    val subtitleOptions = remember(state.selected) {
        state.selected?.subtitles.orEmpty()
            .filter { it.language.isNotBlank() && !it.language.equals("und", ignoreCase = true) }
            .distinctBy { it.language }
    }
    val audioAvailable = remember(state.preferredAudioLanguage, audioLanguages) {
        state.preferredAudioLanguage?.let { pref -> audioLanguages.any { languageMatches(pref, it) } } == true
    }
    val subtitleAvailable = remember(state.preferredSubtitleLanguage, subtitleOptions) {
        state.preferredSubtitleLanguage?.let { pref -> subtitleOptions.any { languageMatches(pref, it.language) } } == true
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
                    Text(
                        state.episode?.title?.takeIf { it.isNotBlank() } ?: vm.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
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
                        SectionTitle("Reprodução")
                        if (state.sources.isNotEmpty()) {
                            Text("Servidor", style = MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.sources.forEach { source ->
                                    FilterChip(
                                        selected = source.sourceId == state.sourceId,
                                        onClick = { vm.switchSource(source.sourceId, player.currentPosition) },
                                        label = { Text(source.name) },
                                    )
                                }
                            }
                        }
                        if (state.videos.isNotEmpty()) {
                            HorizontalDivider()
                            Text("Qualidade", style = MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.videos.forEach { video ->
                                    FilterChip(
                                        selected = video == state.selected,
                                        onClick = { vm.select(video, player.currentPosition) },
                                        label = { Text(video.quality.ifBlank { "Automática" }) },
                                    )
                                }
                            }
                        }
                        HorizontalDivider()
                        Text("Idioma do áudio", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.preferredAudioLanguage == null || !audioAvailable,
                                onClick = { vm.setAudioLanguage(null) },
                                label = { Text("Automático") },
                            )
                            audioLanguages.forEach { language ->
                                FilterChip(
                                    selected = languageMatches(state.preferredAudioLanguage, language),
                                    onClick = { vm.setAudioLanguage(language) },
                                    label = { Text(languageLabel(language)) },
                                )
                            }
                        }
                        if (audioLanguages.isEmpty()) {
                            Text(
                                "O vídeo ainda não informou faixas de áudio alternativas.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        HorizontalDivider()
                        Text("Idioma das legendas", style = MaterialTheme.typography.labelLarge)
                        if (subtitleOptions.isEmpty()) {
                            Text(
                                "Nenhuma legenda encontrada para este vídeo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = state.preferredSubtitleLanguage == null || !subtitleAvailable,
                                    onClick = { vm.setSubtitleLanguage(null) },
                                    label = { Text("Desativadas") },
                                )
                                subtitleOptions.forEach { subtitle ->
                                    FilterChip(
                                        selected = languageMatches(state.preferredSubtitleLanguage, subtitle.language),
                                        onClick = { vm.setSubtitleLanguage(subtitle.language) },
                                        label = { Text(languageLabel(subtitle.language)) },
                                    )
                                }
                            }
                        }

                        HorizontalDivider()
                        Text("Velocidade", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                                FilterChip(
                                    selected = state.playbackSpeed == speed,
                                    onClick = { vm.setPlaybackSpeed(speed) },
                                    label = { Text(speedLabel(speed)) },
                                )
                            }
                        }
                    }
                }

                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionTitle("Baixar para assistir offline")
                        Text(
                            "Salva o vídeo na pasta Downloads do aparelho. Funciona quando a fonte oferece um arquivo " +
                                "direto (MP4, MKV ou WebM); streams HLS/DASH não são baixáveis como arquivo único.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val video = state.selected
                        val canDownload = remember(state.selected) {
                            state.selected != null && DownloadPlanner.isDirectFile(state.selected!!.url)
                        }
                        video?.let {
                            Text(
                                "Qualidade selecionada: ${it.quality.ifBlank { "Automática" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (video != null && !canDownload) {
                            Text(
                                "Esta qualidade é um stream HLS/DASH e não pode ser baixada como arquivo único. " +
                                    "Escolha uma qualidade MP4, se disponível.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Button(
                            onClick = startDownload,
                            enabled = canDownload && !state.loading,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Baixar episódio")
                        }
                    }
                }
                Text(
                    "Gire o celular para assistir em tela cheia.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
