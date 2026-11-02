package com.saannapunta.app.widget

import com.saannapunta.app.ui.chito.ChitoCompanion
import com.saannapunta.app.ui.chito.ChitoLines
import com.saannapunta.app.ui.components.PesoAmountTransformation
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.saannapunta.app.data.DailyHabitEngine
import com.saannapunta.app.data.DataRepository
import com.saannapunta.app.data.model.AccountType
import com.saannapunta.app.data.model.MoneyFormatter
import com.saannapunta.app.data.model.TransactionType
import com.saannapunta.app.parser.SmsMessageParser
import com.saannapunta.app.ui.components.SaanNaPuntaButton
import com.saannapunta.app.ui.theme.Green100
import com.saannapunta.app.ui.theme.Green50
import com.saannapunta.app.ui.theme.Green600
import com.saannapunta.app.ui.theme.Green700
import com.saannapunta.app.ui.theme.Ink
import com.saannapunta.app.ui.theme.InkFaint
import com.saannapunta.app.ui.theme.InkMuted
import com.saannapunta.app.ui.theme.LineBorder
import com.saannapunta.app.ui.theme.SaanNaPuntaTheme
import com.saannapunta.app.ui.theme.StatusWarn
import com.saannapunta.app.ui.theme.SurfaceColor

class QuickCashEntryActivity : FragmentActivity() {

    companion object {
        const val EXTRA_PRESET_AMOUNT_CENTAVOS = "extra_preset_amount_centavos"
        const val EXTRA_OPEN_KEYPAD = "extra_open_keypad"
    }

    private var presetAmountState by mutableLongStateOf(0L)
    private var openKeypadState by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readExtras(intent)

