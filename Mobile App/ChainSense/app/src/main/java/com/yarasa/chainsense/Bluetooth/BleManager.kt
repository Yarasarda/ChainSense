package com.yarasa.chainsense.Bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.annotation.SuppressLint
import java.util.*

// İsim çakışmaması için ismi değiştirdik
class BleManager(
    private val context: Context,
    private val onDataReceived: (String) -> Unit
) {
    // Sisteminkini tam adıyla çağırıyoruz ki karışmasın
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothGatt: BluetoothGatt? = null

    // ESP32'nin UUID'leri (ESP tarafındaki kodunla aynı olmalı aga!)
    val SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    val CHARACTERISTIC_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")

    @SuppressLint("MissingPermission") // İzin kontrolünü UI tarafında yapacağımız için susturuyoruz
    fun connectToDevice(address: String) {
        val device = bluetoothAdapter?.getRemoteDevice(address)
        bluetoothGatt = device?.connectGatt(context, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(SERVICE_UUID)
            val characteristic = service?.getCharacteristic(CHARACTERISTIC_UUID)
            if (characteristic != null) {
                // Bildirimleri (Notification) açalım ki ESP veri yolladığında haberimiz olsun
                gatt.setCharacteristicNotification(characteristic, true)
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            // Veriyi oku ve ViewModel'e gönder
            val data = characteristic.getStringValue(0) ?: "0"
            onDataReceived(data)
        }
    }
}
