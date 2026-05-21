package com.yarasa.chainsense

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yarasa.chainsense.Screens.HomeScreen
import com.yarasa.chainsense.Screens.StatsScreen
import com.yarasa.chainsense.ui.theme.ChainSenseTheme
import com.yarasa.chainsense.ui.theme.CubeFontFamily
import com.yarasa.chainsense.ui.theme.GreatWarriorFamily

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()
        enableEdgeToEdge()

        setContent {
            ChainSenseTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->

                    ChainSenseApp(viewModel = viewModel, modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            viewModel.startAndBindService()
        } else {
            Toast.makeText(this, "Aga izinler eksik, arka planda çalışamam!", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Bluetooth İzinleri (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(android.Manifest.permission.BLUETOOTH_SCAN)
            permissionsToRequest.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissionsToRequest.add(android.Manifest.permission.BLUETOOTH)
            permissionsToRequest.add(android.Manifest.permission.BLUETOOTH_ADMIN)
        }

        // Lokasyon İzni (Bluetooth taraması için şart)
        permissionsToRequest.add(android.Manifest.permission.ACCESS_FINE_LOCATION)

        // BİLDİRİM İZNİ (Android 13+ için ZORUNLU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        permissionLauncher.launch(permissionsToRequest.toTypedArray())
    }


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChainSenseApp(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val items = listOf(Screen.Home, Screen.Stats, Screen.Profile)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "CHAINSENSE",
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = GreatWarriorFamily,
                        fontSize = 22.sp,
                        letterSpacing = 2.sp
                    )
                }
            )
        },
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(viewModel = viewModel)
            }
            composable(Screen.Stats.route) {
                StatsScreen(
                    weeklyCount = viewModel.weeklySlouchCount,
                    monthlyCount = viewModel.monthlySlouchCount,
                    chartData = viewModel.dailyChartData,
                    todayLogs = viewModel.todaySlouchLogs
                )
            }
            composable(Screen.Profile.route) {
                ProfileScreen(viewModel = viewModel)
            }
        }
    }
}
}