        setContent {
            val repository = remember { DataRepository.getInstance(applicationContext) }
            SaanNaPuntaTheme {
                QuickCashEntryBottomSheet(
                    repository = repository,
                    initialPresetCentavos = presetAmountState,
                    initialOpenKeypad = openKeypadState,
                    onSaved = { amountCentavos, category ->
                        Toast.makeText(
                            this,
                            "Logged ${MoneyFormatter.format(amountCentavos, false)} · $category",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readExtras(intent)
    }

    private fun readExtras(intent: Intent?) {
        presetAmountState = intent?.getLongExtra(EXTRA_PRESET_AMOUNT_CENTAVOS, 0L) ?: 0L
        openKeypadState = intent?.getBooleanExtra(EXTRA_OPEN_KEYPAD, false) ?: (presetAmountState <= 0L)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickCashEntryBottomSheet(
    repository: DataRepository,
    initialPresetCentavos: Long,
    initialOpenKeypad: Boolean,
    onSaved: (Long, String) -> Unit,
    onDismiss: () -> Unit
) {
    val accounts by repository.accounts.collectAsState()
    val transactions by repository.transactions.collectAsState()
    val budgets by repository.budgets.collectAsState()

    val recentCategories = remember(transactions, budgets) {
        DailyHabitEngine.getRecentExpenseCategories(transactions, budgets, limit = 4)
    }

    val cashAccount = remember(accounts) {
        DailyHabitEngine.findCashAccount(accounts)
    }

    var selectedAccountId by remember(cashAccount, accounts) {
        mutableStateOf(cashAccount?.id)
    }

    var showKeypadInput by remember(initialPresetCentavos, initialOpenKeypad) {
        mutableStateOf(initialOpenKeypad || initialPresetCentavos <= 0L)
    }

    var amountInput by remember(initialPresetCentavos) {
        val initialText = if (initialPresetCentavos > 0L) {
            val wholePesos = initialPresetCentavos / 100L
            val remCentavos = initialPresetCentavos % 100L
            if (remCentavos == 0L) wholePesos.toString() else "$wholePesos.${remCentavos.toString().padStart(2, '0')}"
        } else {
            ""
        }
        mutableStateOf(initialText)
    }

    val parsedCentavos = remember(amountInput) {
        SmsMessageParser.parseAmount(amountInput)
    }

    val suggestedCategory = remember(transactions, recentCategories, parsedCentavos) {
        if (parsedCentavos > 0L) {
            DailyHabitEngine.getMostFrequentCategoryForAmount(transactions, parsedCentavos, recentCategories)
        } else {
            recentCategories.firstOrNull() ?: "Food & Dining"
        }
    }

    val chipCategories = remember(recentCategories, suggestedCategory) {
        val list = recentCategories.toMutableList()
        if (list.none { it.equals(suggestedCategory, ignoreCase = true) }) {
            if (list.size >= 4) {
                list[list.lastIndex] = suggestedCategory
            } else {
                list.add(suggestedCategory)
            }
        }
        list
    }

    var selectedCategory by remember(suggestedCategory) {
        mutableStateOf(suggestedCategory)
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(showKeypadInput) {
        if (showKeypadInput) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    val possibleDuplicate = remember(selectedAccountId, parsedCentavos, transactions) {
        val accId = selectedAccountId
        if (accId != null && parsedCentavos > 0L) {
            repository.findSimilarAutoIngestedTransaction(
                accountId = accId,
                amountCentavos = parsedCentavos,
                type = TransactionType.EXPENSE
            )
        } else null
    }

    val backdropInteraction = remember { MutableInteractionSource() }
    val sheetInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = backdropInteraction,
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = sheetInteraction,
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = SurfaceColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .navigationBarsPadding()
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Drag Handle
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(LineBorder)
                        .align(Alignment.CenterHorizontally)
                )

                // Title + Default Account Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Fast cash entry",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Ink
                        )
                        val activeAccName = accounts.firstOrNull { it.id == selectedAccountId }?.name
                            ?: cashAccount?.name
                            ?: "Cash on Hand"
                        Text(
                            text = "Account: $activeAccName · Manual entry",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkMuted
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = InkMuted, style = MaterialTheme.typography.labelMedium)
                    }
                }

                ChitoCompanion(line = ChitoLines.quickCash(parsedCentavos), size = 48.dp)

                // Quick Preset Row (₱20, ₱50, ₱100, Keypad)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DailyHabitEngine.PRESET_AMOUNTS_CENTAVOS.forEach { preset ->
                        val isChosen = !showKeypadInput && parsedCentavos == preset
                        val label = MoneyFormatter.format(preset, false)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isChosen) Green100 else Green50)
                                .border(
                                    1.dp,
                                    if (isChosen) Green600 else LineBorder,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    showKeypadInput = false
                                    amountInput = (preset / 100L).toString()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isChosen) Green700 else Ink
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (showKeypadInput) Green100 else Green50)
                            .border(
                                1.dp,
                                if (showKeypadInput) Green600 else LineBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                showKeypadInput = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Keypad",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (showKeypadInput) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (showKeypadInput) Green700 else Ink
                        )
                    }
                }

                // Amount Input or Hero Display
                if (showKeypadInput) {
                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { input ->
                            if (input.matches(Regex("^\\d*(\\.\\d{0,2})?$"))) {
                                amountInput = input
                            }
                        },
                        placeholder = {
                            Text(
                                "0.00",
                                style = MaterialTheme.typography.headlineMedium.copy(color = InkFaint)
                            )
                        },
                        prefix = {
                            Text(
                                "₱ ",
                                style = MaterialTheme.typography.headlineMedium.copy(color = Ink)
                            )
                        },
                        textStyle = MaterialTheme.typography.headlineMedium.copy(
                            color = Ink,
                            fontWeight = FontWeight.Bold
                        ),
                        visualTransformation = PesoAmountTransformation,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceColor,
                            unfocusedContainerColor = SurfaceColor,
                            focusedBorderColor = Green600,
                            unfocusedBorderColor = LineBorder
                        )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Green50)
                            .border(1.dp, LineBorder, RoundedCornerShape(14.dp))
                            .clickable { showKeypadInput = true }
                            .padding(vertical = 10.dp, horizontal = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = MoneyFormatter.format(parsedCentavos, false),
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 26.sp
                                ),
                                color = Ink
                            )
                            Text(
                                text = "Tap to edit",
                                style = MaterialTheme.typography.labelSmall,
                                color = InkMuted
                            )
                        }
                    }
                }

                // Category Chips (User's 4 most recent)
                Column {
                    Text(
                        text = "Category (recent)",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        chipCategories.forEach { cat ->
                            val isSelected = selectedCategory.equals(cat, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isSelected) Green100 else SurfaceColor)
                                    .border(
                                        1.dp,
                                        if (isSelected) Green600 else LineBorder,
                                        CircleShape
                                    )
                                    .clickable { selectedCategory = cat }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = cat,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) Green700 else Ink
                                )
                            }
                        }
                    }
                }

                // Optional Spendable Account Switcher (defaults to Cash)
                val spendableAccounts = remember(accounts) {
                    accounts.filter { it.isSpendable && it.type != AccountType.LIABILITY }
                }
                if (spendableAccounts.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Account:",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkMuted
                        )
                        spendableAccounts.take(3).forEach { acc ->
                            val isSelected = selectedAccountId == acc.id
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isSelected) Green100 else SurfaceColor)
                                    .border(1.dp, if (isSelected) Green600 else LineBorder, CircleShape)
                                    .clickable { selectedAccountId = acc.id }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = acc.name,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    color = if (isSelected) Green700 else InkMuted
                                )
                            }
                        }
                    }
                }

                // Duplicate Warning against Auto-Captured Items
                if (possibleDuplicate != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                        border = BorderStroke(1.dp, StatusWarn)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Possible duplicate detected",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = StatusWarn
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${possibleDuplicate.title} (${possibleDuplicate.sourceInfo}) for ${MoneyFormatter.format(possibleDuplicate.amountCentavos)} was already recorded on this account.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Ink
                            )
                        }
                    }
                }

                // Save Button
                val saveLabel = when {
                    possibleDuplicate != null -> "Save anyway"
                    parsedCentavos > 0L -> "Save ${MoneyFormatter.format(parsedCentavos, false)}"
                    else -> "Save expense"
                }

                SaanNaPuntaButton(
                    text = saveLabel,
                    enabled = parsedCentavos > 0L,
                    onClick = {
                        repository.recordQuickCashExpense(
                            amountCentavos = parsedCentavos,
                            category = selectedCategory,
                            accountId = selectedAccountId,
                            onCreated = { _, resolvedCat, _ ->
                                onSaved(parsedCentavos, resolvedCat)
                            }
                        )
                    }
                )
            }
        }
    }
}

