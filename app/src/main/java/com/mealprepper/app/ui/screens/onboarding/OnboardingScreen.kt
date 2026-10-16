package com.mealprepper.app.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mealprepper.app.data.model.GoalPace
import com.mealprepper.app.ui.components.appViewModel
import com.mealprepper.app.ui.screens.profile.BudgetSlider
import com.mealprepper.app.ui.screens.profile.FormCard
import com.mealprepper.app.ui.screens.profile.HeightFields
import com.mealprepper.app.ui.screens.profile.LabeledSwitch
import com.mealprepper.app.ui.screens.profile.NameField
import com.mealprepper.app.ui.screens.profile.NumberField
import com.mealprepper.app.ui.screens.profile.PaceCards
import com.mealprepper.app.ui.screens.profile.SexSelector
import com.mealprepper.app.ui.screens.profile.TargetsCard
import com.mealprepper.app.ui.theme.ApricotSoft
import com.mealprepper.app.ui.theme.Mint
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 4

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val vm = appViewModel { c -> OnboardingViewModel(c.mealRepository, c.profileRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val pager = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val page = pager.currentPage
    val isLast = page == PAGE_COUNT - 1

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Meal Prepper",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${page + 1} / $PAGE_COUNT",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            userScrollEnabled = state.loaded && !state.building,
        ) { index ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            ) {
                if (index != 0 && !state.loaded) {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    when (index) {
                        0 -> WelcomePage()
                        1 -> AboutYouPage(state, vm)
                        2 -> ActivityPage(state, vm)
                        else -> TargetsPage(state)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        PageIndicator(current = page, modifier = Modifier.align(Alignment.CenterHorizontally))

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (page > 0) {
                TextButton(
                    onClick = { scope.launch { pager.animateScrollToPage(page - 1) } },
                    enabled = !state.building,
                ) { Text("Back") }
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    if (isLast) vm.finish(onDone)
                    else scope.launch { pager.animateScrollToPage(page + 1) }
                },
                enabled = (page == 0 || state.loaded) && !state.building,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 28.dp, vertical = 14.dp),
            ) {
                AnimatedContent(
                    targetState = when {
                        state.building -> "building"
                        isLast -> "Build my first week"
                        page == 0 -> "Let's go"
                        else -> "Next"
                    },
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "cta",
                ) { label ->
                    if (label == "building") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Planning your week…")
                        }
                    } else {
                        Text(label)
                    }
                }
            }
        }
    }
}

