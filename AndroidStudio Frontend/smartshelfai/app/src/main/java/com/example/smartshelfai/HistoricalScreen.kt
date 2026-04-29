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
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color as ComposeColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoricalScreen(
    storeNbr: Int,
    family: String,
    days: Int,
    onPromotion: Int,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    data class Both(val history: List<SalesHistoryPoint>, val forecast: List<ForecastPoint>)
    var state by remember { mutableStateOf<UiState<Both>>(UiState.Loading) }

    LaunchedEffect(Unit) {
        state = UiState.Loading
        try {
            val h = scope.async { RetrofitClient.api.getSalesHistory(storeNbr, family) }
            val f = scope.async { RetrofitClient.api.getForecast(storeNbr, family, days, onPromotion) }
            state = UiState.Success(Both(h.await().data, f.await().forecast))
        } catch (e: Exception) {
            state = UiState.Error(e.message ?: "Unknown error")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Historical vs Forecast · Store $storeNbr",
                        color = ComposeColor.White,
                        fontSize = 15.sp
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
                            Text("Loading historical data…", color = ComposeColor.Gray)
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
                                    val h = async { RetrofitClient.api.getSalesHistory(storeNbr, family) }
                                    val f = async { RetrofitClient.api.getForecast(storeNbr, family, days, onPromotion) }
                                    state = UiState.Success(Both(h.await().data, f.await().forecast))
                                } catch (e: Exception) {
                                    state = UiState.Error(e.message ?: "Unknown error")
                                }
                            }
                        }) { Text("Retry") }
                    }
                }

                is UiState.Success -> {
                    val history  = s.data.history.takeLast(30)
                    val forecast = s.data.forecast
                    val histAvg  = history.map { it.sales }.average()
                    val foreAvg  = forecast.map { it.predicted_sales }.average()
                    val diff     = if (histAvg != 0.0) (foreAvg - histAvg) / histAvg * 100 else 0.0
                    val allLabels = history.map { it.date.takeLast(5) } + forecast.map { it.date.takeLast(5) }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // ── Summary row ───────────────────────────────────
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatMini("Hist Avg", "%.1f".format(histAvg), ComposeColor(0xFFFFD740), Modifier.weight(1f))
                            StatMini("Fore Avg", "%.1f".format(foreAvg), ComposeColor(0xFF9D97FF), Modifier.weight(1f))
                            StatMini(
                                "Δ Change",
                                "${if (diff >= 0) "+" else ""}%.1f%%".format(diff),
                                if (diff >= 0) ComposeColor(0xFF00E676) else ComposeColor(0xFFFF5252),
                                Modifier.weight(1f)
                            )
                        }

                        // ── Combined chart ────────────────────────────────
                        Text(
                            "Historical (yellow) vs Forecast (purple)",
                            color = ComposeColor.Gray,
                            fontSize = 12.sp
                        )
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ComposeColor(0xFF1A1A1A)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            AndroidView(
                                modifier = Modifier.fillMaxWidth().height(240.dp).padding(8.dp),
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
                                            valueFormatter = IndexAxisValueFormatter(allLabels)
                                            labelCount = minOf(allLabels.size, 8)
                                        }
                                        setTouchEnabled(true)
                                        isDragEnabled = true
                                    }
                                },
                                update = { chart ->
                                    val hEntries = history.mapIndexed { i, p ->
                                        Entry(i.toFloat(), p.sales.toFloat())
                                    }
                                    val offset = history.size.toFloat()
                                    val fEntries = forecast.mapIndexed { i, p ->
                                        Entry(offset + i, p.predicted_sales.toFloat())
                                    }
                                    val hDs = LineDataSet(hEntries, "Historical").apply {
                                        color = Color.parseColor("#FFD740"); lineWidth = 2f
                                        setDrawCircles(false); setDrawValues(false)
                                        mode = LineDataSet.Mode.CUBIC_BEZIER
                                        setDrawFilled(true)
                                        fillColor = Color.parseColor("#FFD740"); fillAlpha = 25
                                    }
                                    val fDs = LineDataSet(fEntries, "Forecast").apply {
                                        color = Color.parseColor("#6C63FF"); lineWidth = 2.5f
                                        enableDashedLine(12f, 6f, 0f)
                                        setDrawCircles(true); circleRadius = 3.5f
                                        circleColors = listOf(Color.parseColor("#9D97FF"))
                                        setDrawValues(false)
                                        mode = LineDataSet.Mode.CUBIC_BEZIER
                                        setDrawFilled(true)
                                        fillColor = Color.parseColor("#6C63FF"); fillAlpha = 30
                                    }
                                    chart.data = LineData(hDs, fDs)
                                    chart.invalidate()
                                    chart.animateX(1200)
                                }
                            )
                        }

                        // ── Historical table (last 10) ────────────────────
                        Text(
                            "Historical Sales (last 10)",
                            color = ComposeColor.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        history.takeLast(10).forEachIndexed { i, p ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (i % 2 == 0) ComposeColor(0xFF111111) else ComposeColor(0xFF1A1A1A))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(p.date, color = ComposeColor.White,
                                    modifier = Modifier.weight(1f), fontSize = 13.sp)
                                Text(
                                    "%.2f".format(p.sales),
                                    color = ComposeColor(0xFFFFD740),
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.End,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        // ── Forecast vs historical average table ──────────
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Forecast vs Historical Average",
                            color = ComposeColor.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        forecast.forEachIndexed { i, p ->
                            val d = if (histAvg != 0.0) (p.predicted_sales - histAvg) / histAvg * 100 else 0.0
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (i % 2 == 0) ComposeColor(0xFF111111) else ComposeColor(0xFF1A1A1A))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(p.date, color = ComposeColor.White,
                                    modifier = Modifier.weight(1f), fontSize = 13.sp)
                                Text(
                                    "%.2f".format(p.predicted_sales),
                                    color = ComposeColor(0xFF9D97FF),
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.End,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    "${if (d >= 0) "+" else ""}%.1f%%".format(d),
                                    color = if (d >= 0) ComposeColor(0xFF00E676) else ComposeColor(0xFFFF5252),
                                    modifier = Modifier.width(60.dp),
                                    textAlign = TextAlign.End,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}