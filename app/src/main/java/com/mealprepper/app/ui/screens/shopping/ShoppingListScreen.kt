package com.mealprepper.app.ui.screens.shopping

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mealprepper.app.data.model.Aisle
import com.mealprepper.app.data.model.ShoppingItemEntity
import com.mealprepper.app.ui.components.EmptyState
import com.mealprepper.app.ui.components.MacroRing
import com.mealprepper.app.ui.components.appViewModel
import com.mealprepper.app.ui.components.formatQuantity
import com.mealprepper.app.ui.components.formatUsd
import com.mealprepper.app.ui.theme.Basil
import com.mealprepper.app.ui.theme.Tomato
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShoppingListScreen(
    contentPadding: PaddingValues,
    onOpenBuilder: () -> Unit,
    onOpenStoreMode: () -> Unit,
    onOpenPantry: () -> Unit,
) {
    val vm = appViewModel { c -> ShoppingViewModel(c.mealRepository, c.profileRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var confirmClearAll by rememberSaveable { mutableStateOf(false) }

    val onDelete: (ShoppingItemEntity) -> Unit = { item ->
        if (vm.requestDelete(item.id)) {
            snackbar.currentSnackbarData?.dismiss()
            scope.launch {
                var undone = false
                try {
                    undone = snackbar.showSnackbar(
                        message = "Removed ${item.name}",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short,
                    ) == SnackbarResult.ActionPerformed
                } finally {
                    if (undone) vm.undoDelete(item.id) else vm.commitDelete(item.id)
                }
            }
        }
    }

    val share: () -> Unit = {
        scope.launch {
            val text = vm.shareText()
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Shopping list")
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(send, "Share shopping list"))
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 32.dp),
        ) {
            item(key = "top") {
                TopRow(
                    hasItems = state.totalItems > 0,
                    hasChecked = state.checkedItems > 0,
                    onShare = share,
                    onPantry = onOpenPantry,
                    onClearChecked = vm::clearChecked,
                    onClearAll = { confirmClearAll = true },
                )
            }
            if (state.totalItems > 0) {
                item(key = "summary") {
                    SummaryCard(state = state, onStore = onOpenStoreMode, onBuilder = onOpenBuilder)
                }
            }
            item(key = "quickadd") {
                QuickAddField(onAdd = { text -> vm.quickAdd(text) != null })
            }
            if (state.totalItems == 0 && !state.loading) {
                item(key = "empty") {
                    EmptyState(
                        emoji = "🛒",
                        title = "Your list is empty",
                        body = "Build one list from your week's plan plus any extra recipes. " +
                            "Pantry staples you already have are skipped.",
                        actionLabel = "Build my list",
                        onAction = onOpenBuilder,
                    )
                }
            }
            state.groups.forEach { group ->
                stickyHeader(key = "aisle-${group.aisle.name}") {
                    AisleHeader(aisle = group.aisle, remaining = group.remaining, total = group.items.size)
                }
                items(group.items, key = { it.id }) { item ->
                    SwipeableItemRow(
                        item = item,
                        onToggle = { vm.toggle(item) },
                        onDelete = { onDelete(item) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = contentPadding.calculateBottomPadding() + 8.dp),
        )
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            icon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null) },
            title = { Text("Clear the whole list?") },
            text = { Text("This removes every item, including ones you added by hand. You can rebuild it from your plan anytime.") },
            confirmButton = {
                Button(onClick = {
                    confirmClearAll = false
                    vm.clearAll()
                }) { Text("Clear all") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun TopRow(
    hasItems: Boolean,
    hasChecked: Boolean,
    onShare: () -> Unit,
    onPantry: () -> Unit,
    onClearChecked: () -> Unit,
    onClearAll: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Shopping list", style = MaterialTheme.typography.headlineLarge)
            Text(
                "One list for the whole week",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Share list") },
                    leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                    enabled = hasItems,
                    onClick = {
                        menuOpen = false
                        onShare()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Pantry") },
                    leadingIcon = { Icon(Icons.Rounded.Inventory2, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onPantry()
                    },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Clear checked") },
                    leadingIcon = { Icon(Icons.Rounded.TaskAlt, contentDescription = null) },
                    enabled = hasChecked,
                    onClick = {
                        menuOpen = false
                        onClearChecked()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Clear all") },
                    leadingIcon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null) },
                    enabled = hasItems,
                    onClick = {
                        menuOpen = false
                        onClearAll()
                    },
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(state: ShoppingUiState, onStore: () -> Unit, onBuilder: () -> Unit) {
    val budgetColor = if (state.overBudget) Tomato else Basil
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val ring by animateFloatAsState(state.progress, label = "listProgress")
                MacroRing(progress = ring, color = MaterialTheme.colorScheme.primary, size = 84.dp, stroke = 9.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${state.checkedItems}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "of ${state.totalItems}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Estimated total",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        formatUsd(state.estTotalUsd),
                        style = MaterialTheme.typography.headlineMedium,
                        color = budgetColor,
                    )
                    val diff = state.budgetUsd - state.estTotalUsd
                    Text(
                        if (diff >= 0) "${formatUsd(diff)} under your $${state.budgetUsd} budget"
                        else "${formatUsd(-diff)} over your $${state.budgetUsd} budget",
                        style = MaterialTheme.typography.bodySmall,
                        color = budgetColor,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onStore,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = CircleShape,
            ) {
                Icon(Icons.Rounded.Storefront, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("I'm at the store", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onBuilder,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = CircleShape,
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Build / rebuild")
            }
        }
    }
}

@Composable
private fun QuickAddField(onAdd: (String) -> Boolean) {
    var text by rememberSaveable { mutableStateOf("") }
    val submit: () -> Unit = {
        if (onAdd(text)) text = ""
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        placeholder = { Text("Quick add — try \"2 avocado\" or \"1 lb shrimp\"") },
        leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
        trailingIcon = {
            if (text.isNotBlank()) {
                TextButton(onClick = submit) { Text("Add") }
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { submit() }),
    )
}

@Composable
private fun AisleHeader(aisle: Aisle, remaining: Int, total: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(aisle.emoji, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(10.dp))
        Text(aisle.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(
            if (remaining == 0) "all $total done" else "$remaining of $total left",
            style = MaterialTheme.typography.labelMedium,
            color = if (remaining == 0) Basil else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableItemRow(
    item: ShoppingItemEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnDelete by rememberUpdatedState(onDelete)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                currentOnDelete()
            }
            true
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        backgroundContent = {
            val alignEnd = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.large)
                    .background(Tomato.copy(alpha = 0.9f))
                    .padding(horizontal = 24.dp),
                contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = Color.White)
            }
        },
    ) {
        ShoppingItemRow(item = item, onToggle = onToggle)
    }
}

@Composable
private fun ShoppingItemRow(item: ShoppingItemEntity, onToggle: () -> Unit) {
    val alpha by animateFloatAsState(if (item.checked) 0.5f else 1f, label = "itemAlpha")
    Surface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp).alpha(alpha),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedCheck(checked = item.checked)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.name.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        formatQuantity(item.quantity, item.unit),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                val caption = when {
                    item.forRecipes.isNotEmpty() -> "for: " + item.forRecipes.joinToString(", ")
                    item.isManual -> "added by you"
                    else -> null
                }
                val note = item.note
                val captionText = listOfNotNull(caption, note?.takeIf { it.isNotBlank() }).joinToString(" · ")
                if (captionText.isNotEmpty()) {
                    Text(
                        captionText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (item.estPriceUsd > 0.0) {
                Spacer(Modifier.width(10.dp))
                Text(
                    formatUsd(item.estPriceUsd),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Round check that fills and pops a checkmark. Shared with Store mode. */
@Composable
internal fun AnimatedCheck(checked: Boolean, size: Dp = 26.dp) {
    val fill by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "checkFill",
    )
    val borderColor by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        label = "checkBorder",
    )
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(fill)
            .border(BorderStroke(2.dp, borderColor), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = checked,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(size * 0.7f),
            )
        }
    }
}
