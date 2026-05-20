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
import com.yarasa.chainsense.Bluetooth.PostureService
import com.yarasa.chainsense.Data.ChainSenseDatabase
import com.yarasa.chainsense.Data.SettingsEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {

    enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED }

    // --- ROOM VERİTABANI BAĞLANTILARI ---
    private val database = ChainSenseDatabase.getDatabase(application)
    private val settingsDao = database.settingsDao()
    private val slouchLogDao = database.slouchLogDao()

    // --- 1. UI STATE'LERİ (Arayüzü besleyen veriler) ---
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
            val binder = service as PostureService.LocalBinder
            postureService = binder.getService()
            isBound = true

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
        // MÜHENDİSLİK DOKUNUŞU: Uygulama açılışında artık körleme servis başlatmıyoruz!
        // Onun yerine veritabanındaki ayarları ve geçmişi yüklüyoruz.

        // 1. Kullanıcının kayıtlı ayarlarını çek ve UI'a yansıt
        viewModelScope.launch {
            settingsDao.getSettingsFlow().collect { savedSettings ->
                savedSettings?.let {
                    slouchThreshold = it.slouchTreshold
                    slouchDurationMillis = it.slouchDurationMilis
                    syncSettingsToService() // Servis çalışıyorsa anında ona da ilet
                }
            }
        }

        // 2. Bugünün tarihini bul ve loglardan toplam kamburluk sayısını CANLI dinle!
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        viewModelScope.launch {
            slouchLogDao.getDailySlouchCountFlow(todayStr).collect { count ->
                totalSlouchCount = count // Veritabanı her log yediğinde UI otomatik artacak!
            }
        }
    }

    // MainActivity'den (İzinler Onaylanınca) Çağrılacak!
    fun startAndBindService() {
        val intent = Intent(getApplication(), PostureService::class.java).apply {
            action = PostureService.ACTION_START
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            getApplication<Application>().startForegroundService(intent)
        } else {
            getApplication<Application>().startService(intent)
        }

        getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onCleared() {
        super.onCleared()
        // ViewModel ölürse bağlantıyı kopar (Ama servis arkada yaşamaya devam eder)
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

            // DİKKAT: service.totalSlouchCount dinlemesini sildik!
            // Çünkü artık onu direkt Init bloğunda Room Veritabanından dinliyoruz.

            viewModelScope.launch {
                service.connectionState.collect { isConnected ->
                    connectionStatus = if (isConnected) ConnectionStatus.CONNECTED else ConnectionStatus.DISCONNECTED
                    if (!isConnected) activeDeviceAddress = null
                }
            }
        }
    }

    // --- 4. UI'DAN SERVİSE EMİR GÖNDERME ---
    @SuppressLint("MissingPermission")
    fun scanForDevices() {
        foundDevices.clear()
        postureService?.scanForDevices { device ->
            if (!device.name.isNullOrBlank() && foundDevices.none { it.address == device.address }) {
                foundDevices.add(device)
            }
        }
    }

    @SuppressLint("MissingPermission") // Garantiyi alalım
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

    // --- 5. AYAR GÜNCELLEMELERİ VE VERİTABANINA YAZMA ---
    fun updateThreshold(newThreshold: Float) {
        slouchThreshold = newThreshold
        postureService?.slouchThreshold = newThreshold

        // MÜHENDİSLİK: Ayarı anında veritabanına kazı (Kalıcı hafıza)
        viewModelScope.launch {
            settingsDao.insertOrUpdateSettings(
                SettingsEntity(id = 1, slouchTreshold = newThreshold, slouchDurationMilis = slouchDurationMillis)
            )
        }
    }

    fun updateDuration(newDuration: Long) {
        slouchDurationMillis = newDuration
        postureService?.slouchDurationMillis = newDuration

        // MÜHENDİSLİK: Ayarı anında veritabanına kazı (Kalıcı hafıza)
        viewModelScope.launch {
            settingsDao.insertOrUpdateSettings(
                SettingsEntity(id = 1, slouchTreshold = slouchThreshold, slouchDurationMilis = newDuration)
            )
        }
    }

    private fun syncSettingsToService() {
        postureService?.slouchThreshold = slouchThreshold
        postureService?.slouchDurationMillis = slouchDurationMillis
    }
}