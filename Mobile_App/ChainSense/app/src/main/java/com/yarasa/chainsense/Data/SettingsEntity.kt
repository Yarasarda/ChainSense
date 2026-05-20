package com.yarasa.chainsense.Data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity("settings_table")
data class SettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val slouchTreshold: Float = 15f,
    val slouchDurationMilis : Long = 3000L
)