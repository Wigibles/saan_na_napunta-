package com.saannapunta.app.ui.sheets

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.saannapunta.app.ui.components.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.text.style.TextAlign
import com.saannapunta.app.ui.chito.ChitoReactions
import com.saannapunta.app.ui.chito.ChitoCompanion
import com.saannapunta.app.ui.chito.ChitoLines
import com.saannapunta.app.ui.components.PesoAmountTransformation
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saannapunta.app.data.DataRepository
import com.saannapunta.app.ui.feedback.rememberUiFeedback
import com.saannapunta.app.data.model.TransactionItem
import com.saannapunta.app.data.model.TransactionType
import com.saannapunta.app.ui.components.AccountLogoTile
import com.saannapunta.app.ui.components.SaanNaPuntaButton
import com.saannapunta.app.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionSheet(
    repository: DataRepository,
    initialType: TransactionType = TransactionType.EXPENSE,
    initialTargetAccountId: Long? = null,
    editingTransaction: TransactionItem? = null,
    onDismiss: () -> Unit
) {
    val accounts by repository.accounts.collectAsState()
    val budgets by repository.budgets.collectAsState()
    val overview by repository.overview.collectAsState()
    val feedback = rememberUiFeedback()
    // New transactions open with the amount ready to type
    val amountFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (editingTransaction == null) {
            kotlinx.coroutines.delay(250)
            runCatching { amountFocus.requestFocus() }
        }
    }

    var transactionType by remember(editingTransaction, initialType) {
        mutableStateOf(editingTransaction?.type ?: initialType)
    }
    var amountInput by remember(editingTransaction) {
        mutableStateOf(
            if (editingTransaction != null) {
                val whole = editingTransaction.amountCentavos / 100L
                val rem = editingTransaction.amountCentavos % 100L
                if (rem == 0L) {
                    whole.toString()
                } else {
                    String.format(Locale.US, "%d.%02d", whole, rem)
                }
            } else ""
        )
    }
    var titleInput by remember(editingTransaction) {
        mutableStateOf(editingTransaction?.title ?: "")
    }
    var noteInput by remember(editingTransaction) {
        mutableStateOf(editingTransaction?.note ?: "")
    }

    val incomeCategoryNamesLower = remember {
        setOf(
            "salary", "income", "salary / payroll", "allowance / baon",
            "freelance / side hustle", "business / sales", "bonus / 13th month",
            "remittance / padala", "gift / pamasko", "refund / cashback",
            "interest / savings", "other income"
        )
    }

    val expenseCategories = remember(budgets, editingTransaction) {
        val base = budgets.map { it.category }
            .filter { it.lowercase(Locale.ROOT) !in incomeCategoryNamesLower }
            .toMutableList()
        val defaultExpenseCats = listOf(
            "Food & Dining",
            "Groceries",
            "Utilities",
            "Transportation",
            "Personal",
            "Shopping",
            "Load & Data",
            "Rent & Housing",
            "Health & Medical",
            "Education & School",
            "Entertainment",
            "Subscriptions",
            "Family & Pets",
            "Other Expense"
        )
        for (defaultCat in defaultExpenseCats) {
            if (base.none { it.equals(defaultCat, ignoreCase = true) }) {
                base.add(defaultCat)
            }
        }
        if (editingTransaction?.type == TransactionType.EXPENSE &&
            editingTransaction.category.isNotBlank() &&
            base.none { it.equals(editingTransaction.category, ignoreCase = true) }
        ) {
            base.add(0, editingTransaction.category)
        }
        base
    }

    val incomeCategories = remember(editingTransaction) {
        val base = mutableListOf(
            "Salary / Payroll",
            "Allowance / Baon",
            "Freelance / Side Hustle",
            "Business / Sales",
            "Bonus / 13th Month",
            "Remittance / Padala",
            "Gift / Pamasko",
            "Refund / Cashback",
            "Interest / Savings",
            "Other Income"
        )
        if (editingTransaction?.type == TransactionType.INCOME &&
            editingTransaction.category.isNotBlank() &&
            base.none { it.equals(editingTransaction.category, ignoreCase = true) }
        ) {
            base.add(0, editingTransaction.category)
        }
        base
    }

    val activeCategories = if (transactionType == TransactionType.INCOME) {
        incomeCategories
    } else {
        expenseCategories
    }

    var selectedCategory by remember(editingTransaction, initialType) {
        val initialCat = editingTransaction?.category ?: if (initialType == TransactionType.INCOME) {
            incomeCategories.first()
        } else {
            expenseCategories.firstOrNull() ?: "Food & Dining"
        }
        mutableStateOf(initialCat)
    }

    var isCustomCategory by remember(editingTransaction) { mutableStateOf(false) }
    var customCategoryInput by remember(editingTransaction) { mutableStateOf("") }
    var selectedSubCategory by remember(editingTransaction, selectedCategory) {
        mutableStateOf(editingTransaction?.subCategory ?: "")
    }

    var selectedAccountId by remember(editingTransaction, accounts, initialTargetAccountId) {
        mutableStateOf(
            editingTransaction?.accountId
                ?: accounts.firstOrNull { it.id != initialTargetAccountId && it.isSpendable }?.id
                ?: accounts.firstOrNull()?.id
                ?: 1L
        )
    }
    var selectedTargetAccountId by remember(editingTransaction, accounts, initialTargetAccountId) {
        mutableStateOf(
            editingTransaction?.targetAccountId
                ?: initialTargetAccountId
                ?: accounts.firstOrNull { it.id != selectedAccountId }?.id
                ?: accounts.firstOrNull()?.id
                ?: 2L
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurfaceColor,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = LineBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (editingTransaction != null) "Review transaction" else "Add transaction",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Ink
                )

                if (editingTransaction != null && editingTransaction.sourceInfo != "Manual") {
                    Box(
                        modifier = Modifier
                            .background(Green50, CircleShape)
                            .border(1.dp, LineBorder, CircleShape)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = editingTransaction.sourceInfo,
                            style = MaterialTheme.typography.labelSmall,
                            color = Green700
                        )
                    }
                }
            }

            // Type selector: each type has its own color
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(Canvas)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    TransactionType.EXPENSE to "Expense",
                    TransactionType.INCOME to "Income",
                    TransactionType.TRANSFER to "Transfer"
                ).forEach { (type, label) ->
                    val isSelected = transactionType == type
                    val bg by animateColorAsState(if (isSelected) typeColor(type) else Canvas, tween(180), label = "TypeBg")
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(CircleShape)
                            .background(bg)
                            .clickable {
                                if (!isSelected) feedback.select()
                                transactionType = type
                                isCustomCategory = false
                                selectedSubCategory = ""
                                when (type) {
                                    TransactionType.INCOME -> {
                                        if (incomeCategories.none { it.equals(selectedCategory, ignoreCase = true) }) {
                                            selectedCategory = incomeCategories.first()
                                        }
                                    }
                                    TransactionType.EXPENSE -> {
                                        if (expenseCategories.none { it.equals(selectedCategory, ignoreCase = true) }) {
                                            selectedCategory = expenseCategories.firstOrNull() ?: "Food & Dining"
                                        }
                                    }
                                    TransactionType.TRANSFER -> Unit
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isSelected) SurfaceColor else InkMuted
                        )
                    }
                }
            }

            // Chito reacts to the amount as it's typed
            ChitoCompanion(
                line = ChitoLines.addTransaction(
                    type = transactionType,
                    amountCentavos = com.saannapunta.app.parser.SmsMessageParser.parseAmount(amountInput),
                    leftTodayCentavos = overview.leftTodayCentavos,
                    isEditing = editingTransaction != null
                ),
                size = 56.dp
            )

            // Amount: the hero of the sheet
            val accent = typeColor(transactionType)
            val amountBg by animateColorAsState(accent.copy(alpha = 0.08f), tween(200), label = "AmountBg")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(amountBg)
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when (transactionType) {
                        TransactionType.EXPENSE -> "How much did you spend?"
                        TransactionType.INCOME -> "How much came in?"
                        TransactionType.TRANSFER -> "How much are you moving?"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = accent
                )
                // ₱ and the number stay centered together; the field grows with the amount
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { amountFocus.requestFocus() }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "₱",
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold, color = accent)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    BasicTextField(
                        value = amountInput,
                        onValueChange = { input ->
                            if (input.matches(Regex("^\\d*(\\.\\d{0,2})?$"))) {
                                amountInput = input
                            }
                        },
                        textStyle = MaterialTheme.typography.displayLarge.copy(fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Ink),
                        visualTransformation = PesoAmountTransformation,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(accent),
                        modifier = Modifier
                            .width(IntrinsicSize.Min)
                            .widthIn(min = 28.dp)
                            .focusRequester(amountFocus),
                        decorationBox = { inner ->
                            Box {
                                if (amountInput.isEmpty()) {
                                    Text(
                                        "0",
                                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 44.sp, fontWeight = FontWeight.Bold, color = InkFaint)
                                    )
                                }
                                inner()
                            }
                        }
                    )
                }
            }

            // Title / Merchant / Source
            Column {
                val fieldLabel = when (transactionType) {
                    TransactionType.EXPENSE -> "What did you spend on?"
                    TransactionType.INCOME -> "Income source / Description"
                    TransactionType.TRANSFER -> "Transfer description"
                }
                val fieldPlaceholder = when (transactionType) {
                    TransactionType.EXPENSE -> "e.g. Jollibee, Meralco, Grab, Puregold"
                    TransactionType.INCOME -> "e.g. Monthly salary, Client payout, Weekly baon"
                    TransactionType.TRANSFER -> "e.g. Cash in to GCash, ATM withdrawal"
                }
                Text(
                    text = fieldLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    placeholder = {
                        Text(
                            text = fieldPlaceholder,
                            color = InkFaint,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceColor,
                        unfocusedContainerColor = Canvas,
                        focusedBorderColor = Green600,
                        unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent
                    ),
                    singleLine = true
                )
            }

            // Account Selector
            Column {
                val accountLabel = when (transactionType) {
                    TransactionType.EXPENSE -> "Paid from account"
                    TransactionType.INCOME -> "Deposit to account"
                    TransactionType.TRANSFER -> "From account"
                }
                Text(
                    text = accountLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(accounts) { acc ->
                        val isSelected = selectedAccountId == acc.id
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) accent.copy(alpha = 0.10f) else Canvas)
                                .border(if (isSelected) 1.5.dp else 1.dp, if (isSelected) accent else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(16.dp))
                                .clickable {
                                    feedback.select()
                                    selectedAccountId = acc.id
                                    if (selectedTargetAccountId == acc.id) {
                                        selectedTargetAccountId = accounts.firstOrNull { it.id != acc.id }?.id ?: acc.id
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AccountLogoTile(iconResName = acc.iconResName, fallbackName = acc.name, size = 30)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = acc.name,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium),
                                color = Ink
                            )
                        }
                    }
                }
            }

            // Target Account (For Transfer) vs Aligned Category Selector
            if (transactionType == TransactionType.TRANSFER) {
                Column {
                    Text(
                        text = "To account",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(accounts.filter { it.id != selectedAccountId }) { acc ->
                            val isSelected = selectedTargetAccountId == acc.id
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isSelected) accent.copy(alpha = 0.10f) else Canvas)
                                    .border(if (isSelected) 1.5.dp else 1.dp, if (isSelected) accent else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(16.dp))
                                    .clickable {
                                        feedback.select()
                                        selectedTargetAccountId = acc.id
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AccountLogoTile(iconResName = acc.iconResName, fallbackName = acc.name, size = 30)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = acc.name,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium),
                                    color = Ink
                                )
                            }
                        }
                    }
                }
            } else {
                // Aligned Category Selector for Expense vs Income
                Column {
                    val categoryLabel = if (transactionType == TransactionType.INCOME) {
                        "Income category"
                    } else {
                        "Expense category"
                    }
                    Text(
                        text = categoryLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(activeCategories) { cat ->
                            val isSelected = !isCustomCategory && selectedCategory.equals(cat, ignoreCase = true)
                            val catColor = if (transactionType == TransactionType.INCOME) Green600 else categoryColor(cat)
                            Row(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isSelected) catColor.copy(alpha = 0.14f) else Canvas)
                                    .border(if (isSelected) 1.5.dp else 1.dp, if (isSelected) catColor else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
                                    .clickable {
                                        feedback.select()
                                        isCustomCategory = false
                                        selectedCategory = cat
                                        selectedSubCategory = ""
                                    }
                                    .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CategoryIconChip(
                                    category = cat,
                                    type = if (transactionType == TransactionType.INCOME) TransactionType.INCOME else TransactionType.EXPENSE,
                                    size = 28.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = cat,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
                                    color = Ink
                                )
                            }
                        }
                        item {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isCustomCategory) Green100 else Canvas)
                                    .border(1.dp, if (isCustomCategory) Green600 else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
                                    .clickable { isCustomCategory = true }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "+ Custom",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isCustomCategory) FontWeight.SemiBold else FontWeight.Medium),
                                    color = if (isCustomCategory) Green700 else Green600
                                )
                            }
                        }
                    }

                    if (isCustomCategory) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customCategoryInput,
                            onValueChange = { customCategoryInput = it },
                            placeholder = {
                                Text(
                                    text = if (transactionType == TransactionType.INCOME) "Enter custom income category" else "Enter custom expense category",
                                    color = InkFaint,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SurfaceColor,
                                unfocusedContainerColor = Canvas,
                                focusedBorderColor = Green600,
                                unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent
                            ),
                            singleLine = true
                        )
                    }

                    // Optional Subcategory Selector
                    val availableSubCategories = remember(selectedCategory, budgets) {
                        repository.getSubCategoriesForCategory(selectedCategory)
                    }

                    if (availableSubCategories.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Subcategory (Optional)",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkMuted
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                val noneSelected = selectedSubCategory.isBlank()
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (noneSelected) Green100 else Canvas)
                                        .border(1.dp, if (noneSelected) Green600 else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
                                        .clickable { selectedSubCategory = "" }
                                        .padding(horizontal = 12.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "None",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (noneSelected) FontWeight.Bold else FontWeight.Normal),
                                        color = if (noneSelected) Green700 else InkMuted
                                    )
                                }
                            }
                            items(availableSubCategories) { sub ->
                                val isSel = selectedSubCategory.equals(sub, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (isSel) Green100 else Canvas)
                                        .border(1.dp, if (isSel) Green600 else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
                                        .clickable {
                                            selectedSubCategory = sub
                                            if (titleInput.isBlank()) {
                                                titleInput = sub
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = sub,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal),
                                        color = if (isSel) Green700 else Ink
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Note (Optional)
            OutlinedTextField(
                value = noteInput,
                onValueChange = { noteInput = it },
                placeholder = { Text("Note (optional)", color = InkFaint, style = MaterialTheme.typography.bodyMedium) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceColor,
                    unfocusedContainerColor = Canvas,
                    focusedBorderColor = Green600,
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(4.dp))

            val resolvedCategory = when {
                transactionType == TransactionType.TRANSFER -> "Transfer"
                isCustomCategory && customCategoryInput.isNotBlank() -> customCategoryInput.trim()
                else -> selectedCategory
            }

            val parsedCentavos = remember(amountInput) {
                com.saannapunta.app.parser.SmsMessageParser.parseAmount(amountInput)
            }

            val possibleDuplicate = remember(editingTransaction, selectedAccountId, parsedCentavos, transactionType) {
                if (editingTransaction == null && parsedCentavos > 0L) {
                    repository.findSimilarAutoIngestedTransaction(
                        accountId = selectedAccountId,
                        amountCentavos = parsedCentavos,
                        type = transactionType
                    )
                } else null
            }

            if (possibleDuplicate != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                    border = BorderStroke(1.dp, StatusWarn)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Possible duplicate detected",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = StatusWarn
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${possibleDuplicate.title} (${possibleDuplicate.sourceInfo}) for ${com.saannapunta.app.data.model.MoneyFormatter.format(possibleDuplicate.amountCentavos)} was already recorded on this account.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Ink
                        )
                    }
                }
            }

            // Primary Save Button
            SaanNaPuntaButton(
                text = if (possibleDuplicate != null) "Save anyway" else "Save transaction",
                enabled = amountInput.isNotBlank() &&
                    parsedCentavos > 0L &&
                    (!isCustomCategory || customCategoryInput.isNotBlank()),
                onClick = {
                    val centavos = parsedCentavos
                    val title = titleInput.trim().ifBlank {
                        when (transactionType) {
                            TransactionType.TRANSFER -> "Transfer"
                            TransactionType.INCOME -> resolvedCategory
                            TransactionType.EXPENSE -> resolvedCategory
                        }
                    }

                    val newItem = TransactionItem(
                        id = editingTransaction?.id ?: 0L,
                        title = title,
                        amountCentavos = centavos,
                        type = transactionType,
                        accountId = selectedAccountId,
                        targetAccountId = if (transactionType == TransactionType.TRANSFER) selectedTargetAccountId else null,
                        category = resolvedCategory,
                        subCategory = if (transactionType == TransactionType.TRANSFER) null else selectedSubCategory.ifBlank { null },
                        timestamp = editingTransaction?.timestamp ?: System.currentTimeMillis(),
                        note = noteInput.ifBlank { null },
                        sourceInfo = editingTransaction?.sourceInfo ?: "Manual"
                    )

                    if (editingTransaction != null) {
                        repository.updateTransaction(editingTransaction, newItem)
                    } else {
                        repository.addTransaction(newItem)
                    }
                    feedback.success()
                    ChitoReactions.react(ChitoLines.saved(transactionType, centavos, overview, editingTransaction != null))
                    onDismiss()
                }
            )

            if (editingTransaction != null) {
                OutlinedButton(
                    onClick = {
                        feedback.delete()
                        ChitoReactions.react(ChitoLines.deleted())
                        repository.deleteTransaction(editingTransaction)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, LineBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusOver)
                ) {
                    Text(
                        text = "Delete transaction",
                        style = MaterialTheme.typography.labelLarge,
                        color = StatusOver
                    )
                }
            }
        }
    }
}

private fun editingBillAmountCentavos(amount: Long): Long = amount / 100L

/** Expense orange, income green, transfer blue: used for the sheet's selector, amount and chips. */
private fun typeColor(type: TransactionType): androidx.compose.ui.graphics.Color = when (type) {
    TransactionType.EXPENSE -> androidx.compose.ui.graphics.Color(0xFFE8833A)
    TransactionType.INCOME -> Green600
    TransactionType.TRANSFER -> androidx.compose.ui.graphics.Color(0xFF2F80ED)
}
