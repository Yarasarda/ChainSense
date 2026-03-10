package com.yarasa.chainsense

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel

class MainViewModel: ViewModel() {
    private val _currentPitch = mutableFloatStateOf(0f)
    val currentPitch: State<Float> = _currentPitch
    private var offset = 0f

    fun onBluetoothDataReceived(data: String){
        val pitch = data.toFloatOrNull() ?: 0f
    }

    fun updatePitch(rawPitch: Float){
        val calibratedPitch = rawPitch - offset
        _currentPitch.value = calibratedPitch
    }

    fun calibrate(){
        offset = _currentPitch.value + offset
        _currentPitch.value = 0f
    }
}