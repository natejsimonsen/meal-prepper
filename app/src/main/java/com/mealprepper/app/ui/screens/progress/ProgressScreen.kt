package com.mealprepper.app.ui.screens.progress

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mealprepper.app.ui.components.SectionHeader
import com.mealprepper.app.ui.components.appViewModel
import com.mealprepper.app.ui.theme.Apricot
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

private fun lb(value: Double): String = String.format(Locale.US, "%.1f", value)

private fun yards(value: Int): String = String.format(Locale.US, "%,d", value)

@Composable
fun ProgressScreen(onBack: () -> Unit) {
    val vm = appViewModel { c -> ProgressViewModel(c.mealRepository, c.profileRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    var showWeightDialog by rememberSaveable { mutableStateOf(false) }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(top = top + 8.dp, bottom = bottom + 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "top") {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.width(4.dp))
                Column {
                    Text("Progress", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Last 30 days",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item(key = "quick") {
            QuickActions(
                swamToday = (state.today?.swimYards ?: 0) > 0,
                defaultYards = state.profile.swimYardsPerDay,
                onLogSwim = vm::logDefaultSwim,
                onAddYards = { vm.addYards(250) },
                onWeight = { showWeightDialog = true },
            )
        }

        item(key = "stats") { StatsGrid(state) }

        item(key = "swimChart") {
            ChartCard(
                title = "Swim yards",
                subtitle = "Today: ${yards(state.today?.swimYards ?: 0)} yd · ${state.today?.swimMinutes ?: 0} min",
                action = if ((state.today?.swimYards ?: 0) > 0) "Clear today" else null,
                onAction = vm::clearSwim,
            ) {
                SwimBarChart(
                    values = state.days.map { it.yards },
                    goal = state.profile.swimYardsPerDay,
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                )
                AxisLabels()
            }
        }

        item(key = "weightChart") {
            ChartCard(
                title = "Weight trend",
                subtitle = state.latestWeight?.let { "Latest: ${lb(it)} lb · goal ${lb(state.profile.goalWeightLb)} lb" }
                    ?: "Goal ${lb(state.profile.goalWeightLb)} lb",
                action = "Log weight",
                onAction = { showWeightDialog = true },
            ) {
                WeightChart(
                    weights = state.days.map { it.weightLb },
                    modifier = Modifier.fillMaxWidth().height(170.dp),
                )
                AxisLabels()
            }
        }

        item(key = "projection") { ProjectionCard(state) }

        item(key = "water") {
            WaterCard(
                cups = state.today?.waterCups ?: 0,
                target = state.targets?.waterCups ?: 8,
                onChange = vm::changeWater,
            )
        }
    }

    if (showWeightDialog) {
        WeightDialog(
            initial = state.latestWeight ?: state.profile.weightLb,
            onDismiss = { showWeightDialog = false },
            onSave = { value ->
                vm.logWeight(value)
                showWeightDialog = false
            },
        )
    }
}

@Composable
private fun QuickActions(
    swamToday: Boolean,
    defaultYards: Int,
    onLogSwim: () -> Unit,
    onAddYards: () -> Unit,
    onWeight: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        QuickTile(
            emoji = if (swamToday) "✅" else "🏊",
            label = if (swamToday) "Swim logged" else "Log ${yards(defaultYards)} yd",
            highlighted = !swamToday,
            enabled = !swamToday,
            onClick = onLogSwim,
            modifier = Modifier.weight(1f),
        )
        QuickTile("➕", "+250 yd", highlighted = false, enabled = true, onClick = onAddYards, modifier = Modifier.weight(1f))
        QuickTile("⚖️", "Weigh in", highlighted = false, enabled = true, onClick = onWeight, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun QuickTile(
    emoji: String,
    label: String,
    highlighted: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier,
    ) {
        Column(
            Modifier.padding(vertical = 16.dp, horizontal = 8.dp).animateContentSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(emoji, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@Composable
private fun StatsGrid(state: ProgressUiState) {
    val change = state.weightChange
    val changeText = when {
        change == null -> "—"
        abs(change) < 0.05 -> "±0 lb"
        change < 0 -> "−${lb(-change)} lb"
        else -> "+${lb(change)} lb"
    }
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("This week", "${yards(state.yardsWeek)} yd", "last 7 days", Modifier.weight(1f))
            StatTile("This month", "${yards(state.yardsMonth)} yd", "last 30 days", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                "Streak",
                if (state.streak == 1) "1 day" else "${state.streak} days",
                if (state.streak > 0) "🔥 keep it going" else "swim today to start",
                Modifier.weight(1f),
            )
            StatTile("Weight change", changeText, "vs. 30 days ago", Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, caption: String, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChartCard(
    title: String,
    subtitle: String,
    action: String?,
    onAction: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    ) {
        Column(Modifier.padding(top = 8.dp, bottom = 20.dp)) {
            SectionHeader(title = title, action = action, onAction = onAction)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(16.dp))
            Column(Modifier.padding(horizontal = 20.dp)) { content() }
        }
    }
}

@Composable
private fun AxisLabels() {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text("30 days ago", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text("Today", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Animates 0 → 1 once when first shown. */
@Composable
private fun rememberReveal(): Float {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val reveal by animateFloatAsState(if (started) 1f else 0f, tween(durationMillis = 900), label = "reveal")
    return reveal
}

@Composable
private fun SwimBarChart(values: List<Int>, goal: Int, modifier: Modifier = Modifier) {
    val reveal = rememberReveal()
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val goalColor = MaterialTheme.colorScheme.outline
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val maxY = max(values.maxOrNull() ?: 0, goal).coerceAtLeast(1).toFloat() * 1.1f
        val slot = size.width / values.size
        val barW = slot * 0.62f
        val minH = 3.dp.toPx()
        values.forEachIndexed { i, v ->
            val left = i * slot + (slot - barW) / 2f
            if (v <= 0) {
                drawRoundRect(
                    color = track,
                    topLeft = Offset(left, size.height - minH),
                    size = Size(barW, minH),
                    cornerRadius = CornerRadius(minH / 2f),
                )
            } else {
                val h = max(minH, size.height * (v / maxY) * reveal)
                val isToday = i == values.lastIndex
                drawRoundRect(
                    color = if (isToday) Apricot else Apricot.copy(alpha = 0.6f),
                    topLeft = Offset(left, size.height - h),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(barW / 2f),
                )
            }
        }
        if (goal > 0) {
            val y = size.height - size.height * (goal / maxY)
            drawLine(
                color = goalColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
            )
        }
    }
}

@Composable
private fun WeightChart(weights: List<Double?>, modifier: Modifier = Modifier) {
    val points = weights.mapIndexedNotNull { i, w -> w?.let { i to it } }
    if (points.size < 2) {
        Box(
            modifier
                .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Weigh in a couple of times to see your trend. Same time of day works best (morning, after the bathroom).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val minW = points.minOf { it.second }
    val maxW = points.maxOf { it.second }
    val reveal = rememberReveal()
    val line = MaterialTheme.colorScheme.primary
    Row(modifier) {
        Column(
            Modifier.fillMaxHeight().padding(end = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(lb(maxW), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            Text(lb(minW), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
        Canvas(Modifier.weight(1f).fillMaxHeight()) {
            val range = (maxW - minW).takeIf { it > 0.1 } ?: 1.0
            val lastIndex = (weights.size - 1).coerceAtLeast(1)
            val padY = 8.dp.toPx()
            fun x(i: Int): Float = size.width * i / lastIndex
            fun y(w: Double): Float = padY + (size.height - 2 * padY) * (1f - ((w - minW) / range).toFloat())

            val path = Path()
            points.forEachIndexed { k, (i, w) ->
                if (k == 0) {
                    path.moveTo(x(i), y(w))
                } else {
                    val (pi, pw) = points[k - 1]
                    val cx = (x(pi) + x(i)) / 2f
                    path.cubicTo(cx, y(pw), cx, y(w), x(i), y(w))
                }
            }
            val fill = Path().apply {
                addPath(path)
                lineTo(x(points.last().first), size.height)
                lineTo(x(points.first().first), size.height)
                close()
            }
            clipRect(right = size.width * reveal) {
                drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.22f), Color.Transparent)))
                drawPath(
                    path,
                    color = line,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                val (li, lw) = points.last()
                drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(x(li), y(lw)))
                drawCircle(line, radius = 4.dp.toPx(), center = Offset(x(li), y(lw)))
            }
        }
    }
}

@Composable
private fun ProjectionCard(state: ProgressUiState) {
    val p = state.profile
    val (emoji, headline, body) = when {
        state.goalReached -> Triple(
            "🎉", "Goal reached",
            "You're at or under ${lb(p.goalWeightLb)} lb. Consider switching to Maintain in Profile.",
        )
        state.projectedGoalDate != null -> Triple(
            "📅",
            "On pace for ${lb(p.goalWeightLb)} lb by ${
                state.projectedGoalDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
            }",
            "At ${p.pace.label.lowercase()} from ${lb(state.latestWeight ?: p.weightLb)} lb. " +
                "Real progress wobbles week to week; the trend is what counts.",
        )
        else -> Triple(
            "⚖️", "Maintaining",
            "Pick a Gentle or Steady pace in Profile to see a projected goal date.",
        )
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(headline, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun WaterCard(cups: Int, target: Int, onChange: (Int) -> Unit) {
    val progress by animateFloatAsState(
        (cups.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f),
        label = "water",
    )
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("💧 Water today", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "$cups of $target cups",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalIconButton(onClick = { onChange(-1) }, enabled = cups > 0) {
                    Icon(Icons.Rounded.Remove, contentDescription = "One less cup")
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalIconButton(onClick = { onChange(1) }) {
                    Icon(Icons.Rounded.Add, contentDescription = "One more cup")
                }
            }
            Spacer(Modifier.height(14.dp))
            val fillColor = MaterialTheme.colorScheme.tertiary
            val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                val r = CornerRadius(size.height / 2f)
                drawRoundRect(trackColor, cornerRadius = r)
                if (progress > 0f) {
                    drawRoundRect(fillColor, size = Size(size.width * progress, size.height), cornerRadius = r)
                }
            }
            AnimatedVisibility(visible = cups >= target) {
                Text(
                    "Nice, you've hit your water target.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun WeightDialog(initial: Double, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var text by rememberSaveable { mutableStateOf(lb(initial)) }
    val value = text.toDoubleOrNull()
    val valid = value != null && value in 70.0..600.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Today's weight") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { new -> text = new.filter { it.isDigit() || it == '.' }.take(6) },
                label = { Text("Weight") },
                suffix = { Text("lb") },
                singleLine = true,
                isError = !valid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = MaterialTheme.shapes.medium,
            )
        },
        confirmButton = {
            TextButton(onClick = { if (value != null && valid) onSave(value) }, enabled = valid) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
