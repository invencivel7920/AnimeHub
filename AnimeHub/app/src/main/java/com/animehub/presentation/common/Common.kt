package com.animehub.presentation.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    isError: Boolean = false,
) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(vertical = 8.dp),
    )
}

/** Efeito de brilho animado para áreas em carregamento. A animação só redesenha, não recompõe. */
@Composable
fun Modifier.shimmer(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerProgress",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    return this.drawBehind {
        val w = size.width.coerceAtLeast(1f)
        val shift = (progress * 3f - 1f) * w
        drawRect(
            Brush.linearGradient(
                colors = listOf(base, highlight, base),
                start = Offset(shift - w, 0f),
                end = Offset(shift, 0f),
            )
        )
    }
}

@Composable
fun SkeletonGrid(modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(120.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
        modifier = modifier.fillMaxSize(),
    ) {
        items(12) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(16.dp))
                    .shimmer(),
            )
        }
    }
}

/** Pôster com título sobre um gradiente na base, no estilo de apps de streaming. */
@Composable
fun WorkCard(
    title: String,
    coverUrl: String?,
    badge: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    title.take(1).uppercase(),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                    .padding(start = 8.dp, end = 8.dp, top = 28.dp, bottom = 8.dp),
            ) {
                Text(
                    text = title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
            }
            if (badge != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                ) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}
