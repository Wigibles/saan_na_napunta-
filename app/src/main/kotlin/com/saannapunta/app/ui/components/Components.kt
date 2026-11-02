package com.saannapunta.app.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saannapunta.app.data.model.Account
import com.saannapunta.app.data.model.MoneyFormatter
import com.saannapunta.app.data.model.TransactionItem
import com.saannapunta.app.data.model.TransactionType
import com.saannapunta.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AccountLogoTile(
    iconResName: String?,
    fallbackName: String,
    modifier: Modifier = Modifier,
    size: Int = 40
) {
    val context = LocalContext.current
    // Cash accounts saved before the cash icon existed have no icon name
    val iconName = iconResName?.takeIf { it.isNotEmpty() }
        ?: if (fallbackName.contains("cash", ignoreCase = true)) "ic_cash" else null
    val resId = if (iconName != null) {
        context.resources.getIdentifier(iconName, "drawable", context.packageName)
    } else 0

    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceColor)
            .border(1.dp, LineBorder, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (resId != 0) {
            Image(
                painter = painterResource(id = resId),
                contentDescription = fallbackName,
                modifier = Modifier
                    .size((size - 10).dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            // Monogram fallback per Section 5.2
            val monogram = fallbackName.take(2).uppercase()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Green50),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = monogram,
                    color = Green700,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (size / 3).sp
                )
            }
        }
    }
}

@Composable
fun HeroBalanceCard(
    totalBalanceCentavos: Long,
    spentTodayCentavos: Long,
    spentThisPeriodCentavos: Long,
    hideBalance: Boolean = false,
    modifier: Modifier = Modifier,
    leftTodayCentavos: Long? = null,
    daysLeft: Int? = null,
    nextPaydayText: String? = null,
    budgetLimitCentavos: Long = 0L,
    onToggleHide: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(14.dp, shape, ambientColor = Green900.copy(alpha = 0.25f), spotColor = Green900.copy(alpha = 0.35f))
            .clip(shape)
            .background(HeroGradient)
    ) {
        // Soft decorative circles in the corner
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 60.dp, y = (-70).dp)
                .size(200.dp)
                .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.06f), CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 20.dp, y = 40.dp)
                .size(90.dp)
                .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f), CircleShape)
        )

        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Available to spend",
                    style = MaterialTheme.typography.labelLarge,
                    color = OnHeroMuted,
                    modifier = Modifier.weight(1f)
                )
                if (onToggleHide != null) {
                    IconButton(onClick = onToggleHide, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (hideBalance) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (hideBalance) "Show amounts" else "Hide amounts",
                            tint = OnHeroMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (hideBalance) {
                Text(
                    text = "₱ ••••••",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 38.sp, fontWeight = FontWeight.Bold),
                    color = OnHero
                )
            } else {
                val fullText = MoneyFormatter.format(rememberCountUp(totalBalanceCentavos))
                val annotatedBalance = buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 26.sp, fontWeight = FontWeight.Medium)) { append("₱") }
                    val number = fullText.removePrefix("₱")
                    val dot = number.lastIndexOf('.')
                    if (dot > 0) {
                        append(number.substring(0, dot))
                        withStyle(SpanStyle(fontSize = 24.sp, fontWeight = FontWeight.Medium)) { append(number.substring(dot)) }
                    } else append(number)
                }
                Text(
                    text = annotatedBalance,
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 40.sp, fontWeight = FontWeight.Bold),
                    color = OnHero
                )
            }

            if (leftTodayCentavos != null || daysLeft != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (leftTodayCentavos != null) {
                        HeroChip(
                            text = if (hideBalance) "₱•••• left today" else "${MoneyFormatter.format(leftTodayCentavos, false)} left today",
                            icon = Icons.Outlined.Savings
                        )
                    }
                    if (daysLeft != null) {
                        HeroChip(
                            text = if (nextPaydayText != null) "$daysLeft days to $nextPaydayText" else "$daysLeft days left",
                            icon = Icons.Outlined.Event
                        )
                    }
                }
            }

            if (budgetLimitCentavos > 0L) {
                Spacer(modifier = Modifier.height(16.dp))
                val ratio = (spentThisPeriodCentavos.toFloat() / budgetLimitCentavos.toFloat()).coerceIn(0f, 1f)
                val shown by animateFloatAsState(ratio, tween(600, easing = FastOutSlowInEasing), label = "HeroBudget")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.16f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(shown)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(if (spentThisPeriodCentavos > budgetLimitCentavos) StatusOver else Green500)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (hideBalance) "Cycle budget used: ${(ratio * 100).toInt()}%"
                    else "${MoneyFormatter.format(spentThisPeriodCentavos, false)} of ${MoneyFormatter.format(budgetLimitCentavos, false)} cycle budget used",
                    style = MaterialTheme.typography.labelMedium,
                    color = OnHeroMuted
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.14f))
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                HeroStat("Spent today", if (hideBalance) "₱••••" else MoneyFormatter.format(spentTodayCentavos, false), Modifier.weight(1f))
                HeroStat("This cycle", if (hideBalance) "₱••••" else MoneyFormatter.format(spentThisPeriodCentavos, false), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = OnHeroMuted)
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = OnHero)
    }
}

