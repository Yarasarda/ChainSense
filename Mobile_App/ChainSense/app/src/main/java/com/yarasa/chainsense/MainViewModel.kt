package com.yarasa.chainsense

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import com.yarasa.chainsense.Bluetooth.BleManager

class MainViewModel(application: Application): AndroidViewModel(application) {
    enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED }

    var connectionStatus by mutableStateOf(ConnectionStatus.DISCONNECTED)
    var activeDeviceAddress by mutableStateOf<String?>(null)

    private val _currentPitch = mutableFloatStateOf(0f)
    val currentPitch: State<Float> = _currentPitch
    private var offset = 0f

    private var bleManager: BleManager? = null
    val foundDevices = mutableStateListOf<BluetoothDevice>()

    private fun ensureBleManagerInitialized() {
        if (bleManager == null) {
            bleManager = BleManager(
                context = getApplication(),
                onConnectionStateChanged = { isConnected ->
                    connectionStatus = if (isConnected) ConnectionStatus.CONNECTED else ConnectionStatus.DISCONNECTED
                },
                onDataReceived = { data ->
                    updatePitch(data.toFloatOrNull() ?: 0f)
                }
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun scanForDevices() {
        ensureBleManagerInitialized()
        foundDevices.clear()
        bleManager?.startScanning { device ->
            val deviceName = device.name
            val isNewAndNamed = !deviceName.isNullOrBlank() &&
                    foundDevices.none { it.address == device.address }
            if (isNewAndNamed) {
                foundDevices.add(device)
            }
        }
    }

    fun initializeBluetooth(macAddress: String) {
        activeDeviceAddress = macAddress
        connectionStatus = ConnectionStatus.CONNECTING
        ensureBleManagerInitialized()
        bleManager?.connectToDevice(macAddress)
    }

    fun updatePitch(rawPitch: Float){
        _currentPitch.floatValue = rawPitch - offset
    }

    fun calibrate(){
        offset += _currentPitch.floatValue
        _currentPitch.floatValue = 0f
    }
}