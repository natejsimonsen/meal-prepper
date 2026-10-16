package com.mealprepper.app.ui.screens.recipes

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mealprepper.app.data.model.MealType
import com.mealprepper.app.ui.components.EmptyState
import com.mealprepper.app.ui.components.RecipeCard
import com.mealprepper.app.ui.components.appViewModel
import kotlin.math.abs

@Composable
fun RecipesScreen(
    contentPadding: PaddingValues,
    onOpenRecipe: (String) -> Unit,
    onCreateRecipe: () -> Unit,
) {
    val vm = appViewModel(key = "recipes") { c -> RecipesViewModel(c.mealRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val bottom = contentPadding.calculateBottomPadding()
    val gridState = rememberLazyStaggeredGridState()
    val fabExpanded by remember { derivedStateOf { gridState.firstVisibleItemIndex < 2 } }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottom + 104.dp),
            verticalItemSpacing = 14.dp,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "header", span = StaggeredGridItemSpan.FullLine) {
                RecipesHeader(
                    shown = state.recipes.size,
                    total = state.totalCount,
                    filtered = state.hasActiveFilters || vm.query.isNotBlank(),
                )
            }
            item(key = "search", span = StaggeredGridItemSpan.FullLine) {
                SearchField(query = vm.query, onQueryChange = vm::onQueryChange)
            }
            item(key = "mealTypes", span = StaggeredGridItemSpan.FullLine) {
                MealTypeRow(selected = state.mealType, onSelect = vm::setMealType)
            }
            item(key = "quick", span = StaggeredGridItemSpan.FullLine) {
                QuickFilterRow(
                    quick = state.quick,
                    tags = state.allTags,
                    selectedTags = state.selectedTags,
                    onToggleQuick = vm::toggleQuick,
                    onToggleTag = vm::toggleTag,
                )
            }
            if (!state.loading && state.recipes.isEmpty()) {
                item(key = "empty", span = StaggeredGridItemSpan.FullLine) {
                    if (state.totalCount == 0 && !state.hasActiveFilters && vm.query.isBlank()) {
                        EmptyState(
                            emoji = "🍳",
                            title = "No recipes yet",
                            body = "Your recipe box is warming up. Add your own go-to meal in a minute.",
                            actionLabel = "New recipe",
                            onAction = onCreateRecipe,
                        )
                    } else {
                        EmptyState(
                            emoji = "🥕",
                            title = "Nothing matches",
                            body = "Try fewer filters or a different search — there's a tasty high-protein option waiting.",
                            actionLabel = "Clear filters",
                            onAction = vm::clearAll,
                        )
                    }
                }
            }
            items(state.recipes, key = { it.id }) { recipe ->
                // Gentle stagger: deterministic per recipe so the layout doesn't reshuffle.
                val tall = abs(recipe.id.hashCode()) % 3 == 0
                RecipeCard(
                    recipe = recipe,
                    onClick = { onOpenRecipe(recipe.id) },
                    imageHeight = if (tall) 170.dp else 128.dp,
                )
            }
        }

        ExtendedFloatingActionButton(
            text = { Text("New recipe") },
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            onClick = onCreateRecipe,
            expanded = fabExpanded,
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = bottom + 20.dp),
        )
    }
}

@Composable
private fun RecipesHeader(shown: Int, total: Int, filtered: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 20.dp, start = 4.dp, end = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Recipes", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.padding(start = 10.dp))
            Text(
                if (filtered) "$shown of $total" else "$total",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Text(
            "Easy, high-protein & built for meal prep",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search chicken, tacos, oats…") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(22.dp),
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun MealTypeRow(selected: MealType?, onSelect: (MealType?) -> Unit) {
    val options: List<MealType?> = listOf<MealType?>(null) + MealType.entries
    LazyRow(
        modifier = Modifier.bleed(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options, key = { it?.name ?: "ALL" }) { type ->
            PillChip(
                selected = selected == type,
                label = type?.label ?: "All",
                onClick = { onSelect(type) },
                strong = true,
            )
        }
    }
}

@Composable
private fun QuickFilterRow(
    quick: Set<QuickFilter>,
    tags: List<String>,
    selectedTags: Set<String>,
    onToggleQuick: (QuickFilter) -> Unit,
    onToggleTag: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .bleed(16.dp)
            .padding(bottom = 6.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(QuickFilter.entries, key = { "q-" + it.name }) { f ->
            PillChip(
                selected = f in quick,
                label = "${f.emoji}  ${f.label}",
                onClick = { onToggleQuick(f) },
            )
        }
        items(tags, key = { "t-$it" }) { tag ->
            PillChip(
                selected = tag in selectedTags,
                label = "#$tag",
                onClick = { onToggleTag(tag) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PillChip(selected: Boolean, label: String, onClick: () -> Unit, strong: Boolean = false) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        shape = RoundedCornerShape(50),
        border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = if (strong) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            selectedLabelColor = if (strong) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
        ),
    )
}

/** Lets a full-width child (e.g. a chip row) extend past the parent's horizontal padding. */
internal fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    if (!constraints.hasBoundedWidth) {
        val p = measurable.measure(constraints)
        return@layout layout(p.width, p.height) { p.place(0, 0) }
    }
    val side = horizontal.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + side * 2,
            maxWidth = constraints.maxWidth + side * 2,
        )
    )
    layout(constraints.maxWidth, placeable.height) { placeable.place(-side, 0) }
}
