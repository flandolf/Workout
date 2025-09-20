package com.flandolf.workout.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Workout::class, ExerciseEntity::class, SetEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context, allowMainThread: Boolean = false): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val builder = Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "workout_db"
                )
                val instance = if (allowMainThread) builder.allowMainThreadQueries()
                    .build() else builder.build()
                INSTANCE = instance
                instance
            }
        }
    }
}
