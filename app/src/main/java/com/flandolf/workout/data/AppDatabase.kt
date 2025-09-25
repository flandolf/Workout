package com.flandolf.workout.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Workout::class, ExerciseEntity::class, SetEntity::class, Template::class],
    version = 2
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun templateDao(): TemplateDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add firestoreId column to templates table
                try {
                    db.execSQL("ALTER TABLE templates ADD COLUMN firestoreId TEXT")
                } catch (_: Exception) { /* Column may already exist */ }
                try {
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_templates_firestoreId ON templates(firestoreId)")
                } catch (_: Exception) { /* Index may already exist */ }
            }
        }

        fun getInstance(context: Context, allowMainThread: Boolean = false): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val builder = Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "workout_db"
                ).addMigrations(MIGRATION_1_2)
                val instance = if (allowMainThread) builder.allowMainThreadQueries()
                    .build() else builder.build()
                INSTANCE = instance
                instance
            }
        }
    }
}
