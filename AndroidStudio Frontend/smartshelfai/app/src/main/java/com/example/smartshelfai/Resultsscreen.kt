package com.example.smartshelfai

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color as ComposeColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    storeNbr: Int,
    family: String,
    days: Int,
    onPromotion: Int,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UiState<ForecastResponse>>(UiState.Loading) }

    LaunchedEffect(Unit) {
        state = UiState.Loading
        try {
            state = UiState.Success(
                RetrofitClient.api.getForecast(storeNbr, family, days, onPromotion)
            )
        } catch (e: Exception) {
            state = UiState.Error(e.message ?: "Unknown error")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Forecast · $family · Store $storeNbr",
                        color = ComposeColor.White,
                        fontSize = 16.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = ComposeColor.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ComposeColor(0xFF1A1A1A))
            )
        },
        containerColor = ComposeColor.Black
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {

                is UiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = ComposeColor(0xFF6C63FF))
                            Spacer(Modifier.height(12.dp))
                            Text("Running forecast model…", color = ComposeColor.Gray)
                        }
                    }
                }

                is UiState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "⚠  Error",
                            color = ComposeColor(0xFFFF5252),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(s.message, color = ComposeColor.Gray)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = {
                            scope.launch {
                                state = UiState.Loading
                                try {
                                    state = UiState.Success(
                                        RetrofitClient.api.getForecast(storeNbr, family, days, onPromotion)
                                    )
                                } catch (e: Exception) {
                                    state = UiState.Error(e.message ?: "Unknown error")
                                }
                            }
                        }) { Text("Retry") }
                    }
                }

                is UiState.Success -> {
                    val forecast = s.data.forecast
                    val labels   = forecast.map { it.date.takeLast(5) }
                    val values   = forecast.map { it.predicted_sales }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // ── Summary stats ─────────────────────────────────
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatMini("Total",  "%.0f".format(values.sum()),            ComposeColor(0xFF00E676), Modifier.weight(1f))
                            StatMini("Avg",    "%.1f".format(values.average()),        ComposeColor(0xFF9D97FF), Modifier.weight(1f))
                            StatMini("Peak",   "%.1f".format(values.maxOrNull() ?: 0.0), ComposeColor(0xFFFFD740), Modifier.weight(1f))
                        }

                        // ── Chart ─────────────────────────────────────────
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ComposeColor(0xFF1A1A1A)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            AndroidView(
                                modifier = Modifier.fillMaxWidth().height(220.dp).padding(8.dp),
                                factory = { ctx ->
                                    LineChart(ctx).apply {
                                        setBackgroundColor(Color.TRANSPARENT)
                                        description.isEnabled = false
                                        legend.textColor = Color.parseColor("#9090AA")
                                        axisLeft.apply {
                                            textColor = Color.parseColor("#9090AA")
                                            gridColor = Color.parseColor("#2A2A3D")
                                        }
                                        axisRight.isEnabled = false
                                        xAxis.apply {
                                            textColor = Color.parseColor("#9090AA")
                                            position  = XAxis.XAxisPosition.BOTTOM
                                            granularity = 1f
                                            labelRotationAngle = -45f
                                            valueFormatter = IndexAxisValueFormatter(labels)
                                            labelCount = minOf(labels.size, 7)
                                        }
                                        setTouchEnabled(true)
                                        isDragEnabled = true
                                    }
                                },
                                update = { chart ->
                                    val entries = values.mapIndexed { i, v -> Entry(i.toFloat(), v.toFloat()) }
                                    val ds = LineDataSet(entries, "Predicted").apply {
                                        color = Color.parseColor("#6C63FF"); lineWidth = 2.5f
                                        setDrawCircles(true); circleRadius = 4f
                                        circleColors = listOf(Color.parseColor("#9D97FF"))
                                        setDrawValues(false)
                                        mode = LineDataSet.Mode.CUBIC_BEZIER
                                        setDrawFilled(true)
                                        fillColor = Color.parseColor("#6C63FF"); fillAlpha = 40
                                    }
                                    chart.data = LineData(ds)
                                    chart.invalidate()
                                    chart.animateX(1000)
                                }
                            )
                        }

                        // ── Daily breakdown table ─────────────────────────
                        Text(
                            "Daily Breakdown",
                            color = ComposeColor.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )

                        // Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(ComposeColor(0xFF6C63FF).copy(alpha = 0.2f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("#",     color = ComposeColor.Gray, modifier = Modifier.width(28.dp), fontSize = 12.sp)
                            Text("Date",  color = ComposeColor.Gray, modifier = Modifier.weight(1f),   fontSize = 12.sp)
                            Text("Sales", color = ComposeColor.Gray, modifier = Modifier.weight(1f),
                                textAlign = TextAlign.End, fontSize = 12.sp)
                            Text("↕",     color = ComposeColor.Gray, modifier = Modifier.width(32.dp),
                                textAlign = TextAlign.End, fontSize = 12.sp)
                        }

                        // Rows
                        forecast.forEachIndexed { i, point ->
                            val prev  = if (i > 0) forecast[i - 1].predicted_sales else null
                            val trend = when {
                                prev == null                    -> "—"
                                point.predicted_sales > prev   -> "↑"
                                point.predicted_sales < prev   -> "↓"
                                else                           -> "→"
                            }
                            val trendColor = when (trend) {
                                "↑"  -> ComposeColor(0xFF00E676)
                                "↓"  -> ComposeColor(0xFFFF5252)
                                else -> ComposeColor.Gray
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (i % 2 == 0) ComposeColor(0xFF111111) else ComposeColor(0xFF1A1A1A))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${i + 1}", color = ComposeColor.Gray,
                                    modifier = Modifier.width(28.dp), fontSize = 13.sp)
                                Text(point.date, color = ComposeColor.White,
                                    modifier = Modifier.weight(1f), fontSize = 13.sp)
                                Text(
                                    "%.2f".format(point.predicted_sales),
                                    color = ComposeColor(0xFF4CAF50),
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.End,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                                Text(trend, color = trendColor,
                                    modifier = Modifier.width(32.dp),
                                    textAlign = TextAlign.End,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp)
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}