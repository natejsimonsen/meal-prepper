package com.mealprepper.app.ui.screens.plan

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mealprepper.app.data.model.MealType
import com.mealprepper.app.data.model.PlannedMeal
import com.mealprepper.app.data.model.RecipeEntity
import com.mealprepper.app.ui.components.EmptyState
import com.mealprepper.app.ui.components.MacroChip
import com.mealprepper.app.ui.components.RecipeImage
import com.mealprepper.app.ui.components.SectionHeader
import com.mealprepper.app.ui.components.appViewModel
import com.mealprepper.app.ui.components.formatUsd
import com.mealprepper.app.ui.theme.Apricot
import com.mealprepper.app.ui.theme.CaloriesColor
import com.mealprepper.app.ui.theme.Lemon
import com.mealprepper.app.ui.theme.ProteinColor
import com.mealprepper.app.ui.theme.Tomato
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun PlanScreen(
    contentPadding: PaddingValues,
    onOpenRecipe: (String) -> Unit,
    onBuildList: () -> Unit,
) {
    val vm = appViewModel { c -> PlanViewModel(c.mealRepository, c.profileRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val picker by vm.picker.collectAsStateWithLifecycle()
    val pickerOpen by vm.isPickerOpen.collectAsStateWithLifecycle()
    var confirmGenerate by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 32.dp),
        ) {
            item(key = "header") {
                WeekHeader(
                    state = state,
                    onPrev = vm::previousWeek,
                    onNext = vm::nextWeek,
                    onToday = vm::goToToday,
                )
            }
            item(key = "overview") {
                WeekOverviewCard(
                    state = state,
                    onGenerate = { confirmGenerate = true },
                    onBuildList = onBuildList,
                )
            }
            item(key = "days") {
                DayStrip(state = state, onSelect = vm::selectDay)
            }
            item(key = "macros") {
                DayMacroCard(state = state)
            }
            MealType.entries.forEach { slot ->
                val meals = state.slots[slot].orEmpty()
                item(key = "slot-${slot.name}") {
                    SlotHeader(slot = slot, onAdd = { vm.openPicker(slot) })
                }
                if (meals.isEmpty()) {
                    item(key = "empty-${slot.name}") {
                        EmptySlot(slot = slot, onAdd = { vm.openPicker(slot) })
                    }
                } else {
                    items(meals, key = { "meal-${it.entry.id}" }) { meal ->
                        PlannedMealCard(
                            meal = meal,
                            onOpen = { onOpenRecipe(meal.recipe.id) },
                            onSwap = { vm.swap(meal.entry.id) },
                            onRemove = { vm.remove(meal.entry.id) },
                            onToggleCooked = { vm.toggleCooked(meal) },
                        )
                    }
                }
            }
            item(key = "prep") {
                PrepDayCard(state = state, onToggle = vm::togglePrep, onOpenRecipe = onOpenRecipe)
            }
        }

        if (state.generating) {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shadowElevation = 8.dp,
            ) {
                Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(16.dp))
                    Text("Planning a varied week…", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }

    if (confirmGenerate) {
        AlertDialog(
            onDismissRequest = { confirmGenerate = false },
            icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
            title = { Text("Generate this week?") },
            text = {
                Text(
                    "This replaces the plan for ${state.weekStart.format(PlanViewModel.RangeFormatter)} – " +
                        "${state.weekEnd.format(PlanViewModel.RangeFormatter)} with a varied, high-protein " +
                        "plan that reuses batch-cooked leftovers to keep it easy.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirmGenerate = false
                    vm.generateWeek()
                }) { Text("Generate") }
            },
            dismissButton = {
                TextButton(onClick = { confirmGenerate = false }) { Text("Cancel") }
            },
        )
    }

    if (pickerOpen) {
        RecipePickerSheet(
            picker = picker,
            date = state.selectedDay,
            onQuery = vm::setPickerQuery,
            onPick = vm::addFromPicker,
            onDismiss = vm::closePicker,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Header & overview
// ---------------------------------------------------------------------------------------------

@Composable
private fun WeekHeader(
    state: PlanUiState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    val weeksAway = ChronoUnit.WEEKS.between(mondayOf(state.today), state.weekStart)
    val title = when (weeksAway) {
        0L -> "This week"
        1L -> "Next week"
        -1L -> "Last week"
        else -> "Week of ${state.weekStart.format(PlanViewModel.RangeFormatter)}"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${state.weekStart.format(PlanViewModel.RangeFormatter)} – " +
                        state.weekEnd.format(PlanViewModel.RangeFormatter),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!state.isCurrentWeek) {
                    TextButton(onClick = onToday, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("Today")
                    }
                }
            }
        }
        FilledTonalIconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous week")
        }
        Spacer(Modifier.width(4.dp))
        FilledTonalIconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next week")
        }
    }
}

