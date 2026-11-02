package com.saannapunta.app.ui.sheets

import com.saannapunta.app.ui.chito.ChitoReactions
import com.saannapunta.app.ui.feedback.rememberUiFeedback
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.saannapunta.app.data.DataRepository
import com.saannapunta.app.data.model.CategoryBudget
import com.saannapunta.app.data.model.SavingsGoal
import com.saannapunta.app.ui.components.SaanNaPuntaButton
import com.saannapunta.app.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCategoryBudgetSheet(
    repository: DataRepository,
    editingBudget: CategoryBudget? = null,
    onDismiss: () -> Unit
) {
    val existingBudgets by repository.budgets.collectAsState()

    var categoryName by remember(editingBudget) {
        mutableStateOf(editingBudget?.category ?: "")
    }
    var limitInput by remember(editingBudget) {
        mutableStateOf(
            if (editingBudget != null) {
                val pesos = editingBudget.limitCentavos / 100.0
                if (editingBudget.limitCentavos % 100L == 0L) {
                    (editingBudget.limitCentavos / 100L).toString()
                } else {
                    String.format(Locale.US, "%.2f", pesos)
                }
            } else ""
        )
    }
    var examplesInput by remember(editingBudget) {
        mutableStateOf(editingBudget?.examples ?: "")
    }

    val suggestedExpenseCategories = remember(existingBudgets, editingBudget) {
        val presets = listOf(
            "Food & Dining" to "Fast food, coffee, dining out",
            "Groceries" to "Supermarket, wet market, pantry",
            "Utilities" to "Meralco, water, internet",
            "Transportation" to "Grab, Angkas, gas, commute",
            "Personal" to "Self-care, Watsons, toiletries",
            "Shopping" to "Shopee, Lazada, clothing",
            "Load & Data" to "Prepaid load, mobile data",
            "Rent & Housing" to "Rent, condo dues, maintenance",
            "Health & Medical" to "Medicine, clinic, gym",
            "Education & School" to "Tuition, books, school supplies",
            "Entertainment" to "Movies, games, hangouts",
            "Subscriptions" to "Netflix, Spotify, cloud storage",
            "Family & Pets" to "Allowance, pet food, family support"
        )
        presets.filter { (name, _) ->
            editingBudget?.category?.equals(name, ignoreCase = true) == true ||
                existingBudgets.none { it.category.equals(name, ignoreCase = true) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurfaceColor,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = LineBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = if (editingBudget != null) "Edit category budget" else "Add category budget",
                style = MaterialTheme.typography.titleMedium,
                color = Ink
            )

            ChitoCompanion(
                line = ChitoLines.categoryBudget(com.saannapunta.app.parser.SmsMessageParser.parseAmount(limitInput)),
                size = 52.dp
            )

            if (editingBudget == null && suggestedExpenseCategories.isNotEmpty()) {
                Column {
                    Text(
                        text = "Quick pick expense category",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(suggestedExpenseCategories) { (presetName, presetExamples) ->
                            val isSelected = categoryName.equals(presetName, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isSelected) Green100 else SurfaceColor)
                                    .border(1.dp, if (isSelected) Green600 else LineBorder, CircleShape)
                                    .clickable {
                                        categoryName = presetName
                                        if (examplesInput.isBlank()) {
                                            examplesInput = presetExamples
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = presetName,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    color = if (isSelected) Green700 else Ink
                                )
                            }
                        }
                    }
                }
            }

            Column {
                Text("Category name", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = categoryName,
                    onValueChange = { categoryName = it },
                    placeholder = { Text("e.g. Food & Dining, Groceries, Utilities", color = InkFaint) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceColor,
                        unfocusedContainerColor = SurfaceColor,
                        focusedBorderColor = Green600,
                        unfocusedBorderColor = LineBorder
                    ),
                    singleLine = true
                )
            }

            Column {
                Text("Budget limit", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = limitInput,
                    onValueChange = { input ->
                        if (input.matches(Regex("^\\d*(\\.\\d{0,2})?$"))) {
                            limitInput = input
                        }
                    },
                    placeholder = { Text("0.00", color = InkFaint) },
                    prefix = { Text("₱ ", color = Ink) },
                    visualTransformation = PesoAmountTransformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceColor,
                        unfocusedContainerColor = SurfaceColor,
                        focusedBorderColor = Green600,
                        unfocusedBorderColor = LineBorder
                    ),
                    singleLine = true
                )
            }

            Column {
                Text("Examples (optional)", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = examplesInput,
                    onValueChange = { examplesInput = it },
                    placeholder = { Text("e.g. Fast food, coffee, supermarket", color = InkFaint) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceColor,
                        unfocusedContainerColor = SurfaceColor,
                        focusedBorderColor = Green600,
                        unfocusedBorderColor = LineBorder
                    ),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            SaanNaPuntaButton(
                text = "Save budget",
                enabled = categoryName.isNotBlank() && limitInput.isNotBlank() && (limitInput.toDoubleOrNull() ?: 0.0) > 0.0,
                onClick = {
                    val limitPesos = limitInput.toDoubleOrNull() ?: 0.0
                    val centavos = Math.round(limitPesos * 100.0)
                    repository.addCategoryBudget(
                        CategoryBudget(
                            id = editingBudget?.id ?: 0L,
                            category = categoryName.trim(),
                            limitCentavos = centavos,
                            examples = examplesInput.trim()
                        )
                    )
                    onDismiss()
                }
            )

            if (editingBudget != null) {
                OutlinedButton(
                    onClick = {
                        repository.deleteCategoryBudget(editingBudget.id)
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
                        text = "Delete budget",
                        style = MaterialTheme.typography.labelLarge,
                        color = StatusOver
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSavingsGoalSheet(
    repository: DataRepository,
    editingGoal: SavingsGoal? = null,
    onDismiss: () -> Unit
) {
    val goalFeedback = rememberUiFeedback()
    var goalName by remember(editingGoal) {
        mutableStateOf(editingGoal?.name ?: "")
    }
    var targetInput by remember(editingGoal) {
        mutableStateOf(
            if (editingGoal != null) {
                (editingGoal.targetCentavos / 100L).toString()
            } else ""
        )
    }
    var savedInput by remember(editingGoal) {
        mutableStateOf(
            if (editingGoal != null) {
                (editingGoal.savedCentavos / 100L).toString()
            } else "0"
        )
    }
    val accounts by repository.accounts.collectAsState()
    val spendableAccounts = remember(accounts) {
        accounts.filter { it.isSpendable && it.type != com.saannapunta.app.data.model.AccountType.LIABILITY }
    }
    var quickDepositInput by remember(editingGoal) { mutableStateOf("") }
    var deductFromAccountId by remember(spendableAccounts) {
        mutableStateOf<Long?>(spendableAccounts.firstOrNull()?.id)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurfaceColor,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = LineBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (editingGoal != null) "Edit savings goal" else "Add savings goal",
                style = MaterialTheme.typography.titleMedium,
                color = Ink
            )

            ChitoCompanion(
                line = ChitoLines.savingsGoal,
                size = 52.dp
            )

            Column {
                Text("Goal name", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = goalName,
                    onValueChange = { goalName = it },
                    placeholder = { Text("e.g. Emergency Fund, Ipon Jar", color = InkFaint) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceColor,
                        unfocusedContainerColor = SurfaceColor,
                        focusedBorderColor = Green600,
                        unfocusedBorderColor = LineBorder
                    ),
                    singleLine = true
                )
            }

            Column {
                Text("Target amount", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = targetInput,
                    onValueChange = { input ->
                        if (input.matches(Regex("^\\d*(\\.\\d{0,2})?$"))) {
                            targetInput = input
                        }
                    },
                    placeholder = { Text("4000", color = InkFaint) },
                    prefix = { Text("₱ ", color = Ink) },
                    visualTransformation = PesoAmountTransformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceColor,
                        unfocusedContainerColor = SurfaceColor,
                        focusedBorderColor = Green600,
                        unfocusedBorderColor = LineBorder
                    ),
                    singleLine = true
                )
            }

            Column {
                Text("Saved so far", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = savedInput,
                    onValueChange = { input ->
                        if (input.matches(Regex("^\\d*(\\.\\d{0,2})?$"))) {
                            savedInput = input
                        }
                    },
                    placeholder = { Text("0", color = InkFaint) },
                    prefix = { Text("₱ ", color = Ink) },
                    visualTransformation = PesoAmountTransformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceColor,
                        unfocusedContainerColor = SurfaceColor,
                        focusedBorderColor = Green600,
                        unfocusedBorderColor = LineBorder
                    ),
                    singleLine = true
                )
            }

            if (editingGoal != null) {
                HorizontalDivider(color = LineBorder, thickness = 1.dp)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Quick deposit (+ Add funds)",
                        style = MaterialTheme.typography.titleSmall,
                        color = Ink
                    )
                    OutlinedTextField(
                        value = quickDepositInput,
                        onValueChange = { input ->
                            if (input.matches(Regex("^\\d*(\\.\\d{0,2})?$"))) {
                                quickDepositInput = input
                            }
                        },
                        placeholder = { Text("Amount to deposit (e.g. 500)", color = InkFaint) },
                        prefix = { Text("₱ ", color = Ink) },
                        visualTransformation = PesoAmountTransformation,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceColor,
                            unfocusedContainerColor = SurfaceColor,
                            focusedBorderColor = Green600,
                            unfocusedBorderColor = LineBorder
                        ),
                        singleLine = true
                    )

                    if (spendableAccounts.isNotEmpty()) {
                        Text(
                            text = "Deduct from wallet (optional):",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkMuted
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                val noneSelected = deductFromAccountId == null
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (noneSelected) Green100 else SurfaceColor)
                                        .border(1.dp, if (noneSelected) Green600 else LineBorder, CircleShape)
                                        .clickable { deductFromAccountId = null }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Goal only", color = if (noneSelected) Green700 else InkMuted, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            items(spendableAccounts) { acc ->
                                val isSel = deductFromAccountId == acc.id
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (isSel) Green100 else SurfaceColor)
                                        .border(1.dp, if (isSel) Green600 else LineBorder, CircleShape)
                                        .clickable { deductFromAccountId = acc.id }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(acc.name, color = if (isSel) Green700 else Ink, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    val depositCentavos = Math.round((quickDepositInput.toDoubleOrNull() ?: 0.0) * 100.0)
                    if (depositCentavos > 0L) {
                        SaanNaPuntaButton(
                            text = "Deposit +₱$quickDepositInput to ${editingGoal.name}",
                            onClick = {
                                repository.contributeToSavingsGoal(editingGoal, depositCentavos, deductFromAccountId)
                                goalFeedback.success()
                                ChitoReactions.react(
                                    ChitoLines.goalDeposit(
                                        goalName = editingGoal.name,
                                        amountCentavos = depositCentavos,
                                        reachedTarget = editingGoal.savedCentavos < editingGoal.targetCentavos &&
                                            editingGoal.savedCentavos + depositCentavos >= editingGoal.targetCentavos
                                    )
                                )
                                onDismiss()
                            }
                        )
                    }
                }
                HorizontalDivider(color = LineBorder, thickness = 1.dp)
            }

            SaanNaPuntaButton(
                text = "Save goal",
                enabled = goalName.isNotBlank() && targetInput.isNotBlank(),
                onClick = {
                    val targetCentavos = Math.round((targetInput.toDoubleOrNull() ?: 0.0) * 100.0)
                    val savedCentavos = Math.round((savedInput.toDoubleOrNull() ?: 0.0) * 100.0)
                    repository.saveSavingsGoal(
                        SavingsGoal(
                            id = editingGoal?.id ?: 0L,
                            name = goalName.trim(),
                            targetCentavos = targetCentavos,
                            savedCentavos = savedCentavos
                        )
                    )
                    onDismiss()
                }
            )

            if (editingGoal != null) {
                OutlinedButton(
                    onClick = {
                        repository.deleteSavingsGoal(editingGoal.id)
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
                        text = "Delete goal",
                        style = MaterialTheme.typography.labelLarge,
                        color = StatusOver
                    )
                }
            }
        }
    }
}
