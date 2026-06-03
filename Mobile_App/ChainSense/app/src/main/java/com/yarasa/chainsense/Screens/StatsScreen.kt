package com.yarasa.chainsense.Screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yarasa.chainsense.Data.SlouchLogEntity
import com.yarasa.chainsense.MainViewModel
import java.util.Calendar

@Composable
fun StatsScreen(
    weeklyCount: Int,
    monthlyCount: Int,
    todayLogs: List<SlouchLogEntity>,
    chartData: List<MainViewModel.ChartPoint>
) {
    var selectedHour by remember { mutableStateOf<Int?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "İstatistikler", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SummaryCard("Son 7 Gün", weeklyCount, Modifier.weight(1f))
            Spacer(modifier = Modifier.width(16.dp))
            SummaryCard("Son 30 Gün", monthlyCount, Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(32.dp))

        Text(text = "Bugünkü Aktivite", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))

        if (chartData.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = "Aga bugün krallar gibi dik durdun, hiç kambur kaydın yok! Aynen devam.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            val maxCount = chartData.maxOfOrNull { it.count }?.toFloat() ?: 1f

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(Color.LightGray.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.BottomCenter,
            ){
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    chartData.forEach { point ->
                        val barHeightfraction = (point.count / maxCount).coerceIn(0f, 1f)
                        val isSelected = selectedHour == point.hour
                        val maxBarHeight = 110.dp

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier
                                .clickable { selectedHour = point.hour}
                                .padding(horizontal = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(24.dp)
                                    .height(maxBarHeight * barHeightfraction)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${point.hour}:00",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.DarkGray
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (selectedHour != null){
            Text(
                text = "Saat ${selectedHour}:00 Detayları",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            val filteredLogs = todayLogs.filter { logs ->
                val calendar = Calendar.getInstance().apply { timeInMillis = logs.timestamp }
                calendar.get(Calendar.HOUR_OF_DAY) == selectedHour
            }

            if (filteredLogs.isEmpty()){
                Text(text = "Bu saatte herhangi bir veri bulunmadı.\nHayata karşı dik duruyorsun :D",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(filteredLogs){ logs ->
                        val calendar = Calendar.getInstance().apply { timeInMillis = logs.timestamp }
                        val min = calendar.get(Calendar.MINUTE)
                        val formattedMin = min.toString().padStart(2, '0')

                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Text(
                                text = "Saat ${selectedHour}:$formattedMin - Kamburluk Tespit Edildi",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        } else {
            Text(
                text = "Detayları görmek için grafikte bir saate dokunun",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun SummaryCard(title: String, count: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = Color.Gray)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = count.toString(), style = MaterialTheme.typography.headlineMedium)
        }
    }
}