@Composable
fun TransactionRow(
    item: TransactionItem,
    account: Account?,
    targetAccount: Account? = null,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val timeStr = timeFormat.format(Date(item.timestamp))

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Category icon, with the account's logo tucked into the corner
        Box {
            CategoryIconChip(category = item.category, type = item.type, size = 46.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 4.dp, y = 4.dp)
            ) {
                AccountLogoTile(
                    iconResName = account?.iconResName,
                    fallbackName = account?.name ?: "SP",
                    size = 20
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            val accName = account?.name ?: "Wallet"
            val subtext = when {
                item.type == TransactionType.TRANSFER && targetAccount != null -> "$accName → ${targetAccount.name}"
                !item.subCategory.isNullOrBlank() -> "$accName · ${item.subCategory}"
                else -> "$accName · ${item.category}"
            }
            Text(
                text = subtext,
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = MoneyFormatter.formatWithSign(item.amountCentavos, item.type),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = when (item.type) {
                    TransactionType.INCOME -> Green600
                    else -> Ink // Section 2: spending is shown in ink, never red
                },
                textAlign = TextAlign.End
            )
            Text(timeStr, style = MaterialTheme.typography.labelSmall, color = InkFaint)
        }
    }
}

@Composable
fun BudgetCardItem(
    category: String,
    spentCentavos: Long,
    limitCentavos: Long,
    examples: String = "",
    daysLeftInCycle: Int = 12,
    iconName: String? = null,
    colorHex: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val ratio = if (limitCentavos > 0) spentCentavos.toFloat() / limitCentavos.toFloat() else 0f
    val shownRatio by animateFloatAsState(ratio.coerceIn(0f, 1f), tween(500, easing = FastOutSlowInEasing), label = "BudgetFill")
    val isOver = ratio > 1.0f
    val isNearLimit = !isOver && ratio >= 0.85f
    val leftCentavos = limitCentavos - spentCentavos

    val catColor = remember(colorHex, category) {
        com.saannapunta.app.ui.categories.CategoryIconCatalog.getColor(colorHex, androidx.compose.ui.graphics.Color.Unspecified)
    }.takeIf { it != androidx.compose.ui.graphics.Color.Unspecified } ?: categoryColor(category)
    val icon = remember(iconName, category) {
        com.saannapunta.app.ui.categories.CategoryIconCatalog.getIconForCategory(iconName, category)
    }
    val barColor = when {
        isOver -> StatusOver
        isNearLimit -> StatusWarn
        else -> catColor
    }

    SoftCard(modifier = modifier.fillMaxWidth(), elevation = 4.dp, onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(catColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = catColor, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(category, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Ink)
                    if (examples.isNotEmpty()) {
                        Text(examples, style = MaterialTheme.typography.labelSmall, color = InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        MoneyFormatter.format(spentCentavos, false),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isOver) StatusOver else Ink
                    )
                    Text("of ${MoneyFormatter.format(limitCentavos, false)}", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(catColor.copy(alpha = 0.12f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(shownRatio)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(barColor)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                when {
                    isOver -> StatusChip("Over by ${MoneyFormatter.format(-leftCentavos, false)}", StatusOver, icon = Icons.Outlined.Warning)
                    isNearLimit -> StatusChip("${MoneyFormatter.format(leftCentavos, false)} left · near limit", StatusWarn, icon = Icons.Outlined.Warning)
                    else -> StatusChip("${MoneyFormatter.format(leftCentavos, false)} left", Green700, icon = Icons.Outlined.CheckCircle)
                }
                Spacer(modifier = Modifier.weight(1f))
                if (leftCentavos > 0 && daysLeftInCycle > 0) {
                    Text(
                        "${MoneyFormatter.format(leftCentavos / daysLeftInCycle, false)}/day safe",
                        style = MaterialTheme.typography.labelMedium,
                        color = InkMuted
                    )
                } else {
                    Text("${(ratio * 100).toInt()}% used", style = MaterialTheme.typography.labelMedium, color = InkMuted)
                }
            }
        }
    }
}

@Composable
fun SavingsGoalCardItem(
    name: String,
    savedCentavos: Long,
    targetCentavos: Long,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val ratio = if (targetCentavos > 0) savedCentavos.toFloat() / targetCentavos.toFloat() else 0f
    val shownRatio by animateFloatAsState(ratio.coerceIn(0f, 1f), tween(500, easing = FastOutSlowInEasing), label = "GoalFill")
    val done = targetCentavos in 1..savedCentavos

    SoftCard(modifier = modifier.fillMaxWidth(), elevation = 4.dp, onClick = onClick) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(if (done) com.saannapunta.app.R.drawable.chito_confetti else com.saannapunta.app.R.drawable.chito_piggy),
                contentDescription = if (done) "Chito celebrating" else "Chito with a piggy bank",
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Ink,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    StatusChip(if (done) "Reached" else "Ipon", Green700)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(Green50)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(shownRatio)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(Green600)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                val remaining = Math.max(0L, targetCentavos - savedCentavos)
                Text(
                    text = "${MoneyFormatter.format(savedCentavos, false)} of ${MoneyFormatter.format(targetCentavos, false)}" +
                        if (remaining > 0) " · ${MoneyFormatter.format(remaining, false)} to go" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkMuted
                )
            }
        }
    }
}

@Composable
fun SaanNaPuntaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Green600,
            contentColor = SurfaceColor,
            disabledContainerColor = LineBorder,
            disabledContentColor = InkFaint
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = SurfaceColor
        )
    }
}

/** Set after the first count-up so returning to Home later doesn't replay it. */
private var hasCountedUp = false

/**
 * Counts a peso amount up from zero the first time it is shown in this app session (design spec
 * section 10: the one entrance animation allowed), then eases between later values. Exact at rest.
 */
@Composable
fun rememberCountUp(targetCentavos: Long): Long {
    var from by remember { mutableLongStateOf(if (hasCountedUp) targetCentavos else 0L) }
    var settled by remember { mutableLongStateOf(from) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(targetCentavos) {
        if (targetCentavos == settled) return@LaunchedEffect
        from = settled
        progress.snapTo(0f)
        progress.animateTo(1f, tween(if (hasCountedUp) 300 else 700, easing = FastOutSlowInEasing))
        settled = targetCentavos
        hasCountedUp = true
    }
    return if (progress.value >= 1f) targetCentavos
    else from + ((targetCentavos - from) * progress.value.toDouble()).toLong()
}
