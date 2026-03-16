package com.yarasa.chainsense

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home: Screen("Home", "Ana Sayfa", Icons.Default.Home)
    object Stats: Screen("stats", "İstatistikler", Icons.Default.Info)
    object Profile: Screen("profile", "Profil", Icons.Default.Person)
}