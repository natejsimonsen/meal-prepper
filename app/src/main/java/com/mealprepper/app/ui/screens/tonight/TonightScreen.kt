package com.mealprepper.app.ui.screens.tonight

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mealprepper.app.data.model.RecipeEntity
import com.mealprepper.app.data.model.RecipeWithIngredients
import com.mealprepper.app.ui.components.EmptyState
import com.mealprepper.app.ui.components.MacroChip
import com.mealprepper.app.ui.components.RecipeImage
import com.mealprepper.app.ui.components.appViewModel
import com.mealprepper.app.ui.components.formatQuantity
import com.mealprepper.app.ui.theme.CaloriesColor
import com.mealprepper.app.ui.theme.ProteinColor
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TonightScreen(onBack: () -> Unit, onOpenRecipe: (String) -> Unit, onOpenStoreMode: () -> Unit) {
    val vm = appViewModel { c -> TonightViewModel(c.mealRepository, c.profileRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(top = top + 8.dp, bottom = bottom + 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "back") {
            IconButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
        }
        item(key = "header") { TonightHeader() }

        when {
            state.loading -> item(key = "loading") {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
            }
            state.picks.isEmpty() -> item(key = "empty") {
                EmptyState(
                    emoji = "🍳",
                    title = "No quick picks yet",
                    body = "Recipes are still loading. Give it a second and come back.",
                )
            }
            else -> items(state.picks, key = { it.id }) { recipe ->
                PickCard(
                    recipe = recipe,
                    expanded = state.selectedId == recipe.id,
                    details = state.selected,
                    servings = state.servings,
                    adding = state.adding,
                    onToggle = { vm.toggle(recipe.id) },
                    onServings = vm::setServings,
                    onShop = { vm.addToListAndShop(onOpenStoreMode) },
                    onCook = { onOpenRecipe(recipe.id) },
                )
            }
        }

        item(key = "tips") { SleepTipsCard() }
    }
}

@Composable
private fun TonightHeader() {
    val now = remember { LocalTime.now() }
    val evening = now.hour >= 19 || now.hour < 3
    val bedLine = remember(now) {
        // An hour from now, rounded up to the next half hour: 9:30 → 10:30.
        val target = now.plusMinutes(60)
        val rounded = when {
            target.minute == 0 || target.minute == 30 -> target.withSecond(0).withNano(0)
            target.minute < 30 -> target.withMinute(30).withSecond(0).withNano(0)
            else -> target.plusHours(1).withMinute(0).withSecond(0).withNano(0)
        }
        "Bed by ${rounded.format(DateTimeFormatter.ofPattern("h:mm", Locale.getDefault()))}."
    }
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text("🌙", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        Text("Dinner in 10.", style = MaterialTheme.typography.displaySmall)
        Text(
            if (evening) bedLine else "Feet up after.",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Low effort, high protein, barely any dishes. Tap one to see what you need.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
    }
}

/** A one-line, honest reason this pick is good tonight. */
private fun whyLine(r: RecipeEntity): String {
    val tags = r.tags.map { it.lowercase() }
    return when {
        "no-cook" in tags -> "Zero cooking, just assemble and eat."
        r.totalMinutes <= 10 -> "Fastest option: on the table in ${r.totalMinutes} minutes."
        r.proteinG >= 40 -> "Big protein hit to help you recover from today's swim."
        r.calories in 1..550 -> "Light enough that it shouldn't sit heavy at bedtime."
        "one-pan" in tags || "sheet-pan" in tags -> "One pan, so cleanup takes a minute."
        r.mealPrepFriendly && r.servings > 1 -> "Make extra and tomorrow's lunch is done."
        r.subtitle.isNotBlank() -> r.subtitle
        else -> "Simple, filling and quick."
    }
}

@Composable
private fun PickCard(
    recipe: RecipeEntity,
    expanded: Boolean,
    details: RecipeWithIngredients?,
    servings: Int,
    adding: Boolean,
    onToggle: () -> Unit,
    onServings: (Int) -> Unit,
    onShop: () -> Unit,
    onCook: () -> Unit,
) {
    val borderColor by animateColorAsState(
        if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest,
        label = "pickBorder",
    )
    Surface(
        onClick = onToggle,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(2.dp, borderColor),
        shadowElevation = if (expanded) 2.dp else 0.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    ) {
        Column(Modifier.animateContentSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RecipeImage(recipe, Modifier.size(84.dp), corner = 20.dp, illustrationPadding = 10.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        recipe.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MacroChip("min", "${recipe.totalMinutes}", CaloriesColor)
                        MacroChip("protein", "${recipe.proteinG}g", ProteinColor)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        whyLine(recipe),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                PickDetails(
                    recipe = recipe,
                    details = details,
                    servings = servings,
                    adding = adding,
                    onServings = onServings,
                    onShop = onShop,
                    onCook = onCook,
                )
            }
        }
    }
}

@Composable
private fun PickDetails(
    recipe: RecipeEntity,
    details: RecipeWithIngredients?,
    servings: Int,
    adding: Boolean,
    onServings: (Int) -> Unit,
    onShop: () -> Unit,
    onCook: () -> Unit,
) {
    Column(Modifier.padding(top = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "${recipe.prepMinutes} min prep · ${recipe.cookMinutes} min cook · ${recipe.calories} kcal",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (recipe.servings > 1) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = servings == 1,
                    onClick = { onServings(1) },
                    label = { Text("Just tonight · 1") },
                )
                FilterChip(
                    selected = servings == recipe.servings,
                    onClick = { onServings(recipe.servings) },
                    label = { Text("Make ${recipe.servings} (leftovers)") },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("You'll need", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        if (details == null) {
            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        } else {
            val scale = servings.toDouble() / recipe.servings.coerceAtLeast(1)
            details.ingredients.sortedBy { it.position }.forEach { ing ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (ing.pantryStaple) MaterialTheme.colorScheme.outline
                                else MaterialTheme.colorScheme.primary
                            ),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        ing.name.replaceFirstChar { it.titlecase(Locale.getDefault()) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (ing.pantryStaple) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (ing.pantryStaple) "pantry" else formatQuantity(ing.quantity * scale, ing.unit),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onShop,
            enabled = !adding,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (adding) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(Icons.Rounded.ShoppingBag, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add to list & go shop")
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onCook, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Rounded.Restaurant, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("I have it, cook it")
        }
    }
}

@Composable
private fun SleepTipsCard() {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("😴  Sleep-friendly tips", style = MaterialTheme.typography.titleMedium)
            Tip("Go lighter and protein-forward this late. A smaller plate tends to sit better before sleep.")
            Tip("When you can, finish eating 2–3 hours before bed. On late nights, just keep it small.")
            Tip("Swam today? Have a glass or two of water with dinner.")
            Tip("Some people sleep worse after caffeine, alcohol, or very heavy or spicy meals late at night.")
            Text(
                "General tips, not medical advice.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun Tip(text: String) {
    Row {
        Text("•", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
