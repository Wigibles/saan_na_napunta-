package com.saannapunta.app.ui.screens

import com.saannapunta.app.ui.components.*
import com.saannapunta.app.ui.feedback.rememberUiFeedback
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import com.saannapunta.app.ui.chito.ChitoCompanion
import com.saannapunta.app.ui.chito.ChitoLines
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saannapunta.app.data.DataRepository
import com.saannapunta.app.data.model.Account
import com.saannapunta.app.data.model.AccountType
import com.saannapunta.app.data.model.MoneyFormatter
import com.saannapunta.app.ui.components.AccountLogoTile
import com.saannapunta.app.ui.components.SaanNaPuntaButton
import com.saannapunta.app.ui.theme.*
import com.saannapunta.app.data.model.isCountableSpend
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    repository: DataRepository,
    onAddAccountClick: () -> Unit,
    onEditAccountClick: (Account) -> Unit,
    onForecastClick: () -> Unit = {},
    onViewAccountTransactions: (Account) -> Unit = {},
    onPayCreditAccount: (Account) -> Unit = {},
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accounts by repository.accounts.collectAsState()
    val overview by repository.overview.collectAsState()
    val transactions by repository.transactions.collectAsState()

    var hideBalance by remember { mutableStateOf(false) }
    var selectedSegment by remember { mutableIntStateOf(0) } // 0: All, 1: Assets, 2: Liabilities
    val feedback = rememberUiFeedback()

    // Account action sheet state
    var selectedAccountForAction by remember { mutableStateOf<Account?>(null) }
    var accountToDelete by remember { mutableStateOf<Account?>(null) }

    // Group collapse states
    var creditExpanded by remember { mutableStateOf(true) }
    var savingsExpanded by remember { mutableStateOf(true) }
    var eWalletsExpanded by remember { mutableStateOf(true) }
    var cashExpanded by remember { mutableStateOf(true) }

    val filteredAccounts = remember(accounts, selectedSegment) {
        when (selectedSegment) {
            1 -> accounts.filter { it.type != AccountType.LIABILITY }
            2 -> accounts.filter { it.type == AccountType.LIABILITY }
            else -> accounts
        }
    }

    val creditList = filteredAccounts.filter { it.type == AccountType.LIABILITY }
    val savingsList = filteredAccounts.filter { it.type == AccountType.BANK }
    val eWalletsList = filteredAccounts.filter { it.type == AccountType.E_WALLET }
    val cashList = filteredAccounts.filter { it.type == AccountType.CASH }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Canvas),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Ink)
                    }
                }
                ScreenHeader(eyebrow = "Wallet", title = "Your money", modifier = Modifier.weight(1f)) {
                    TextButton(onClick = onForecastClick) {
                        Text("Insights", style = MaterialTheme.typography.labelLarge, color = Green700)
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Green600)
                            .clickable(onClickLabel = "Add account") { onAddAccountClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add account", tint = Color.White)
                    }
                }
            }
        }

        // Chito on where the money is
        item {
            ChitoCompanion(
                line = ChitoLines.accounts(
                    netWorthCentavos = overview.netWorthCentavos,
                    accountCount = accounts.size,
                    needsBalance = accounts.count { it.needsOpeningBalance }
                ),
                size = 72.dp,
                bubbleColor = SurfaceColor
            )
        }

        // 2. Net worth hero
        item {
            val assets = accounts.filter { it.type != AccountType.LIABILITY }.sumOf { it.balanceCentavos }
            val owed = accounts.filter { it.type == AccountType.LIABILITY }.sumOf { it.balanceCentavos }
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
                    Text("Net worth", style = MaterialTheme.typography.labelLarge, color = OnHeroMuted, modifier = Modifier.weight(1f))
                    IconButton(onClick = { hideBalance = !hideBalance }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (hideBalance) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (hideBalance) "Show balances" else "Hide balances",
                            tint = OnHeroMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(
                    text = if (hideBalance) "₱ ••••••" else MoneyFormatter.format(rememberCountUp(overview.netWorthCentavos)),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 36.sp, fontWeight = FontWeight.Bold),
                    color = OnHero
                )
                Spacer(modifier = Modifier.height(10.dp))
                HeroChip(
                    text = if (accounts.size == 1) "1 account" else "${accounts.size} accounts",
                    icon = Icons.Outlined.AccountBalanceWallet
                )
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("You have", style = MaterialTheme.typography.labelMedium, color = OnHeroMuted)
                        Text(
                            if (hideBalance) "₱••••" else MoneyFormatter.format(assets, false),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = OnHero
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("You owe", style = MaterialTheme.typography.labelMedium, color = OnHeroMuted)
                        Text(
                            if (hideBalance) "₱••••" else MoneyFormatter.format(owed, false),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = OnHero
                        )
                    }
                }
            }
        }

        // 3. Filter: All / Assets / Liabilities
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(SurfaceColor)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("All", "Assets", "Owed").forEachIndexed { idx, title ->
                    val isSelected = selectedSegment == idx
                    val bg by animateColorAsState(if (isSelected) Green600 else SurfaceColor, tween(180), label = "WalletSegBg")
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(CircleShape)
                            .background(bg)
                            .clickable {
                                if (!isSelected) feedback.select()
                                selectedSegment = idx
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isSelected) Color.White else InkMuted
                        )
                    }
                }
            }
        }

        // 6. Accordion Group: Credit (Liabilities)
        if (creditList.isNotEmpty()) {
            item {
                val subtotal = creditList.sumOf { it.balanceCentavos }
                AccountAccordionHeader(
                    title = "Credit",
                    subtotalCentavos = subtotal,
                    isExpanded = creditExpanded,
                    hideBalance = hideBalance,
                    onToggle = { creditExpanded = !creditExpanded }
                )
            }
            if (creditExpanded) {
                items(creditList.chunked(2)) { pair ->
                    AccountGridRow(
                        pair = pair,
                        hideBalance = hideBalance,
                        onAccountClick = { onEditAccountClick(it) },
                        onOptionsClick = { selectedAccountForAction = it }
                    )
                }
            }
        }

        // 7. Accordion Group: Savings (Banks)
        if (savingsList.isNotEmpty()) {
            item {
                val subtotal = savingsList.sumOf { it.balanceCentavos }
                AccountAccordionHeader(
                    title = "Savings",
                    subtotalCentavos = subtotal,
                    isExpanded = savingsExpanded,
                    hideBalance = hideBalance,
                    onToggle = { savingsExpanded = !savingsExpanded }
                )
            }
            if (savingsExpanded) {
                items(savingsList.chunked(2)) { pair ->
                    AccountGridRow(
                        pair = pair,
                        hideBalance = hideBalance,
                        onAccountClick = { onEditAccountClick(it) },
                        onOptionsClick = { selectedAccountForAction = it }
                    )
                }
            }
        }

        // 8. Accordion Group: E-wallets
        if (eWalletsList.isNotEmpty()) {
            item {
                val subtotal = eWalletsList.sumOf { it.balanceCentavos }
                AccountAccordionHeader(
                    title = "E-wallets",
                    subtotalCentavos = subtotal,
                    isExpanded = eWalletsExpanded,
                    hideBalance = hideBalance,
                    onToggle = { eWalletsExpanded = !eWalletsExpanded }
                )
            }
            if (eWalletsExpanded) {
                items(eWalletsList.chunked(2)) { pair ->
                    AccountGridRow(
                        pair = pair,
                        hideBalance = hideBalance,
                        onAccountClick = { onEditAccountClick(it) },
                        onOptionsClick = { selectedAccountForAction = it }
                    )
                }
            }
        }

        // 9. Accordion Group: Cash (if any)
        if (cashList.isNotEmpty()) {
            item {
                val subtotal = cashList.sumOf { it.balanceCentavos }
                AccountAccordionHeader(
                    title = "Cash",
                    subtotalCentavos = subtotal,
                    isExpanded = cashExpanded,
                    hideBalance = hideBalance,
                    onToggle = { cashExpanded = !cashExpanded }
                )
            }
            if (cashExpanded) {
                items(cashList.chunked(2)) { pair ->
                    AccountGridRow(
                        pair = pair,
                        hideBalance = hideBalance,
                        onAccountClick = { onEditAccountClick(it) },
                        onOptionsClick = { selectedAccountForAction = it }
                    )
                }
            }
        }

        // 10. Empty state if no accounts match the filter
        if (filteredAccounts.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(LineBorder))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No accounts in this view.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkMuted
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        SaanNaPuntaButton(
                            text = "Add account",
                            onClick = onAddAccountClick
                        )
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet for Account Options (Edit / Delete)
    selectedAccountForAction?.let { account ->
        ModalBottomSheet(
            onDismissRequest = { selectedAccountForAction = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = SurfaceColor,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            dragHandle = { BottomSheetDefaults.DragHandle(color = LineBorder) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AccountLogoTile(
                        iconResName = account.iconResName,
                        fallbackName = account.name,
                        size = 36
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = account.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Ink
                        )
                        Text(
                            text = MoneyFormatter.format(account.balanceCentavos),
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // View Transactions Button
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            val acc = account
                            selectedAccountForAction = null
                            onViewAccountTransactions(acc)
                        },
                    color = Canvas
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.History, contentDescription = "Transactions", tint = Green700)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "View Account Transactions",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Ink
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Pay Credit / Balance Button (for Liability accounts)
                if (account.type == AccountType.LIABILITY) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val acc = account
                                selectedAccountForAction = null
                                onPayCreditAccount(acc)
                            },
                        color = Green50
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CompareArrows, contentDescription = "Pay Credit", tint = Green700)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Pay Credit Card / Loan Balance",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Green700
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Edit Button
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            val acc = account
                            selectedAccountForAction = null
                            onEditAccountClick(acc)
                        },
                    color = Canvas
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Green700)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Edit Account Details",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Ink
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Delete Button
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            val acc = account
                            selectedAccountForAction = null
                            accountToDelete = acc
                        },
                    color = Canvas
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusOver)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Delete Account",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = StatusOver
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Confirmation dialog for deletion
    accountToDelete?.let { acc ->
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = { Text("Delete ${acc.name}?", color = Ink) },
            text = { Text("Remove this account from your wallets? Its past transactions and transfers stay in your history, and other wallets' balances are not changed.", color = InkMuted) },
            confirmButton = {
                TextButton(
                    onClick = {
                        repository.deleteAccount(acc.id)
                        accountToDelete = null
                        Toast.makeText(context, "${acc.name} deleted", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Delete", color = StatusOver, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToDelete = null }) {
                    Text("Cancel", color = InkMuted)
                }
            },
            containerColor = SurfaceColor
        )
    }
}

