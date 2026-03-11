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
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Cihaz Yönetimi", fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Button(
            onClick = { viewModel.scanForDevices() },
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Cihazları Tara")
        }

        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(devices) { device ->
                val isThisDeviceConnecting = device.address == activeAddress

                val statusText = if (isThisDeviceConnecting) {
                    when (status) {
                        MainViewModel.ConnectionStatus.CONNECTING -> "Bağlanıyor..."
                        MainViewModel.ConnectionStatus.CONNECTED -> "Bağlandı ✓"
                        else -> "Bağlan"
                    }
                } else "Bağlan"

                DeviceItem(
                    device = device,
                    statusText = statusText,
                    isConnected = isThisDeviceConnecting && status == MainViewModel.ConnectionStatus.CONNECTED
                ) {
                    viewModel.initializeBluetooth(device.address)
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun DeviceItem(device: BluetoothDevice, statusText: String, isConnected: Boolean, onConnectClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        onClick = onConnectClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = device.name ?: "Bilinmeyen", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(text = device.address, fontSize = 12.sp, color = Color.Gray)
            }
            Text(
                text = statusText,
                color = if (isConnected) Color(0xFF4CAF50) else Color(0xFF2196F3),
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}