package com.yarasa.chainsense

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProfileScreen(viewModel: MainViewModel) {
    val devices = viewModel.foundDevices
    val activeAddress = viewModel.activeDeviceAddress
    val status = viewModel.connectionStatus

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = { viewModel.scanForDevices() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Cihazları Ara")
        }

        Divider(modifier = Modifier.padding(bottom = 8.dp))

        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(devices) { device ->
                val isThisDeviceActive = device.address == activeAddress

                val isConnected = isThisDeviceActive && status == MainViewModel.ConnectionStatus.CONNECTED
                val isConnecting = isThisDeviceActive && status == MainViewModel.ConnectionStatus.CONNECTING

                DeviceItem(
                    device = device,
                    isConnected = isConnected,
                    isConnecting = isConnecting,
                    onConnectClick = { viewModel.initializeBluetooth(device.address) },
                    onDisconnectClick = { viewModel.disconnectDevice() }
                )
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun DeviceItem(
    device: BluetoothDevice,
    isConnected: Boolean,
    isConnecting: Boolean,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name ?: "Bilinmeyen Cihaz",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = device.address,
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }

            Button(
                onClick = { if (isConnected) onDisconnectClick() else onConnectClick() },
                enabled = !isConnecting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) Color.Red else Color(0xFF17A605)
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                Text(
                    text = when {
                        isConnecting -> "..."
                        isConnected -> "Kes ＞﹏＜"
                        else -> "Bağlan ヾ(•ω•`)o"
                    },
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}