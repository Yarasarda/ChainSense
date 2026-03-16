package com.yarasa.chainsense.Screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier


@Composable
fun StatsScreen() {
    Row(modifier = Modifier.fillMaxSize()) {
        Text(text = "İstatistikler")
    }
}