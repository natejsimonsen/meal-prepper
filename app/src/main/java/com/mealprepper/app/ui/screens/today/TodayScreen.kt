package com.mealprepper.app.ui.screens.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mealprepper.app.data.model.DailyLogEntity
import com.mealprepper.app.data.model.NutritionTargets
import com.mealprepper.app.data.model.PlannedMeal
import com.mealprepper.app.data.model.UserProfile
import com.mealprepper.app.data.repo.ShoppingSummary
import com.mealprepper.app.ui.components.EmptyState
import com.mealprepper.app.ui.components.MacroRing
import com.mealprepper.app.ui.components.RecipeCard
import com.mealprepper.app.ui.components.RecipeImage
import com.mealprepper.app.ui.components.SectionHeader
import com.mealprepper.app.ui.components.appViewModel
import com.mealprepper.app.ui.components.formatUsd
import com.mealprepper.app.ui.theme.BasilDark
import com.mealprepper.app.ui.theme.CaloriesColor
import com.mealprepper.app.ui.theme.CarbsColor
import com.mealprepper.app.ui.theme.FatColor
import com.mealprepper.app.ui.theme.Plum
import com.mealprepper.app.ui.theme.ProteinColor
import com.mealprepper.app.ui.theme.Tomato
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TodayScreen(
    contentPadding: PaddingValues,
    onOpenRecipe: (String) -> Unit,
    onOpenPlan: () -> Unit,
    onOpenList: () -> Unit,
    onOpenTonight: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenProgress: () -> Unit,
) {
    val vm = appViewModel { c -> TodayViewModel(c.mealRepository, c.profileRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val hour = remember { LocalTime.now().hour }
    val isLate = hour >= 20 || hour < 4
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            top = top + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "header") { Header(state.profile.name, hour, onOpenProfile) }

        if (isLate) {
            item(key = "tonightHero") { TonightHero(onOpenTonight) }
        }

        item(key = "macros") { MacroDashboard(state.targets, state.planned, state.eaten) }

        item(key = "mealsHeader") {
            SectionHeader(
                title = "Today's meals",
                action = if (state.meals.isNotEmpty()) "Full week" else null,
                onAction = onOpenPlan,
            )
        }
        when {
            state.loading -> item(key = "mealsLoading") {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
            }
            state.meals.isEmpty() -> item(key = "mealsEmpty") {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        EmptyState(
                            emoji = "🗓️",
                            title = "No meals planned yet",
                            body = "One tap builds 7 days of easy, high-protein meals with leftovers baked in.",
                            actionLabel = if (state.generating) "Building your week…" else "Generate my week",
                            onAction = vm::generateWeek,
                        )
                        if (state.generating) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.TopEnd).padding(20.dp).size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
            }
            else -> items(state.meals, key = { it.entry.id }) { meal ->
                MealRow(
                    meal = meal,
                    onOpen = { onOpenRecipe(meal.recipe.id) },
                    onToggle = { checked -> vm.setEaten(meal.entry.id, checked) },
                )
            }
        }

        if (!isLate) {
            item(key = "tonightSmall") { TonightSmallCard(onOpenTonight) }
        }

        item(key = "swim") {
            SwimCard(
                log = state.todayLog,
                profile = state.profile,
                streak = state.swimStreak,
                onLog = vm::logSwim,
                onUndo = vm::undoSwim,
                onOpen = onOpenProgress,
            )
        }

        item(key = "list") { ListSummaryCard(state.shopping, state.profile.weeklyBudgetUsd, onOpenList) }

        if (state.picks.isNotEmpty()) {
            item(key = "picks") {
                Column {
                    SectionHeader(title = "Quick picks", action = "See all", onAction = onOpenTonight)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.picks, key = { it.id }) { recipe ->
                            RecipeCard(
                                recipe = recipe,
                                onClick = { onOpenRecipe(recipe.id) },
                                modifier = Modifier.width(172.dp),
                                imageHeight = 116.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(name: String, hour: Int, onOpenProfile: () -> Unit) {
    val greeting = when (hour) {
        in 4..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..20 -> "Good evening"
        else -> "Late night"
    }
    val date = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()))
    }
    val first = name.trim()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                date.uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (first.isEmpty()) "$greeting 👋" else "$greeting,\n$first 👋",
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        Spacer(Modifier.width(12.dp))
        Surface(
            onClick = onOpenProfile,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(52.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (first.isNotEmpty()) {
                    Text(
                        first.take(1).uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    Icon(
                        Icons.Rounded.Person,
                        contentDescription = "Profile",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun TonightHero(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    ) {
        Box(
            Modifier
                .background(Brush.linearGradient(listOf(Plum, BasilDark)))
                .padding(24.dp),
        ) {
            Column {
                Text("🌙", style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Late night?",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.8f),
                )
                Text(
                    "10-minute dinners →",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "High protein, barely any dishes. Eat, then bed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun TonightSmallCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🌙", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Short on time?", style = MaterialTheme.typography.labelMedium)
                Text("10-minute dinners", style = MaterialTheme.typography.titleMedium)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
private fun MacroDashboard(targets: NutritionTargets?, planned: MacroTotals, eaten: MacroTotals) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    ) {
        Column(Modifier.padding(20.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Today's fuel", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (targets != null) {
                    val left = (targets.calories - eaten.calories).coerceAtLeast(0)
                    Text(
                        "$left kcal to go",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            if (targets == null) {
                Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else {
                Row(Modifier.fillMaxWidth()) {
                    RingStat("Calories", eaten.calories, planned.calories, targets.calories, "", CaloriesColor, Modifier.weight(1f))
                    RingStat("Protein", eaten.proteinG, planned.proteinG, targets.proteinG, "g", ProteinColor, Modifier.weight(1f))
                    RingStat("Carbs", eaten.carbsG, planned.carbsG, targets.carbsG, "g", CarbsColor, Modifier.weight(1f))
                    RingStat("Fat", eaten.fatG, planned.fatG, targets.fatG, "g", FatColor, Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LegendDot(MaterialTheme.colorScheme.onSurface, "Eaten")
                    Spacer(Modifier.width(14.dp))
                    LegendDot(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f), "Planned")
                    Spacer(Modifier.weight(1f))
                    val pct = if (targets.proteinG > 0) planned.proteinG * 100 / targets.proteinG else 0
                    Text(
                        "Plan hits $pct% of protein",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val RingSize: Dp = 64.dp
private val RingStroke: Dp = 7.dp

/** Faint arc = planned, solid arc = eaten, both vs. the daily target. */
@Composable
private fun RingStat(
    label: String,
    eaten: Int,
    planned: Int,
    target: Int,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val t = target.coerceAtLeast(1).toFloat()
    val plannedP by animateFloatAsState(planned / t, tween(durationMillis = 900), label = "plannedRing")
    val eatenP by animateFloatAsState(eaten / t, tween(durationMillis = 900), label = "eatenRing")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        MacroRing(
            progress = plannedP,
            color = color.copy(alpha = 0.28f),
            size = RingSize,
            stroke = RingStroke,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val sweep = 360f * eatenP.coerceIn(0f, 1f)
                if (sweep > 0.5f) {
                    val s = RingStroke.toPx()
                    drawArc(
                        color = color,
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(s / 2f, s / 2f),
                        size = Size(size.width - s, size.height - s),
                        style = Stroke(width = s, cap = StrokeCap.Round),
                    )
                }
            }
            Text("$eaten", style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(
            "$planned/$target$unit",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun MealRow(meal: PlannedMeal, onOpen: () -> Unit, onToggle: (Boolean) -> Unit) {
    val eaten = meal.entry.cooked
    val fade by animateFloatAsState(if (eaten) 0.55f else 1f, label = "mealFade")
    val servings = meal.entry.servings
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RecipeImage(
                meal.recipe,
                Modifier.size(64.dp).alpha(fade),
                corner = 18.dp,
                illustrationPadding = 8.dp,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).alpha(fade)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        meal.entry.slot.label.uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (meal.entry.isLeftover) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "LEFTOVERS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    meal.recipe.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (eaten) TextDecoration.LineThrough else TextDecoration.None,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        append("${meal.recipe.calories * servings} kcal · ${meal.recipe.proteinG * servings}g protein")
                        if (servings > 1) append(" · ×$servings")
                        if (meal.entry.isLeftover) append(" · just reheat") else append(" · ${meal.recipe.totalMinutes} min")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Checkbox(checked = eaten, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun SwimCard(
    log: DailyLogEntity?,
    profile: UserProfile,
    streak: Int,
    onLog: () -> Unit,
    onUndo: () -> Unit,
    onOpen: () -> Unit,
) {
    val swam = (log?.swimYards ?: 0) > 0
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        color = if (swam) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    ) {
        Column(Modifier.padding(20.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🏊", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (swam) "Swim done" else "Today's swim", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (swam && log != null) "${log.swimYards} yd · ${log.swimMinutes} min. Nice work."
                        else "${profile.swimYardsPerDay} yd · ${profile.swimMinutesPerDay} min",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (streak > 0) {
                    Text(
                        "🔥 $streak",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            AnimatedVisibility(visible = !swam, enter = fadeIn(), exit = fadeOut()) {
                Button(
                    onClick = onLog,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(48.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Log ${profile.swimYardsPerDay} yd with one tap")
                }
            }
            AnimatedVisibility(visible = swam, enter = fadeIn(), exit = fadeOut()) {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (streak == 1) "1-day streak" else "$streak-day streak",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onUndo) { Text("Undo") }
                    TextButton(onClick = onOpen) { Text("Progress") }
                }
            }
        }
    }
}

@Composable
private fun ListSummaryCard(summary: ShoppingSummary?, budgetUsd: Int, onClick: () -> Unit) {
    val total = summary?.totalItems ?: 0
    val left = total - (summary?.checkedItems ?: 0)
    val est = summary?.estTotalUsd ?: 0.0
    val budget = summary?.budgetUsd ?: budgetUsd
    val over = budget > 0 && est > budget
    val fraction by animateFloatAsState(
        if (budget > 0) (est / budget).toFloat().coerceIn(0f, 1f) else 0f,
        label = "budget",
    )
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerLowest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.ShoppingBag, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            total == 0 -> "Shopping list is empty"
                            left == 0 -> "Everything's in the cart ✓"
                            left == 1 -> "1 item left to grab"
                            else -> "$left items left to grab"
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        if (total == 0) "Build it from your plan in one tap"
                        else "Est. ${formatUsd(est)} of \$$budget budget",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (over) Tomato else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    )
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Open list")
            }
            if (total > 0) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = if (over) Tomato else MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    strokeCap = StrokeCap.Round,
                )
            }
        }
    }
}
