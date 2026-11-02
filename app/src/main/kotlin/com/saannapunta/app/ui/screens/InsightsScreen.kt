package com.saannapunta.app.ui.screens

import com.saannapunta.app.ui.chito.ChitoCompanion
import com.saannapunta.app.ui.chito.ChitoLine
import com.saannapunta.app.ui.chito.ChitoMood
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saannapunta.app.data.DataRepository
import com.saannapunta.app.data.model.MoneyFormatter
import com.saannapunta.app.data.model.isCountableSpend
import com.saannapunta.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private data class DailyBarPoint(
    val label: String,
    val spentCentavos: Long,
    val ratio: Float,
    val isToday: Boolean
)

@Composable
fun InsightsScreen(
    repository: DataRepository,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val overview by repository.overview.collectAsState()
    val transactions by repository.transactions.collectAsState()
    val upcomingBills by repository.upcomingBills.collectAsState()


    // Spending by category breakdown
    val categoryTotals = remember(transactions) {
        val map = mutableMapOf<String, Long>()
        for (tx in transactions) {
            if (tx.isCountableSpend()) {
                map[tx.category] = (map[tx.category] ?: 0L) + tx.amountCentavos
            }
        }
        map.toList().sortedByDescending { it.second }
    }

    val totalExpense = remember(categoryTotals) {
        categoryTotals.sumOf { it.second }.coerceAtLeast(1L)
    }

    // Live 7-day bar chart from actual transactions
    val last7DaysBars = remember(transactions) {
        val nowCal = Calendar.getInstance()
        val dowFmt = SimpleDateFormat("EEE", Locale.getDefault())
        val rawPoints = mutableListOf<Triple<String, Long, Boolean>>()

        for (offset in 6 downTo 0) {
            val dayCal = (nowCal.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, -offset)
            }
            val start = (dayCal.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val end = (dayCal.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis

            val daySum = transactions
                .filter { it.isCountableSpend() && it.timestamp in start..end }
                .sumOf { it.amountCentavos }

            val label = if (offset == 0) "Today" else dowFmt.format(dayCal.time)
            rawPoints.add(Triple(label, daySum, offset == 0))
        }

        val maxSpend = rawPoints.maxOfOrNull { it.second }?.coerceAtLeast(10000L) ?: 10000L
        rawPoints.map { (label, sum, isToday) ->
            val ratio = if (sum == 0L) 0.08f else (sum.toFloat() / maxSpend.toFloat()).coerceIn(0.12f, 1.0f)
            DailyBarPoint(label = label, spentCentavos = sum, ratio = ratio, isToday = isToday)
        }
    }

    val last7DaysTotal = remember(last7DaysBars) {
        last7DaysBars.sumOf { it.spentCentavos }
    }

    val dailyAverage7d = remember(last7DaysTotal) {
        last7DaysTotal / 7L
    }

    // Top merchants
    val merchantTotals = remember(transactions) {
        val map = mutableMapOf<String, Pair<Int, Long>>() // name -> (count, total)
        for (tx in transactions) {
            if (tx.isCountableSpend()) {
                val current = map[tx.title] ?: Pair(0, 0L)
                map[tx.title] = Pair(current.first + 1, current.second + tx.amountCentavos)
            }
        }
        map.toList().sortedByDescending { it.second.second }.take(5)
    }

    val upcomingTotal = remember(upcomingBills) {
        upcomingBills.sumOf { it.amountCentavos }
    }
    val leftAfterBills = remember(overview.totalBalanceCentavos, upcomingTotal) {
        overview.totalBalanceCentavos - upcomingTotal
    }

    val chitoInsightText = remember(categoryTotals, overview) {
        val topCategory = categoryTotals.firstOrNull()
        if (topCategory != null) {
            val pct = ((topCategory.second.toFloat() / totalExpense.toFloat()) * 100).toInt()
            "${topCategory.first} accounts for $pct% of your spending (${MoneyFormatter.format(topCategory.second, false)}) this cycle."
        } else {
            "No expenses recorded yet in this cycle."
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Canvas),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Ink
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = "Forecast & Insights",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Ink
                )
            }
        }

        // 2. Chito's read on the spending pattern
        item {
            ChitoCompanion(line = ChitoLine(ChitoMood.THINKING, chitoInsightText), size = 68.dp)
        }

        // 3. Card 1: Spending Pace (Live 7-Day Chart)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(LineBorder))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Spending pace (Last 7 days)",
                            style = MaterialTheme.typography.titleSmall,
                            color = Ink
                        )
                        Text(
                            text = MoneyFormatter.format(last7DaysTotal, false),
                            style = MaterialTheme.typography.titleSmall,
                            color = Ink
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Averaging ${MoneyFormatter.format(dailyAverage7d, false)} a day over the past 7 days.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkMuted
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Live Weekday Bars in single green hue per Section 2
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(108.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        last7DaysBars.forEach { bar ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (bar.spentCentavos > 0L) {
                                    Text(
                                        text = "₱${bar.spentCentavos / 100L}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = if (bar.isToday) Ink else InkMuted
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                Box(
                                    modifier = Modifier
                                        .width(22.dp)
                                        .height((64 * bar.ratio).dp)
                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                        .background(if (bar.isToday) Green900 else if (bar.spentCentavos > 0L) Green500 else LineBorder)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = bar.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = if (bar.isToday) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (bar.isToday) Ink else InkFaint
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Card 2: Where It Went (Horizontal bars by category per Section 8)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(LineBorder))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Where it went",
                        style = MaterialTheme.typography.titleSmall,
                        color = Ink
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    if (categoryTotals.isEmpty()) {
                        Text(
                            text = "No category spending recorded yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkMuted
                        )
                    } else {
                        categoryTotals.forEachIndexed { idx, (cat, amount) ->
                            if (idx > 0) Spacer(modifier = Modifier.height(12.dp))
                            val pct = ((amount.toFloat() / totalExpense.toFloat()) * 100).toInt()

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "$cat · $pct%",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                        color = Ink
                                    )
                                    Text(
                                        text = MoneyFormatter.format(amount, false),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Ink
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { (pct / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(CircleShape),
                                    color = Green600,
                                    trackColor = Green50
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Card 3: Before Next Payday
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(LineBorder))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Before next payday (${overview.nextPaydayText})",
                        style = MaterialTheme.typography.titleSmall,
                        color = Ink
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Available now", style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                        Text(text = MoneyFormatter.format(overview.totalBalanceCentavos), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = Ink)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Bills due before payday", style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                        Text(text = "−${MoneyFormatter.format(upcomingTotal)}", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = Ink)
                    }

                    HorizontalDivider(color = LineBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Left after bills", style = MaterialTheme.typography.titleSmall, color = Ink)
                        Text(text = MoneyFormatter.format(leftAfterBills), style = MaterialTheme.typography.titleMedium, color = Green700)
                    }
                }
            }
        }

        // 6. Card 4: Top Merchants
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(LineBorder))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Top merchants",
                        style = MaterialTheme.typography.titleSmall,
                        color = Ink
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (merchantTotals.isEmpty()) {
                        Text(
                            text = "No merchant transactions yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkMuted
                        )
                    } else {
                        merchantTotals.forEachIndexed { idx, (merchant, pair) ->
                            if (idx > 0) {
                                HorizontalDivider(color = LineBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 10.dp))
                            }
                            val (count, total) = pair
                            val sharePct = ((total.toFloat() / totalExpense.toFloat()) * 100).toInt()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = merchant,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                        color = Ink
                                    )
                                    Text(
                                        text = "$count transaction${if (count > 1) "s" else ""} · $sharePct% of spend",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = InkMuted
                                    )
                                }
                                Text(
                                    text = MoneyFormatter.format(total, false),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Ink
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
