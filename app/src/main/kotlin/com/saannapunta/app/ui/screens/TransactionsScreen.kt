package com.saannapunta.app.ui.screens

import com.saannapunta.app.ui.components.*
import com.saannapunta.app.ui.feedback.rememberUiFeedback
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.saannapunta.app.ui.chito.ChitoCompanion
import com.saannapunta.app.ui.chito.ChitoLines
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saannapunta.app.data.DataRepository
import com.saannapunta.app.data.model.MoneyFormatter
import com.saannapunta.app.data.model.PayCycleConfig
import com.saannapunta.app.data.model.TransactionItem
import com.saannapunta.app.data.model.TransactionType
import com.saannapunta.app.data.model.isCountableSpend
import com.saannapunta.app.ui.components.SaanNaPuntaButton
import com.saannapunta.app.ui.components.TransactionRow
import com.saannapunta.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun TransactionsScreen(
    repository: DataRepository,
    onAddTransactionClick: () -> Unit,
    onEditTransaction: (TransactionItem) -> Unit,
    initialAccountFilter: Long? = null,
    modifier: Modifier = Modifier
) {
    val transactions by repository.transactions.collectAsState()
    val accounts by repository.accounts.collectAsState()
    val allAccounts by repository.allAccounts.collectAsState()
    val possibleTransfers by repository.possibleTransfers.collectAsState()
    val budgets by repository.budgets.collectAsState()
    val overview by repository.overview.collectAsState()
    val payCycleConfig by repository.payCycleConfig.collectAsState()
    val feedback = rememberUiFeedback()

    var selectedView by remember { mutableIntStateOf(0) } // 0: List, 1: Calendar
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterCategory by remember { mutableStateOf("All") }
    var selectedAccountFilter by remember(initialAccountFilter) { mutableStateOf(initialAccountFilter) }

    val nowCal = remember { Calendar.getInstance() }
    var displayedYear by remember { mutableIntStateOf(nowCal.get(Calendar.YEAR)) }
    var displayedMonth by remember { mutableIntStateOf(nowCal.get(Calendar.MONTH)) } // 0-based
    var selectedCalendarDay by remember { mutableIntStateOf(nowCal.get(Calendar.DAY_OF_MONTH)) }

    val categories = remember(budgets, transactions) {
        val list = mutableListOf("All", "Expenses", "Income", "Transfer")
        val candidates = budgets.map { it.category } +
            transactions.map { it.category } +
            listOf("Food & Dining", "Groceries", "Utilities", "Transportation", "Personal")
        for (cat in candidates) {
            if (cat.isNotBlank() &&
                !cat.equals("Transfer", ignoreCase = true) &&
                !cat.equals("Income", ignoreCase = true) &&
                list.none { it.equals(cat, ignoreCase = true) }
            ) {
                list.add(cat)
            }
        }
        list
    }

    val filteredTransactions = remember(transactions, searchQuery, selectedFilterCategory, selectedAccountFilter) {
        transactions.filter { item ->
            val matchCategory = when (selectedFilterCategory) {
                "All" -> true
                "Expenses" -> item.type == TransactionType.EXPENSE
                "Income" -> item.type == TransactionType.INCOME
                "Transfer" -> item.type == TransactionType.TRANSFER
                else -> item.category.equals(selectedFilterCategory, ignoreCase = true)
            }
            val matchAccount = selectedAccountFilter == null ||
                item.accountId == selectedAccountFilter ||
                item.targetAccountId == selectedAccountFilter
            val matchSearch = searchQuery.isEmpty() ||
                item.title.contains(searchQuery, ignoreCase = true) ||
                item.category.contains(searchQuery, ignoreCase = true) ||
                (item.note?.contains(searchQuery, ignoreCase = true) == true)
            matchCategory && matchAccount && matchSearch
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Canvas),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header with List / Calendar switch
        item {
            ScreenHeader(eyebrow = "History", title = "Transactions") {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(SurfaceColor)
                        .padding(4.dp)
                ) {
                    listOf("List", "Calendar").forEachIndexed { index, name ->
                        val isSelected = selectedView == index
                        val bg by animateColorAsState(if (isSelected) Green600 else SurfaceColor, tween(180), label = "HistoryViewBg")
                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .clip(CircleShape)
                                .background(bg)
                                .clickable {
                                    if (!isSelected) feedback.select()
                                    selectedView = index
                                }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isSelected) SurfaceColor else InkMuted
                            )
                        }
                    }
                }
            }
        }

        item {
            ChitoCompanion(
                line = ChitoLines.transactions(transactions.size, overview.spentTodayCentavos),
                size = 72.dp,
                bubbleColor = SurfaceColor
            )
        }

        if (selectedView == 0) {
            // LIST VIEW
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search merchant, category, or note", color = InkFaint, style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = InkMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceColor,
                        unfocusedContainerColor = SurfaceColor,
                        focusedBorderColor = Green600,
                        unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent
                    ),
                    singleLine = true
                )
            }

            // Account & Category Filter Chips
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val allSelected = selectedAccountFilter == null
                        FilterChipPill(
                            label = "All accounts",
                            isSelected = allSelected,
                            onClick = { selectedAccountFilter = null }
                        )
                        accounts.forEach { acc ->
                            FilterChipPill(
                                label = acc.name,
                                isSelected = selectedAccountFilter == acc.id,
                                onClick = {
                                    selectedAccountFilter = if (selectedAccountFilter == acc.id) null else acc.id
                                }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { cat ->
                            FilterChipPill(
                                label = cat,
                                isSelected = selectedFilterCategory == cat,
                                onClick = { selectedFilterCategory = cat }
                            )
                        }
                    }

                    possibleTransfers.forEach { suggestion ->
                        val fromAccName = allAccounts.firstOrNull { it.id == suggestion.expenseTx.accountId }?.name ?: "Account"
                        val toAccName = allAccounts.firstOrNull { it.id == suggestion.incomeTx.accountId }?.name ?: "Account"
                        SoftCard(modifier = Modifier.fillMaxWidth(), elevation = 4.dp, color = Green50) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(com.saannapunta.app.R.drawable.chito_pointing),
                                contentDescription = "Chito pointing",
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Possible transfer: ${MoneyFormatter.format(suggestion.amountCentavos)}",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = Green700
                                )
                                Text(
                                    text = "$fromAccName (${suggestion.expenseTx.title}) \u2192 $toAccName (${suggestion.incomeTx.title}) within 5 mins",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Ink
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(
                                        onClick = { repository.confirmPossibleTransfer(suggestion) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                    ) {
                                        Text("Confirm transfer", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Green700)
                                    }
                                    TextButton(
                                        onClick = { repository.dismissPossibleTransfer(suggestion) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                    ) {
                                        Text("Dismiss", style = MaterialTheme.typography.labelMedium, color = InkMuted)
                                    }
                                }
                            }
                            }
                        }
                    }
                }
            }

            // Group transactions by Calendar Day
            val todayYear = nowCal.get(Calendar.YEAR)
            val todayDayOfYear = nowCal.get(Calendar.DAY_OF_YEAR)
            val yesterdayCal = (nowCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
            val yesterdayYear = yesterdayCal.get(Calendar.YEAR)
            val yesterdayDayOfYear = yesterdayCal.get(Calendar.DAY_OF_YEAR)

            val groupedByDay = filteredTransactions.groupBy { item ->
                val cal = Calendar.getInstance().apply { timeInMillis = item.timestamp }
                val y = cal.get(Calendar.YEAR)
                val doy = cal.get(Calendar.DAY_OF_YEAR)
                val dom = cal.get(Calendar.DAY_OF_MONTH)
                val monthShort = cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()) ?: ""
                val label = when {
                    y == todayYear && doy == todayDayOfYear -> "Today · $monthShort $dom"
                    y == yesterdayYear && doy == yesterdayDayOfYear -> "Yesterday · $monthShort $dom"
                    else -> "$monthShort $dom"
                }
                Pair(label, dom)
            }

            if (groupedByDay.isEmpty()) {
                item {
                    SoftCard(modifier = Modifier.fillMaxWidth(), elevation = 4.dp) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                painter = painterResource(com.saannapunta.app.R.drawable.chito_detective),
                                contentDescription = "Chito searching",
                                modifier = Modifier.size(88.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (transactions.isEmpty()) "No transactions yet." else "Chito looked everywhere. No matching transactions.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = InkMuted
                            )
                        }
                    }
                }
            }

            groupedByDay.forEach { (pair, itemsForDay) ->
                val (dayLabel, dayOfMonth) = pair
                val dayExpenseTotal = itemsForDay.filter { it.isCountableSpend() }.sumOf { it.amountCentavos }
                val isPayday = (payCycleConfig.type == com.saannapunta.app.data.model.PayCycleType.SEMI_MONTHLY_15_30 ||
                    payCycleConfig.type == com.saannapunta.app.data.model.PayCycleType.MONTHLY_END) &&
                    (dayOfMonth == payCycleConfig.firstPayday || dayOfMonth == payCycleConfig.secondPayday)

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = dayLabel,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Ink
                            )
                            if (isPayday) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .background(Green100, CircleShape)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Payday",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = Green700
                                    )
                                }
                            }
                        }

                        if (dayExpenseTotal > 0) {
                            Text(
                                text = "−${MoneyFormatter.format(dayExpenseTotal, false)}",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = InkMuted
                            )
                        }
                    }
                }

                item {
                    SoftCard(modifier = Modifier.fillMaxWidth(), elevation = 4.dp) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            itemsForDay.forEachIndexed { idx, item ->
                                if (idx > 0) {
                                    HorizontalDivider(
                                        color = LineBorder,
                                        thickness = 1.dp,
                                        modifier = Modifier.padding(start = 74.dp)
                                    )
                                }
                                val acc = accounts.find { it.id == item.accountId }
                                val targetAcc = if (item.targetAccountId != null) accounts.find { it.id == item.targetAccountId } else null
                                TransactionRow(
                                    item = item,
                                    account = acc,
                                    targetAccount = targetAcc,
                                    onClick = { onEditTransaction(item) },
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // LIVE CALENDAR HEATMAP VIEW
            val dailySpendMap = mutableMapOf<Int, Long>()
            val cal = Calendar.getInstance()
            for (tx in filteredTransactions) {
                if (tx.isCountableSpend()) {
                    cal.timeInMillis = tx.timestamp
                    if (cal.get(Calendar.YEAR) == displayedYear && cal.get(Calendar.MONTH) == displayedMonth) {
                        val d = cal.get(Calendar.DAY_OF_MONTH)
                        dailySpendMap[d] = (dailySpendMap[d] ?: 0L) + tx.amountCentavos
                    }
                }
            }

            item {
                CalendarHeatmapCard(
                    year = displayedYear,
                    month = displayedMonth,
                    selectedDay = selectedCalendarDay,
                    dailySpendMap = dailySpendMap,
                    payCycleConfig = payCycleConfig,
                    safePerDayCentavos = overview.safeToSpendPerDayCentavos,
                    onSelectDay = { selectedCalendarDay = it },
                    onPrevMonth = {
                        val navCal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, displayedYear)
                            set(Calendar.MONTH, displayedMonth)
                            set(Calendar.DAY_OF_MONTH, 1)
                            add(Calendar.MONTH, -1)
                        }
                        displayedYear = navCal.get(Calendar.YEAR)
                        displayedMonth = navCal.get(Calendar.MONTH)
                        selectedCalendarDay = 1
                    },
                    onNextMonth = {
                        val navCal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, displayedYear)
                            set(Calendar.MONTH, displayedMonth)
                            set(Calendar.DAY_OF_MONTH, 1)
                            add(Calendar.MONTH, 1)
                        }
                        displayedYear = navCal.get(Calendar.YEAR)
                        displayedMonth = navCal.get(Calendar.MONTH)
                        selectedCalendarDay = 1
                    }
                )
            }

            val monthShortName = Calendar.getInstance().apply {
                set(Calendar.YEAR, displayedYear)
                set(Calendar.MONTH, displayedMonth)
                set(Calendar.DAY_OF_MONTH, 1)
            }.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()) ?: ""

            val selectedDaySpend = dailySpendMap[selectedCalendarDay] ?: 0L

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$monthShortName $selectedCalendarDay",
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink
                    )
                    Text(
                        text = if (selectedDaySpend > 0L) {
                            "Spent: ${MoneyFormatter.format(selectedDaySpend, false)}"
                        } else {
                            "No expenses"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = InkMuted
                    )
                }
            }

            val itemsForSelectedDay = filteredTransactions.filter { item ->
                val cal = Calendar.getInstance().apply { timeInMillis = item.timestamp }
                cal.get(Calendar.YEAR) == displayedYear &&
                    cal.get(Calendar.MONTH) == displayedMonth &&
                    cal.get(Calendar.DAY_OF_MONTH) == selectedCalendarDay
            }

            if (itemsForSelectedDay.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(LineBorder))
                    ) {
                        Column {
                            itemsForSelectedDay.forEachIndexed { idx, item ->
                                if (idx > 0) {
                                    HorizontalDivider(color = LineBorder, thickness = 1.dp, modifier = Modifier.padding(start = 58.dp))
                                }
                                val acc = accounts.find { it.id == item.accountId }
                                val targetAcc = if (item.targetAccountId != null) accounts.find { it.id == item.targetAccountId } else null
                                TransactionRow(
                                    item = item,
                                    account = acc,
                                    targetAccount = targetAcc,
                                    onClick = { onEditTransaction(item) },
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(LineBorder))
                    ) {
                        Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No transactions on $monthShortName $selectedCalendarDay.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = InkMuted
                            )
                        }
                    }
                }
            }
        }

        // Primary Button at bottom: "Add transaction"
        item {
            SaanNaPuntaButton(
                text = "Add transaction",
                onClick = onAddTransactionClick
            )
        }
    }
}

