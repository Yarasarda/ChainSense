package com.yarasa.chainsense.Data

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity("slouch_log_table")
data class SlouchLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val dateString: String,
    val timestamp: Long
    //daha sonrasında ne kadar süre kambur kalındığı da eklenebilir...
)