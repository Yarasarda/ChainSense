package com.yarasa.chainsense

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yarasa.chainsense.Bluetooth.BleManager
import com.yarasa.chainsense.Bluetooth.PostureService
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED }

    // --- 1. UI STATE'LERİ (Servisten beslenecekler) ---
    var connectionStatus by mutableStateOf(ConnectionStatus.DISCONNECTED)
        private set
    var activeDeviceAddress by mutableStateOf<String?>(null)
        private set

    private val _currentPitch = mutableFloatStateOf(0f)
    val currentPitch: State<Float> = _currentPitch

    var slouchProgress by mutableFloatStateOf(0f)
        private set
    var totalSlouchCount by mutableIntStateOf(0)
        private set

    val foundDevices = mutableStateListOf<BluetoothDevice>()

    // Ayarlar UI'da gösterilecek
    var slouchThreshold by mutableFloatStateOf(15f)
    var slouchDurationMillis by mutableLongStateOf(3000L)

    // --- 2. SERVİS BAĞLANTISI (BINDING) ---
    private var postureService: PostureService? = null
    private var isBound by mutableStateOf(false)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            // Servis bağlandığında köprüyü kur
            val binder = service as PostureService.LocalBinder
            postureService = binder.getService()
            isBound = true

            // Servis bağlandığı an verileri dinlemeye ve ayarları eşitlemeye başla
            syncSettingsToService()
            observeServiceData()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            postureService = null
            isBound = false
            connectionStatus = ConnectionStatus.DISCONNECTED
        }
    }

    init {
        // ViewModel ilk yaratıldığında Servisi başlat ve bağlan
        startAndBindService()
    }

    fun startAndBindService() {
        val intent = Intent(getApplication(), PostureService::class.java).apply {
            action = PostureService.ACTION_START
        }

        // Android 8.0+ için Foreground Service olarak başlatmak zorunlu
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            getApplication<Application>().startForegroundService(intent)
        } else {
            getApplication<Application>().startService(intent)
        }

        // Servise Bind (Abone) ol
        getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onCleared() {
        super.onCleared()
        // ViewModel ölürse bağlantıyı kopar (Ama servis arkada yaşamaya devam eder!)
        if (isBound) {
            getApplication<Application>().unbindService(serviceConnection)
            isBound = false
        }
    }

    // --- 3. SERVİSTEN GELEN AKIŞI (FLOW) DİNLEME ---
    private fun observeServiceData() {
        postureService?.let { service ->
            viewModelScope.launch {
                service.currentPitch.collect { _currentPitch.floatValue = it }
            }
            viewModelScope.launch {
                service.slouchProgress.collect { slouchProgress = it }
            }
            viewModelScope.launch {
                service.totalSlouchCount.collect { totalSlouchCount = it }
            }
            viewModelScope.launch {
                service.connectionState.collect { isConnected ->
                    connectionStatus = if (isConnected) ConnectionStatus.CONNECTED else ConnectionStatus.DISCONNECTED
                    if (!isConnected) activeDeviceAddress = null
                }
            }
        }
    }

    // --- 4. UI'DAN SERVİSE EMİR GÖNDERME FONKSİYONLARI ---
    @SuppressLint("MissingPermission") // Android Studio'ya "Aga sen karışma, izinleri ben MainActivity'de hallettim" diyoruz.
    fun scanForDevices() {
        foundDevices.clear()
        postureService?.scanForDevices { device ->
            if (!device.name.isNullOrBlank() && foundDevices.none { it.address == device.address }) {
                foundDevices.add(device)
            }
        }
    }

    fun initializeBluetooth(macAddress: String) {
        activeDeviceAddress = macAddress
        connectionStatus = ConnectionStatus.CONNECTING
        postureService?.connectToDevice(macAddress)
    }

    fun disconnectDevice() {
        postureService?.disconnectDevice()
        activeDeviceAddress = null
    }

    fun calibrate() {
        postureService?.calibrate()
    }

    // --- 5. AYAR GÜNCELLEMELERİ ---
    // HomeScreen'deki Slider'lar değiştiğinde bu fonksiyonları çağır ki Servis'in haberi olsun
    fun updateThreshold(newThreshold: Float) {
        slouchThreshold = newThreshold
        postureService?.slouchThreshold = newThreshold
    }

    fun updateDuration(newDuration: Long) {
        slouchDurationMillis = newDuration
        postureService?.slouchDurationMillis = newDuration
    }

    private fun syncSettingsToService() {
        postureService?.slouchThreshold = slouchThreshold
        postureService?.slouchDurationMillis = slouchDurationMillis
    }
}