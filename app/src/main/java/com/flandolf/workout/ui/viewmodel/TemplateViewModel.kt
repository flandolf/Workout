package com.flandolf.workout.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flandolf.workout.data.TemplateRepository
import com.flandolf.workout.data.Template
import com.flandolf.workout.data.TemplateWithExercises
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TemplateViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = TemplateRepository(application.applicationContext)

    private val _template = MutableStateFlow<TemplateWithExercises?>(null)
    val template: StateFlow<TemplateWithExercises?> = _template

    private var templateId: Long? = null
    fun loadTemplate(id: Long) {
        viewModelScope.launch {
            if (id <= 0L) {
                // Create a new template when the requested id is 0 (new template)
                val newId = repo.insertTemplate(Template(name = ""))
                templateId = newId
                _template.value = repo.getTemplate(newId)
            } else {
                templateId = id
                _template.value = repo.getTemplate(id)
            }
        }
    }

    // Expose reactive Flow for templates list
    fun templatesFlow(): Flow<List<TemplateWithExercises>> = repo.getAllTemplatesWithExercises()

    suspend fun getAllTemplatesWithExercises() = repo.getAllTemplatesWithExercises()

    // Insert a simple template (no exercises)
    suspend fun insertTemplate(template: Template): Long = repo.insertTemplate(template)

    // Persist a change to the template's name
    fun updateTemplateName(newName: String) {
        viewModelScope.launch {
            // Ensure a template exists to update
            if (templateId == null) {
                val newId = repo.insertTemplate(Template(name = newName))
                templateId = newId
                _template.value = repo.getTemplate(newId)
                return@launch
            }
            val id = templateId ?: return@launch
            val current = repo.getTemplate(id) ?: return@launch
            val t = current.template.copy(name = newName)
            repo.updateTemplate(t)
            _template.value = repo.getTemplate(id)
        }
    }

    // If the currently-loaded template is empty (blank name AND no exercises), delete it.
    fun discardIfEmpty() {
        val id = templateId ?: return
        viewModelScope.launch {
            val current = repo.getTemplate(id)
            if (current == null) {
                // Nothing to do
                templateId = null
                _template.value = null
                return@launch
            }
            val nameBlank = current.template.name.isBlank()
            val noExercises = current.exercises.isEmpty()
            if (nameBlank && noExercises) {
                repo.deleteTemplateById(id)
                templateId = null
                _template.value = null
            }
        }
    }

    // Allow deleting a template from the templates list
    fun deleteTemplate(id: Long) {
        viewModelScope.launch {
            repo.deleteTemplateById(id)
        }
    }

    fun addExercise(name: String) {
        viewModelScope.launch {
            // Lazily create a template if none exists yet (e.g., user opened editor and immediately adds an exercise)
            if (templateId == null) {
                val newId = repo.insertTemplate(Template(name = ""))
                templateId = newId
                _template.value = repo.getTemplate(newId)
            }
            val id = templateId ?: return@launch
            repo.addExercise(id, name)
            _template.value = repo.getTemplate(id)
        }
    }

    fun deleteExercise(exerciseId: Long) {
        val id = templateId ?: return
        viewModelScope.launch {
            repo.deleteExerciseById(exerciseId)
            _template.value = repo.getTemplate(id)
        }
    }

    fun addSet(exerciseId: Long, reps: Int, weight: Float) {
        val id = templateId ?: return
        viewModelScope.launch {
            repo.addSet(exerciseId, reps, weight)
            _template.value = repo.getTemplate(id)
        }
    }

    fun updateSet(exerciseId: Long, setIndex: Int, reps: Int, weight: Float) {
        val id = templateId ?: return
        viewModelScope.launch {
            val currentTemplate = _template.value ?: repo.getTemplate(id) ?: return@launch
            val ex = currentTemplate.exercises.find { it.exercise.id == exerciseId } ?: return@launch
            if (setIndex !in ex.sets.indices) return@launch
            val setId = ex.sets[setIndex].id
            repo.updateSet(setId, reps, weight)
            _template.value = repo.getTemplate(id)
        }
    }

    fun deleteSet(exerciseId: Long, setIndex: Int) {
        val id = templateId ?: return
        viewModelScope.launch {
            val currentTemplate = _template.value ?: repo.getTemplate(id) ?: return@launch
            val ex = currentTemplate.exercises.find { it.exercise.id == exerciseId } ?: return@launch
            if (setIndex !in ex.sets.indices) return@launch
            val setId = ex.sets[setIndex].id
            repo.deleteSetById(setId)
            _template.value = repo.getTemplate(id)
        }
    }

    fun moveExerciseUp(exerciseId: Long) {
        val id = templateId ?: return
        viewModelScope.launch {
            val currentTemplate = _template.value ?: repo.getTemplate(id) ?: return@launch
            val index = currentTemplate.exercises.indexOfFirst { it.exercise.id == exerciseId }
            if (index > 0) {
                val updatedExercises = currentTemplate.exercises.toMutableList()
                val exA = updatedExercises[index]
                val exB = updatedExercises[index - 1]
                repo.swapExercisePositions(exA.exercise, exB.exercise)
                _template.value = repo.getTemplate(id)
            }
        }
    }

    fun moveExerciseDown(exerciseId: Long) {
        val id = templateId ?: return
        viewModelScope.launch {
            val currentTemplate = _template.value ?: repo.getTemplate(id) ?: return@launch
            val index = currentTemplate.exercises.indexOfFirst { it.exercise.id == exerciseId }
            if (index >= 0 && index < currentTemplate.exercises.size - 1) {
                val updatedExercises = currentTemplate.exercises.toMutableList()
                val exA = updatedExercises[index]
                val exB = updatedExercises[index + 1]
                repo.swapExercisePositions(exA.exercise, exB.exercise)
                _template.value = repo.getTemplate(id)
            }
        }
    }

}
