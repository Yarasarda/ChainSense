package com.yarasa.chainsense

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import com.yarasa.chainsense.Bluetooth.BleManager

class MainViewModel(application: Application) : AndroidViewModel(application) {
    enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED }

    // --- DURUM VE BAĞLANTI ---
    var connectionStatus by mutableStateOf(ConnectionStatus.DISCONNECTED)
    var activeDeviceAddress by mutableStateOf<String?>(null)
    val foundDevices = mutableStateListOf<BluetoothDevice>()

    // --- AÇI VE KALİBRASYON ---
    private val _currentPitch = mutableFloatStateOf(0f)
    val currentPitch: State<Float> = _currentPitch
    private var offset = 0f

    // --- KAMBURLUK AYARLARI (HomeScreen'den Slider ile kontrol edilecek) ---
    var slouchThreshold by mutableFloatStateOf(15f) // Kaç dereceyi geçerse kambur?
    var slouchDurationMillis by mutableLongStateOf(3000L) // Kaç saniye (ms) durmalı?

    // --- KAMBURLUK TAKİP MOTORU ---
    var totalSlouchCount by mutableIntStateOf(0) // Toplam kaç kere tescillendi?
        private set

    var slouchProgress by mutableFloatStateOf(0f) // UI'daki halka için (0.0 - 1.0 arası)
        private set

    private var slouchStartTime: Long = 0
    private var isCheckingSlouch = false

    private var bleManager: BleManager? = null

    private fun ensureBleManagerInitialized() {
        if (bleManager == null) {
            bleManager = BleManager(
                context = getApplication(),
                onConnectionStateChanged = { isConnected ->
                    connectionStatus = if (isConnected) ConnectionStatus.CONNECTED else ConnectionStatus.DISCONNECTED

                    if (!isConnected) {
                        resetLogicState() // Bağlantı koptuğunda verileri temizle
                    }
                },
                onDataReceived = { data ->
                    updatePitch(data.toFloatOrNull() ?: 0f)
                }
            )
        }
    }

    // --- KRİTİK MANTIK: HER VERİ GELDİĞİNDE ÇALIŞAN MOTOR ---
    fun updatePitch(rawPitch: Float) {
        val adjustedPitch = rawPitch - offset
        _currentPitch.floatValue = adjustedPitch

        // Kamburluk tespiti başlasın mı?
        if (adjustedPitch > slouchThreshold) {
            if (!isCheckingSlouch) {
                // İlk kez eşiği aştı, kronometreyi başlat
                isCheckingSlouch = true
                slouchStartTime = System.currentTimeMillis()
                slouchProgress = 0f
            } else {
                // Zaten eşiğin üstünde, ne kadar süredir böyle duruyor?
                val elapsed = System.currentTimeMillis() - slouchStartTime

                // İlerlemeyi 0.0 ile 1.0 arasında güncelle (Halka doluyor...)
                slouchProgress = (elapsed.toFloat() / slouchDurationMillis).coerceIn(0f, 1f)

                if (elapsed >= slouchDurationMillis) {
                    // TESCİLLİ KAMBURLUK! Sayacı bir artır
                    totalSlouchCount++

                    // İşlem bitti, bir sonraki tespit için sıfırla
                    isCheckingSlouch = false
                    slouchStartTime = 0
                    slouchProgress = 0f

                    // TODO: Buraya bir titreşim/vibration tetikleyici eklenebilir
                }
            }
        } else {
            // Kullanıcı dikleştiği an her şeyi durdur (False positive önleme)
            // Hysteresis etkisi için eşiğin bir tık altına (örn -2) inmesini de bekleyebilirsin
            isCheckingSlouch = false
            slouchStartTime = 0
            slouchProgress = 0f
        }
    }

    // --- KONTROL FONKSİYONLARI ---
    fun calibrate() {
        offset += _currentPitch.floatValue
        _currentPitch.floatValue = 0f
    }

    fun disconnectDevice() {
        bleManager?.disconnect()
        resetLogicState()
    }

    private fun resetLogicState() {
        activeDeviceAddress = null
        _currentPitch.floatValue = 0f
        offset = 0f
        totalSlouchCount = 0
        slouchProgress = 0f
        isCheckingSlouch = false
    }

    @SuppressLint("MissingPermission")
    fun scanForDevices() {
        ensureBleManagerInitialized()
        foundDevices.clear()
        bleManager?.startScanning { device ->
            if (!device.name.isNullOrBlank() && foundDevices.none { it.address == device.address }) {
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
}