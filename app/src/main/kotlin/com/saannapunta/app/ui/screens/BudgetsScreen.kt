package com.saannapunta.app.ui.screens

import com.saannapunta.app.ui.components.*
import com.saannapunta.app.ui.feedback.rememberUiFeedback
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import com.saannapunta.app.ui.chito.ChitoCompanion
import com.saannapunta.app.ui.chito.ChitoLines
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.saannapunta.app.ui.categories.CategoryCustomizationActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saannapunta.app.data.DataRepository
import com.saannapunta.app.data.model.CategoryBudget
import com.saannapunta.app.data.model.MoneyFormatter
import com.saannapunta.app.data.model.SavingsGoal
import com.saannapunta.app.data.model.isCountableSpend
import com.saannapunta.app.ui.components.BudgetCardItem
import com.saannapunta.app.ui.components.SaanNaPuntaButton
import com.saannapunta.app.ui.components.SavingsGoalCardItem
import com.saannapunta.app.ui.theme.*
import java.util.Calendar

@Composable
fun BudgetsScreen(
    repository: DataRepository,
    onAddBudgetClick: () -> Unit,
    onEditBudgetClick: (CategoryBudget) -> Unit,
    onAddGoalClick: () -> Unit,
    onEditGoalClick: (SavingsGoal) -> Unit,
    onAddUpcomingBill: () -> Unit = {},
    onBillClick: (com.saannapunta.app.data.model.UpcomingBill) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val budgets by repository.budgets.collectAsState()
    val transactions by repository.transactions.collectAsState()
    val goals by repository.savingsGoals.collectAsState()
    val upcomingBills by repository.upcomingBills.collectAsState()
    val overview by repository.overview.collectAsState()
    val payCycleConfig by repository.payCycleConfig.collectAsState()
    val feedback = rememberUiFeedback()

    var selectedPeriodIndex by remember { mutableIntStateOf(0) }
    val periods = listOf(
        "Current period (${overview.cycleStartDateText}–${overview.cycleEndDateText})",
        "This month",
        "All time"
    )

    // Filter expenses by selected period
    val periodExpenses = remember(transactions, selectedPeriodIndex, overview) {
        val nowCal = Calendar.getInstance()
        val monthStart = (nowCal.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }.timeInMillis

        transactions.filter { tx ->
            tx.isCountableSpend() && when (selectedPeriodIndex) {
                0 -> tx.timestamp in overview.cycleStartMillis..overview.cycleEndMillis
                1 -> tx.timestamp >= monthStart
                else -> true
            }
        }
    }

    val spentByCategory = remember(periodExpenses) {
        val map = mutableMapOf<String, Long>()
        for (tx in periodExpenses) {
            map[tx.category] = (map[tx.category] ?: 0L) + tx.amountCentavos
        }
        map
    }

    val periodTotalSpent = remember(periodExpenses) {
        periodExpenses.sumOf { it.amountCentavos }
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
            ScreenHeader(eyebrow = "Plan", title = "Budgets & sahod") {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Green600)
                        .clickable(onClickLabel = "Add category budget") { onAddBudgetClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add category budget", tint = SurfaceColor)
                }
            }
        }

        // Chito watches the category budgets
        item {
            ChitoCompanion(line = ChitoLines.budgets(budgets, spentByCategory), size = 72.dp, bubbleColor = SurfaceColor)
        }

        // 2. Period selector
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                periods.forEachIndexed { idx, p ->
                    val isSelected = selectedPeriodIndex == idx
                    val bg by animateColorAsState(if (isSelected) Green600 else SurfaceColor, tween(180), label = "PeriodBg")
                    Box(
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .clip(CircleShape)
                            .background(bg)
                            .then(if (!isSelected) Modifier.border(1.dp, LineBorder, CircleShape) else Modifier)
                            .clickable {
                                if (!isSelected) feedback.select()
                                selectedPeriodIndex = idx
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = p,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isSelected) SurfaceColor else InkMuted
                        )
                    }
                }
            }
        }

        // 3. Cycle budget hero
        item {
            val limit = overview.totalBudgetLimitCentavos
            val shape = RoundedCornerShape(28.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(14.dp, shape, ambientColor = Green900.copy(alpha = 0.25f), spotColor = Green900.copy(alpha = 0.35f))
                    .clip(shape)
                    .background(HeroGradient)
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when (selectedPeriodIndex) {
                            0 -> "Spent this cycle"
                            1 -> "Spent this month"
                            else -> "Spent all time"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = OnHeroMuted,
                        modifier = Modifier.weight(1f)
                    )
                    if (limit > 0L) {
                        val pct = ((periodTotalSpent.toFloat() / limit) * 100).toInt()
                        HeroChip("$pct% used")
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = MoneyFormatter.format(rememberCountUp(periodTotalSpent), false),
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 36.sp, fontWeight = FontWeight.Bold),
                        color = OnHero
                    )
                    if (limit > 0L) {
                        Text(
                            text = " / ${MoneyFormatter.format(limit, false)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = OnHeroMuted,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }
                if (limit > 0L) {
                    val ratio = (periodTotalSpent.toFloat() / limit).coerceIn(0f, 1f)
                    val shown by animateFloatAsState(ratio, tween(600, easing = FastOutSlowInEasing), label = "PlanHeroFill")
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(shown)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(if (periodTotalSpent > limit) StatusOver else Green500)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    val diff = limit - periodTotalSpent
                    Text(
                        text = if (diff >= 0) "${MoneyFormatter.format(diff, false)} left to spend" else "${MoneyFormatter.format(-diff, false)} over budget",
                        style = MaterialTheme.typography.labelLarge,
                        color = OnHero
                    )
                } else {
                    Text("No category limits yet. Your safe amount comes from your wallets.", style = MaterialTheme.typography.bodySmall, color = OnHeroMuted)
                }
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Safe per day", style = MaterialTheme.typography.labelMedium, color = OnHeroMuted)
                        Text(
                            MoneyFormatter.format(overview.safeToSpendPerDayCentavos, false),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = OnHero
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            when (payCycleConfig.type) {
                                com.saannapunta.app.data.model.PayCycleType.FLEXIBLE -> "Period resets"
                                com.saannapunta.app.data.model.PayCycleType.WEEKLY -> "Week ends"
                                else -> "Next payday"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = OnHeroMuted
                        )
                        Text(
                            "${overview.nextPaydayText} · ${overview.daysLeftInCycle}d",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = OnHero
                        )
                    }
                }
            }
        }

        // 4. Upcoming bills before payday
        item {
            SoftCard(modifier = Modifier.fillMaxWidth(), elevation = 4.dp) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 12.dp)) {
                    SectionHeader("Upcoming bills", actionLabel = "+ Add", onAction = onAddUpcomingBill)
                    if (upcomingBills.isEmpty()) {
                        Text(
                            text = "No unpaid bills. Add rent, kuryente, or dues and I'll set money aside for them.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkMuted,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    } else {
                        upcomingBills.forEach { bill ->
                            BillRow(bill, hideBalance = false) { onBillClick(bill) }
                        }
                    }
                }
            }
        }

        // 5. Category budgets
        item {
            val context = LocalContext.current
            SectionHeader(
                "Category budgets",
                actionLabel = "Customize ›",
                onAction = { context.startActivity(Intent(context, CategoryCustomizationActivity::class.java)) }
            )
        }

        if (budgets.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Green50),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(LineBorder))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "No category budgets set",
                            style = MaterialTheme.typography.titleSmall,
                            color = Green700
                        )
                        Text(
                            text = "You're currently tracking expenses without category limits. Tap '+ Add budget' anytime to set spending limits for Food & Dining, Utilities, Transportation, or any custom category.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Ink
                        )
                    }
                }
            }
        }

        items(budgets) { b ->
            val spent = spentByCategory[b.category] ?: 0L
            BudgetCardItem(
                category = b.category,
                spentCentavos = spent,
                limitCentavos = b.limitCentavos,
                examples = b.examples,
                daysLeftInCycle = overview.daysLeftInCycle,
                iconName = b.iconName,
                colorHex = b.colorHex,
                onClick = { onEditBudgetClick(b) }
            )
        }

        // 6. Savings goals
        item {
            SectionHeader("Savings goals", modifier = Modifier.padding(top = 8.dp), actionLabel = "+ Add goal", onAction = onAddGoalClick)
        }

        items(goals) { g ->
            SavingsGoalCardItem(
                name = g.name,
                savedCentavos = g.savedCentavos,
                targetCentavos = g.targetCentavos,
                onClick = { onEditGoalClick(g) }
            )
        }

        // 7. Primary Action Button
        item {
            SaanNaPuntaButton(
                text = "Add category budget",
                onClick = onAddBudgetClick
            )
        }
    }
}
