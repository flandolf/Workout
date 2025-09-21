package com.flandolf.workout.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Workout::class, ExerciseEntity::class, SetEntity::class], version = 2)
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
