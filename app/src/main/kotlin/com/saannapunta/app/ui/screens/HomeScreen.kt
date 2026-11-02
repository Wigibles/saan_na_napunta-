package com.saannapunta.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saannapunta.app.R
import com.saannapunta.app.data.CycleCalculator
import com.saannapunta.app.data.DataRepository
import com.saannapunta.app.data.model.Account
import com.saannapunta.app.data.model.AccountType
import com.saannapunta.app.data.model.MoneyFormatter
import com.saannapunta.app.data.model.TransactionItem
import com.saannapunta.app.data.model.TransactionType
import com.saannapunta.app.data.model.UpcomingBill
import com.saannapunta.app.data.model.isCountableSpend
import com.saannapunta.app.ui.chito.ChitoCompanion
import com.saannapunta.app.ui.chito.ChitoLines
import com.saannapunta.app.ui.components.*
import com.saannapunta.app.ui.feedback.rememberUiFeedback
import com.saannapunta.app.ui.theme.*
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale

@Composable
fun HomeScreen(
    repository: DataRepository,
    onNavigateToAccounts: () -> Unit,
    onNavigateToTransactions: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onOpenAddTransaction: (TransactionType) -> Unit,
    onEditTransaction: (TransactionItem) -> Unit,
    onAddUpcomingBill: () -> Unit = {},
    onBillClick: (UpcomingBill) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val overview by repository.overview.collectAsState()
    val accounts by repository.accounts.collectAsState()
    val transactions by repository.transactions.collectAsState()
    val upcomingBills by repository.upcomingBills.collectAsState()
    val userName by repository.userName.collectAsState()
    val feedback = rememberUiFeedback()

    var hideBalance by remember { mutableStateOf(false) }

    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Magandang umaga"
            hour < 18 -> "Magandang hapon"
            else -> "Magandang gabi"
        }
    }

    var chitoTaps by remember { mutableIntStateOf(0) }
    val chitoLine = ChitoLines.home(overview, transactions.isNotEmpty(), chitoTaps)
    // Any touch on Home counts as activity, so Chito stays awake while the user is here
    var touches by remember { mutableIntStateOf(0) }

    val last7Days = remember(transactions) { spendingLast7Days(transactions) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Canvas)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        // Count presses only, so scrolling doesn't recompose Home on every move
                        if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) touches++
                    }
                }
            },
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Header: avatar, greeting, settings
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(HeroGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = userName.trim().take(1).uppercase().ifBlank { "S" },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = OnHero
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(greeting, style = MaterialTheme.typography.labelLarge, color = InkMuted)
                    Text(
                        text = userName.ifBlank { "Friend" },
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(SurfaceColor)
                        .clickable(onClickLabel = "Settings") { onNavigateToSettings() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = Ink)
                }
            }
        }

        // Chito: reacts to today's numbers, does his own thing when left alone, tap for tips
        item {
            ChitoCompanion(
                line = chitoLine,
                size = 80.dp,
                onTap = { chitoTaps++ },
                bubbleColor = SurfaceColor,
                idleLife = true,
                interactionKey = touches
            )
        }

        item {
            HeroBalanceCard(
                totalBalanceCentavos = overview.totalBalanceCentavos,
                spentTodayCentavos = overview.spentTodayCentavos,
                spentThisPeriodCentavos = overview.totalSpentInCycleCentavos,
                hideBalance = hideBalance,
                leftTodayCentavos = overview.leftTodayCentavos,
                daysLeft = overview.daysLeftInCycle,
                nextPaydayText = overview.nextPaydayText,
                budgetLimitCentavos = overview.totalBudgetLimitCentavos,
                onToggleHide = {
                    feedback.toggle(!hideBalance)
                    hideBalance = !hideBalance
                }
            )
        }

        // Quick actions
        item {
            SoftCard(modifier = Modifier.fillMaxWidth(), elevation = 4.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CircleAction("Expense", Icons.Outlined.ArrowUpward, Color(0xFFE8833A), {
                        feedback.tap()
                        onOpenAddTransaction(TransactionType.EXPENSE)
                    }, Modifier.weight(1f))
                    CircleAction("Income", Icons.Outlined.ArrowDownward, Green600, {
                        feedback.tap()
                        onOpenAddTransaction(TransactionType.INCOME)
                    }, Modifier.weight(1f))
                    CircleAction("Transfer", Icons.Outlined.CompareArrows, Color(0xFF2F80ED), {
                        feedback.tap()
                        onOpenAddTransaction(TransactionType.TRANSFER)
                    }, Modifier.weight(1f))
                    CircleAction("Bill", Icons.Outlined.ReceiptLong, Color(0xFF9B51E0), {
                        feedback.tap()
                        onAddUpcomingBill()
                    }, Modifier.weight(1f))
                }
            }
        }

        // Spending pulse: last 7 days + today against today's safe amount
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SoftCard(modifier = Modifier.weight(1.15f), elevation = 4.dp) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Last 7 days", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = InkMuted)
                        Text(
                            if (hideBalance) "₱••••" else MoneyFormatter.format(last7Days.sumOf { it.second }, false),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Ink
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        MiniBarChart(last7Days)
                    }
                }
                SoftCard(modifier = Modifier.weight(1f), elevation = 4.dp) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Spent today", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = InkMuted)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (hideBalance) "₱••••" else MoneyFormatter.format(overview.spentTodayCentavos, false),
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = Ink
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        val over = overview.dayAllowanceCentavos > 0 && overview.spentTodayCentavos > overview.dayAllowanceCentavos
                        if (over) {
                            StatusChip("Over today's safe", StatusWarn, icon = Icons.Outlined.Warning)
                        } else {
                            StatusChip("Within safe amount", Green700, icon = Icons.Outlined.CheckCircle)
                        }
                    }
                }
            }
        }

        // Accounts carousel
        item {
            Column {
                SectionHeader("Your accounts", actionLabel = "See all", onAction = onNavigateToAccounts)
                Spacer(modifier = Modifier.height(4.dp))
                if (accounts.isEmpty()) {
                    Text("No accounts yet. Add one in Wallet.", style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 2.dp)
                    ) {
                        items(accounts, key = { it.id }) { account ->
                            AccountMiniCard(account, hideBalance, onClick = onNavigateToAccounts)
                        }
                    }
                }
            }
        }

        // Upcoming bills
        item {
            SoftCard(modifier = Modifier.fillMaxWidth(), elevation = 4.dp) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 12.dp)) {
                    SectionHeader("Upcoming bills", actionLabel = "+ Add", onAction = onAddUpcomingBill)
                    if (upcomingBills.isEmpty()) {
                        Text(
                            "No bills yet. Add rent, kuryente, or subscriptions and I'll set money aside for them.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkMuted,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    } else {
                        upcomingBills.take(4).forEach { bill ->
                            BillRow(bill, hideBalance) { onBillClick(bill) }
                        }
                    }
                }
            }
        }

        // Recent transactions
        item {
            SectionHeader("Recent", actionLabel = "See all", onAction = onNavigateToTransactions)
        }
        item {
            SoftCard(modifier = Modifier.fillMaxWidth(), elevation = 4.dp) {
                if (transactions.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(R.drawable.chito_waving),
                            contentDescription = "Chito waving",
                            modifier = Modifier.size(88.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "No transactions yet. Add one, or turn on message reading for GCash.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkMuted,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        SaanNaPuntaButton(text = "Add transaction", onClick = { onOpenAddTransaction(TransactionType.EXPENSE) })
                    }
                } else {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                        transactions.take(5).forEachIndexed { index, item ->
                            if (index > 0) HorizontalDivider(color = LineBorder, modifier = Modifier.padding(start = 64.dp))
                            TransactionRow(
                                item = item,
                                account = accounts.find { it.id == item.accountId },
                                targetAccount = item.targetAccountId?.let { id -> accounts.find { it.id == id } },
                                onClick = { onEditTransaction(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** (day label, spent centavos) for the last 7 days, oldest first, in Manila time. */
private fun spendingLast7Days(transactions: List<TransactionItem>): List<Pair<String, Long>> {
    val today = CycleCalculator.toLocalDate(System.currentTimeMillis())
    return (6 downTo 0).map { back ->
        val day = today.minusDays(back.toLong())
        val start = CycleCalculator.startOfDayMillis(day)
        val end = CycleCalculator.endOfDayMillis(day)
        val spent = transactions.filter { it.isCountableSpend() && it.timestamp in start..end }.sumOf { it.amountCentavos }
        day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.US) to spent
    }
}

@Composable
private fun MiniBarChart(days: List<Pair<String, Long>>) {
    val max = (days.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        days.forEachIndexed { index, (label, spent) ->
            val isToday = index == days.lastIndex
            val target = if (spent == 0L) 0.06f else (spent.toFloat() / max).coerceIn(0.12f, 1f)
            val height by animateFloatAsState(target, tween(500, delayMillis = index * 40, easing = FastOutSlowInEasing), label = "Bar$index")
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .height((44 * height).dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (isToday) Green600 else Green100)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal),
                    color = if (isToday) Ink else InkFaint
                )
            }
        }
    }
}

@Composable
private fun AccountMiniCard(account: Account, hideBalance: Boolean, onClick: () -> Unit) {
    val accent = providerAccent(account.provider)
    SoftCard(modifier = Modifier.width(156.dp), elevation = 4.dp, onClick = onClick) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .background(accent)
        )
        Column(modifier = Modifier.padding(14.dp)) {
            AccountLogoTile(iconResName = account.iconResName, fallbackName = account.name, size = 36)
            Spacer(modifier = Modifier.height(10.dp))
            Text(account.name, style = MaterialTheme.typography.labelLarge, color = InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = when {
                    hideBalance -> "₱••••"
                    account.needsOpeningBalance -> "Set balance"
                    else -> MoneyFormatter.format(account.balanceCentavos, false)
                },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (account.type == AccountType.LIABILITY) StatusOver else Ink
            )
            Text(
                text = when (account.type) {
                    AccountType.E_WALLET -> "E-wallet"
                    AccountType.BANK -> "Bank"
                    AccountType.CASH -> "Cash"
                    AccountType.LIABILITY -> "Owed"
                },
                style = MaterialTheme.typography.labelSmall,
                color = InkFaint
            )
        }
    }
}

@Composable
internal fun BillRow(bill: UpcomingBill, hideBalance: Boolean, onClick: () -> Unit) {
    val today = CycleCalculator.toLocalDate(System.currentTimeMillis())
    val due = CycleCalculator.toLocalDate(bill.dueDateTimestamp)
    val days = java.time.temporal.ChronoUnit.DAYS.between(today, due)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryIconChip(category = bill.category, size = 40.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(bill.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${due.month.getDisplayName(TextStyle.SHORT, Locale.US)} ${due.dayOfMonth}" + if (bill.recurring) " · Monthly" else "",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (hideBalance) "₱••••" else "−${MoneyFormatter.format(bill.amountCentavos, false)}",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Ink
            )
            when {
                days < 0 -> StatusChip("Overdue", StatusOver)
                days == 0L -> StatusChip("Due today", StatusWarn)
                days <= 3 -> StatusChip("In ${days}d", StatusWarn)
                else -> Text("In ${days}d", style = MaterialTheme.typography.labelSmall, color = InkFaint)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
    }
}
