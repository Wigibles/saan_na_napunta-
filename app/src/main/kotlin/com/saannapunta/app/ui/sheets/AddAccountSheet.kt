package com.saannapunta.app.ui.sheets

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
import com.saannapunta.app.data.DataRepository
import com.saannapunta.app.data.model.Account
import com.saannapunta.app.data.model.AccountType
import com.saannapunta.app.ui.components.AccountLogoTile
import com.saannapunta.app.ui.components.SaanNaPuntaButton
import com.saannapunta.app.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountSheet(
    repository: DataRepository,
    editingAccount: Account? = null,
    onDismiss: () -> Unit
) {
    val presetProviders = listOf(
        Triple("GCash", "ic_gcash", AccountType.E_WALLET),
        Triple("Maya", "ic_maya", AccountType.E_WALLET),
        Triple("GoTyme", "ic_gotyme", AccountType.BANK),
        Triple("BPI", "ic_bpi", AccountType.BANK),
        Triple("BDO", "ic_bdo", AccountType.BANK),
        Triple("UnionBank", "ic_unionbank", AccountType.BANK),
        Triple("Metrobank", "ic_metrobank", AccountType.BANK),
        Triple("Security Bank", "ic_security_bank", AccountType.BANK),
        Triple("RCBC", "ic_rcbc", AccountType.BANK),
        Triple("PNB", "ic_pnb", AccountType.BANK),
        Triple("China Bank", "ic_chinabank", AccountType.BANK),
        Triple("EastWest", "ic_eastwest", AccountType.BANK),
        Triple("PSBank", "ic_psbank", AccountType.BANK),
        Triple("Landbank", null, AccountType.BANK),
        Triple("MariBank", "ic_maribank", AccountType.BANK),
        Triple("CIMB", "ic_cimb", AccountType.BANK),
        Triple("Tonik", "ic_tonik", AccountType.BANK),
        Triple("UnionDigital", "ic_uniondigital", AccountType.BANK),
        Triple("UnoBank", "ic_unobank", AccountType.BANK),
        Triple("ShopeePay", "ic_spay", AccountType.E_WALLET),
        Triple("GrabPay", "ic_grabpay", AccountType.E_WALLET),
        Triple("PalawanPay", "ic_palawanpay", AccountType.E_WALLET),
        Triple("Coins.ph", "ic_coinsph", AccountType.E_WALLET),
        Triple("Vybe", "ic_vybe", AccountType.E_WALLET),
        Triple("Cash on Hand", "ic_cash", AccountType.CASH),
        Triple("Credit Card / Loan", null, AccountType.LIABILITY)
    )

    var accountName by remember(editingAccount) {
        mutableStateOf(editingAccount?.name ?: "UnionBank")
    }
    var selectedProvider by remember(editingAccount) {
        mutableStateOf(editingAccount?.provider ?: "unionbank")
    }
    var selectedType by remember(editingAccount) {
        mutableStateOf(editingAccount?.type ?: AccountType.BANK)
    }
    var selectedIconRes by remember(editingAccount) {
        mutableStateOf<String?>(editingAccount?.iconResName ?: "ic_unionbank")
    }
    var balanceInput by remember(editingAccount) {
        mutableStateOf(
            if (editingAccount != null) {
                val pesos = editingAccount.balanceCentavos / 100.0
                if (editingAccount.balanceCentavos % 100L == 0L) {
                    (editingAccount.balanceCentavos / 100L).toString()
                } else {
                    String.format(Locale.US, "%.2f", pesos)
                }
            } else ""
        )
    }
    var maskedNumberInput by remember(editingAccount) {
        mutableStateOf(editingAccount?.accountNumberMasked ?: "")
    }
    var isSpendable by remember(editingAccount) {
        mutableStateOf(editingAccount?.isSpendable ?: true)
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
                text = if (editingAccount != null) "Edit account" else "Add account",
                style = MaterialTheme.typography.titleMedium,
                color = Ink
            )

            ChitoCompanion(line = ChitoLines.account(editingAccount != null), size = 52.dp)

            // Select Provider Preset
            Column {
                Text("Select provider", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presetProviders) { (name, iconRes, type) ->
                        val isSelected = accountName == name
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Green100 else SurfaceColor)
                                .border(1.dp, if (isSelected) Green600 else LineBorder, RoundedCornerShape(12.dp))
                                .clickable {
                                    accountName = name
                                    selectedProvider = name.lowercase().replace(" ", "").replace(".", "")
                                    selectedType = type
                                    selectedIconRes = iconRes
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AccountLogoTile(iconResName = iconRes, fallbackName = name, size = 26)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal),
                                color = if (isSelected) Green700 else Ink
                            )
                        }
                    }
                }
            }

            // Custom Name
            Column {
                Text("Account name", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = accountName,
                    onValueChange = { accountName = it },
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

            // Current Balance
            Column {
                Text("Current balance", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = balanceInput,
                    onValueChange = { input ->
                        if (input.matches(Regex("^\\d*(\\.\\d{0,2})?$"))) {
                            balanceInput = input
                        }
                    },
                    placeholder = { Text("0.00", style = MaterialTheme.typography.titleMedium.copy(color = InkFaint)) },
                    prefix = { Text("₱ ", style = MaterialTheme.typography.titleMedium.copy(color = Ink)) },
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

            // Masked account number (optional)
            Column {
                Text("Account number / nickname (optional)", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = maskedNumberInput,
                    onValueChange = { maskedNumberInput = it },
                    placeholder = { Text("e.g. •••• 1234 or Payroll", color = InkFaint, style = MaterialTheme.typography.bodyMedium) },
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

            // Spendable Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Include in Total balance", style = MaterialTheme.typography.bodyMedium, color = Ink)
                    Text("Turn off for locked savings or emergency vaults", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                }
                Switch(
                    checked = isSpendable,
                    onCheckedChange = { isSpendable = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = SurfaceColor, checkedTrackColor = Green600)
                )
            }

            SaanNaPuntaButton(
                text = "Save account",
                enabled = accountName.isNotBlank(),
                onClick = {
                    val balancePesos = balanceInput.toDoubleOrNull() ?: 0.0
                    val centavos = Math.round(balancePesos * 100.0)

                    val acc = Account(
                        id = editingAccount?.id ?: 0L,
                        name = accountName.trim(),
                        provider = selectedProvider,
                        type = selectedType,
                        accountNumberMasked = maskedNumberInput.trim(),
                        balanceCentavos = centavos,
                        isSpendable = isSpendable,
                        statusLine = editingAccount?.statusLine ?: "Manual account",
                        iconResName = selectedIconRes
                    )

                    if (editingAccount != null) {
                        repository.updateAccount(acc)
                    } else {
                        repository.addAccount(acc)
                    }
                    onDismiss()
                }
            )

            if (editingAccount != null) {
                OutlinedButton(
                    onClick = {
                        repository.deleteAccount(editingAccount.id)
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
                        text = "Delete account",
                        style = MaterialTheme.typography.labelLarge,
                        color = StatusOver
                    )
                }
            }
        }
    }
}
