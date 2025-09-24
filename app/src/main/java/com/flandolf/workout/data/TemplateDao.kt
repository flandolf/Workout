package com.flandolf.workout.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface TemplateDao {
    @Insert
    suspend fun insertTemplate(template: Template): Long

    @Update
    suspend fun updateTemplate(template: Template)

    @Delete
    suspend fun deleteTemplate(template: Template)

    @Transaction
    @Query("SELECT * FROM templates ORDER BY name ASC")
    suspend fun getAllTemplatesWithExercises(): List<TemplateWithExercises>

}
