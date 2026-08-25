package com.jrprofessor.sketchly.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SketchlyEntity::class, ContactEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class SketchlyDatabase : RoomDatabase() {

    abstract fun sketchDao(): SketchlyDao
    abstract fun contactDao(): ContactDao

    companion object {
        @Volatile
        private var INSTANCE: SketchlyDatabase? = null

        fun getInstance(context: Context): SketchlyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SketchlyDatabase::class.java,
                    "sketchly_database",
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
