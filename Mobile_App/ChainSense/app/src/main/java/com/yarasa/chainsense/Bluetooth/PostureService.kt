package com.yarasa.chainsense.Bluetooth

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.yarasa.chainsense.Data.ChainSenseDatabase
import com.yarasa.chainsense.Data.SettingsEntity
import com.yarasa.chainsense.Data.SlouchLogEntity
import com.yarasa.chainsense.MainActivity
import com.yarasa.chainsense.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.jvm.java

class PostureService : Service(){
    // --- BINDER KÖPRÜSÜ (ViewModel'in bu servise bağlanması için) ---
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): PostureService = this@PostureService
    }

    // --- COROUTINE VE DATABASE
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var  database: ChainSenseDatabase

    // --- CANLI VERİLER (StateFlow ile UI'a akacak) ---
    private val _currentPitch = MutableStateFlow(0f)
    val currentPitch: StateFlow<Float> = _currentPitch.asStateFlow()

    private val _slouchProgress = MutableStateFlow(0f)
    val slouchProgress: StateFlow<Float> = _slouchProgress.asStateFlow()

    private val _totalSlouchCount = MutableStateFlow(0)
    val totalSlouchCount: StateFlow<Int> = _totalSlouchCount.asStateFlow()

    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    // MÜHENDİSLİK: Batarya State'i
    private val _batteryLevel = MutableStateFlow(-1)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    // --- KAMBURLUK AYARLARI ---
    var slouchThreshold = 15f
    var slouchDurationMillis = 3000L
    private var offset = 0f
    private var lastRawPitch = 0f

    // --- İÇ MOTOR DEĞİŞKENLERİ ---
    private var bleManager: BleManager? = null
    private var isCheckingSlouch = false
    private var slouchStartTime: Long = 0
    private var isAlertActive = false
    private val hysteresisOffset = 3f


    companion object {
        const val CHANNEL_ID = "ChainSense_Service_Channel"
        const val NOTIFICATION_ID = 352
        const val ACTION_START = "ACTION_START_POSTURE_SERVICE"
        const val ACTION_STOP = "ACTION_STOP_POSTURE_SERVICE"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        database = ChainSenseDatabase.getDatabase(this)

        serviceScope.launch {
            val savedSettings = database.settingsDao().getSettingsNow()
            if (savedSettings != null) {
                slouchThreshold = savedSettings.slouchTreshold
                slouchDurationMillis = savedSettings.slouchDurationMilis
            } else {
                database.settingsDao().insertOrUpdateSettings(
                    SettingsEntity(1, slouchThreshold, slouchDurationMillis)
                )
            }
        }

        initBleManager()
    }

    override fun onDestroy() {
        bleManager?.disconnect()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startForeground(NOTIFICATION_ID, buildNotification())
            ACTION_STOP -> {
                bleManager?.disconnect()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    // --- BLE VE MOTOR MANTIĞI ---
    private fun initBleManager() {
        bleManager = BleManager(
            context = this,
            onConnectionStateChanged = { isConnected ->
                _connectionState.value = isConnected
                if (!isConnected) resetLogicState()
            },
            onDataReceived = { data ->
                processPitch(data.toFloatOrNull() ?: 0f)
            },
            // MÜHENDİSLİK: Eksik olan batarya kanalı eklendi!
            onBatteryReceived = { level ->
                _batteryLevel.value = level
            }
        )
    }

    private fun processPitch(rawPitch: Float) {
        lastRawPitch = rawPitch
        val adjustedPitch = kotlin.math.abs(rawPitch - offset)
        _currentPitch.value = adjustedPitch

        if (isAlertActive) {
            if (adjustedPitch < (slouchThreshold - hysteresisOffset)) {
                isAlertActive = false
                isCheckingSlouch = false
                slouchStartTime = 0
                _slouchProgress.value = 0f
            } else {
                _slouchProgress.value = 1f
            }
            return
        }

        if (adjustedPitch > slouchThreshold) {
            if (!isCheckingSlouch) {
                isCheckingSlouch = true
                slouchStartTime = System.currentTimeMillis()
                _slouchProgress.value = 0f
            } else {
                val elapsed = System.currentTimeMillis() - slouchStartTime
                _slouchProgress.value = (elapsed.toFloat() / slouchDurationMillis).coerceIn(0f, 1f)

                if (elapsed >= slouchDurationMillis) {
                    _totalSlouchCount.value += 1
                    isAlertActive = true
                    _slouchProgress.value = 1f

                    updateNotification(_totalSlouchCount.value)

                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val todayStr = dateFormat.format(Date())

                    serviceScope.launch {
                        database.slouchLogDao().insertLog(
                            SlouchLogEntity(
                                dateString = todayStr,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }

                    // TODO: Titreşim tetiklenecek
                }
            }
        } else {
            if (adjustedPitch < (slouchThreshold - 1f)) {
                isCheckingSlouch = false
                slouchStartTime = 0
                _slouchProgress.value = 0f
            }
        }
    }

    fun connectToDevice(macAddress: String) {
        bleManager?.connectToDevice(macAddress)
    }

    fun disconnectDevice() {
        bleManager?.disconnect()
    }

    @SuppressLint("MissingPermission")
    fun scanForDevices(onDeviceFound: (BluetoothDevice) -> Unit) {
        bleManager?.startScanning(onDeviceFound)
    }

    fun calibrate() {
        offset = lastRawPitch
        _currentPitch.value = 0f

        isCheckingSlouch = false
        isAlertActive = false
        slouchStartTime = 0
        _slouchProgress.value = 0f
    }

    private fun resetLogicState() {
        _currentPitch.value = 0f
        _slouchProgress.value = 0f
        // MÜHENDİSLİK: Bağlantı kopunca bataryayı da belirsiz (-1) yap!
        _batteryLevel.value = -1
        isCheckingSlouch = false
        isAlertActive = false
    }

    private fun createNotificationChannel(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ChainSense Takip Servisi",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "ChainSense Duruş Takibi"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(currentCount: Int) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(currentCount))
    }

    private fun buildNotification(slouchCount: Int = 0): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val contentText = if (slouchCount > 0) {
            "$slouchCount kez kambur durdunuz!"
        } else {
            "Hayata karşı oldukça dik duruyorsun :D"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ChainSense")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }
}