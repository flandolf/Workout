package com.flandolf.workout.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.flandolf.workout.data.TemplateRepository
import com.flandolf.workout.data.Template
import com.flandolf.workout.data.TemplateWithExercises

class TemplateViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = TemplateRepository(application.applicationContext)
    suspend fun getAllTemplates() = repo.getAllTemplatesWithExercises()

    // Insert a simple template (no exercises)
    suspend fun insertTemplate(template: Template): Long = repo.insertTemplate(template)

    // Insert a template along with nested exercises and sets
    suspend fun insertTemplateWithExercises(tpl: TemplateWithExercises): Long =
        repo.insertTemplateWithExercises(tpl)

}
