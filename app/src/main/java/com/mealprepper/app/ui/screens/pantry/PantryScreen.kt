package com.mealprepper.app.ui.screens.pantry

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mealprepper.app.ui.components.EmptyState
import com.mealprepper.app.ui.components.SectionHeader
import com.mealprepper.app.ui.components.appViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantryScreen(onBack: () -> Unit) {
    val vm = appViewModel { c -> PantryViewModel(c.mealRepository, c.profileRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    val submit: () -> Unit = {
        if (vm.add(text)) text = ""
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Pantry") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
        ) {
            item(key = "explain") {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Rounded.Inventory2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                "Already have it",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Staples you keep at home. When you build a shopping list, anything in your " +
                                    "pantry is skipped, so you only buy what you actually need.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                            )
                        }
                    }
                }
            }

            item(key = "add") {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    placeholder = { Text("Add a staple, e.g. brown rice") },
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
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
            }

            if (state.suggestions.isNotEmpty()) {
                item(key = "suggest") {
                    Column {
                        SectionHeader(title = "Common staples")
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.suggestions.forEach { name ->
                                AssistChip(
                                    onClick = { vm.add(name) },
                                    label = { Text(name) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Rounded.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                                        )
                                    },
                                    shape = CircleShape,
                                )
                            }
                        }
                    }
                }
            }

            item(key = "list-header") {
                SectionHeader(
                    title = if (state.items.isEmpty()) "Your pantry" else "Your pantry · ${state.items.size}",
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            if (state.items.isEmpty() && !state.loading) {
                item(key = "empty") {
                    EmptyState(
                        emoji = "🫙",
                        title = "Pantry is empty",
                        body = "Tap a common staple above or add your own.",
                    )
                }
            }

            items(state.items, key = { it }) { name ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .animateItem(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                ) {
                    Row(
                        Modifier.padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            name.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { vm.remove(name) }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove $name")
                        }
                    }
                }
            }
        }
    }
}
