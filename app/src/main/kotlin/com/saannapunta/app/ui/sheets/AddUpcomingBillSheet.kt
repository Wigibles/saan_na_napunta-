package com.saannapunta.app.ui.sheets

import com.saannapunta.app.ui.chito.ChitoCompanion
import com.saannapunta.app.ui.chito.ChitoLines
import com.saannapunta.app.ui.components.PesoAmountTransformation
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.saannapunta.app.data.DataRepository
import com.saannapunta.app.data.model.UpcomingBill
import com.saannapunta.app.parser.SmsMessageParser
import com.saannapunta.app.reminders.BillReminderScheduler
import com.saannapunta.app.ui.components.SaanNaPuntaButton
import com.saannapunta.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddUpcomingBillSheet(
    repository: DataRepository,
    editingBill: UpcomingBill? = null,
    onDismiss: () -> Unit
) {
    val predefinedSubcategories = remember {
        listOf(
            "⚡ Kuryente" to "Kuryente",
            "💧 Tubig" to "Tubig",
            "🌐 Internet" to "Internet",
            "📱 Telepono / Load" to "Telepono",
            "🏠 Upa / Rent" to "Rent & Housing",
            "💳 Credit Card" to "Credit Card",
            "📺 Subscriptions" to "Subscriptions",
            "🛡️ Insurance / Dues" to "Insurance & Dues"
        )
    }

    var selectedSubCategory by remember(editingBill) {
        val initial = editingBill?.subCategory ?: "Kuryente"
        val match = predefinedSubcategories.firstOrNull { it.second.equals(initial, ignoreCase = true) || it.first.contains(initial, ignoreCase = true) }
        mutableStateOf(match?.second ?: initial)
    }

    var isCustomSubCategory by remember(editingBill) {
        val isPredefined = editingBill?.subCategory == null || predefinedSubcategories.any {
            it.second.equals(editingBill.subCategory, ignoreCase = true)
        }
        mutableStateOf(!isPredefined)
    }

    var customSubCategoryInput by remember(editingBill) {
        mutableStateOf(if (isCustomSubCategory) editingBill?.subCategory ?: "" else "")
    }

    var titleInput by remember(editingBill) {
        mutableStateOf(editingBill?.title ?: if (editingBill == null) "Meralco (Kuryente)" else "")
    }

    val context = LocalContext.current
    var showReminderPermissionPrompt by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            BillReminderScheduler.scheduleDailyCheck(context)
            BillReminderScheduler.runCheckNow(context)
        }
        showReminderPermissionPrompt = false
        onDismiss()
    }

    var amountInput by remember(editingBill) {
        mutableStateOf(
            if (editingBill != null) {
                val whole = editingBill.amountCentavos / 100L
                val rem = editingBill.amountCentavos % 100L
                if (rem == 0L) {
                    whole.toString()
                } else {
                    String.format(Locale.US, "%d.%02d", whole, rem)
                }
            } else ""
        )
    }

    var dueDayOfMonth by remember(editingBill) {
        val cal = Calendar.getInstance()
        if (editingBill != null) {
            cal.timeInMillis = editingBill.dueDateTimestamp
        }
        mutableIntStateOf(cal.get(Calendar.DAY_OF_MONTH))
    }

    var isRecurring by remember(editingBill) {
        mutableStateOf(editingBill?.recurring ?: true)
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
                text = if (editingBill != null) "Edit upcoming bill" else "Add upcoming bill",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Ink
            )

            val reminderDays by repository.billReminderDaysBefore.collectAsState()
            ChitoCompanion(
                line = ChitoLines.bill(SmsMessageParser.parseAmount(amountInput), reminderDays),
                size = 52.dp
            )

            // Bill Subcategory selector (e.g. Kuryente, Tubig, Internet)
            Column {
                Text(
                    text = "Subcategory (Uri ng Bill)",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    predefinedSubcategories.forEach { (displayLabel, valueKey) ->
                        val isSelected = !isCustomSubCategory && selectedSubCategory.equals(valueKey, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isSelected) Green100 else SurfaceColor)
                                .border(1.dp, if (isSelected) Green600 else LineBorder, CircleShape)
                                .clickable {
                                    isCustomSubCategory = false
                                    selectedSubCategory = valueKey
                                    if (editingBill == null) {
                                        when (valueKey) {
                                            "Kuryente" -> if (titleInput.isBlank() || titleInput.contains("Tubig") || titleInput.contains("Internet")) titleInput = "Meralco (Kuryente)"
                                            "Tubig" -> if (titleInput.isBlank() || titleInput.contains("Kuryente") || titleInput.contains("Internet")) titleInput = "Maynilad / Water"
                                            "Internet" -> if (titleInput.isBlank() || titleInput.contains("Kuryente") || titleInput.contains("Water")) titleInput = "Converge / PLDT Fiber"
                                            "Telepono" -> if (titleInput.isBlank()) titleInput = "Globe / Smart Postpaid"
                                            "Rent & Housing" -> if (titleInput.isBlank()) titleInput = "House / Condo Rent"
                                            "Credit Card" -> if (titleInput.isBlank()) titleInput = "Credit Card Bill"
                                            "Subscriptions" -> if (titleInput.isBlank()) titleInput = "Netflix / Spotify"
                                        }
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = displayLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal),
                                color = if (isSelected) Green700 else Ink
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isCustomSubCategory) Green100 else SurfaceColor)
                            .border(1.dp, if (isCustomSubCategory) Green600 else LineBorder, CircleShape)
                            .clickable { isCustomSubCategory = true }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "+ Custom",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isCustomSubCategory) FontWeight.SemiBold else FontWeight.Medium),
                            color = if (isCustomSubCategory) Green700 else Green600
                        )
                    }
                }

                if (isCustomSubCategory) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customSubCategoryInput,
                        onValueChange = { customSubCategoryInput = it },
                        placeholder = { Text("Enter custom subcategory (e.g. Assoc dues, Garbage fee)", color = InkFaint) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceColor,
                            unfocusedContainerColor = SurfaceColor,
                            focusedBorderColor = Green600,
                            unfocusedBorderColor = LineBorder
                        )
                    )
                }
            }

            // Bill Title
            Column {
                Text("Bill name / Description", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    placeholder = { Text("e.g. Meralco, Maynilad, Converge, Rent") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Amount
            Column {
                Text("Amount", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { input ->
                        if (input.matches(Regex("^\\d*(\\.\\d{0,2})?$"))) amountInput = input
                    },
                    prefix = { Text("₱", color = Ink) },
                    visualTransformation = PesoAmountTransformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    placeholder = { Text("0.00") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Due Day of Month
            Column {
                Text("Due day of the month (1–31)", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = dueDayOfMonth.toString(),
                        onValueChange = {
                            val v = it.toIntOrNull()
                            if (v != null && v in 1..31) {
                                dueDayOfMonth = v
                            } else if (it.isEmpty()) {
                                dueDayOfMonth = 1
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.width(100.dp),
                        shape = RoundedCornerShape(12.dp)
                    )

                    val previewDateStr = remember(dueDayOfMonth) {
                        val cal = Calendar.getInstance().apply {
                            val today = get(Calendar.DAY_OF_MONTH)
                            set(Calendar.DAY_OF_MONTH, dueDayOfMonth.coerceAtMost(getActualMaximum(Calendar.DAY_OF_MONTH)))
                            if (get(Calendar.DAY_OF_MONTH) < today) {
                                add(Calendar.MONTH, 1)
                            }
                        }
                        SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(cal.time)
                    }

                    Text("Next due: $previewDateStr", style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                }
            }

            // Recurring Monthly Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Recurring bill", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = Ink)
                    Text("Rolls forward one month after being marked as paid", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                }
                Switch(
                    checked = isRecurring,
                    onCheckedChange = { isRecurring = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = SurfaceColor, checkedTrackColor = Green600)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            val resolvedSubCategory = when {
                isCustomSubCategory && customSubCategoryInput.isNotBlank() -> customSubCategoryInput.trim()
                else -> selectedSubCategory
            }

            // Save Button
            val parsedCentavos = remember(amountInput) {
                SmsMessageParser.parseAmount(amountInput)
            }
            val isValid = titleInput.isNotBlank() && parsedCentavos > 0L
            SaanNaPuntaButton(
                text = "Save bill",
                enabled = isValid,
                onClick = {
                    val centavos = parsedCentavos

                    val cal = Calendar.getInstance().apply {
                        val today = get(Calendar.DAY_OF_MONTH)
                        set(Calendar.DAY_OF_MONTH, dueDayOfMonth.coerceAtMost(getActualMaximum(Calendar.DAY_OF_MONTH)))
                        set(Calendar.HOUR_OF_DAY, 12)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        if (get(Calendar.DAY_OF_MONTH) < today && editingBill == null) {
                            add(Calendar.MONTH, 1)
                        }
                    }

                    val bill = UpcomingBill(
                        id = editingBill?.id ?: 0L,
                        title = titleInput.trim(),
                        amountCentavos = centavos,
                        dueDateTimestamp = cal.timeInMillis,
                        isPaid = false,
                        recurring = isRecurring,
                        category = "Utilities",
                        subCategory = resolvedSubCategory.ifBlank { null },
                        anchorDayOfMonth = dueDayOfMonth.coerceIn(1, 31)
                    )
                    repository.saveUpcomingBill(bill)
                    BillReminderScheduler.scheduleDailyCheck(context)

                    val needsNotifPerm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED

                    if (needsNotifPerm) {
                        showReminderPermissionPrompt = true
                    } else {
                        BillReminderScheduler.runCheckNow(context)
                        onDismiss()
                    }
                }
            )

            // Delete option if editing
            if (editingBill != null) {
                OutlinedButton(
                    onClick = {
                        repository.deleteUpcomingBill(editingBill.id)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, LineBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusOver)
                ) {
                    Text("Delete bill", color = StatusOver, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    if (showReminderPermissionPrompt) {
        AlertDialog(
            onDismissRequest = {
                showReminderPermissionPrompt = false
                onDismiss()
            },
            title = {
                Text(
                    text = "Remind me about bills?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Ink
                )
            },
            text = {
                Text(
                    text = "Allow notifications so SaanNaPunta can remind you before your bills are due and on the due day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            showReminderPermissionPrompt = false
                            onDismiss()
                        }
                    }
                ) {
                    Text("Remind me", color = Green700, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showReminderPermissionPrompt = false
                        onDismiss()
                    }
                ) {
                    Text("Not now", color = InkMuted)
                }
            },
            containerColor = SurfaceColor
        )
    }
}
