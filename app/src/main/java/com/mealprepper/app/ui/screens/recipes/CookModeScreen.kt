package com.mealprepper.app.ui.screens.recipes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mealprepper.app.data.model.IngredientEntity
import com.mealprepper.app.ui.components.EmptyState
import com.mealprepper.app.ui.components.RecipeImage
import com.mealprepper.app.ui.components.appViewModel
import com.mealprepper.app.ui.components.formatQuantity
import com.mealprepper.app.ui.theme.Apricot
import com.mealprepper.app.ui.theme.Basil
import com.mealprepper.app.ui.theme.BasilLight
import com.mealprepper.app.ui.theme.Lemon
import com.mealprepper.app.ui.theme.Plum
import com.mealprepper.app.ui.theme.Tomato
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookModeScreen(recipeId: String, onBack: () -> Unit) {
    val vm = appViewModel(key = "cook-$recipeId") { c -> CookModeViewModel(c.mealRepository, recipeId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val timers by vm.timers.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    // Keep the screen awake while cooking.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Haptic nudge whenever a timer finishes (no VIBRATE permission needed).
    val finishedIds = timers.values.filter { it.finished }.map { it.id }.toSet()
    var notifiedIds by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(finishedIds) {
        val fresh = finishedIds - notifiedIds
        notifiedIds = finishedIds
        if (fresh.isNotEmpty()) {
            repeat(4) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                delay(180)
            }
        }
    }

    val data = state.data
    if (data == null) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            if (state.loading) {
                CircularProgressIndicator()
            } else {
                EmptyState("🍽️", "Recipe not found", "It may have been deleted.", actionLabel = "Go back", onAction = onBack)
            }
        }
        return
    }
    val recipe = data.recipe
    val steps = recipe.steps
    val pageCount = steps.size + 1 // last page = celebration
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    var showIngredients by rememberSaveable { mutableStateOf(false) }
    val onFinishPage = pagerState.currentPage >= steps.size

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        ) {
            // Top bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.Close, contentDescription = "Exit cook mode") }
                Text(
                    recipe.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showIngredients = true }) {
                    Icon(Icons.AutoMirrored.Rounded.List, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ingredients")
                }
            }

            // Progress
            val progress by animateFloatAsState(
                targetValue = if (steps.isEmpty()) 1f else ((pagerState.currentPage + 1).coerceAtMost(steps.size)).toFloat() / steps.size,
                label = "progress",
            )
            Column(Modifier.padding(horizontal = 24.dp)) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (onFinishPage) "All done!" else "Step ${pagerState.currentPage + 1} of ${steps.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Timers running on other steps stay visible.
            val activeElsewhere = timers.values
                .filter { !it.finished && it.stepIndex != pagerState.currentPage }
                .sortedBy { it.remainingSeconds }
            if (activeElsewhere.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 10.dp),
                ) {
                    items(activeElsewhere, key = { it.id }) { t ->
                        Surface(
                            onClick = { scope.launch { pagerState.animateScrollToPage(t.stepIndex) } },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Step ${t.stepIndex + 1} · ${formatCountdown(t.remainingSeconds)}" +
                                        if (t.running) "" else " (paused)",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                if (page < steps.size) {
                    StepPage(
                        number = page + 1,
                        text = steps[page],
                        durations = state.stepTimers.getOrNull(page) ?: emptyList(),
                        timers = timers,
                        onStart = { i, d -> vm.start(page, i, d) },
                        onPause = vm::pause,
                        onReset = vm::reset,
                    )
                } else {
                    FinishPage(title = recipe.title, fridgeDays = if (recipe.mealPrepFriendly) recipe.fridgeDays else 0, onDone = onBack) {
                        RecipeImage(recipe, Modifier.size(150.dp), corner = 40.dp)
                    }
                }
            }

            // Bottom navigation
            if (!onFinishPage) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                        enabled = pagerState.currentPage > 0,
                        modifier = Modifier.height(58.dp),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Previous step")
                    }
                    Spacer(Modifier.width(12.dp))
                    val last = pagerState.currentPage == steps.size - 1
                    Button(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Text(if (last) "Finish" else "Next step", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            if (last) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                        )
                    }
                }
            }
        }

        // Prominent "timer done" banner.
        val finished = timers.values.filter { it.finished }.sortedBy { it.stepIndex }
        AnimatedVisibility(
            visible = finished.isNotEmpty(),
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .systemBarsPadding()
                .padding(16.dp),
        ) {
            TimerDoneBanner(
                labels = finished.map { "Step ${it.stepIndex + 1} · ${it.label}" },
                onDismiss = { finished.forEach { vm.reset(it.id) } },
            )
        }
    }

    if (showIngredients) {
        ModalBottomSheet(
            onDismissRequest = { showIngredients = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            IngredientsSheetContent(
                servings = recipe.servings,
                ingredients = data.ingredients.sortedBy { it.position },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepPage(
    number: Int,
    text: String,
    durations: List<ParsedDuration>,
    timers: Map<String, CookTimer>,
    onStart: (Int, ParsedDuration) -> Unit,
    onPause: (String) -> Unit,
    onReset: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Text(
            "STEP $number",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Medium,
                lineHeight = MaterialTheme.typography.headlineMedium.lineHeight,
            ),
        )
        if (durations.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                durations.forEachIndexed { i, d ->
                    val id = CookModeViewModel.timerId(number - 1, i)
                    TimerChip(
                        duration = d,
                        timer = timers[id],
                        onStart = { onStart(i, d) },
                        onPause = { onPause(id) },
                        onReset = { onReset(id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TimerChip(
    duration: ParsedDuration,
    timer: CookTimer?,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
) {
    val running = timer?.running == true
    val finished = timer?.finished == true
    val container = when {
        finished -> MaterialTheme.colorScheme.tertiaryContainer
        timer != null -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val content = when {
        finished -> MaterialTheme.colorScheme.onTertiaryContainer
        timer != null -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        onClick = {
            when {
                finished -> onReset()
                running -> onPause()
                else -> onStart()
            }
        },
        shape = RoundedCornerShape(22.dp),
        color = container,
        contentColor = content,
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = if (timer != null && !finished) 6.dp else 18.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                when {
                    finished -> Icons.Rounded.Check
                    running -> Icons.Rounded.Pause
                    timer != null -> Icons.Rounded.PlayArrow
                    else -> Icons.Rounded.Timer
                },
                contentDescription = null,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    finished -> "Done · ${duration.label}"
                    timer != null -> formatCountdown(timer.remainingSeconds)
                    else -> "Start ${duration.label} timer"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            if (timer != null && !finished) {
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onReset, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Replay, contentDescription = "Reset timer", tint = content)
                }
            }
        }
    }
}

@Composable
private fun TimerDoneBanner(labels: List<String>, onDismiss: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "pulseScale",
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(pulse),
        shape = RoundedCornerShape(26.dp),
        color = Tomato,
        contentColor = Color.White,
        shadowElevation = 10.dp,
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("⏰", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Time's up!", style = MaterialTheme.typography.titleLarge)
                Text(labels.joinToString("\n"), style = MaterialTheme.typography.bodyMedium)
            }
            FilledTonalButton(onClick = onDismiss) { Text("Got it") }
        }
    }
}

@Composable
private fun IngredientsSheetContent(servings: Int, ingredients: List<IngredientEntity>) {
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        Text(
            "Ingredients",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Text(
            "For $servings serving${if (servings == 1) "" else "s"}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(ingredients, key = { "${it.id}-${it.position}-${it.name}" }) { ing ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(ing.aisle.emoji, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        ing.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        formatQuantity(ing.quantity, ing.unit),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun FinishPage(
    title: String,
    fridgeDays: Int,
    onDone: () -> Unit,
    image: @Composable () -> Unit,
) {
    val pop = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) {
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    Box(Modifier.fillMaxSize()) {
        Confetti(Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.scale(pop.value)) { image() }
            Spacer(Modifier.height(24.dp))
            Text("Nice work, chef! 🎉", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                if (fridgeDays > 0) {
                    "$title is ready. Portion it out — it keeps $fridgeDays days in the fridge."
                } else {
                    "$title is ready. Enjoy it while it's hot!"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Text("Done", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private class ConfettiParticle(
    val x: Float,
    val offset: Float,
    val speed: Int,
    val sizeDp: Float,
    val color: Color,
    val spin: Float,
    val phase: Float,
)

/** Lightweight confetti rain drawn on a Canvas. */
@Composable
private fun Confetti(modifier: Modifier = Modifier) {
    val particles = remember {
        val colors = listOf(Basil, BasilLight, Apricot, Plum, Tomato, Lemon)
        val rnd = Random(42)
        List(80) {
            ConfettiParticle(
                x = rnd.nextFloat(),
                offset = rnd.nextFloat(),
                speed = 1 + rnd.nextInt(2), // integer so the loop wraps seamlessly
                sizeDp = 6f + rnd.nextFloat() * 7f,
                color = colors[rnd.nextInt(colors.size)],
                spin = (rnd.nextFloat() * 2f - 1f) * 720f,
                phase = rnd.nextFloat() * 2f * PI.toFloat(),
            )
        }
    }
    val t by rememberInfiniteTransition(label = "confetti").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart),
        label = "confettiT",
    )
    Canvas(modifier) {
        val sway = 22.dp.toPx()
        particles.forEach { p ->
            val progress = (t * p.speed + p.offset) % 1f
            val y = -40f + progress * (size.height + 80f)
            val x = p.x * size.width + sin(progress * 4f * PI.toFloat() + p.phase) * sway
            val w = p.sizeDp.dp.toPx()
            rotate(degrees = p.spin * progress, pivot = Offset(x, y)) {
                drawRect(
                    color = p.color.copy(alpha = 0.9f),
                    topLeft = Offset(x - w / 2f, y - w / 4f),
                    size = Size(w, w / 2f),
                )
            }
        }
    }
}
