package com.mealprepper.app.ui.screens.recipes

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealprepper.app.data.model.MealType
import com.mealprepper.app.data.model.RecipeWithIngredients
import com.mealprepper.app.data.repo.MealRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

data class RecipeDetailUiState(
    val loading: Boolean = true,
    val data: RecipeWithIngredients? = null,
    /** Servings currently selected in the stepper (ingredient quantities scale to this). */
    val servings: Int = 1,
)

class RecipeDetailViewModel(
    private val repo: MealRepository,
    private val recipeId: String,
) : ViewModel() {

    /** null = "use the recipe's own servings". */
    private val selectedServings = MutableStateFlow<Int?>(null)

    val state: StateFlow<RecipeDetailUiState> = combine(
        repo.recipe(recipeId),
        selectedServings,
    ) { data, selected ->
        RecipeDetailUiState(
            loading = false,
            data = data,
            servings = selected ?: data?.recipe?.servings?.coerceAtLeast(1) ?: 1,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeDetailUiState())

    private val _messages = Channel<String>(Channel.BUFFERED)
    /** One-off snackbar messages. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun setServings(value: Int) {
        selectedServings.value = value.coerceIn(1, MAX_SERVINGS)
    }

    fun toggleFavorite() {
        viewModelScope.launch { repo.toggleFavorite(recipeId) }
    }

    fun addToList(servings: Int) {
        viewModelScope.launch {
            runCatching { repo.addRecipeToList(recipeId, servings) }
                .onSuccess {
                    _messages.send("Added ingredients for $servings serving${if (servings == 1) "" else "s"} to your list")
                }
                .onFailure { _messages.send("Couldn't update your list") }
        }
    }

    fun addToPlan(date: LocalDate, slot: MealType, servings: Int) {
        viewModelScope.launch {
            runCatching { repo.addToPlan(date, slot, recipeId, servings) }
                .onSuccess {
                    val day = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                    _messages.send("Planned for $day · ${slot.label}")
                }
                .onFailure { _messages.send("Couldn't add to your plan") }
        }
    }

    /** Saves an editable user copy of a built-in recipe, then reports the new id. */
    fun duplicate(onDone: (String) -> Unit) {
        viewModelScope.launch {
            val src = repo.recipe(recipeId).first() ?: return@launch
            val newId = newUserRecipeId()
            val copy = src.recipe.copy(
                id = newId,
                title = "${src.recipe.title} (my version)",
                isUserCreated = true,
                isFavorite = false,
                userPhotoPath = null,
            )
            val ingredients = src.ingredients
                .sortedBy { it.position }
                .mapIndexed { i, ing -> ing.copy(id = 0, recipeId = newId, position = i) }
            runCatching { repo.saveUserRecipe(copy, ingredients) }
                .onSuccess { savedId -> onDone(savedId) }
                .onFailure { _messages.send("Couldn't duplicate this recipe") }
        }
    }

    /**
     * Copies the picked image into app-private storage and points the recipe at it.
     * A fresh file name per pick makes the image reload instantly; the previous file is removed.
     */
    fun setPhoto(appContext: Context, uri: Uri) {
        val previous = state.value.data?.recipe?.userPhotoPath
        viewModelScope.launch {
            val path: String? = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(appContext.filesDir, "photos").apply { mkdirs() }
                    val safeId = recipeId.replace(Regex("[^A-Za-z0-9_-]"), "_")
                    val out = File(dir, "$safeId-${System.currentTimeMillis()}.jpg")
                    val input = appContext.contentResolver.openInputStream(uri)
                        ?: error("Unable to open image")
                    input.use { src -> out.outputStream().use { dst -> src.copyTo(dst) } }
                    if (previous != null && previous != out.absolutePath) File(previous).delete()
                    out.absolutePath
                }.getOrNull()
            }
            if (path != null) {
                repo.setRecipePhoto(recipeId, path)
                _messages.send("Photo updated")
            } else {
                _messages.send("Couldn't import that photo")
            }
        }
    }

    fun removePhoto() {
        val previous = state.value.data?.recipe?.userPhotoPath
        viewModelScope.launch {
            repo.setRecipePhoto(recipeId, null)
            if (previous != null) withContext(Dispatchers.IO) { runCatching { File(previous).delete() } }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val photo = state.value.data?.recipe?.userPhotoPath
        viewModelScope.launch {
            runCatching { repo.deleteUserRecipe(recipeId) }
                .onSuccess {
                    if (photo != null) withContext(Dispatchers.IO) { runCatching { File(photo).delete() } }
                    onDeleted()
                }
                .onFailure { _messages.send("Couldn't delete this recipe") }
        }
    }

    companion object {
        const val MAX_SERVINGS = 24
    }
}
