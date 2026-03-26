package com.yarasa.chainsense.Screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import com.yarasa.chainsense.MainViewModel

@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val currentPitch by viewModel.currentPitch

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {


        PostureVisualizer(currentPitch = currentPitch)

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = {viewModel.calibrate()}) {
            Text(text = "Kalibre Et")
        }
    }
}

@Composable
fun PostureVisualizer(currentPitch: Float) {
    // 1. MUTLAK DEĞER: Ekranda ve hesapta her zaman pozitif çalış
    val absolutePitch = abs(currentPitch)

    // 2. SENKRONİZE ANİMASYON: 150ms hayat kurtarır, lag yapmaz.
    val animatedPitch by animateFloatAsState(
        targetValue = absolutePitch,
        animationSpec = tween(durationMillis = 150),
        label = "pitch_anim"
    )

    val currentAbs = animatedPitch.coerceIn(0f, 45f)

    // Senin renk mantığın (Stabil tutuldu)
    val indicatorColor = when {
        currentAbs <= 7.5f -> Color.Green
        currentAbs <= 15.0f -> Color(0xFFFF5722)
        else -> Color.Red
    }

    Box(
        modifier = Modifier.size(300.dp).padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Gri Arka Plan Yayın
            drawArc(
                color = Color.LightGray.copy(alpha = 0.3f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = 50f, cap = StrokeCap.Round)
            )

            // Hareketli Ön Plan Yayın
            val finalSweep = (currentAbs / 45f) * 270f
            drawArc(
                color = indicatorColor,
                startAngle = 135f,
                sweepAngle = finalSweep.coerceAtLeast(0.1f),
                useCenter = false,
                style = Stroke(width = 40f, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Rakamın değiştiğini görmek için tek hane formatı
            Text(
                text = "${String.format("%.1f", absolutePitch)}°",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(text = "Eğim Açısı", fontSize = 14.sp, color = Color.Gray)
        }
    }
}