@Composable
private fun FilterChipPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val feedback = rememberUiFeedback()
    val bg by animateColorAsState(if (isSelected) Green600 else SurfaceColor, tween(180), label = "FilterChipBg")
    Box(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(CircleShape)
            .background(bg)
            .then(if (!isSelected) Modifier.border(1.dp, LineBorder, CircleShape) else Modifier)
            .clickable {
                if (!isSelected) feedback.select()
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (isSelected) SurfaceColor else InkMuted
        )
    }
}

@Composable
fun CalendarHeatmapCard(
    year: Int,
    month: Int,
    selectedDay: Int,
    dailySpendMap: Map<Int, Long>,
    payCycleConfig: PayCycleConfig,
    safePerDayCentavos: Long,
    onSelectDay: (Int) -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val monthCal = remember(year, month) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }
    val monthTitle = remember(year, month) {
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(monthCal.time)
    }
    val firstDayOffset = monthCal.get(Calendar.DAY_OF_WEEK) - 1 // Sunday = 0
    val totalDaysInMonth = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val totalCells = firstDayOffset + totalDaysInMonth
    val rows = (totalCells + 6) / 7

    val safeThreshold = if (safePerDayCentavos > 0L) safePerDayCentavos else 50000L

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(LineBorder))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Month Header with Prev / Next navigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevMonth, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month", tint = InkMuted)
                }
                Text(
                    text = monthTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = Ink
                )
                IconButton(onClick = onNextMonth, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next month", tint = InkMuted)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Weekday Headers
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { dayLetter ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = dayLetter,
                            style = MaterialTheme.typography.labelSmall,
                            color = InkFaint
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            for (row in 0 until rows) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                ) {
                    for (col in 0..6) {
                        val cellIndex = row * 7 + col
                        val dayNumber = cellIndex - firstDayOffset + 1
                        if (dayNumber in 1..totalDaysInMonth) {
                            val isSelected = dayNumber == selectedDay
                            val isPayday = (payCycleConfig.type == com.saannapunta.app.data.model.PayCycleType.SEMI_MONTHLY_15_30 ||
                                payCycleConfig.type == com.saannapunta.app.data.model.PayCycleType.MONTHLY_END) &&
                                (dayNumber == payCycleConfig.firstPayday ||
                                    dayNumber == payCycleConfig.secondPayday.coerceAtMost(totalDaysInMonth))
                            val daySpend = dailySpendMap[dayNumber] ?: 0L

                            val spendType = when {
                                daySpend == 0L -> 0
                                daySpend <= safeThreshold -> 1
                                else -> 2
                            }

                            val tileBg = when {
                                isSelected -> Green600
                                spendType == 1 -> Green100
                                spendType == 2 -> Green50
                                else -> SurfaceColor
                            }

                            val textColor = when {
                                isSelected -> SurfaceColor
                                spendType == 2 -> StatusWarn
                                else -> Ink
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .padding(horizontal = 2.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(tileBg)
                                    .then(
                                        if (isPayday && !isSelected) Modifier.border(1.5.dp, Green600, RoundedCornerShape(10.dp))
                                        else Modifier
                                    )
                                    .clickable { onSelectDay(dayNumber) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$dayNumber",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected || isPayday || spendType > 0) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = textColor
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendItem(color = Green100, label = "Within budget")
                LegendItem(textColor = StatusWarn, label = "High spend")
                LegendRingItem(borderColor = Green600, label = "Payday")
            }
        }
    }
}

@Composable
fun LegendItem(color: Color = SurfaceColor, textColor: Color = InkMuted, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
                .border(1.dp, LineBorder, RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = textColor)
    }
}

@Composable
fun LegendRingItem(borderColor: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .border(1.5.dp, borderColor, RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = InkMuted)
    }
}
