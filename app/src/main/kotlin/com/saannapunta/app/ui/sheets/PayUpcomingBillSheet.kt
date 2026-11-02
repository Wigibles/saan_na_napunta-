package com.saannapunta.app.ui.sheets

import com.saannapunta.app.ui.chito.ChitoReactions
import com.saannapunta.app.ui.feedback.rememberUiFeedback
import com.saannapunta.app.ui.chito.ChitoCompanion
import com.saannapunta.app.ui.chito.ChitoLines
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saannapunta.app.data.DataRepository
import com.saannapunta.app.data.model.MoneyFormatter
import com.saannapunta.app.data.model.UpcomingBill
import com.saannapunta.app.ui.components.AccountLogoTile
import com.saannapunta.app.ui.components.SaanNaPuntaButton
import com.saannapunta.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayUpcomingBillSheet(
    bill: UpcomingBill,
    repository: DataRepository,
    onEditClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val accounts by repository.accounts.collectAsState()
    val feedback = rememberUiFeedback()
    var selectedAccountId by remember(accounts) {
        mutableStateOf(accounts.firstOrNull { it.isSpendable }?.id ?: accounts.firstOrNull()?.id ?: 1L)
    }

    val dueText = remember(bill.dueDateTimestamp) {
        SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(bill.dueDateTimestamp))
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
            ChitoCompanion(line = ChitoLines.payBill(bill.title), size = 52.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = bill.title, style = MaterialTheme.typography.titleMedium, color = Ink)
                    val subTag = if (!bill.subCategory.isNullOrBlank()) " · ${bill.subCategory}" else ""
                    Text(
                        text = "Due $dueText$subTag · ${if (bill.recurring) "Recurring monthly" else "One-time bill"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted
                    )
                }

                Text(
                    text = MoneyFormatter.format(bill.amountCentavos, false),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 24.sp),
                    color = Ink
                )
            }

            HorizontalDivider(color = LineBorder, thickness = 1.dp)

            Text("Pay from account:", style = MaterialTheme.typography.labelSmall, color = InkMuted)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                accounts.forEach { acc ->
                    val isSelected = selectedAccountId == acc.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedAccountId = acc.id },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isSelected) Green50 else SurfaceColor),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(if (isSelected) Green600 else LineBorder)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AccountLogoTile(iconResName = acc.iconResName, fallbackName = acc.name, size = 32)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = acc.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = Ink)
                                Text(text = "Balance: ${MoneyFormatter.format(acc.balanceCentavos, false)}", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                            }
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .background(Green600, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = SurfaceColor, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Action: Mark as Paid
            SaanNaPuntaButton(
                text = "Mark as Paid (−${MoneyFormatter.format(bill.amountCentavos, false)})",
                enabled = accounts.isNotEmpty(),
                onClick = {
                    repository.markBillPaid(bill, selectedAccountId)
                    feedback.success()
                    ChitoReactions.react(ChitoLines.billPaid(bill.title))
                    onDismiss()
                }
            )

            // Secondary: Edit or Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onEditClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, LineBorder)
                ) {
                    Text("Edit bill", color = Ink)
                }

                OutlinedButton(
                    onClick = {
                        repository.deleteUpcomingBill(bill.id)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, LineBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusOver)
                ) {
                    Text("Delete", color = StatusOver)
                }
            }
        }
    }
}
