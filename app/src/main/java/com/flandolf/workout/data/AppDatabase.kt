package com.flandolf.workout.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Workout::class, ExerciseEntity::class, SetEntity::class], version = 3)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context, allowMainThread: Boolean = false): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        // Add nullable firestoreId columns to tables
                        db.execSQL("ALTER TABLE workouts ADD COLUMN firestoreId TEXT")
                        db.execSQL("ALTER TABLE exercises ADD COLUMN firestoreId TEXT")
                        db.execSQL("ALTER TABLE sets ADD COLUMN firestoreId TEXT")
                    }
                }

                val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        // Create unique indices on firestoreId to prevent duplicate imports
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_workouts_firestoreId ON workouts(firestoreId)")
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_exercises_firestoreId ON exercises(firestoreId)")
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sets_firestoreId ON sets(firestoreId)")
                    }
                }

                val builder = Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "workout_db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                val instance = if (allowMainThread) builder.allowMainThreadQueries()
                    .build() else builder.build()
                INSTANCE = instance
                instance
            }
        }
    }
}
