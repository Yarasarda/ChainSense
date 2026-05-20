package com.yarasa.chainsense.Data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database([SettingsEntity::class, SlouchLogEntity::class], version = 1, exportSchema = false)
abstract class ChainSenseDatabase : RoomDatabase() {

    abstract fun settingsDao() : SettingsDao
    abstract fun slouchLogDao() : SlouchLogDao

    //singleton pattern
    companion object {
        @Volatile
        private var INSTANCE: ChainSenseDatabase? = null

        fun getDatabase(context: Context): ChainSenseDatabase{
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChainSenseDatabase::class.java,
                    "chainsense_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}