@Composable
private fun WeekOverviewCard(
    state: PlanUiState,
    onGenerate: () -> Unit,
    onBuildList: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(Modifier.padding(20.dp)) {
            if (state.weekMealCount == 0 && !state.loading) {
                Text(
                    "Nothing planned yet",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Let us build a varied, high-protein week with easy batch-cooked leftovers.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onGenerate,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = CircleShape,
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("Generate my week", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                Row(Modifier.fillMaxWidth()) {
                    OverviewStat(
                        label = "Protein / day",
                        value = "${state.avgProteinPerDay}g",
                        sub = state.targets?.let { "goal ${it.proteinG}g" } ?: "avg",
                        modifier = Modifier.weight(1f),
                    )
                    val over = state.estCostUsd > state.budgetUsd
                    OverviewStat(
                        label = "Groceries",
                        value = "~${formatUsd(state.estCostUsd).substringBefore('.')}",
                        sub = "of $${state.budgetUsd} budget",
                        valueColor = if (over) Tomato else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    OverviewStat(
                        label = "Recipes",
                        value = "${state.uniqueRecipes}",
                        sub = "${state.weekMealCount} meals",
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onBuildList,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = CircleShape,
                ) {
                    Icon(Icons.Rounded.ShoppingCart, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("Build shopping list", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onGenerate,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Regenerate week")
                }
            }
        }
    }
}

@Composable
private fun OverviewStat(
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
        )
        Text(value, style = MaterialTheme.typography.headlineSmall, color = valueColor, maxLines = 1)
        Text(
            sub,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Day strip & macros
// ---------------------------------------------------------------------------------------------

@Composable
private fun DayStrip(state: PlanUiState, onSelect: (LocalDate) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        state.days.forEach { day ->
            DayPill(
                day = day,
                selected = day.date == state.selectedDay,
                onClick = { onSelect(day.date) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DayPill(day: PlanDay, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest,
        label = "dayBg",
    )
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(shape)
            .background(bg)
            .then(
                if (day.isToday && !selected) {
                    Modifier.border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary), shape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            shortDayName(day.date),
            style = MaterialTheme.typography.labelSmall,
            color = fg.copy(alpha = 0.75f),
            maxLines = 1,
        )
        Text(
            "${day.date.dayOfMonth}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = fg,
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(6.dp)) {
            val dots = day.mealCount.coerceAtMost(4)
            repeat(dots) { i ->
                val dotColor = when {
                    i < day.cookedCount -> if (selected) MaterialTheme.colorScheme.onPrimary else ProteinColor
                    selected -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.55f)
                    else -> Apricot
                }
                Box(Modifier.size(5.dp).clip(CircleShape).background(dotColor))
            }
        }
    }
}

@Composable
private fun DayMacroCard(state: PlanUiState) {
    val targets = state.targets
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                state.selectedDay.format(PlanViewModel.DayFormatter),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(14.dp))
            MacroBar(
                label = "Calories",
                value = state.dayTotals.calories,
                target = targets?.calories ?: 0,
                unit = "kcal",
                color = CaloriesColor,
            )
            Spacer(Modifier.height(12.dp))
            MacroBar(
                label = "Protein",
                value = state.dayTotals.proteinG,
                target = targets?.proteinG ?: 0,
                unit = "g",
                color = ProteinColor,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MacroChip("carbs", "${state.dayTotals.carbsG}g", Apricot)
                MacroChip("fat", "${state.dayTotals.fatG}g", MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
private fun MacroBar(label: String, value: Int, target: Int, unit: String, color: Color) {
    val fraction = if (target > 0) value.toFloat() / target else 0f
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "macroBar")
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(
                if (target > 0) "$value / $target $unit" else "$value $unit",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated)
                    .clip(CircleShape)
                    .background(if (fraction > 1.1f) Tomato else color),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Meal slots
// ---------------------------------------------------------------------------------------------

@Composable
private fun SlotHeader(slot: MealType, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 18.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(slot.label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = onAdd) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add")
        }
    }
}

@Composable
private fun EmptySlot(slot: MealType, onAdd: () -> Unit) {
    val shape = MaterialTheme.shapes.large
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(shape)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape)
            .clickable(onClick = onAdd)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(14.dp))
        Text(
            "Add ${slot.label.lowercase()}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlannedMealCard(
    meal: PlannedMeal,
    onOpen: () -> Unit,
    onSwap: () -> Unit,
    onRemove: () -> Unit,
    onToggleCooked: () -> Unit,
) {
    val recipe = meal.recipe
    val cooked = meal.entry.cooked
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 1.dp,
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            RecipeImage(recipe, Modifier.size(68.dp), corner = 18.dp, illustrationPadding = 8.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    recipe.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (cooked) TextDecoration.LineThrough else null,
                    color = if (cooked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Schedule, contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        if (meal.entry.isLeftover) "2 min" else "${recipe.totalMinutes} min",
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
                if (meal.entry.isLeftover || meal.entry.servings > 1) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (meal.entry.isLeftover) Badge("Leftovers", Lemon)
                        if (meal.entry.servings > 1) Badge("${meal.entry.servings} servings", Apricot)
                    }
                }
            }
            IconButton(onClick = onToggleCooked) {
                Icon(
                    if (cooked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = if (cooked) "Mark not cooked" else "Mark cooked",
                    tint = if (cooked) ProteinColor else MaterialTheme.colorScheme.outline,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More actions")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Swap for another") },
                        leadingIcon = { Icon(Icons.Rounded.SwapHoriz, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onSwap()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (cooked) "Mark not cooked" else "Mark cooked") },
                        leadingIcon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onToggleCooked()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Remove") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onRemove()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.28f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

// ---------------------------------------------------------------------------------------------
// Prep day
// ---------------------------------------------------------------------------------------------

@Composable
private fun PrepDayCard(state: PlanUiState, onToggle: (String) -> Unit, onOpenRecipe: (String) -> Unit) {
    if (state.loading || state.weekMealCount == 0) return
    val prepDate = state.weekStart.minusDays(1)
    Column {
        SectionHeader(title = "Prep day", modifier = Modifier.padding(top = 18.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Kitchen, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "${shortDayName(prepDate)}, ${prepDate.format(PlanViewModel.RangeFormatter)} batch prep",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        val doneCount = state.prepTasks.count { it.done }
                        Text(
                            if (state.prepTasks.isEmpty()) "Nothing to batch-cook this week"
                            else "$doneCount of ${state.prepTasks.size} done · about an hour or two",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                        )
                    }
                }
                if (state.prepTasks.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    state.prepTasks.forEachIndexed { index, task ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f))
                        }
                        PrepRow(task = task, onToggle = { onToggle(task.id) }, onOpen = { onOpenRecipe(task.recipe.id) })
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Tip: cook grains and proteins first, chop veg while they go, then portion into containers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PrepRow(task: PrepTask, onToggle: () -> Unit, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (task.done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = if (task.done) "Done" else "Not done",
            tint = if (task.done) ProteinColor else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Cook ${task.portions} serving${if (task.portions == 1) "" else "s"} of ${task.recipe.title}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
            )
            Text(
                "Covers ${task.covers}" + if (task.freezeSome) " · freeze the last portions" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
            )
        }
        TextButton(onClick = onOpen) { Text("Recipe") }
    }
}

// ---------------------------------------------------------------------------------------------
// Recipe picker
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipePickerSheet(
    picker: PickerState,
    date: LocalDate,
    onQuery: (String) -> Unit,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by rememberSaveable { mutableStateOf("") }
    val slotLabel = picker.slot?.label ?: "meal"
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Add $slotLabel".lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        date.format(PlanViewModel.DayFormatter),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Close") }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    onQuery(it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search recipes") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = CircleShape,
            )
            Spacer(Modifier.height(8.dp))
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
        ) {
            if (picker.recipes.isEmpty()) {
                item {
                    EmptyState(emoji = "🔍", title = "No matches", body = "Try another search.")
                }
            }
            items(picker.recipes, key = { it.id }) { recipe ->
                PickerRow(recipe = recipe, onClick = { onPick(recipe.id) })
            }
            item { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}

@Composable
private fun PickerRow(recipe: RecipeEntity, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecipeImage(recipe, Modifier.size(56.dp), corner = 16.dp, illustrationPadding = 6.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                recipe.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${recipe.totalMinutes} min · ${recipe.proteinG}g protein · ${recipe.calories} kcal" +
                    if (recipe.mealPrepFriendly) " · preps well" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OutlinedButton(
            onClick = onClick,
            contentPadding = ButtonDefaults.TextButtonContentPadding,
            shape = CircleShape,
        ) {
            Text("Add", textAlign = TextAlign.Center)
        }
    }
}
