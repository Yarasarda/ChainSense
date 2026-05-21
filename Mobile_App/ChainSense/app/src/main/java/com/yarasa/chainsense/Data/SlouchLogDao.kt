package com.yarasa.chainsense.Data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.sql.Date

@Dao
interface SlouchLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SlouchLogEntity)

    @Query("SELECT COUNT(*) FROM slouch_log_table WHERE dateString = :date")
    fun getDailySlouchCountFlow(date: String): Flow<Int>

    @Query("SELECT * FROM slouch_log_table WHERE dateString = :date ORDER BY timestamp ASC")
    suspend fun getLogsForDate(date: String): List<SlouchLogEntity>

    @Query("SELECT * FROM slouch_log_table WHERE dateString = :date ORDER BY timestamp DESC")
    fun getTodayLogFlow(date: String): Flow<List<SlouchLogEntity>>

    @Query("SELECT COUNT(*) FROM slouch_log_table WHERE dateString BETWEEN :startDate AND :endDate")
    fun getSlouchCountBetweenDatesFlow(startDate: String, endDate: String): Flow<Int>

}