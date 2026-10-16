package com.mealprepper.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mealprepper.app.data.model.RecipeEntity
import com.mealprepper.app.ui.theme.ProteinColor
import java.io.File
import java.util.Locale

/** Parses "#RRGGBB" safely. */
fun parseHex(hex: String, fallback: Color = Color(0xFF1E7A5A)): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: IllegalArgumentException) {
    fallback
}

/**
 * Recipe artwork: the user's own photo if set, otherwise an offline vector illustration
 * over a soft gradient of the recipe's accent color.
 */
@Composable
fun RecipeImage(
    recipe: RecipeEntity,
    modifier: Modifier = Modifier,
    corner: Dp = 22.dp,
    illustrationPadding: Dp = 18.dp,
) {
    val accent = parseHex(recipe.accentColor)
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.linearGradient(listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0.55f)))
            ),
        contentAlignment = Alignment.Center,
    ) {
        val photo = recipe.userPhotoPath
        if (photo != null && File(photo).exists()) {
            AsyncImage(
                model = File(photo),
                contentDescription = recipe.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Image(
                painter = painterResource(FoodImageKey.from(recipe.imageKey).res),
                contentDescription = recipe.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(illustrationPadding),
            )
        }
    }
}

@Composable
fun MacroChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Circular progress ring with content in the middle. [progress] may exceed 1f. */
@Composable
fun MacroRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    stroke: Dp = 8.dp,
    content: @Composable () -> Unit = {},
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val arc = Size(this.size.width - s, this.size.height - s)
            val tl = Offset(s / 2, s / 2)
            drawArc(track, 0f, 360f, false, tl, arc, style = Stroke(s))
            drawArc(
                color, -90f, 360f * progress.coerceIn(0f, 1f), false, tl, arc,
                style = Stroke(s, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (action != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(action) }
        }
    }
}

@Composable
fun EmptyState(
    emoji: String,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            androidx.compose.material3.Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Vertical recipe card used in grids and carousels. */
@Composable
fun RecipeCard(
    recipe: RecipeEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageHeight: Dp = 128.dp,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(10.dp)) {
            Box {
                RecipeImage(recipe, Modifier.fillMaxWidth().height(imageHeight), corner = 18.dp)
                if (recipe.isFavorite) {
                    Icon(
                        Icons.Rounded.Favorite, contentDescription = "Favorite",
                        tint = Color.White,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                recipe.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Schedule, contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "${recipe.totalMinutes} min",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                Box(Modifier.size(6.dp).clip(CircleShape).background(ProteinColor))
                Spacer(Modifier.width(4.dp))
                Text(
                    "${recipe.proteinG}g protein",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** "1.5 cup", "2 whole" → "2", "0.25 lb" → "¼ lb". */
fun formatQuantity(quantity: Double, unit: String): String {
    val whole = quantity.toInt()
    val frac = quantity - whole
    val fracStr = when {
        frac < 0.06 -> ""
        frac < 0.29 -> "¼"
        frac < 0.40 -> "⅓"
        frac < 0.60 -> "½"
        frac < 0.71 -> "⅔"
        frac < 0.94 -> "¾"
        else -> null
    }
    val number = when {
        fracStr == null -> "${whole + 1}"
        whole == 0 && fracStr.isNotEmpty() -> fracStr
        fracStr.isEmpty() -> "$whole"
        else -> "$whole$fracStr"
    }
    val metric = unit in setOf("g", "ml")
    val num = if (metric) String.format(Locale.US, "%.0f", quantity) else number
    return if (unit == "whole") num else "$num $unit"
}

fun formatUsd(value: Double): String = String.format(Locale.US, "$%.2f", value)
