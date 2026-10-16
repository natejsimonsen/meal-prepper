package com.mealprepper.app.ui.screens.profile

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mealprepper.app.ui.components.appViewModel

private data class AvoidOption(val tag: String, val label: String)

private val avoidOptions = listOf(
    AvoidOption("spicy", "🌶️ Spicy"),
    AvoidOption("air-fryer", "Air fryer"),
    AvoidOption("grill", "Grill"),
    AvoidOption("microwave", "Microwave"),
)

private data class Source(val name: String, val detail: String, val url: String)

private val sources = listOf(
    Source(
        "Mifflin-St Jeor equation",
        "Resting energy (BMR). Mifflin MD et al., Am J Clin Nutr, 1990.",
        "https://pubmed.ncbi.nlm.nih.gov/2305711/",
    ),
    Source(
        "Compendium of Physical Activities",
        "MET values used to estimate what your swim burns.",
        "https://pacompendium.com/",
    ),
    Source(
        "ISSN position stand: protein and exercise",
        "Protein ranges for active people (Jäger et al., 2017).",
        "https://jissn.biomedcentral.com/articles/10.1186/s12970-017-0177-8",
    ),
    Source(
        "USDA FoodData Central",
        "Reference nutrient data for recipe estimates.",
        "https://fdc.nal.usda.gov/",
    ),
    Source(
        "Dietary Guidelines for Americans",
        "Fiber, fat and overall eating-pattern guidance.",
        "https://www.dietaryguidelines.gov/",
    ),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(onBack: () -> Unit, onOpenPantry: () -> Unit, onOpenProgress: () -> Unit) {
    val vm = appViewModel { c -> ProfileViewModel(c.mealRepository, c.profileRepository) }
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(4.dp))
            Text("Profile", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.weight(1f))
            Text(
                "Saved automatically",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp),
            )
        }

        if (!state.loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        val p = state.profile
        run {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                state.targets?.let { TargetsCard(it, title = "Live daily targets") }

                FormCard(title = "About you") {
                    NameField(p.name, onValueChange = { v -> vm.edit { it.copy(name = v) } })
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberField(
                            label = "Age", value = p.age.toDouble(), suffix = "yrs", min = 13.0, max = 100.0,
                            onValueChange = { v -> vm.edit { it.copy(age = v.toInt()) } },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.weight(1f))
                    }
                    SexSelector(p.sex, onSexChange = { s -> vm.edit { it.copy(sex = s) } })
                    HeightFields(p.heightIn, onHeightChange = { h -> vm.edit { it.copy(heightIn = h) } })
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberField(
                            label = "Weight", value = p.weightLb, suffix = "lb", allowDecimal = true,
                            min = 70.0, max = 600.0,
                            onValueChange = { v -> vm.edit { it.copy(weightLb = v) } },
                            modifier = Modifier.weight(1f),
                        )
                        NumberField(
                            label = "Goal", value = p.goalWeightLb, suffix = "lb", allowDecimal = true,
                            min = 70.0, max = 600.0,
                            onValueChange = { v -> vm.edit { it.copy(goalWeightLb = v) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                FormCard(title = "Activity & goal") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberField(
                            label = "Swim / day", value = p.swimMinutesPerDay.toDouble(), suffix = "min",
                            min = 0.0, max = 300.0,
                            onValueChange = { v -> vm.edit { it.copy(swimMinutesPerDay = v.toInt()) } },
                            modifier = Modifier.weight(1f),
                        )
                        NumberField(
                            label = "Distance", value = p.swimYardsPerDay.toDouble(), suffix = "yd",
                            min = 0.0, max = 20_000.0,
                            onValueChange = { v -> vm.edit { it.copy(swimYardsPerDay = v.toInt()) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    PaceCards(p.pace, onPaceChange = { pace -> vm.edit { it.copy(pace = pace) } })
                    BudgetSlider(p.weeklyBudgetUsd, onValueChange = { b -> vm.edit { it.copy(weeklyBudgetUsd = b) } })
                }

                FormCard(title = "Preferences") {
                    LabeledSwitch(
                        title = "Include a daily snack",
                        subtitle = "Adds a protein-forward snack to your plan",
                        checked = p.includeSnack,
                        onCheckedChange = { v -> vm.edit { it.copy(includeSnack = v) } },
                    )
                    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    LabeledSwitch(
                        title = "Material You colors",
                        subtitle = if (dynamicSupported) "Match your wallpaper instead of Basil green"
                        else "Needs Android 12 or newer",
                        checked = p.useDynamicColor,
                        onCheckedChange = { v -> vm.edit { it.copy(useDynamicColor = v) } },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("Skip recipes that are…", style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        avoidOptions.forEach { option ->
                            val selected = option.tag in p.avoidTags
                            FilterChip(
                                selected = selected,
                                onClick = { vm.toggleAvoidTag(option.tag) },
                                label = { Text(option.label) },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Rounded.Check, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }
                                } else null,
                            )
                        }
                    }
                }

                FormCard {
                    LinkRow(Icons.Rounded.Kitchen, "Pantry", "Staples you have, skipped on your list", onOpenPantry)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    LinkRow(Icons.Rounded.Insights, "Progress", "Swims, weight and water", onOpenProgress)
                }

                PrivacyCard()
                SourcesCard()

                Text(
                    "Targets are estimates for healthy adults, not medical advice. " +
                        "Check with a professional if you have a health condition.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PrivacyCard() {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("Privacy", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "• This app has no internet permission. It can't send anything anywhere.\n" +
                    "• No account, no ads, no analytics.\n" +
                    "• Cloud backups are turned off, so your data never leaves this phone.\n" +
                    "• Everything (recipes, plans, lists, swims, weight) is stored locally. " +
                    "Uninstalling the app deletes it.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SourcesCard() {
    val uriHandler = LocalUriHandler.current
    FormCard(title = "About & sources") {
        Text(
            "How your targets and recipe numbers are estimated:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        sources.forEach { source ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable {
                        try {
                            uriHandler.openUri(source.url)
                        } catch (ignored: Exception) {
                            // No browser available: the plain-text citation is still shown.
                        }
                    }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(source.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        source.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${source.url} · opens in browser",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = "Opens in browser",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
