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
    suspend fun insertTemplate(template: Templates): Long

    @Update
    suspend fun updateTemplate(template: Templates)

    @Delete
    suspend fun deleteTemplate(template: Templates)

    // Lookups to support upsert during download
    @Query("SELECT * FROM templates WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getTemplateByFirestoreId(firestoreId: String): Templates?

    @Query("SELECT * FROM templates WHERE id = :localId LIMIT 1")
    suspend fun getTemplateByLocalId(localId: Long): Templates?

    @Transaction
    @Query("SELECT * FROM templates ORDER BY date DESC")
    suspend fun getAllTemplates(): List<Templates>

}
