package com.yarasa.chainsense.Screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
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
    val slouchProgress = viewModel.slouchProgress
    val slouchCount = viewModel.totalSlouchCount
    val isConnected = viewModel.connectionStatus == MainViewModel.ConnectionStatus.CONNECTED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Üst Kısım: Senin hazırladığın Açı Görselleştirici
        PostureVisualizer(currentPitch = currentPitch)

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Onay Barı (Kambur durduğunda saniyeleri sayan bar)
        SlouchProgressBar(progress = slouchProgress)

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Sayaç ve İstatistik Kartı
        SlouchCounterCard(count = slouchCount)

        Spacer(modifier = Modifier.height(24.dp))

        // 4. Ayarlar Bölümü (Hassasiyet ve Süre)
        SettingsSection(viewModel = viewModel)

        Spacer(modifier = Modifier.weight(1f))

        // 5. Kalibrasyon Butonu
        Button(
            onClick = { viewModel.calibrate() },
            enabled = isConnected,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(text = "Duruşumu Sıfırla (Kalibre Et)", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SlouchProgressBar(progress: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (progress > 0f) "Kamburluk Tescilleniyor..." else "Duruş Takip Ediliyor",
            fontSize = 12.sp,
            color = if (progress > 0f) Color.Red else Color.Gray
        )
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp).padding(horizontal = 32.dp),
            color = Color.Red,
            trackColor = Color.LightGray.copy(alpha = 0.3f),
            strokeCap = StrokeCap.Round
        )
    }
}

@Composable
fun SlouchCounterCard(count: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = "Toplam Kamburluk", fontSize = 14.sp)
                Text(text = "$count Kez", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            }
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (count > 10) Color.Red else Color.Unspecified
            )
        }
    }
}

@Composable
fun SettingsSection(viewModel: MainViewModel) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Hassasiyet Ayarları", fontWeight = FontWeight.Bold, fontSize = 16.sp)

        Spacer(modifier = Modifier.height(8.dp))

        // Uyarı Açısı Slider
        Text(text = "Uyarı Eşiği: ${viewModel.slouchThreshold.toInt()}°", fontSize = 14.sp)
        Slider(
            value = viewModel.slouchThreshold,
            onValueChange = { viewModel.updateThreshold(it)},
            valueRange = 5f..45f
        )

        // Onay Süresi Slider
        Text(text = "Onay Süresi: ${viewModel.slouchDurationMillis / 1000} Saniye", fontSize = 14.sp)
        Slider(
            value = (viewModel.slouchDurationMillis / 1000).toFloat(),
            onValueChange = { viewModel.updateDuration((it * 1000).toLong()) },
            valueRange = 1f..30f,
            steps = 9
        )
    }
}

@Composable
fun PostureVisualizer(currentPitch: Float) {
    val absolutePitch = abs(currentPitch)
    val animatedPitch by animateFloatAsState(
        targetValue = absolutePitch,
        animationSpec = tween(durationMillis = 150),
        label = "pitch_anim"
    )
    val currentAbs = animatedPitch.coerceIn(0f, 45f)
    val targerColor = when {
        currentAbs <= 7.5f -> Color.Green
        currentAbs <= 15.0f -> Color(0xFFFF5722)
        else -> Color.Red
    }
    val indicatorColor by animateColorAsState(targerColor, tween(durationMillis = 500), "color animation")

    Box(modifier = Modifier.size(280.dp).padding(8.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(
                color = Color.LightGray.copy(alpha = 0.3f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = 45f, cap = StrokeCap.Round)
            )
            val finalSweep = (currentAbs / 45f) * 270f
            drawArc(
                color = indicatorColor,
                startAngle = 135f,
                sweepAngle = finalSweep.coerceAtLeast(0.1f),
                useCenter = false,
                style = Stroke(width = 35f, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "${String.format("%.1f", absolutePitch)}°", fontSize = 48.sp, fontWeight = FontWeight.Bold)
            Text(text = "Eğim Açısı", fontSize = 14.sp, color = Color.Gray)
        }
    }
}