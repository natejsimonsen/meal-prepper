package com.mealprepper.app.ui.screens.shopping

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mealprepper.app.data.model.Aisle
import com.mealprepper.app.data.model.ShoppingItemEntity
import com.mealprepper.app.ui.components.EmptyState
import com.mealprepper.app.ui.components.appViewModel
import com.mealprepper.app.ui.components.formatQuantity
import com.mealprepper.app.ui.components.formatUsd
import kotlinx.coroutines.launch

private sealed interface StoreRow {
    val key: String

    data class Header(val group: AisleGroup) : StoreRow {
        override val key: String get() = "h-${group.aisle.name}"
    }

    data class Item(val item: ShoppingItemEntity) : StoreRow {
        override val key: String get() = "i-${item.id}"
    }
}

@Composable
fun StoreModeScreen(onBack: () -> Unit) {
    val vm = appViewModel { c -> StoreModeViewModel(c.mealRepository, c.profileRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Keep the screen awake while shopping.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val rows: List<StoreRow> = remember(state.groups) {
        buildList {
            state.groups.forEach { g ->
                add(StoreRow.Header(g))
                g.items.forEach { add(StoreRow.Item(it)) }
            }
        }
    }
    val headerIndex: Map<Aisle, Int> = remember(rows) {
        buildMap {
            rows.forEachIndexed { i, row -> if (row is StoreRow.Header) put(row.group.aisle, i) }
        }
    }
    val currentAisle: Aisle? by remember(rows) {
        derivedStateOf {
            val first = listState.firstVisibleItemIndex
            var found: Aisle? = null
            for (i in first downTo 0) {
                val row = rows.getOrNull(i)
                if (row is StoreRow.Header) {
                    found = row.group.aisle
                    break
                }
            }
            found ?: rows.firstNotNullOfOrNull { (it as? StoreRow.Header)?.group?.aisle }
        }
    }

    val onToggle: (ShoppingItemEntity) -> Unit = { item ->
        val completesList = !item.checked && state.checkedItems + 1 == state.totalItems
        haptic.performHapticFeedback(
            if (completesList) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove,
        )
        vm.toggle(item)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        // --- Top bar + progress ---------------------------------------------------------------
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Text(
                    "Store mode",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = vm::toggleHideChecked) {
                    Icon(
                        if (state.hideChecked) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.hideChecked) "Show done" else "Hide done")
                }
            }
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${state.checkedItems} of ${state.totalItems}",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "· ~${formatUsd(state.remainingUsd)} left",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                val animated by animateFloatAsState(state.progress, label = "storeProgress")
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animated)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }

            // --- Aisle jump bar ---------------------------------------------------------------
            if (state.groups.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.groups, key = { it.aisle.name }) { group ->
                        val selected = group.aisle == currentAisle
                        AisleJumpChip(
                            group = group,
                            selected = selected,
                            onClick = {
                                val index = headerIndex[group.aisle]
                                if (index != null) scope.launch { listState.animateScrollToItem(index) }
                            },
                        )
                    }
                }
            }
        }

        // --- Celebration -----------------------------------------------------------------------
        AnimatedVisibility(
            visible = state.allDone,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("🎉", style = MaterialTheme.typography.displayMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Cart complete!",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text(
                        "Everything on the list is in the cart. Head to checkout — prep day is going to be easy.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = onBack,
                        shape = CircleShape,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onPrimary,
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) { Text("Done shopping") }
                }
            }
        }

        // --- Items ------------------------------------------------------------------------------
        if (state.totalItems == 0 && !state.loading) {
            EmptyState(
                emoji = "🧺",
                title = "Nothing to shop for",
                body = "Build your list first, then come back here at the store.",
                actionLabel = "Back",
                onAction = onBack,
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                items(rows, key = { it.key }) { row ->
                    when (row) {
                        is StoreRow.Header -> StoreAisleHeader(row.group)
                        is StoreRow.Item -> StoreItemRow(item = row.item, onToggle = { onToggle(row.item) })
                    }
                }
                item(key = "bottom-space") { Spacer(Modifier.navigationBarsPadding()) }
            }
        }
    }
}

@Composable
private fun AisleJumpChip(group: AisleGroup, selected: Boolean, onClick: () -> Unit) {
    val done = group.remaining == 0
    val bg = when {
        selected -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val fg = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(group.aisle.emoji, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(6.dp))
        Text(group.aisle.label, style = MaterialTheme.typography.labelLarge, color = fg)
        Spacer(Modifier.width(6.dp))
        Text(
            if (done) "✓" else "${group.remaining}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = fg,
        )
    }
}

@Composable
private fun StoreAisleHeader(group: AisleGroup) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(group.aisle.emoji, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.width(12.dp))
        Text(
            group.aisle.label,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (group.remaining == 0) "done" else "${group.remaining} left",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StoreItemRow(item: ShoppingItemEntity, onToggle: () -> Unit) {
    val alpha by animateFloatAsState(if (item.checked) 0.38f else 1f, label = "storeAlpha")
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .heightIn(min = 76.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .alpha(alpha),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedCheck(checked = item.checked, size = 34.dp)
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.name.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
            )
            val note = item.note
            if (!note.isNullOrBlank()) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            formatQuantity(item.quantity, item.unit),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
