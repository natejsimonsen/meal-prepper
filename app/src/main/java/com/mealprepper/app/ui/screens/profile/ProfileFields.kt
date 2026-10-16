package com.mealprepper.app.ui.screens.profile

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mealprepper.app.data.model.GoalPace
import com.mealprepper.app.data.model.NutritionTargets
import com.mealprepper.app.data.model.Sex
import com.mealprepper.app.ui.components.MacroChip
import com.mealprepper.app.ui.theme.CaloriesColor
import com.mealprepper.app.ui.theme.CarbsColor
import com.mealprepper.app.ui.theme.FatColor
import com.mealprepper.app.ui.theme.ProteinColor
import java.util.Locale
import kotlin.math.roundToInt

/*
 * Form building blocks shared by Onboarding and Profile (both owned by the same author).
 * Text fields keep their own local text state so typing never fights an async round-trip;
 * they push parsed, in-range values upward.
 */

/** Big rounded white card used to group form fields. */
@Composable
internal fun FormCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

internal fun formatNumber(value: Double, allowDecimal: Boolean): String =
    if (!allowDecimal || value % 1.0 == 0.0) value.roundToInt().toString()
    else String.format(Locale.US, "%.1f", value)

@Composable
internal fun NumberField(
    label: String,
    value: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    allowDecimal: Boolean = false,
    min: Double = 0.0,
    max: Double = 10_000.0,
) {
    var text by rememberSaveable { mutableStateOf(formatNumber(value, allowDecimal)) }
    val parsed = text.toDoubleOrNull()
    val valid = parsed != null && parsed >= min && parsed <= max
    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            var seenDot = false
            val cleaned = new.filter { ch ->
                when {
                    ch.isDigit() -> true
                    allowDecimal && ch == '.' && !seenDot -> {
                        seenDot = true
                        true
                    }
                    else -> false
                }
            }.take(6)
            text = cleaned
            val v = cleaned.toDoubleOrNull()
            if (v != null && v >= min && v <= max) onValueChange(v)
        },
        label = { Text(label) },
        suffix = if (suffix != null) {
            { Text(suffix) }
        } else null,
        isError = !valid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = ImeAction.Next,
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
    )
}

@Composable
internal fun NameField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            val trimmed = new.take(30)
            text = trimmed
            onValueChange(trimmed)
        },
        label = { Text("First name (optional)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Next,
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SexSelector(sex: Sex, onSexChange: (Sex) -> Unit, modifier: Modifier = Modifier) {
    val options = Sex.entries
    Column(modifier) {
        Text(
            "Sex (for the calorie formula)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == sex,
                    onClick = { onSexChange(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) {
                    Text(
                        when (option) {
                            Sex.MALE -> "Male"
                            Sex.FEMALE -> "Female"
                        }
                    )
                }
            }
        }
    }
}

/** Height as feet + inches; reports total inches. */
@Composable
internal fun HeightFields(heightIn: Int, onHeightChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var feet by rememberSaveable { mutableStateOf(heightIn / 12) }
    var inches by rememberSaveable { mutableStateOf(heightIn % 12) }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NumberField(
            label = "Height · feet",
            value = feet.toDouble(),
            onValueChange = {
                feet = it.toInt()
                onHeightChange(feet * 12 + inches)
            },
            suffix = "ft",
            min = 4.0,
            max = 7.0,
            modifier = Modifier.weight(1f),
        )
        NumberField(
            label = "Inches",
            value = inches.toDouble(),
            onValueChange = {
                inches = it.toInt()
                onHeightChange(feet * 12 + inches)
            },
            suffix = "in",
            min = 0.0,
            max = 11.0,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun paceBlurb(pace: GoalPace): Pair<String, String> = when (pace) {
    GoalPace.MAINTAIN -> "⚖️" to "Fuel your swims and hold your weight."
    GoalPace.GENTLE -> "🌱" to "Recommended. Easy to stick with and kind to your muscles."
    GoalPace.STEADY -> "🏁" to "A little faster, a little hungrier. Still sustainable."
}

@Composable
internal fun PaceCards(pace: GoalPace, onPaceChange: (GoalPace) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GoalPace.entries.forEach { option ->
            val selected = option == pace
            val borderColor by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                label = "paceBorder",
            )
            val (emoji, blurb) = paceBlurb(option)
            Surface(
                onClick = { onPaceChange(option) },
                shape = MaterialTheme.shapes.medium,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(emoji, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(option.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            blurb,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    RadioButton(selected = selected, onClick = { onPaceChange(option) })
                }
            }
        }
    }
}

/** $50–$300 in $10 steps. Commits when the drag ends. */
@Composable
internal fun BudgetSlider(value: Int, onValueChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var local by remember(value) { mutableFloatStateOf(value.coerceIn(50, 300).toFloat()) }
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Weekly grocery budget", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Used to keep your list in check",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "\$${local.roundToInt()}",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            valueRange = 50f..300f,
            steps = 24,
            onValueChangeFinished = { onValueChange(local.roundToInt()) },
        )
        Row {
            Text("\$50", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text("\$300", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun LabeledSwitch(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun MacroTile(label: String, grams: Int, color: Color, modifier: Modifier = Modifier) {
    val animated by animateIntAsState(grams, tween(700), label = "macroTile")
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(color.copy(alpha = 0.12f))
            .padding(vertical = 14.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.height(6.dp))
        Text("${animated}g", style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Live daily targets: big calorie number + macro tiles + fiber/water chips. */
@Composable
internal fun TargetsCard(targets: NutritionTargets, modifier: Modifier = Modifier, title: String = "Your daily targets") {
    val calories by animateIntAsState(targets.calories, tween(700), label = "targetCalories")
    FormCard(modifier = modifier) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$calories", style = MaterialTheme.typography.displayMedium, color = CaloriesColor)
            Spacer(Modifier.width(8.dp))
            Text(
                "kcal / day",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MacroTile("Protein", targets.proteinG, ProteinColor, Modifier.weight(1f))
            MacroTile("Carbs", targets.carbsG, CarbsColor, Modifier.weight(1f))
            MacroTile("Fat", targets.fatG, FatColor, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MacroChip("fiber", "${targets.fiberG}g", ProteinColor)
            MacroChip("cups water", "${targets.waterCups}", MaterialTheme.colorScheme.tertiary)
        }
    }
}
