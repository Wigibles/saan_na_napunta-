package com.saannapunta.app.ui.sheets

import com.saannapunta.app.ui.chito.ChitoCompanion
import com.saannapunta.app.ui.chito.ChitoLines
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saannapunta.app.data.DataRepository
import com.saannapunta.app.data.model.Account
import com.saannapunta.app.data.model.MoneyFormatter
import com.saannapunta.app.data.model.TransactionItem
import com.saannapunta.app.data.model.TransactionType
import com.saannapunta.app.parser.SmsBackfillCandidate
import com.saannapunta.app.parser.SmsBackfillScanner
import com.saannapunta.app.ui.components.AccountLogoTile
import com.saannapunta.app.ui.components.SaanNaPuntaButton
import com.saannapunta.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsBackfillSheet(
    repository: DataRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val accounts by repository.accounts.collectAsState()
    val transactions by repository.transactions.collectAsState()

    var candidates by remember { mutableStateOf<List<SmsBackfillCandidate>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val found = SmsBackfillScanner.scanLast30Days(
            context = context,
            accounts = accounts,
            existingTransactions = transactions
        )
        candidates = found
        isLoading = false
    }

    val selectedCount = remember(candidates) { candidates.count { it.isSelected } }
    val allSelected = remember(candidates) { candidates.isNotEmpty() && candidates.all { it.isSelected } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurfaceColor,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = LineBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SMS Inbox Backfill",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Ink
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Last 30 days of bank & e-wallet SMS",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted
                    )
                }

                if (candidates.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            val target = !allSelected
                            candidates = candidates.map { it.copy(isSelected = target) }
                        }
                    ) {
                        Text(
                            text = if (allSelected) "Deselect All" else "Select All",
                            color = Green700,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            ChitoCompanion(line = ChitoLines.backfill(if (isLoading) 0 else candidates.size), size = 52.dp)
            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Green600)
                }
            } else if (candidates.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Inbox,
                        contentDescription = null,
                        tint = InkFaint,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Transaction SMS Found",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Ink
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "No messages from GCash, Maya, or supported banks were found in the last 30 days.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    SaanNaPuntaButton(
                        text = "Close",
                        onClick = onDismiss
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(candidates, key = { it.smsId }) { item ->
                        val dateFmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                        val dateStr = dateFmt.format(Date(item.timestamp))

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, if (item.isSelected) Green600 else LineBorder, RoundedCornerShape(16.dp))
                                .clickable {
                                    candidates = candidates.map {
                                        if (it.smsId == item.smsId) it.copy(isSelected = !it.isSelected) else it
                                    }
                                },
                            color = if (item.isSelected) Green50 else SurfaceColor
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Checkbox circle
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(if (item.isSelected) Green600 else Canvas)
                                        .border(1.5.dp, if (item.isSelected) Green600 else LineBorder, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (item.isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = SurfaceColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // Logo tile (matched account or catalog provider icon)
                                val catalogIcon = com.saannapunta.app.data.ProviderCatalog.all
                                    .firstOrNull { it.provider == item.parsedTx.provider }?.iconResName
                                AccountLogoTile(
                                    iconResName = item.matchedAccount?.iconResName ?: catalogIcon,
                                    fallbackName = item.matchedAccount?.name ?: item.parsedTx.provider,
                                    size = 32
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                // Details
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.parsedTx.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = Ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val walletLabel = item.matchedAccount?.name
                                        ?: (com.saannapunta.app.data.ProviderCatalog.all.firstOrNull { it.provider == item.parsedTx.provider }?.name
                                            ?.let { "New $it wallet" } ?: item.parsedTx.provider.uppercase())
                                    Text(
                                        text = "$dateStr · $walletLabel · ${item.parsedTx.category}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = InkMuted
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Amount
                                val isExpense = item.parsedTx.type == TransactionType.EXPENSE
                                Text(
                                    text = (if (isExpense) "-" else "+") + MoneyFormatter.format(item.parsedTx.amountCentavos),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isExpense) Ink else Green700
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action button
                SaanNaPuntaButton(
                    text = "Import Selected ($selectedCount)",
                    enabled = selectedCount > 0,
                    onClick = {
                        val toImport = candidates.filter { it.isSelected }
                        repository.importBackfillTransactions(toImport) { count ->
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                Toast.makeText(context, "Imported $count transaction(s)!", Toast.LENGTH_SHORT).show()
                            }
                        }
                        onDismiss()
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
