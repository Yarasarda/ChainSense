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
import com.yarasa.chainsense.Data.SlouchLogEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.collections.emptyList

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
    var batteryLevel by mutableIntStateOf(-1)
        private set

    val foundDevices = mutableStateListOf<BluetoothDevice>()

    // Ayarlar UI'da gösterilecek
    var slouchThreshold by mutableFloatStateOf(15f)
    var slouchDurationMillis by mutableLongStateOf(3000L)

    // --- 2. SERVİS BAĞLANTISI (BINDING) ---
    @SuppressLint("StaticFieldLeak")
    private var postureService: PostureService? = null
    private var isBound by mutableStateOf(false)

    // --- İSTATİSTİKLER ---
    val todaySlouchLogs = mutableStateListOf<SlouchLogEntity>()
    var weeklySlouchCount by mutableIntStateOf(0)
        private set
    var monthlySlouchCount by mutableIntStateOf(0)
        private set

    // MÜHENDİSLİK: Grafiğin okuyacağı veri modeli
    data class ChartPoint(val hour: Int, val minute: Int, val count: Int)

    var dailyChartData by mutableStateOf<List<ChartPoint>>(emptyList())
        private set

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
        // 1. Kullanıcının kayıtlı ayarlarını çek ve UI'a yansıt
        viewModelScope.launch {
            settingsDao.getSettingsFlow().collect { savedSettings ->
                savedSettings?.let {
                    slouchThreshold = it.slouchTreshold
                    slouchDurationMillis = it.slouchDurationMilis
                    syncSettingsToService()
                }
            }
        }

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val weekAgoStr = getDateDaysAgo(7)
        val monthAgoStr = getDateDaysAgo(30)

        viewModelScope.launch {
            slouchLogDao.getDailySlouchCountFlow(todayStr).collect { count ->
                totalSlouchCount = count
            }
        }

        viewModelScope.launch {
            slouchLogDao.getTodayLogFlow(todayStr).collect { logs ->
                todaySlouchLogs.clear()
                todaySlouchLogs.addAll(logs)

                // MÜHENDİSLİK: İşte senin o kopyalamaya üşendiğin, SADECE SAATE GÖRE gruplayan kod bloğu!
                val calendar = java.util.Calendar.getInstance()

                val groupedData = logs.groupBy { log ->
                    calendar.timeInMillis = log.timestamp
                    calendar.get(java.util.Calendar.HOUR_OF_DAY) // Dakikayı çöpe attık, sadece saati alıyoruz.
                }.map { (hour, logList) ->
                    ChartPoint(
                        hour = hour,
                        minute = 0, // Grafikte dakikanın bir önemi yok
                        count = logList.size // O saat içindeki vukuatların toplam sayısı
                    )
                }.sortedBy { it.hour } // Saatleri sıraya diziyoruz ki grafik yamulmasın.

                dailyChartData = groupedData
            }
        }

        viewModelScope.launch {
            slouchLogDao.getSlouchCountBetweenDatesFlow(weekAgoStr, todayStr).collect { count ->
                weeklySlouchCount = count
            }
        }

        viewModelScope.launch {
            slouchLogDao.getSlouchCountBetweenDatesFlow(monthAgoStr, todayStr).collect { count ->
                monthlySlouchCount = count
            }
        }
    }

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

            // MÜHENDİSLİK: Servisteki batarya verisini canlı olarak UI'a taşıyoruz!
            viewModelScope.launch {
                service.batteryLevel.collect { batteryLevel = it }
            }

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

        viewModelScope.launch {
            settingsDao.insertOrUpdateSettings(
                SettingsEntity(id = 1, slouchTreshold = newThreshold, slouchDurationMilis = slouchDurationMillis)
            )
        }
    }

    fun updateDuration(newDuration: Long) {
        slouchDurationMillis = newDuration
        postureService?.slouchDurationMillis = newDuration

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

    private fun getDateDaysAgo(days: Int): String {
        val calendar = java.util.Calendar.getInstance()

        calendar.add(java.util.Calendar.DAY_OF_YEAR, days * -1)
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(calendar.time)
    }
}