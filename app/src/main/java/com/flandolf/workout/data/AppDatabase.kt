package com.flandolf.workout.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Workout::class, ExerciseEntity::class, SetEntity::class], version = 5)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercises ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workouts ADD COLUMN startTime INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}")
            }
        }

        fun getInstance(context: Context, allowMainThread: Boolean = false): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val builder = Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "workout_db"
                ).addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                val instance = if (allowMainThread) builder.allowMainThreadQueries()
                    .build() else builder.build()
                INSTANCE = instance
                instance
            }
        }
    }
}