@Composable
private fun PageIndicator(current: Int, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(PAGE_COUNT) { i ->
            val selected = i == current
            val width by animateDpAsState(if (selected) 26.dp else 8.dp, label = "dotWidth")
            val color by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                label = "dotColor",
            )
            Box(
                Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
private fun PageTitle(kicker: String, title: String, body: String? = null) {
    Spacer(Modifier.height(8.dp))
    Text(
        kicker.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(6.dp))
    Text(title, style = MaterialTheme.typography.headlineLarge)
    if (body != null) {
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun WelcomePage() {
    Spacer(Modifier.height(16.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.linearGradient(listOf(Mint, ApricotSoft))),
        contentAlignment = Alignment.Center,
    ) {
        Text("🥗", style = MaterialTheme.typography.displayLarge.copy(fontSize = MaterialTheme.typography.displayLarge.fontSize * 1.6f))
    }
    Spacer(Modifier.height(28.dp))
    Text("Meal prep,\nmade easy.", style = MaterialTheme.typography.displaySmall)
    Spacer(Modifier.height(10.dp))
    Text(
        "Tasty, high-protein meals that change every week, and one shopping list for the store.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))
    FeatureRow("🔒", "Everything stays on your phone", "No account, no internet. Your data never leaves this device.")
    FeatureRow("💪", "Protein-forward & varied", "Built around your swims and a slow, steady cut.")
    FeatureRow("🛒", "One smart list", "Sorted by aisle, with price estimates against your budget.")
}

@Composable
private fun FeatureRow(emoji: String, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.size(48.dp)) {
            Box(contentAlignment = Alignment.Center) { Text(emoji, style = MaterialTheme.typography.titleLarge) }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AboutYouPage(state: OnboardingUiState, vm: OnboardingViewModel) {
    val p = state.draft
    PageTitle("About you", "Let's get your numbers right.", "We use these only to estimate your calories. They stay on this phone.")
    FormCard {
        NameField(p.name, onValueChange = { v -> vm.edit { it.copy(name = v) } })
        NumberField(
            label = "Age", value = p.age.toDouble(), suffix = "yrs", min = 13.0, max = 100.0,
            onValueChange = { v -> vm.edit { it.copy(age = v.toInt()) } },
            modifier = Modifier.fillMaxWidth(),
        )
        SexSelector(p.sex, onSexChange = { s -> vm.edit { it.copy(sex = s) } })
        HeightFields(p.heightIn, onHeightChange = { h -> vm.edit { it.copy(heightIn = h) } })
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(
                label = "Weight", value = p.weightLb, suffix = "lb", allowDecimal = true, min = 70.0, max = 600.0,
                onValueChange = { v -> vm.edit { it.copy(weightLb = v) } },
                modifier = Modifier.weight(1f),
            )
            NumberField(
                label = "Goal weight", value = p.goalWeightLb, suffix = "lb", allowDecimal = true, min = 70.0, max = 600.0,
                onValueChange = { v -> vm.edit { it.copy(goalWeightLb = v) } },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ActivityPage(state: OnboardingUiState, vm: OnboardingViewModel) {
    val p = state.draft
    PageTitle("Activity & goal", "How you move, how fast to go.")
    FormCard(title = "🏊 Daily swim") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(
                label = "Minutes", value = p.swimMinutesPerDay.toDouble(), suffix = "min", min = 0.0, max = 300.0,
                onValueChange = { v -> vm.edit { it.copy(swimMinutesPerDay = v.toInt()) } },
                modifier = Modifier.weight(1f),
            )
            NumberField(
                label = "Distance", value = p.swimYardsPerDay.toDouble(), suffix = "yd", min = 0.0, max = 20_000.0,
                onValueChange = { v -> vm.edit { it.copy(swimYardsPerDay = v.toInt()) } },
                modifier = Modifier.weight(1f),
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    Text("Pace", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
    PaceCards(p.pace, onPaceChange = { pace -> vm.edit { it.copy(pace = pace) } })
    Spacer(Modifier.height(16.dp))
    FormCard {
        BudgetSlider(p.weeklyBudgetUsd, onValueChange = { b -> vm.edit { it.copy(weeklyBudgetUsd = b) } })
        LabeledSwitch(
            title = "Include a daily snack",
            subtitle = "A protein-forward snack between meals",
            checked = p.includeSnack,
            onCheckedChange = { v -> vm.edit { it.copy(includeSnack = v) } },
        )
    }
}

@Composable
private fun TargetsPage(state: OnboardingUiState) {
    val p = state.draft
    val t = state.targets ?: return
    val name = p.name.trim()
    PageTitle(
        "Your targets",
        if (name.isEmpty()) "Here's your plan." else "Here's your plan, $name.",
    )
    TargetsCard(t)
    Spacer(Modifier.height(16.dp))
    val deficit = t.tdee - t.calories
    val paceLine = when (p.pace) {
        GoalPace.MAINTAIN -> "Since you're maintaining, we eat roughly what you burn."
        GoalPace.GENTLE, GoalPace.STEADY ->
            if (deficit > 0) "For a ${p.pace.label.lowercase()} pace we aim about $deficit kcal below that each day."
            else "We keep you close to what you burn so you have energy for the pool."
    }
    FormCard(title = "How we got here") {
        Text(
            "Your resting burn is about ${t.bmr} kcal (Mifflin-St Jeor). Add daily life and your " +
                "${p.swimMinutesPerDay}-minute swim and you burn roughly ${t.tdee} kcal a day. $paceLine",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Protein is set high (${t.proteinG} g) to help you keep muscle while you lean out, and it keeps you full. " +
                "These are estimates. Weigh in weekly and adjust in Profile if needed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(12.dp))
    Text(
        "Next, we'll plan 7 days of easy meals. You can swap anything later.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
