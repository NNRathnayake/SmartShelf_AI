package com.example.smartshelfai

import android.app.DatePickerDialog
import android.graphics.Color
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.launch
import java.util.*
import androidx.compose.ui.graphics.Color as ComposeColor



private val BG       = ComposeColor(0xFF0A0A0A)
private val Surface  = ComposeColor(0xFF111111)
private val Border   = ComposeColor(0xFF1F1F1F)
private val Accent   = ComposeColor(0xFFE8E8E8)
private val Muted    = ComposeColor(0xFF555555)
private val TextPrim = ComposeColor(0xFFEEEEEE)
private val TextSec  = ComposeColor(0xFF888888)
private val Positive = ComposeColor(0xFF5DB075)



data class SalesPoint(val year_month: String, val avg_sales: Float)

val ALL_STORES = listOf(
    "1 - Quito","2 - Quito","3 - Quito","4 - Quito","5 - Santo Domingo",
    "6 - Quito","7 - Quito","8 - Quito","9 - Quito","10 - Quito",
    "11 - Cayambe","12 - Latacunga","13 - Latacunga","14 - Riobamba",
    "15 - Ibarra","16 - Santo Domingo","17 - Quito","18 - Quito",
    "19 - Guaranda","20 - Quito","21 - Santo Domingo","22 - Puyo",
    "23 - Ambato","24 - Guayaquil","25 - Salinas","26 - Guayaquil",
    "27 - Daule","28 - Guayaquil","29 - Guayaquil","30 - Guayaquil",
    "31 - Babahoyo","32 - Guayaquil","33 - Quevedo","34 - Guayaquil",
    "35 - Playas","36 - Libertad","37 - Cuenca","38 - Loja",
    "39 - Cuenca","40 - Machala","41 - Machala","42 - Cuenca",
    "43 - Esmeraldas","44 - Quito","45 - Quito","46 - Quito",
    "47 - Quito","48 - Quito","49 - Quito","50 - Ambato",
    "51 - Guayaquil","52 - Manta","53 - Manta","54 - El Carmen"
)

val ALL_FAMILIES = listOf(
    "AUTOMOTIVE","BABY CARE","BEAUTY","BEVERAGES","BOOKS",
    "BREAD/BAKERY","CELEBRATION","CLEANING","DAIRY","DELI",
    "EGGS","FROZEN FOODS","GROCERY I","GROCERY II","HARDWARE",
    "HOME AND KITCHEN I","HOME AND KITCHEN II","HOME APPLIANCES",
    "HOME CARE","LADIESWEAR","LAWN AND GARDEN","LINGERIE",
    "LIQUOR,WINE,BEER","MAGAZINES","MEATS","PERSONAL CARE",
    "PET SUPPLIES","PLAYERS AND ELECTRONICS","POULTRY",
    "PREPARED FOODS","PRODUCE","SCHOOL AND OFFICE SUPPLIES","SEAFOOD"
)



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Dropdown(
    items: List<String>,
    selected: String,
    label: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, color = TextSec, fontSize = 12.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = Accent,
                unfocusedBorderColor = Border,
                focusedTextColor     = TextPrim,
                unfocusedTextColor   = TextPrim,
                cursorColor          = Accent,
                focusedTrailingIconColor   = TextSec,
                unfocusedTrailingIconColor = TextSec,
            ),
            shape = RoundedCornerShape(8.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Surface)
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item, color = TextPrim, fontSize = 14.sp) },
                    onClick = { onSelected(item); expanded = false },
                    modifier = Modifier.background(
                        if (item == selected) Border else Surface
                    )
                )
            }
        }
    }
}



@Composable
fun StatMini(label: String, value: String, color: ComposeColor, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(8.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = TextSec, fontSize = 11.sp, letterSpacing = 0.5.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, color = color, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}



sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}



@Composable
private fun SectionLabel(text: String) {
    Text(
        text  = text.uppercase(),
        color = TextSec,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}



@Composable
private fun ShelfDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        color = Border,
        thickness = 1.dp
    )
}



