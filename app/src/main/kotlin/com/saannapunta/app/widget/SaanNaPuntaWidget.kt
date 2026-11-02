package com.saannapunta.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.saannapunta.app.MainActivity
import com.saannapunta.app.data.DataRepository
import com.saannapunta.app.data.model.MoneyFormatter
import com.saannapunta.app.data.model.PayCycleType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object SaanNaPuntaWidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun requestUpdate(context: Context) {
        val appCtx = context.applicationContext
        scope.launch {
            runCatching {
                SaanNaPuntaWidget().updateAll(appCtx)
            }
        }
    }
}

class SaanNaPuntaWidget : GlanceAppWidget() {

    companion object {
        private val SMALL_SIZE = DpSize(110.dp, 80.dp)
        private val MEDIUM_SIZE = DpSize(230.dp, 110.dp)

        private val CardSurface = ColorProvider(Color(0xFFFFFFFF))
        private val PillGreen = ColorProvider(Color(0xFFE6F4EC))
        private val PrimaryGreen = ColorProvider(Color(0xFF0E7C47))
        private val InkPrimary = ColorProvider(Color(0xFF102119))
        private val InkMutedColor = ColorProvider(Color(0xFF5A6D63))
        private val WarnRed = ColorProvider(Color(0xFFC93B2B))
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SMALL_SIZE, MEDIUM_SIZE)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = DataRepository.getInstance(context.applicationContext)

        provideContent {
            val overview by repository.overview.collectAsState()
            val payCycleConfig by repository.payCycleConfig.collectAsState()
            val biometricEnabled by repository.biometricLockEnabled.collectAsState()
            val alwaysHideAmounts by repository.widgetHideAmounts.collectAsState()
            val oneTapMode by repository.widgetOneTapMode.collectAsState()

            val hideAmount = biometricEnabled || alwaysHideAmounts
            val size = LocalSize.current
            val isMedium = size.width >= MEDIUM_SIZE.width

            GlanceTheme {
                WidgetContent(
                    context = context,
                    leftTodayCentavos = overview.leftTodayCentavos,
                    isOverBudget = overview.isOverBudget,
                    daysLeftInCycle = overview.daysLeftInCycle,
                    nextPaydayText = overview.nextPaydayText,
                    cycleType = payCycleConfig.type,
                    hideAmount = hideAmount,
                    oneTapMode = oneTapMode,
                    showQuickAddRow = isMedium
                )
            }
        }
    }

    @Composable
    private fun WidgetContent(
        context: Context,
        leftTodayCentavos: Long,
        isOverBudget: Boolean,
        daysLeftInCycle: Int,
        nextPaydayText: String,
        cycleType: PayCycleType,
        hideAmount: Boolean,
        oneTapMode: Boolean,
        showQuickAddRow: Boolean
    ) {
        val openHomeIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val countdownLabel = when (cycleType) {
            PayCycleType.FLEXIBLE -> "${daysLeftInCycle}d left · Resets $nextPaydayText"
            PayCycleType.WEEKLY -> "${daysLeftInCycle}d left · Ends $nextPaydayText"
            else -> "${daysLeftInCycle}d left · Payday $nextPaydayText"
        }

        val amountText = if (hideAmount) {
            "Tap to view"
        } else {
            MoneyFormatter.format(leftTodayCentavos, false)
        }

        val amountColor = when {
            hideAmount -> InkMutedColor
            isOverBudget || leftTodayCentavos == 0L -> WarnRed
            else -> PrimaryGreen
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(CardSurface)
                .cornerRadius(20.dp)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Top section: "Left today" + cycle countdown (tap opens app / unlocks)
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity(openHomeIntent))
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Left today",
                        style = TextStyle(
                            color = InkMutedColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = countdownLabel,
                        style = TextStyle(
                            color = InkMutedColor,
                            fontSize = 11.sp
                        )
                    )
                }

                Spacer(modifier = GlanceModifier.height(4.dp))

                Text(
                    text = amountText,
                    style = TextStyle(
                        color = amountColor,
                        fontSize = if (hideAmount) 18.sp else 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            if (showQuickAddRow) {
                Spacer(modifier = GlanceModifier.height(10.dp))

                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PresetButton(
                        context = context,
                        label = "+₱20",
                        amountCentavos = 20_00L,
                        oneTapMode = oneTapMode,
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    PresetButton(
                        context = context,
                        label = "+₱50",
                        amountCentavos = 50_00L,
                        oneTapMode = oneTapMode,
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    PresetButton(
                        context = context,
                        label = "+₱100",
                        amountCentavos = 100_00L,
                        oneTapMode = oneTapMode,
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    KeypadButton(
                        context = context,
                        modifier = GlanceModifier.defaultWeight()
                    )
                }
            }
        }
    }

    @Composable
    private fun PresetButton(
        context: Context,
        label: String,
        amountCentavos: Long,
        oneTapMode: Boolean,
        modifier: GlanceModifier
    ) {
        val clickAction = if (oneTapMode) {
            val broadcastIntent = Intent(context, QuickCashActionReceiver::class.java).apply {
                action = QuickCashActionReceiver.ACTION_ONE_TAP_ADD
                putExtra(QuickCashEntryActivity.EXTRA_PRESET_AMOUNT_CENTAVOS, amountCentavos)
            }
            actionSendBroadcast(broadcastIntent)
        } else {
            val sheetIntent = Intent(context, QuickCashEntryActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(QuickCashEntryActivity.EXTRA_PRESET_AMOUNT_CENTAVOS, amountCentavos)
                putExtra(QuickCashEntryActivity.EXTRA_OPEN_KEYPAD, false)
            }
            actionStartActivity(sheetIntent)
        }

        Box(
            modifier = modifier
                .height(34.dp)
                .background(PillGreen)
                .cornerRadius(12.dp)
                .clickable(clickAction),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = TextStyle(
                    color = PrimaryGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }

    @Composable
    private fun KeypadButton(
        context: Context,
        modifier: GlanceModifier
    ) {
        val keypadIntent = Intent(context, QuickCashEntryActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(QuickCashEntryActivity.EXTRA_PRESET_AMOUNT_CENTAVOS, 0L)
            putExtra(QuickCashEntryActivity.EXTRA_OPEN_KEYPAD, true)
        }

        Box(
            modifier = modifier
                .height(34.dp)
                .background(PillGreen)
                .cornerRadius(12.dp)
                .clickable(actionStartActivity(keypadIntent)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Keypad",
                style = TextStyle(
                    color = InkPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

class SaanNaPuntaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SaanNaPuntaWidget()
}

