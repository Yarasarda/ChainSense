package com.yarasa.chainsense.Screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import java.util.Locale
import com.yarasa.chainsense.MainViewModel

@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val currentPitch by viewModel.currentPitch
    val slouchProgress = viewModel.slouchProgress
    val slouchCount = viewModel.totalSlouchCount
    val isConnected = viewModel.connectionStatus == MainViewModel.ConnectionStatus.CONNECTED

    // Kaydırma motoru için state eklendi
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState), // Kaydırma motoru buraya takıldı
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isConnected) "Cihaz Bağlı: Sırta Takılı" else "Cihaz Aranıyor...",
                style = MaterialTheme.typography.titleMedium,
                color = if (isConnected) MaterialTheme.colorScheme.onSurface else Color.Gray
            )

            BatteryWidget(level = viewModel.batteryLevel)
        }

        // 1.Açı Görselleştirici
        PostureVisualizer(currentPitch = currentPitch)

        Spacer(modifier = Modifier.height(16.dp))

        // 2.Onay Barı
        SlouchProgressBar(progress = slouchProgress)

        Spacer(modifier = Modifier.height(16.dp))

        // 3.Sayaç ve İstatistik Kartı
        SlouchCounterCard(count = slouchCount)

        Spacer(modifier = Modifier.height(24.dp))

        // 4.Ayarlar Bölümü
        SettingsSection(viewModel = viewModel)

        // weight(1f) bombası imha edildi, yerine butonu itecek sabit boşluk eklendi
        Spacer(modifier = Modifier.height(32.dp))

        // 5.Kalibrasyon Butonu
        Button(
            onClick = { viewModel.calibrate() },
            enabled = isConnected,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(text = "Kalibre Et", fontWeight = FontWeight.Bold)
        }

        // Listenin en altına nefes payı eklendi ki buton en alta yapışmasın
        Spacer(modifier = Modifier.height(16.dp))
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

    val targetColor = when {
        currentAbs <= 7.5f -> Color.Green
        currentAbs <= 15.0f -> Color(0xFFFF5722)
        else -> Color.Red
    }
    val indicatorColor by animateColorAsState(targetColor, tween(durationMillis = 500), "color animation")

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
            Text(
                // String format çökmesini engellemek için yerel format eklendi
                text = "${String.format(Locale.getDefault(), "%.1f", absolutePitch)}°",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold
            )
            Text(text = "Eğim Açısı", fontSize = 14.sp, color = Color.Gray)
        }
    }
}

@Composable
fun BatteryWidget(level: Int) {
    if (level < 0) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(Color.Gray.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(Color.Gray, RoundedCornerShape(50))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Bekleniyor",
                style = MaterialTheme.typography.labelLarge,
                color = Color.Gray,
                fontWeight = FontWeight.Bold
            )
        }
        return // Bekleniyor çizildiyse aşağıya inme
    }

    val color = when {
        level > 80 -> MaterialTheme.colorScheme.primary
        level > 20 -> MaterialTheme.colorScheme.onSurface
        else -> Color.Red
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, RoundedCornerShape(50))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "%$level",
            style = MaterialTheme.typography.labelLarge,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}