package com.healthcoach.ircoach.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.healthcoach.ircoach.R
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ProgressScreen(vm: ChatViewModel, modifier: Modifier = Modifier) {
    val weights by vm.weights.collectAsState() // oldest first
    Column(modifier.fillMaxSize().background(Color.White).padding(horizontal = 16.dp)) {
        Text("Your progress", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp))
        Text("Weight trend from your daily weigh-ins", style = MaterialTheme.typography.bodyMedium, color = CoachColors.InkMuted)

        if (weights.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.size(88.dp).clip(CircleShape).background(CoachColors.Mint), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_scale), null, Modifier.size(44.dp), tint = CoachColors.Green)
                }
                Text("No weigh-ins yet", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
                Text(
                    "Send today's weight in the Coach tab and your chart will appear here.",
                    style = MaterialTheme.typography.bodyMedium, color = CoachColors.InkMuted,
                    modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp),
                )
            }
            return@Column
        }

        val first = weights.first().second
        val last = weights.last().second
        val total = last - first
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("Now", String.format(Locale.US, "%.1f kg", last), CoachColors.Green, Modifier.weight(1f))
            Stat(
                "Since ${weights.first().first.format(SHORT)}",
                String.format(Locale.US, "%+.1f kg", total),
                if (total > 0.1) CoachColors.Danger else CoachColors.Green,
                Modifier.weight(1f),
            )
            Stat("Weigh-ins", weights.size.toString(), CoachColors.GreenDark, Modifier.weight(1f))
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, CoachColors.Outline),
        ) {
            Canvas(Modifier.fillMaxWidth().height(190.dp).padding(16.dp)) {
                val min = weights.minOf { it.second } - 0.5
                val max = weights.maxOf { it.second } + 0.5
                fun y(v: Double) = (size.height * (1 - (v - min) / (max - min))).toFloat()
                fun x(i: Int) = if (weights.size == 1) size.width / 2 else size.width * i / (weights.size - 1)
                for (k in 0..3) {
                    val gy = size.height * k / 3
                    drawLine(CoachColors.Outline, Offset(0f, gy), Offset(size.width, gy), 1f)
                }
                val line = Path()
                weights.forEachIndexed { i, (_, kg) -> if (i == 0) line.moveTo(x(i), y(kg)) else line.lineTo(x(i), y(kg)) }
                if (weights.size > 1) {
                    val fill = Path().apply {
                        addPath(line)
                        lineTo(x(weights.lastIndex), size.height)
                        lineTo(x(0), size.height)
                        close()
                    }
                    drawPath(fill, Brush.verticalGradient(listOf(CoachColors.GreenLight.copy(alpha = 0.28f), Color.Transparent)))
                }
                drawPath(line, CoachColors.Green, style = Stroke(width = 3.dp.toPx()))
                weights.forEachIndexed { i, (_, kg) ->
                    drawCircle(Color.White, 5.dp.toPx(), Offset(x(i), y(kg)))
                    drawCircle(CoachColors.Green, 3.5.dp.toPx(), Offset(x(i), y(kg)))
                }
            }
        }

        Text("History", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
        val rows = weights.zipWithNext { a, b -> b to (b.second - a.second) }.reversed() + listOf(weights.first() to null)
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            items(rows) { (entry, delta) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(entry.first.format(FMT), color = CoachColors.Ink)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(String.format(Locale.US, "%.2f kg", entry.second), fontWeight = FontWeight.SemiBold)
                        DeltaPill(delta)
                    }
                }
                HorizontalDivider(color = CoachColors.Outline)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(CoachColors.MintSoft).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = CoachColors.InkMuted, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleLarge, color = color, maxLines = 1)
    }
}

@Composable
private fun DeltaPill(delta: Double?) {
    val (text, bg, fg) = when {
        delta == null -> Triple("start", CoachColors.MintSoft, CoachColors.InkMuted)
        delta > 0.1 -> Triple(String.format(Locale.US, "▲ %.2f", delta), Color(0xFFFBE6E4), CoachColors.Danger)
        delta < -0.1 -> Triple(String.format(Locale.US, "▼ %.2f", -delta), CoachColors.Mint, CoachColors.GreenDark)
        else -> Triple("same", CoachColors.MintSoft, CoachColors.InkMuted)
    }
    Text(
        text, style = MaterialTheme.typography.labelMedium, color = fg,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

private val FMT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)
private val SHORT = DateTimeFormatter.ofPattern("d MMM", Locale.US)
