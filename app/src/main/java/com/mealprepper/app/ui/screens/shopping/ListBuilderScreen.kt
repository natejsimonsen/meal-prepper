package com.mealprepper.app.ui.screens.shopping

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mealprepper.app.data.model.RecipeEntity
import com.mealprepper.app.ui.components.RecipeImage
import com.mealprepper.app.ui.components.appViewModel
import com.mealprepper.app.ui.components.formatUsd
import com.mealprepper.app.ui.theme.Tomato
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val rangeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun utcMillisToDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListBuilderScreen(onBack: () -> Unit, onBuilt: () -> Unit) {
    val vm = appViewModel { c -> ListBuilderViewModel(c.mealRepository, c.profileRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    var showRangePicker by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    val currentOnBuilt by rememberUpdatedState(onBuilt)
    LaunchedEffect(state.built) {
        if (state.built) currentOnBuilt()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Build your list") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            BuildBar(state = state, onBuild = vm::build)
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item(key = "intro") {
                Text(
                    "Everything goes into one list, merged by ingredient and sorted by aisle.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            // --- Section 1: plan range -------------------------------------------------------
            item(key = "plan") {
                SectionCard(number = 1, title = "From your plan") {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RangePreset.entries.forEach { preset ->
                            FilterChip(
                                selected = state.options.preset == preset,
                                onClick = {
                                    if (preset == RangePreset.CUSTOM) showRangePicker = true
                                    else vm.selectPreset(preset)
                                },
                                label = { Text(preset.label) },
                                leadingIcon = if (preset == RangePreset.CUSTOM) {
                                    { Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                } else {
                                    null
                                },
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    val start = state.rangeStart
                    val end = state.rangeEnd
                    if (start == null || end == null) {
                        Text(
                            if (state.options.preset == RangePreset.CUSTOM) "Pick a date range" else "Not using the plan",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "Only the recipes you add below will be included.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            if (start == end) start.format(rangeFormatter)
                            else "${start.format(rangeFormatter)} – ${end.format(rangeFormatter)}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            when {
                                state.planMeals == 0 -> "No meals planned in these days yet"
                                else -> "${state.planMeals} meal${plural(state.planMeals)} · " +
                                    "${state.planCookSessions} to cook (leftovers need no shopping)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // --- Section 2: extra recipes ----------------------------------------------------
            item(key = "extras-header") {
                SectionCard(number = 2, title = "Add recipes") {
                    Text(
                        "Cooking something extra? Add it with the servings you need.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.extraRecipes.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.extraRecipes.forEach { recipe ->
                                InputChip(
                                    selected = true,
                                    onClick = { vm.removeExtra(recipe.id) },
                                    label = {
                                        Text(
                                            "${recipe.title} ×${state.extras[recipe.id] ?: 0}",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 200.dp),
                                        )
                                    },
                                    trailingIcon = {
                                        Icon(Icons.Rounded.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                                    },
                                    shape = CircleShape,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            vm.setQuery(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search recipes") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        shape = CircleShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                    )
                }
            }
            items(state.searchResults.take(40), key = { "r-${it.id}" }) { recipe ->
                ExtraRecipeRow(
                    recipe = recipe,
                    servings = state.extras[recipe.id] ?: 0,
                    onPlus = { vm.increment(recipe) },
                    onMinus = { vm.decrement(recipe) },
                )
            }

            // --- Section 3: options ----------------------------------------------------------
            item(key = "options") {
                SectionCard(number = 3, title = "Options") {
                    OptionRow(
                        title = "Skip pantry items",
                        body = "Leave out staples you already have at home.",
                        checked = state.options.skipPantry,
                        onChange = vm::setSkipPantry,
                    )
                    HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    OptionRow(
                        title = "Keep my manual items",
                        body = "Anything you typed into the list stays on it.",
                        checked = state.options.keepManual,
                        onChange = vm::setKeepManual,
                    )
                }
            }
        }
    }

    if (showRangePicker) {
        RangePickerDialog(
            initialStart = state.options.customStart ?: state.rangeStart,
            initialEnd = state.options.customEnd ?: state.rangeEnd,
            onDismiss = { showRangePicker = false },
            onConfirm = { s, e ->
                showRangePicker = false
                vm.setCustomRange(s, e)
            },
        )
    }
}

private fun plural(n: Int): String = if (n == 1) "" else "s"

@Composable
private fun SectionCard(number: Int, title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$number",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun ExtraRecipeRow(recipe: RecipeEntity, servings: Int, onPlus: () -> Unit, onMinus: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.large)
            .background(
                if (servings > 0) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.surfaceContainerLowest
            )
            .clickable(onClick = onPlus)
            .padding(10.dp),
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
                "${recipe.proteinG}g protein · ${recipe.totalMinutes} min · makes ${recipe.servings}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (servings == 0) {
            OutlinedButton(onClick = onPlus, shape = CircleShape, contentPadding = PaddingValues(horizontal = 14.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = onMinus, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Remove, contentDescription = "Fewer servings", modifier = Modifier.size(18.dp))
                }
                Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$servings", style = MaterialTheme.typography.titleMedium)
                    Text("serv.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalIconButton(onClick = onPlus, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Add, contentDescription = "More servings", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun OptionRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun BuildBar(state: BuilderUiState, onBuild: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 12.dp,
        shape = MaterialTheme.shapes.extraLarge.copy(
            bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp),
            bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp),
        ),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "~${state.recipeCount} recipe${plural(state.recipeCount)} · ~$${state.estCostUsd.roundToInt()} est.",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "budget $${state.budgetUsd}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (state.estCostUsd > state.budgetUsd) Tomato else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = !state.canBuild && !state.building) {
                Text(
                    "Pick days with planned meals or add a recipe to build a list.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onBuild,
                enabled = state.canBuild,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = CircleShape,
            ) {
                if (state.building) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Merging your list…", style = MaterialTheme.typography.titleMedium)
                } else {
                    Icon(Icons.Rounded.ShoppingCart, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("Build my list", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangePickerDialog(
    initialStart: LocalDate?,
    initialEnd: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit,
) {
    val pickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStart?.toUtcMillis(),
        initialSelectedEndDateMillis = initialEnd?.toUtcMillis(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = pickerState.selectedStartDateMillis != null,
                onClick = {
                    val startMs = pickerState.selectedStartDateMillis
                    if (startMs != null) {
                        val endMs = pickerState.selectedEndDateMillis ?: startMs
                        onConfirm(utcMillisToDate(startMs), utcMillisToDate(endMs))
                    }
                },
            ) { Text("Use these days") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    ) {
        DateRangePicker(
            state = pickerState,
            modifier = Modifier.weight(1f),
            title = {
                Text(
                    "Shop for which days?",
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                )
            },
        )
    }
}
