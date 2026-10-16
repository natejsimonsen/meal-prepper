package com.mealprepper.app.ui.screens.recipes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AddShoppingCart
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mealprepper.app.data.model.IngredientEntity
import com.mealprepper.app.data.model.MealType
import com.mealprepper.app.data.model.RecipeEntity
import com.mealprepper.app.ui.components.EmptyState
import com.mealprepper.app.ui.components.MacroRing
import com.mealprepper.app.ui.components.RecipeImage
import com.mealprepper.app.ui.components.appViewModel
import com.mealprepper.app.ui.components.formatQuantity
import com.mealprepper.app.ui.theme.CaloriesColor
import com.mealprepper.app.ui.theme.CarbsColor
import com.mealprepper.app.ui.theme.FatColor
import com.mealprepper.app.ui.theme.ProteinColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    recipeId: String,
    onBack: () -> Unit,
    onCook: (String) -> Unit,
    onEdit: (String) -> Unit,
) {
    val vm = appViewModel(key = "recipe-detail-$recipeId") { c ->
        RecipeDetailViewModel(c.mealRepository, recipeId)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(vm) {
        vm.messages.collect { msg -> snackbar.showSnackbar(msg) }
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setPhoto(context.applicationContext, uri)
    }

    var showPlanSheet by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

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
                EmptyState(
                    emoji = "🍽️",
                    title = "Recipe not found",
                    body = "It may have been deleted.",
                    actionLabel = "Go back",
                    onAction = onBack,
                )
            }
        }
        return
    }
    val recipe = data.recipe
    val ingredients = remember(data.ingredients) { data.ingredients.sortedBy { it.position } }
    val listState = rememberLazyListState()
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            DetailBottomBar(
                onAddToList = { vm.addToList(state.servings) },
                onAddToPlan = { showPlanSheet = true },
                onCook = { onCook(recipe.id) },
            )
        },
    ) { inner ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = inner.calculateBottomPadding() + 28.dp),
            ) {
                item(key = "hero") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp)),
                    ) {
                        RecipeImage(
                            recipe = recipe,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp),
                            corner = 0.dp,
                            illustrationPadding = 56.dp,
                        )
                    }
                }
                item(key = "title") { TitleBlock(recipe) }
                item(key = "macros") { MacroCard(recipe) }
                item(key = "ingredients-header") {
                    IngredientsHeader(
                        servings = state.servings,
                        baseServings = recipe.servings,
                        onChange = vm::setServings,
                    )
                }
                items(ingredients, key = { "ing-${it.id}-${it.position}-${it.name}" }) { ing ->
                    IngredientRow(
                        ingredient = ing,
                        scale = state.servings.toDouble() / recipe.servings.coerceAtLeast(1),
                    )
                }
                item(key = "steps-header") {
                    SectionTitle(
                        title = "Steps",
                        trailing = "${recipe.steps.size} steps · ${recipe.totalMinutes} min",
                        modifier = Modifier.padding(top = 28.dp),
                    )
                }
                itemsIndexed(recipe.steps, key = { i, _ -> "step-$i" }) { i, step ->
                    StepCard(number = i + 1, text = step)
                }
                item(key = "prep") { MealPrepCard(recipe) }
                item(key = "source") { SourceRow(recipe) }
            }

            // Floating, transparent top bar over the hero.
            val barColor by animateColorAsState(
                if (scrolled) MaterialTheme.colorScheme.surface else Color.Transparent,
                label = "barColor",
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(barColor)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack,
                )
                Box(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    ScrolledTitle(visible = scrolled, title = recipe.title)
                }
                CircleIconButton(
                    icon = if (recipe.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (recipe.isFavorite) "Remove from favorites" else "Add to favorites",
                    onClick = vm::toggleFavorite,
                    tint = if (recipe.isFavorite) CaloriesColor else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(8.dp))
                Box {
                    var menuOpen by remember { mutableStateOf(false) }
                    CircleIconButton(
                        icon = Icons.Rounded.MoreVert,
                        contentDescription = "More options",
                        onClick = { menuOpen = true },
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (recipe.isUserCreated) {
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onEdit(recipe.id)
                                },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Duplicate & edit") },
                                leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    vm.duplicate { newId -> onEdit(newId) }
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(if (recipe.userPhotoPath != null) "Change photo" else "Set photo") },
                            leadingIcon = { Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                pickPhoto.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        )
                        if (recipe.userPhotoPath != null) {
                            DropdownMenuItem(
                                text = { Text("Remove photo") },
                                leadingIcon = { Icon(Icons.Rounded.Close, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    vm.removePhoto()
                                },
                            )
                        }
                        if (recipe.isUserCreated) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    confirmDelete = true
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPlanSheet) {
        AddToPlanSheet(
            defaultSlot = recipe.mealType,
            onDismiss = { showPlanSheet = false },
            onConfirm = { date, slot, servings ->
                showPlanSheet = false
                vm.addToPlan(date, slot, servings)
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete recipe?") },
            text = { Text("“${recipe.title}” will be removed from your recipes and any plans. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(onBack)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Sections
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TitleBlock(recipe: RecipeEntity) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp)) {
        Text(
            listOf(recipe.cuisine, recipe.mealType.label).filter { it.isNotBlank() }.joinToString("  ·  ").uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(recipe.title, style = MaterialTheme.typography.headlineMedium)
        if (recipe.subtitle.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                recipe.subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InfoPill(
                Icons.Rounded.Schedule,
                if (recipe.cookMinutes > 0) {
                    "${recipe.totalMinutes} min · ${recipe.prepMinutes} prep"
                } else {
                    "${recipe.totalMinutes} min"
                },
            )
            InfoPill(Icons.Rounded.SignalCellularAlt, difficultyLabel(recipe.difficulty))
            InfoPill(Icons.Rounded.People, "Serves ${recipe.servings}")
            if (recipe.isUserCreated) InfoPill(Icons.Rounded.Restaurant, "Your recipe")
        }
        if (recipe.description.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(recipe.description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Kept outside any Row/Column scope so the plain (non-scoped) AnimatedVisibility resolves. */
@Composable
private fun ScrolledTitle(visible: Boolean, title: String) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InfoPill(icon: ImageVector, text: String) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun MacroCard(recipe: RecipeEntity) {
    val kcal = recipe.calories.coerceAtLeast(1).toFloat()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 24.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(vertical = 18.dp, horizontal = 12.dp)) {
            Text(
                "Per serving",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                // Calories vs. a ~700 kcal meal; macros as their share of the serving's energy.
                MacroStat("${recipe.calories}", "kcal", "Calories", recipe.calories / 700f, CaloriesColor)
                MacroStat("${recipe.proteinG}g", "", "Protein", recipe.proteinG * 4f / kcal, ProteinColor)
                MacroStat("${recipe.carbsG}g", "", "Carbs", recipe.carbsG * 4f / kcal, CarbsColor)
                MacroStat("${recipe.fatG}g", "", "Fat", recipe.fatG * 9f / kcal, FatColor)
            }
            if (recipe.fiberG > 0) {
                Text(
                    "${recipe.fiberG}g fiber",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun MacroStat(value: String, unit: String, label: String, progress: Float, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        MacroRing(progress = progress, color = color, size = 68.dp, stroke = 7.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (unit.isNotEmpty()) {
                    Text(
                        unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SectionTitle(title: String, trailing: String?, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun IngredientsHeader(servings: Int, baseServings: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Ingredients", style = MaterialTheme.typography.titleLarge)
            Text(
                if (servings == baseServings) "As written" else "Scaled from $baseServings servings",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ServingsStepper(servings = servings, onChange = onChange)
    }
}

@Composable
internal fun ServingsStepper(servings: Int, onChange: (Int) -> Unit, max: Int = RecipeDetailViewModel.MAX_SERVINGS) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(
            onClick = { onChange(servings - 1) },
            enabled = servings > 1,
            modifier = Modifier.size(36.dp),
        ) { Icon(Icons.Rounded.Remove, contentDescription = "Fewer servings") }
        Text(
            "$servings",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 40.dp),
        )
        FilledTonalIconButton(
            onClick = { onChange(servings + 1) },
            enabled = servings < max,
            modifier = Modifier.size(36.dp),
        ) { Icon(Icons.Rounded.Add, contentDescription = "More servings") }
    }
}

@Composable
private fun IngredientRow(ingredient: IngredientEntity, scale: Double) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(ingredient.aisle.emoji, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ingredient.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (ingredient.pantryStaple) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Staple",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            val note = ingredient.note
            if (!note.isNullOrBlank()) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            formatQuantity(ingredient.quantity * scale, ingredient.unit),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun StepCard(number: Int, text: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Row(Modifier.padding(16.dp)) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$number",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun MealPrepCard(recipe: RecipeEntity) {
    val prep = recipe.mealPrepFriendly
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 24.dp),
        shape = MaterialTheme.shapes.large,
        color = if (prep) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                contentAlignment = Alignment.Center,
            ) {
                if (prep) {
                    Icon(Icons.Rounded.AcUnit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                } else {
                    Text("🍽️", style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    if (prep) "Meal-prep friendly" else "Best eaten fresh",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    when {
                        prep && recipe.fridgeDays > 0 ->
                            "Keeps ${recipe.fridgeDays} day${if (recipe.fridgeDays == 1) "" else "s"} in the fridge. Cook once, portion into ${recipe.servings} containers."
                        prep -> "Portions and reheats well."
                        recipe.fridgeDays > 0 -> "Leftovers keep about ${recipe.fridgeDays} day${if (recipe.fridgeDays == 1) "" else "s"}."
                        else -> "Quick enough to make right before eating."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SourceRow(recipe: RecipeEntity) {
    if (recipe.sourceName.isBlank() && recipe.sourceUrl.isBlank()) return
    val uriHandler = LocalUriHandler.current
    val url = recipe.sourceUrl
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp)
            .clip(MaterialTheme.shapes.medium)
            .then(
                if (url.isNotBlank()) {
                    Modifier.clickable { runCatching { uriHandler.openUri(url) } }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Source",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                recipe.sourceName.ifBlank { url },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (url.isNotBlank()) {
                Text(
                    "Opens in browser",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (url.isNotBlank()) {
            Icon(
                Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = "Opens in browser",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Bars, sheets, buttons
// ---------------------------------------------------------------------------------------------

@Composable
private fun DetailBottomBar(onAddToList: () -> Unit, onAddToPlan: () -> Unit, onCook: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarAction(Icons.Rounded.AddShoppingCart, "Add to list", onAddToList)
            Spacer(Modifier.width(4.dp))
            BarAction(Icons.Rounded.EventAvailable, "Add to plan", onAddToPlan)
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onCook,
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Start cooking", style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

@Composable
private fun BarAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
internal fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        shadowElevation = 2.dp,
        modifier = Modifier.size(44.dp),
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = contentDescription, tint = tint)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddToPlanSheet(
    defaultSlot: MealType,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, MealType, Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val today = remember { LocalDate.now() }
    val days = remember(today) { (0L until 7L).map { today.plusDays(it) } }
    var dayIndex by remember { mutableIntStateOf(0) }
    var slot by remember { mutableStateOf(defaultSlot) }
    var servings by remember { mutableIntStateOf(1) }
    val dayFormat = remember { DateTimeFormatter.ofPattern("EEE d", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(
                "Add to plan",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "Day",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(days) { i, day ->
                    val label = when (i) {
                        0 -> "Today"
                        1 -> "Tomorrow"
                        else -> day.format(dayFormat)
                    }
                    SheetChip(selected = dayIndex == i, label = label, onClick = { dayIndex = i })
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "Meal",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                Modifier.padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MealType.entries.forEach { t ->
                    SheetChip(selected = slot == t, label = t.label, onClick = { slot = t })
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Servings", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "How many portions you'll eat",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ServingsStepper(servings = servings, onChange = { servings = it.coerceIn(1, 12) }, max = 12)
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { onConfirm(days[dayIndex], slot, servings) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("Add to ${if (dayIndex == 0) "today" else days[dayIndex].format(dayFormat)}")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetChip(selected: Boolean, label: String, onClick: () -> Unit) {
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

internal fun difficultyLabel(difficulty: Int): String = when {
    difficulty <= 1 -> "Easy"
    difficulty == 2 -> "Medium"
    else -> "Involved"
}
