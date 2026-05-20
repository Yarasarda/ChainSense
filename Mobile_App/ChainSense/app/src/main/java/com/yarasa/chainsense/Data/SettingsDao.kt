package com.yarasa.chainsense.Data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSettings(settings: SettingsEntity)

    @Query("SELECT * FROM settings_table WHERE id = 1")
    fun getSettingsFlow(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings_table WHERE id = 1")
    suspend fun getSettingsNow(): SettingsEntity?

}