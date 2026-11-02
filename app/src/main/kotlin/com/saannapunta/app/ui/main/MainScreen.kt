package com.saannapunta.app.ui.main

import com.saannapunta.app.ui.components.LocalCategoryStyles
import com.saannapunta.app.ui.chito.ChitoReactionHost
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saannapunta.app.data.DataRepository
import com.saannapunta.app.ui.feedback.rememberUiFeedback
import com.saannapunta.app.data.model.Account
import com.saannapunta.app.data.model.CategoryBudget
import com.saannapunta.app.data.model.SavingsGoal
import com.saannapunta.app.data.model.TransactionItem
import com.saannapunta.app.data.model.TransactionType
import com.saannapunta.app.data.model.UpcomingBill
import com.saannapunta.app.ui.screens.*
import com.saannapunta.app.ui.sheets.AddAccountSheet
import com.saannapunta.app.ui.sheets.AddCategoryBudgetSheet
import com.saannapunta.app.ui.sheets.AddSavingsGoalSheet
import com.saannapunta.app.ui.sheets.AddTransactionSheet
import com.saannapunta.app.ui.sheets.AddUpcomingBillSheet
import com.saannapunta.app.ui.sheets.PayUpcomingBillSheet
import com.saannapunta.app.ui.theme.*

enum class BottomTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    WALLET("Wallet", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    PLAN("Plan", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    HISTORY("History", Icons.Filled.History, Icons.Outlined.History)
}

@Composable
fun MainScreen(
    pendingPayBillId: Long? = null,
    onConsumedPayBillId: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { DataRepository.getInstance(context) }
    val onboardingCompleted by repository.onboardingCompleted.collectAsState()
    val upcomingBills by repository.upcomingBills.collectAsState()
    val feedback = rememberUiFeedback()

    var activeTab by remember { mutableStateOf(BottomTab.HOME) }
    var showSettingsScreen by remember { mutableStateOf(false) }
    var showInsightsScreen by remember { mutableStateOf(false) }

    // Sheet states (add & edit)
    var showAddTransactionSheet by remember { mutableStateOf(false) }
    var addTransactionType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var addTransactionTargetAccountId by remember { mutableStateOf<Long?>(null) }
    var editingTransaction by remember { mutableStateOf<TransactionItem?>(null) }
    var historyAccountFilter by remember { mutableStateOf<Long?>(null) }

    var showAddAccountSheet by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<Account?>(null) }

    var showAddBudgetSheet by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf<CategoryBudget?>(null) }

    var showAddGoalSheet by remember { mutableStateOf(false) }
    var editingGoal by remember { mutableStateOf<SavingsGoal?>(null) }

    var showAddBillSheet by remember { mutableStateOf(false) }
    var editingBill by remember { mutableStateOf<UpcomingBill?>(null) }
    var selectedBillForPayment by remember { mutableStateOf<UpcomingBill?>(null) }

    LaunchedEffect(pendingPayBillId, upcomingBills, onboardingCompleted) {
        if (onboardingCompleted == true && pendingPayBillId != null) {
            val match = upcomingBills.firstOrNull { it.id == pendingPayBillId }
            if (match != null) {
                showSettingsScreen = false
                showInsightsScreen = false
                activeTab = BottomTab.HOME
                selectedBillForPayment = match
                onConsumedPayBillId()
            }
        }
    }

    if (onboardingCompleted == null) {
        // Initial load
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Canvas),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Green600)
        }
    } else if (onboardingCompleted == false) {
        OnboardingScreen(
            repository = repository,
            onComplete = {
                repository.refreshAll()
            }
        )
    } else {
        // Lets every row draw a category's chosen icon and color
        val budgetsForStyles by repository.budgets.collectAsState()
        CompositionLocalProvider(LocalCategoryStyles provides budgetsForStyles.associateBy { it.category.lowercase() }) {
        Scaffold(
            bottomBar = {
                if (!showSettingsScreen && !showInsightsScreen) {
                    // Bottom Navigation per Figma:
                    // Home | ( [Wallet] Wallet ) | Plan | History | (+)
                    Surface(
                        color = SurfaceColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, LineBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .height(64.dp)
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BottomTab.values().forEach { tab ->
                                val isSelected = !showSettingsScreen && !showInsightsScreen && activeTab == tab

                                if (isSelected) {
                                    // Active Pill Container
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(Green100)
                                            .clickable {
                                                if (activeTab != tab) feedback.select()
                                                showSettingsScreen = false
                                                showInsightsScreen = false
                                                activeTab = tab
                                            }
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = tab.selectedIcon,
                                            contentDescription = tab.title,
                                            tint = Green700,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = tab.title,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            ),
                                            color = Green700
                                        )
                                    }
                                } else {
                                    // Inactive Icon + Label
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier
                                            .clickable {
                                                if (activeTab != tab) feedback.select()
                                                showSettingsScreen = false
                                                showInsightsScreen = false
                                                activeTab = tab
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = tab.unselectedIcon,
                                            contentDescription = tab.title,
                                            tint = InkMuted,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = tab.title,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = InkMuted
                                        )
                                    }
                                }
                            }

                            // Far Right Circular Green FAB (+)
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Green600)
                                    .clickable {
                                        editingTransaction = null
                                        addTransactionType = TransactionType.EXPENSE
                                        showAddTransactionSheet = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Transaction",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (showSettingsScreen) {
                    SettingsScreen(
                        repository = repository,
                        onBack = { showSettingsScreen = false }
                    )
                } else if (showInsightsScreen) {
                    InsightsScreen(
                        repository = repository,
                        onBack = { showInsightsScreen = false }
                    )
                } else {
                    when (activeTab) {
                        BottomTab.HOME -> {
                            HomeScreen(
                                repository = repository,
                                onNavigateToAccounts = {
                                    showSettingsScreen = false
                                    showInsightsScreen = false
                                    activeTab = BottomTab.WALLET
                                },
                                onNavigateToTransactions = {
                                    showSettingsScreen = false
                                    showInsightsScreen = false
                                    activeTab = BottomTab.HISTORY
                                },
                                onNavigateToSettings = { showSettingsScreen = true },
                                onOpenAddTransaction = { type ->
                                    editingTransaction = null
                                    addTransactionType = type
                                    showAddTransactionSheet = true
                                },
                                onEditTransaction = { tx ->
                                    editingTransaction = tx
                                    showAddTransactionSheet = true
                                },
                                onAddUpcomingBill = {
                                    editingBill = null
                                    showAddBillSheet = true
                                },
                                onBillClick = { bill ->
                                    selectedBillForPayment = bill
                                }
                            )
                        }
                        BottomTab.WALLET -> {
                            AccountsScreen(
                                repository = repository,
                                onAddAccountClick = {
                                    editingAccount = null
                                    showAddAccountSheet = true
                                },
                                onEditAccountClick = { account ->
                                    editingAccount = account
                                    showAddAccountSheet = true
                                },
                                onForecastClick = {
                                    showInsightsScreen = true
                                },
                                onViewAccountTransactions = { account ->
                                    historyAccountFilter = account.id
                                    activeTab = BottomTab.HISTORY
                                },
                                onPayCreditAccount = { creditAccount ->
                                    editingTransaction = null
                                    addTransactionType = TransactionType.TRANSFER
                                    addTransactionTargetAccountId = creditAccount.id
                                    showAddTransactionSheet = true
                                }
                            )
                        }
                        BottomTab.PLAN -> {
                            BudgetsScreen(
                                repository = repository,
                                onAddBudgetClick = {
                                    editingBudget = null
                                    showAddBudgetSheet = true
                                },
                                onEditBudgetClick = { budget ->
                                    editingBudget = budget
                                    showAddBudgetSheet = true
                                },
                                onAddGoalClick = {
                                    editingGoal = null
                                    showAddGoalSheet = true
                                },
                                onEditGoalClick = { goal ->
                                    editingGoal = goal
                                    showAddGoalSheet = true
                                },
                                onAddUpcomingBill = {
                                    editingBill = null
                                    showAddBillSheet = true
                                },
                                onBillClick = { bill ->
                                    selectedBillForPayment = bill
                                }
                            )
                        }
                        BottomTab.HISTORY -> {
                            TransactionsScreen(
                                repository = repository,
                                initialAccountFilter = historyAccountFilter,
                                onAddTransactionClick = {
                                    editingTransaction = null
                                    addTransactionType = TransactionType.EXPENSE
                                    addTransactionTargetAccountId = null
                                    showAddTransactionSheet = true
                                },
                                onEditTransaction = { tx ->
                                    editingTransaction = tx
                                    showAddTransactionSheet = true
                                }
                            )
                        }
                    }
                }
            }

            // Bottom Sheets
            if (showAddTransactionSheet) {
                AddTransactionSheet(
                    repository = repository,
                    initialType = addTransactionType,
                    initialTargetAccountId = addTransactionTargetAccountId,
                    editingTransaction = editingTransaction,
                    onDismiss = {
                        showAddTransactionSheet = false
                        editingTransaction = null
                        addTransactionTargetAccountId = null
                    }
                )
            }

            if (showAddAccountSheet) {
                AddAccountSheet(
                    repository = repository,
                    editingAccount = editingAccount,
                    onDismiss = {
                        showAddAccountSheet = false
                        editingAccount = null
                    }
                )
            }

            if (showAddBudgetSheet) {
                AddCategoryBudgetSheet(
                    repository = repository,
                    editingBudget = editingBudget,
                    onDismiss = {
                        showAddBudgetSheet = false
                        editingBudget = null
                    }
                )
            }

            if (showAddGoalSheet) {
                AddSavingsGoalSheet(
                    repository = repository,
                    editingGoal = editingGoal,
                    onDismiss = {
                        showAddGoalSheet = false
                        editingGoal = null
                    }
                )
            }

            if (showAddBillSheet) {
                AddUpcomingBillSheet(
                    repository = repository,
                    editingBill = editingBill,
                    onDismiss = {
                        showAddBillSheet = false
                        editingBill = null
                    }
                )
            }

            selectedBillForPayment?.let { bill ->
                PayUpcomingBillSheet(
                    bill = bill,
                    repository = repository,
                    onEditClick = {
                        selectedBillForPayment = null
                        editingBill = bill
                        showAddBillSheet = true
                    },
                    onDismiss = {
                        selectedBillForPayment = null
                    }
                )
            }

            // Chito pops up from the bottom after the user saves, pays or deletes something
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                ChitoReactionHost(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp)
                )
            }
        }
        }
    }
}
