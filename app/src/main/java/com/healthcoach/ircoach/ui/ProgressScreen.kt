package com.healthcoach.ircoach.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ProgressScreen(vm: ChatViewModel, modifier: Modifier = Modifier) {
    val weights by vm.weights.collectAsState() // oldest first
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("Weight", style = MaterialTheme.typography.headlineSmall)
        if (weights.isEmpty()) {
            Text("No weigh-ins yet. Send your weight in the Coach tab.", Modifier.padding(top = 12.dp))
            return@Column
        }
        val first = weights.first().second
        val last = weights.last().second
        Text(
            String.format(Locale.US, "%.1f kg now · %+.1f kg since %s", last, last - first, weights.first().first.format(FMT)),
            style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp),
        )
        val line = MaterialTheme.colorScheme.primary
        val grid = MaterialTheme.colorScheme.outlineVariant
        Canvas(Modifier.fillMaxWidth().height(200.dp).padding(vertical = 8.dp)) {
            val min = weights.minOf { it.second } - 0.5
            val max = weights.maxOf { it.second } + 0.5
            fun y(v: Double) = (size.height * (1 - (v - min) / (max - min))).toFloat()
            fun x(i: Int) = if (weights.size == 1) size.width / 2 else size.width * i / (weights.size - 1)
            for (k in 0..3) {
                val gy = size.height * k / 3
                drawLine(grid, Offset(0f, gy), Offset(size.width, gy), 1f)
            }
            val path = Path()
            weights.forEachIndexed { i, (_, kg) -> if (i == 0) path.moveTo(x(i), y(kg)) else path.lineTo(x(i), y(kg)) }
            drawPath(path, line, style = Stroke(width = 3.dp.toPx()))
            weights.forEachIndexed { i, (_, kg) -> drawCircle(line, 4.dp.toPx(), Offset(x(i), y(kg))) }
        }
        HorizontalDivider()
        val rows = weights.zipWithNext { a, b -> b to (b.second - a.second) }.reversed() + listOf(weights.first() to null)
        LazyColumn {
            items(rows) { (entry, delta) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(entry.first.format(FMT))
                    Text(
                        String.format(Locale.US, "%.2f kg", entry.second) +
                            (delta?.let { String.format(Locale.US, "  (%+.2f)", it) } ?: ""),
                        color = when {
                            delta == null -> MaterialTheme.colorScheme.onSurface
                            delta > 0.1 -> MaterialTheme.colorScheme.error
                            delta < -0.1 -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

private val FMT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)
