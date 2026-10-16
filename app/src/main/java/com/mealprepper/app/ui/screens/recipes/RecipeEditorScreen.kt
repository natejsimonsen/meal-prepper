package com.mealprepper.app.ui.screens.recipes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mealprepper.app.data.model.Aisle
import com.mealprepper.app.data.model.MealType
import com.mealprepper.app.data.model.RecipeEntity
import com.mealprepper.app.data.model.Units
import com.mealprepper.app.ui.components.EmptyState
import com.mealprepper.app.ui.components.FoodImageKey
import com.mealprepper.app.ui.components.RecipeImage
import com.mealprepper.app.ui.components.appViewModel
import com.mealprepper.app.ui.components.parseHex
import com.mealprepper.app.ui.theme.CaloriesColor
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeEditorScreen(
    recipeId: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val vm = appViewModel(key = "recipe-editor-${recipeId ?: "new"}") { c ->
        RecipeEditorViewModel(c.mealRepository, recipeId)
    }
    val availableTags by vm.availableTags.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val form = vm.form
    val errors = if (vm.showErrors) vm.errors(form) else emptyMap()

    val saveError = vm.saveError
    LaunchedEffect(saveError) {
        if (saveError != null) {
            snackbar.showSnackbar(saveError)
            vm.clearSaveError()
        }
    }

    fun trySave() {
        vm.save(onSaved)
        if (vm.showErrors && vm.errors().isNotEmpty()) {
            scope.launch {
                if (EditorField.TITLE in vm.errors()) listState.animateScrollToItem(0)
                snackbar.showSnackbar("Please fix the highlighted fields")
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            recipeId == null -> "New recipe"
                            vm.willCopy -> "Save as my recipe"
                            else -> "Edit recipe"
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Rounded.Close, contentDescription = "Discard changes") }
                },
                actions = {
                    TextButton(
                        onClick = { trySave() },
                        enabled = !vm.saving && !vm.loading && !vm.notFound,
                    ) { Text("Save", style = MaterialTheme.typography.titleMedium) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { inner ->
        when {
            vm.loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(inner),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            vm.notFound -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(inner),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState("🧐", "Recipe not found", "It may have been deleted.", actionLabel = "Go back", onAction = onBack)
            }

            else -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(inner)
                    .imePadding(),
                contentPadding = PaddingValues(
                    top = inner.calculateTopPadding() + 4.dp,
                    bottom = inner.calculateBottomPadding() + 32.dp,
                ),
            ) {
                // ---- Preview + basics ----
                item(key = "preview") {
                    Box(Modifier.padding(horizontal = 20.dp)) {
                        RecipeImage(
                            recipe = form.previewEntity(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(170.dp),
                            corner = 28.dp,
                            illustrationPadding = 28.dp,
                        )
                    }
                }
                item(key = "basics") {
                    EditorSection("The basics") {
                        FormField(
                            value = form.title,
                            onValueChange = { v -> vm.update { it.copy(title = v) } },
                            label = "Title",
                            error = errors[EditorField.TITLE],
                            capitalization = KeyboardCapitalization.Words,
                        )
                        FormField(
                            value = form.subtitle,
                            onValueChange = { v -> vm.update { it.copy(subtitle = v) } },
                            label = "Short hook (optional)",
                            placeholder = "Crispy, saucy, 20 minutes",
                        )
                        FormField(
                            value = form.description,
                            onValueChange = { v -> vm.update { it.copy(description = v) } },
                            label = "Description (optional)",
                            singleLine = false,
                            minLines = 2,
                        )
                        FormField(
                            value = form.cuisine,
                            onValueChange = { v -> vm.update { it.copy(cuisine = v) } },
                            label = "Cuisine",
                            placeholder = "Mediterranean, Mexican…",
                            capitalization = KeyboardCapitalization.Words,
                        )
                        FieldLabel("Meal")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MealType.entries.forEach { t ->
                                EditorChip(form.mealType == t, t.label) { vm.update { it.copy(mealType = t) } }
                            }
                        }
                        FieldLabel("Effort")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (1..3).forEach { d ->
                                EditorChip(form.difficulty == d, difficultyLabel(d)) { vm.update { it.copy(difficulty = d) } }
                            }
                        }
                    }
                }
                item(key = "time") {
                    EditorSection("Time & servings") {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FormField(
                                value = form.prepMinutes,
                                onValueChange = { v -> vm.update { it.copy(prepMinutes = v.digitsOnly()) } },
                                label = "Prep",
                                suffix = "min",
                                keyboardType = KeyboardType.Number,
                                error = errors[EditorField.PREP],
                                modifier = Modifier.weight(1f),
                            )
                            FormField(
                                value = form.cookMinutes,
                                onValueChange = { v -> vm.update { it.copy(cookMinutes = v.digitsOnly()) } },
                                label = "Cook",
                                suffix = "min",
                                keyboardType = KeyboardType.Number,
                                error = errors[EditorField.COOK],
                                modifier = Modifier.weight(1f),
                            )
                            FormField(
                                value = form.servings,
                                onValueChange = { v -> vm.update { it.copy(servings = v.digitsOnly()) } },
                                label = "Serves",
                                keyboardType = KeyboardType.Number,
                                error = errors[EditorField.SERVINGS],
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                item(key = "macros") {
                    EditorSection("Nutrition per serving") {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FormField(
                                value = form.protein,
                                onValueChange = { v -> vm.update { it.copy(protein = v.digitsOnly()) } },
                                label = "Protein",
                                suffix = "g",
                                keyboardType = KeyboardType.Number,
                                error = errors[EditorField.PROTEIN],
                                modifier = Modifier.weight(1f),
                            )
                            FormField(
                                value = form.carbs,
                                onValueChange = { v -> vm.update { it.copy(carbs = v.digitsOnly()) } },
                                label = "Carbs",
                                suffix = "g",
                                keyboardType = KeyboardType.Number,
                                error = errors[EditorField.CARBS],
                                modifier = Modifier.weight(1f),
                            )
                            FormField(
                                value = form.fat,
                                onValueChange = { v -> vm.update { it.copy(fat = v.digitsOnly()) } },
                                label = "Fat",
                                suffix = "g",
                                keyboardType = KeyboardType.Number,
                                error = errors[EditorField.FAT],
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FormField(
                                value = form.calories,
                                onValueChange = { v -> vm.update { it.copy(calories = v.digitsOnly()) } },
                                label = "Calories",
                                suffix = "kcal",
                                keyboardType = KeyboardType.Number,
                                error = errors[EditorField.CALORIES],
                                modifier = Modifier.weight(2f),
                            )
                            FormField(
                                value = form.fiber,
                                onValueChange = { v -> vm.update { it.copy(fiber = v.digitsOnly()) } },
                                label = "Fiber",
                                suffix = "g",
                                keyboardType = KeyboardType.Number,
                                error = errors[EditorField.FIBER],
                                modifier = Modifier.weight(1f),
                            )
                        }
                        val computed = form.computedCalories
                        if (computed != null) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = CaloriesColor.copy(alpha = 0.10f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "≈ $computed kcal from macros (4·P + 4·C + 9·F)",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (form.calories != computed.toString()) {
                                        TextButton(onClick = vm::useComputedCalories) { Text("Use") }
                                    } else {
                                        Spacer(Modifier.height(48.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                item(key = "look") {
                    EditorSection("Look") {
                        FieldLabel("Illustration")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            FoodImageKey.entries.forEach { key ->
                                ImageKeyTile(
                                    key = key,
                                    accent = parseHex(form.accentColor),
                                    selected = form.imageKey == key,
                                    onClick = { vm.update { it.copy(imageKey = key) } },
                                )
                            }
                        }
                        FieldLabel("Accent color")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ACCENT_SWATCHES.forEach { hex ->
                                Swatch(
                                    color = parseHex(hex),
                                    selected = form.accentColor.equals(hex, ignoreCase = true),
                                    onClick = { vm.update { it.copy(accentColor = hex) } },
                                )
                            }
                        }
                    }
                }
                item(key = "tags") {
                    var tagInput by rememberSaveable { mutableStateOf("") }
                    EditorSection("Tags") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            (availableTags + form.tags).distinct().forEach { tag ->
                                EditorChip(tag in form.tags, "#$tag") { vm.toggleTag(tag) }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FormField(
                                value = tagInput,
                                onValueChange = { tagInput = it },
                                label = "Add a tag",
                                imeAction = ImeAction.Done,
                                onImeAction = {
                                    vm.addCustomTag(tagInput)
                                    tagInput = ""
                                },
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = {
                                    vm.addCustomTag(tagInput)
                                    tagInput = ""
                                },
                                enabled = tagInput.isNotBlank(),
                            ) { Text("Add") }
                        }
                    }
                }
                item(key = "prep") {
                    EditorSection("Meal prep") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Prep-friendly", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Reheats well and holds up in containers",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = form.mealPrepFriendly,
                                onCheckedChange = { c -> vm.update { it.copy(mealPrepFriendly = c) } },
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Fridge life", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${form.fridgeDays} day${if (form.fridgeDays == 1) "" else "s"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            ServingsStepper(
                                servings = form.fridgeDays.coerceIn(1, 14),
                                onChange = { d -> vm.update { it.copy(fridgeDays = d.coerceIn(1, 14)) } },
                                max = 14,
                            )
                        }
                    }
                }

                // ---- Ingredients ----
                item(key = "ingredients-title") {
                    ListSectionTitle("Ingredients", "For all ${form.servings.ifBlank { "?" }} servings", errors[EditorField.INGREDIENTS])
                }
                itemsIndexed(form.ingredients, key = { _, d -> "ing-${d.key}" }) { _, draft ->
                    IngredientEditorRow(
                        draft = draft,
                        canRemove = form.ingredients.size > 1,
                        onChange = { transform -> vm.updateIngredient(draft.key, transform) },
                        onRemove = { vm.removeIngredient(draft.key) },
                    )
                }
                item(key = "ingredients-add") {
                    OutlinedButton(
                        onClick = vm::addIngredient,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add ingredient")
                    }
                }

                // ---- Steps ----
                item(key = "steps-title") {
                    ListSectionTitle("Steps", "Mention times like “10 min” for automatic timers", errors[EditorField.STEPS])
                }
                itemsIndexed(form.steps, key = { _, s -> "step-${s.key}" }) { index, step ->
                    StepEditorRow(
                        number = index + 1,
                        text = step.text,
                        canMoveUp = index > 0,
                        canMoveDown = index < form.steps.lastIndex,
                        canRemove = form.steps.size > 1,
                        onChange = { vm.updateStep(step.key, it) },
                        onMoveUp = { vm.moveStep(step.key, -1) },
                        onMoveDown = { vm.moveStep(step.key, 1) },
                        onRemove = { vm.removeStep(step.key) },
                    )
                }
                item(key = "steps-add") {
                    OutlinedButton(
                        onClick = vm::addStep,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add step")
                    }
                }

                item(key = "save") {
                    Button(
                        onClick = { trySave() },
                        enabled = !vm.saving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 24.dp)
                            .height(58.dp),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        if (vm.saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(Icons.Rounded.Check, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (vm.willCopy) "Save as my recipe" else "Save recipe",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Building blocks
// ---------------------------------------------------------------------------------------------

@Composable
private fun EditorSection(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ListSectionTitle(title: String, subtitle: String, error: String?) {
    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 6.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    placeholder: String? = null,
    suffix: String? = null,
    prefix: String? = null,
    error: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    imeAction: ImeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        placeholder = placeholder?.let { p -> { Text(p) } },
        suffix = suffix?.let { s -> { Text(s) } },
        prefix = prefix?.let { p -> { Text(p) } },
        isError = error != null,
        supportingText = error?.let { e -> { Text(e) } },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = if (singleLine) 1 else 8,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(
            capitalization = if (keyboardType == KeyboardType.Text) capitalization else KeyboardCapitalization.None,
            keyboardType = keyboardType,
            imeAction = imeAction,
        ),
        keyboardActions = if (onImeAction != null) {
            KeyboardActions(onDone = { onImeAction() }, onGo = { onImeAction() })
        } else {
            KeyboardActions.Default
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        shape = RoundedCornerShape(50),
        border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

@Composable
private fun <T> DropdownField(
    label: String,
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(16.dp),
            color = Color.Transparent,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            Row(
                Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(value, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.heightIn(max = 320.dp),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun IngredientEditorRow(
    draft: IngredientDraft,
    canRemove: Boolean,
    onChange: ((IngredientDraft) -> IngredientDraft) -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center,
                ) { Text(draft.aisle.emoji, style = MaterialTheme.typography.titleMedium) }
                Spacer(Modifier.width(10.dp))
                FormField(
                    value = draft.name,
                    onValueChange = { v -> onChange { it.copy(name = v) } },
                    label = "Ingredient",
                    placeholder = "chicken breast",
                    capitalization = KeyboardCapitalization.None,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRemove, enabled = canRemove) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Remove ingredient")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FormField(
                    value = draft.quantity,
                    onValueChange = { v -> onChange { it.copy(quantity = v) } },
                    label = "Qty",
                    keyboardType = KeyboardType.Text,
                    capitalization = KeyboardCapitalization.None,
                    modifier = Modifier.weight(0.9f),
                )
                DropdownField(
                    label = "Unit",
                    value = draft.unit,
                    options = Units.ALL,
                    optionLabel = { it },
                    onSelect = { u -> onChange { it.copy(unit = u) } },
                    modifier = Modifier.weight(1f),
                )
                FormField(
                    value = draft.price,
                    onValueChange = { v -> onChange { it.copy(price = v.filter { c -> c.isDigit() || c == '.' }) } },
                    label = "Price",
                    prefix = "$",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                DropdownField(
                    label = "Aisle",
                    value = "${draft.aisle.emoji}  ${draft.aisle.label}",
                    options = Aisle.entries,
                    optionLabel = { "${it.emoji}  ${it.label}" },
                    onSelect = { a -> onChange { it.copy(aisle = a) } },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Switch(
                        checked = draft.pantryStaple,
                        onCheckedChange = { c -> onChange { it.copy(pantryStaple = c) } },
                    )
                    Text(
                        "Staple",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StepEditorRow(
    number: Int,
    text: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canRemove: Boolean,
    onChange: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Row(Modifier.padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 4.dp)) {
            Box(
                Modifier
                    .padding(top = 12.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$number",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(10.dp))
            FormField(
                value = text,
                onValueChange = onChange,
                label = "Step $number",
                singleLine = false,
                minLines = 2,
                modifier = Modifier.weight(1f),
            )
            Column {
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move step up")
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move step down")
                }
                IconButton(onClick = onRemove, enabled = canRemove) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Remove step")
                }
            }
        }
    }
}

@Composable
private fun ImageKeyTile(key: FoodImageKey, accent: Color, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .size(64.dp)
            .clip(shape)
            .background(accent.copy(alpha = if (selected) 0.45f else 0.16f))
            .then(
                if (selected) {
                    Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(key.res),
            contentDescription = key.name.lowercase(Locale.US).replace('_', ' '),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
        )
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (selected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = Color.White)
    }
}

private fun String.digitsOnly(): String = filter { it.isDigit() }.take(5)

/** A throwaway entity so the shared [RecipeImage] can preview the current look. */
private fun EditorForm.previewEntity(): RecipeEntity = RecipeEntity(
    id = "preview",
    title = title.ifBlank { "New recipe" },
    subtitle = subtitle,
    description = description,
    mealType = mealType,
    cuisine = cuisine,
    prepMinutes = 0,
    cookMinutes = 0,
    servings = 1,
    calories = 0,
    proteinG = 0,
    carbsG = 0,
    fatG = 0,
    fiberG = 0,
    imageKey = imageKey.name.lowercase(Locale.US),
    accentColor = accentColor,
    tags = emptyList(),
    steps = emptyList(),
    difficulty = difficulty,
    mealPrepFriendly = mealPrepFriendly,
    fridgeDays = fridgeDays,
    sourceName = "",
    sourceUrl = "",
)