@Composable
fun PredictScreen(
    onNavigateToResults:    (store: Int, family: String, days: Int, promo: Int) -> Unit = { _, _, _, _ -> },
    onNavigateToHistorical: (store: Int, family: String, days: Int, promo: Int) -> Unit = { _, _, _, _ -> }
) {
    val scope = rememberCoroutineScope()
    var resultText  by remember { mutableStateOf<String?>(null) }
    var isLoading   by remember { mutableStateOf(false) }

    val salesData = remember {
        listOf(
            SalesPoint("2013-01", 175.93f), SalesPoint("2013-02", 182.09f),
            SalesPoint("2013-03", 191.96f), SalesPoint("2013-04", 191.14f),
            SalesPoint("2013-05", 196.15f), SalesPoint("2013-06", 199.74f),
            SalesPoint("2013-07", 191.07f), SalesPoint("2013-08", 197.70f),
            SalesPoint("2013-09", 202.59f), SalesPoint("2013-10", 197.06f),
            SalesPoint("2013-11", 211.33f), SalesPoint("2013-12", 259.53f),
            SalesPoint("2014-01", 300.89f), SalesPoint("2014-02", 223.28f),
            SalesPoint("2014-03", 320.69f), SalesPoint("2014-04", 220.59f),
            SalesPoint("2014-05", 221.00f), SalesPoint("2014-06", 223.88f),
            SalesPoint("2014-07", 310.53f), SalesPoint("2014-08", 234.03f)
        )
    }

    var selectedStore  by remember { mutableStateOf("1 - Quito") }
    var selectedFamily by remember { mutableStateOf("BEVERAGES") }
    var date           by remember { mutableStateOf("2018-04-01") }
    var onPromotion    by remember { mutableStateOf(false) }
    var forecastDays   by remember { mutableStateOf(7f) }

    val context = LocalContext.current

    fun storeNbr() = selectedStore.split("-")[0].trim().toIntOrNull() ?: 1
    fun promo()    = if (onPromotion) 1 else 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {


        Column {
            Text(
                "SmartShelf",
                color = TextPrim,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
            Text(
                "Sales Forecasting",
                color = TextSec,
                fontSize = 13.sp,
                letterSpacing = 0.2.sp
            )
        }

        ShelfDivider()

        Column {
            SectionLabel("Historical Trend")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Surface)
                    .border(1.dp, Border, RoundedCornerShape(10.dp))
                    .padding(8.dp)
            ) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    factory = { ctx ->
                        LineChart(ctx).apply {
                            setBackgroundColor(Color.TRANSPARENT)
                            description.isEnabled = false
                            legend.isEnabled = false
                            setTouchEnabled(false)
                            axisLeft.apply {
                                textColor = 0xFF555555.toInt()
                                textSize  = 9f
                                setDrawGridLines(true)
                                gridColor = 0xFF1A1A1A.toInt()
                                axisLineColor = 0xFF1F1F1F.toInt()
                            }
                            axisRight.isEnabled = false
                            xAxis.apply {
                                textColor = 0xFF555555.toInt()
                                textSize  = 9f
                                position  = XAxis.XAxisPosition.BOTTOM
                                setDrawGridLines(false)
                                axisLineColor = 0xFF1F1F1F.toInt()
                                granularity = 1f
                            }
                            setExtraOffsets(8f, 8f, 8f, 8f)
                        }
                    },
                    update = { chart ->
                        val entries = salesData.mapIndexed { i, item -> Entry(i.toFloat(), item.avg_sales) }
                        val dataSet = LineDataSet(entries, "").apply {
                            color     = 0xFFEEEEEE.toInt()
                            lineWidth = 1.5f
                            setDrawCircles(false)
                            setDrawValues(false)
                            setDrawFilled(true)
                            fillColor  = 0xFFEEEEEE.toInt()
                            fillAlpha  = 15
                            mode = LineDataSet.Mode.CUBIC_BEZIER
                        }
                        chart.data = LineData(dataSet)
                        chart.invalidate()
                        chart.animateX(600)
                    }
                )
            }
        }

        ShelfDivider()


        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionLabel("Parameters")

            Dropdown(items = ALL_STORES, selected = selectedStore, label = "Store") {
                selectedStore = it
            }

            Dropdown(items = ALL_FAMILIES, selected = selectedFamily, label = "Product Family") {
                selectedFamily = it
            }

            // Date picker
            Column {
                SectionLabel("Start Date")
                OutlinedButton(
                    onClick = {
                        val cal = Calendar.getInstance()
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                date = "$y-${(m + 1).toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrim),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(date, fontSize = 14.sp, color = TextPrim)
                        Text("Change", fontSize = 12.sp, color = TextSec)
                    }
                }
            }

            // Forecast days slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SectionLabel("Forecast Horizon")
                    Text(
                        "${forecastDays.toInt()} days",
                        color = TextPrim,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Slider(
                    value = forecastDays,
                    onValueChange = { forecastDays = it },
                    valueRange = 1f..30f,
                    steps = 28,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor            = Accent,
                        activeTrackColor      = Accent,
                        inactiveTrackColor    = Border,
                        activeTickColor       = ComposeColor.Transparent,
                        inactiveTickColor     = ComposeColor.Transparent,
                    )
                )
            }

            // Promotion toggle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Surface)
                    .border(1.dp, Border, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("On Promotion", color = TextPrim, fontSize = 14.sp)
                        Text("Apply promotional pricing", color = TextSec, fontSize = 12.sp)
                    }
                    Switch(
                        checked = onPromotion,
                        onCheckedChange = { onPromotion = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor   = BG,
                            checkedTrackColor   = Accent,
                            uncheckedThumbColor = TextSec,
                            uncheckedTrackColor = Border
                        )
                    )
                }
            }
        }

        ShelfDivider()

        // ── Forecast result ───────────────────────────────────────────────────
        AnimatedVisibility(visible = resultText != null, enter = fadeIn(), exit = fadeOut()) {
            resultText?.let { text ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Surface)
                        .border(1.dp, Border, RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Forecast Result", color = TextSec, fontSize = 11.sp, letterSpacing = 1.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text,
                            color = if (text.startsWith("Error")) ComposeColor(0xFFCF6679) else Positive,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

     
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

            // Primary: run forecast
            Button(
                onClick = {
                    isLoading = true
                    resultText = null
                    scope.launch {
                        try {
                            val response = RetrofitClient.api.getForecast(
                                storeNbr(), selectedFamily, forecastDays.toInt(), promo()
                            )
                            val avg = response.forecast.map { it.predicted_sales }.average()
                            resultText = "Avg Forecast  %.2f units".format(avg)
                        } catch (e: Exception) {
                            resultText = "Error: ${e.message}"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor   = BG
                ),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = BG,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Run Forecast", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Timeseries detail
                OutlinedButton(
                    onClick = { onNavigateToResults(storeNbr(), selectedFamily, forecastDays.toInt(), promo()) },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrim),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                ) {
                    Text("Forecast Detail", fontSize = 13.sp)
                }

                // Historical compare
                OutlinedButton(
                    onClick = { onNavigateToHistorical(storeNbr(), selectedFamily, forecastDays.toInt(), promo()) },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrim),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                ) {
                    Text("Historical", fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}