@Composable
fun AccountAccordionHeader(
    title: String,
    subtotalCentavos: Long,
    isExpanded: Boolean,
    hideBalance: Boolean,
    onToggle: () -> Unit
) {
    val rotation by animateFloatAsState(if (isExpanded) 0f else -90f, tween(200), label = "AccordionArrow")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggle() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Ink,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (hideBalance) "₱••••" else MoneyFormatter.format(subtotalCentavos, false),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = InkMuted
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            tint = InkMuted,
            modifier = Modifier
                .size(22.dp)
                .rotate(rotation)
        )
    }
}

@Composable
fun AccountGridRow(
    pair: List<Account>,
    hideBalance: Boolean,
    onAccountClick: (Account) -> Unit,
    onOptionsClick: (Account) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AccountGridCard(
            account = pair[0],
            hideBalance = hideBalance,
            onClick = { onAccountClick(pair[0]) },
            onOptionsClick = { onOptionsClick(pair[0]) },
            modifier = Modifier.weight(1f)
        )
        if (pair.size > 1) {
            AccountGridCard(
                account = pair[1],
                hideBalance = hideBalance,
                onClick = { onAccountClick(pair[1]) },
                onOptionsClick = { onOptionsClick(pair[1]) },
                modifier = Modifier.weight(1f)
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun AccountGridCard(
    account: Account,
    hideBalance: Boolean,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = providerAccent(account.provider)
    SoftCard(modifier = modifier.fillMaxWidth(), elevation = 4.dp, onClick = onClick) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .background(accent)
        )
        Column(modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AccountLogoTile(iconResName = account.iconResName, fallbackName = account.name, size = 36)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onOptionsClick, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.MoreHoriz, contentDescription = "Options for ${account.name}", tint = InkMuted, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = account.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val typeLabel = when (account.type) {
                AccountType.LIABILITY -> "Credit · owed"
                AccountType.BANK -> "Bank"
                AccountType.E_WALLET -> "E-wallet"
                AccountType.CASH -> "Cash"
            }
            val masked = account.accountNumberMasked.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
            Text(
                text = typeLabel + masked,
                style = MaterialTheme.typography.labelSmall,
                color = InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = when {
                    hideBalance -> "₱••••"
                    account.needsOpeningBalance -> "Set balance"
                    else -> MoneyFormatter.format(account.balanceCentavos)
                },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = when {
                    account.needsOpeningBalance -> StatusWarn
                    account.type == AccountType.LIABILITY -> StatusOver
                    else -> Ink
                }
            )
        }
    }
}
