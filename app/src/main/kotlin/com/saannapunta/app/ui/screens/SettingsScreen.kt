package com.saannapunta.app.ui.screens

import com.saannapunta.app.ui.chito.ChitoCompanion
import com.saannapunta.app.ui.chito.ChitoLines
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.saannapunta.app.ui.categories.CategoryCustomizationActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.saannapunta.app.data.DataRepository
import com.saannapunta.app.data.model.PayCycleType
import com.saannapunta.app.security.BiometricLockManager
import com.saannapunta.app.ui.categories.CategoryIconCatalog
import com.saannapunta.app.ui.feedback.SoundFx
import com.saannapunta.app.ui.feedback.rememberUiFeedback
import com.saannapunta.app.ui.sheets.SmsBackfillSheet
import com.saannapunta.app.ui.theme.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun android.content.Context.findFragmentActivity(): androidx.fragment.app.FragmentActivity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is androidx.fragment.app.FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun SettingsScreen(
    repository: DataRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val lastBackupDate by repository.lastBackupDate.collectAsState()
    val payCycleConfig by repository.payCycleConfig.collectAsState()
    val userName by repository.userName.collectAsState()
    val biometricEnabled by repository.biometricLockEnabled.collectAsState()
    val widgetHideAmounts by repository.widgetHideAmounts.collectAsState()
    val widgetOneTapMode by repository.widgetOneTapMode.collectAsState()
    val billReminderDaysBefore by repository.billReminderDaysBefore.collectAsState()
    val uiSoundsEnabled by repository.uiSoundsEnabled.collectAsState()
    val feedback = rememberUiFeedback()

    val postNotificationsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            com.saannapunta.app.reminders.BillReminderScheduler.scheduleDailyCheck(context)
            com.saannapunta.app.reminders.BillReminderScheduler.runCheckNow(context)
            Toast.makeText(context, "Bill reminders enabled", Toast.LENGTH_SHORT).show()
        }
    }

    var hasSmsReceivePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasSmsReadPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasNotificationAccess by remember {
        mutableStateOf(
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        )
    }

    // Re-check permissions when returning from system Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasSmsReceivePermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECEIVE_SMS
                ) == PackageManager.PERMISSION_GRANTED
                hasSmsReadPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_SMS
                ) == PackageManager.PERMISSION_GRANTED
                hasNotificationAccess = NotificationManagerCompat
                    .getEnabledListenerPackages(context)
                    .contains(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // SMS Receive Permission Launcher
    val smsReceivePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.RECEIVE_SMS] == true
        hasSmsReceivePermission = granted
        val msg = if (granted) "SMS permission granted" else "If blocked, allow Restricted Settings in App Info first"
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }

    // SMS Read Permission Launcher for Backfill Scanner
    var showSmsBackfillSheet by remember { mutableStateOf(false) }
    val smsReadPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasSmsReadPermission = granted
        if (granted) {
            showSmsBackfillSheet = true
        } else {
            Toast.makeText(context, "Read SMS permission is required to scan inbox", Toast.LENGTH_LONG).show()
        }
    }

    // Native SAF Launchers: JSON Export, JSON Import, CSV Export
    val dateTag = remember { SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val json = repository.exportBackupJson()
            coroutineScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val outStream = context.contentResolver.openOutputStream(uri)
                            ?: throw IllegalStateException("Could not open destination file")
                        outStream.use { stream ->
                            stream.write(json.toByteArray(Charsets.UTF_8))
                            stream.flush()
                        }
                    }
                    repository.recordBackupFileWritten()
                    feedback.success()
                    Toast.makeText(context, "JSON backup saved successfully!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to write backup: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val text = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
                        } ?: ""
                    }
                    val result = repository.importBackupJsonDetailed(text)
                    if (result.success) {
                        val msg = if (result.skippedOrphanedTransactions > 0) {
                            "Backup restored (${result.skippedOrphanedTransactions} orphaned transaction(s) skipped)"
                        } else {
                            "Backup restored successfully!"
                        }
                        feedback.success()
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    } else {
                        feedback.error()
                        Toast.makeText(
                            context,
                            result.errorMessage ?: "Invalid backup file",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to read backup: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            val csv = repository.exportTransactionsCsv()
            coroutineScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            stream.write(csv.toByteArray(Charsets.UTF_8))
                        }
                    }
                    Toast.makeText(context, "Transactions CSV exported successfully!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to write CSV: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    var showStartFreshDialog by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var editNameInput by remember(userName) { mutableStateOf(userName) }
    var showEditPayCycleDialog by remember { mutableStateOf(false) }

    var weekendRule by remember(payCycleConfig) { mutableStateOf(payCycleConfig.payFridayBeforeWeekend) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Canvas),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = Ink
                )
            }
        }

        item {
            ChitoCompanion(line = ChitoLines.settings(lastBackupDate), size = 64.dp)
        }

        // Section 1: Financial Planning & Categories
        item {
            Text("Financial planning & categories", style = MaterialTheme.typography.titleSmall, color = Ink)
        }

        // Card 1: Category Customization
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.startActivity(Intent(context, CategoryCustomizationActivity::class.java))
                    },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(LineBorder))
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Green100),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = CategoryIconCatalog.getIcon(null),
                                contentDescription = null,
                                tint = Green700,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Customize categories & subcategories",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = Ink
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Manage icons, colors, and subcategories like Kuryente or Tubig",
                                style = MaterialTheme.typography.labelSmall,
                                color = InkMuted
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open",
                        tint = InkMuted,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Card 2: Profile & Pay Cycle
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(LineBorder))
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // User Name
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Your name", style = MaterialTheme.typography.bodyMedium, color = Ink)
                            Text(userName.ifBlank { "Not set" }, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = Ink)
                        }
                        OutlinedButton(
                            onClick = { showEditNameDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, LineBorder)
                        ) {
                            Text("Edit", color = Ink, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    HorizontalDivider(color = LineBorder, thickness = 1.dp)

                    // Current Cycle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Pay cycle schedule", style = MaterialTheme.typography.bodyMedium, color = Ink)
                            val cycleDisplayName = when (payCycleConfig.type) {
                                PayCycleType.SEMI_MONTHLY_15_30 -> "Semi-monthly (${payCycleConfig.firstPayday}th & ${payCycleConfig.secondPayday}th)"
                                PayCycleType.MONTHLY_END -> "Monthly (End of month)"
                                PayCycleType.WEEKLY -> "Weekly (7-day cycle)"
                                PayCycleType.FLEXIBLE -> "Flexible / No fixed income"
                            }
                            Text(cycleDisplayName, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = Ink)
                        }
                        OutlinedButton(
                            onClick = { showEditPayCycleDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, LineBorder)
                        ) {
                            Text("Edit", color = Ink, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    HorizontalDivider(color = LineBorder, thickness = 1.dp)

                    // Weekend Rule
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Weekend rule", style = MaterialTheme.typography.bodyMedium, color = Ink)
                            Text("If payday falls on a weekend, count Friday before", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                        }
                        Switch(
                            checked = weekendRule,
                            onCheckedChange = {
                                weekendRule = it
                                repository.updatePayCycleSettings(
                                    payCycleConfig.firstPayday,
                                    payCycleConfig.secondPayday,
                                    it,
                                    payCycleConfig.type
                                )
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = SurfaceColor, checkedTrackColor = Green600)
                        )
                    }
                }
            }
        }

        // Section 2: Ingestion / SMS / Notifications
        item {
            Text("Automatic expense logging", style = MaterialTheme.typography.titleSmall, color = Ink)
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(LineBorder))
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // SMS Receiver Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("SMS reader", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Ink)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                if (hasSmsReceivePermission) "Reading GCash, Maya, and bank SMS" else "Disabled: tap to grant SMS permission",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (hasSmsReceivePermission) Green700 else InkMuted
                            )
                        }
                        Switch(
                            checked = hasSmsReceivePermission,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    smsReceivePermissionLauncher.launch(
                                        arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
                                    )
                                } else {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = android.net.Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = SurfaceColor, checkedTrackColor = Green600)
                        )
                    }

                    HorizontalDivider(color = LineBorder, thickness = 1.dp)

                    // SMS Inbox Backfill (Last 30 Days) Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Scan SMS inbox (last 30 days)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Ink)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Auto-detect and import past transactions from GCash, Maya, and banks",
                                style = MaterialTheme.typography.labelSmall,
                                color = InkMuted
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = {
                                if (hasSmsReadPermission) {
                                    showSmsBackfillSheet = true
                                } else {
                                    smsReadPermissionLauncher.launch(Manifest.permission.READ_SMS)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Green600),
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Inbox, contentDescription = null, tint = SurfaceColor, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Scan", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = SurfaceColor)
                        }
                    }

                    HorizontalDivider(color = LineBorder, thickness = 1.dp)

                    // Notification Listener Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Push notification reader", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Ink)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                if (hasNotificationAccess) "Parsing payment app notifications" else "Disabled: tap to open System settings",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (hasNotificationAccess) Green700 else InkMuted
                            )
                        }
                        Switch(
                            checked = hasNotificationAccess,
                            onCheckedChange = {
                                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                context.startActivity(intent)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = SurfaceColor, checkedTrackColor = Green600)
                        )
                    }
                }
            }
        }

        // Section 3: Security & Privacy (Biometric Lock)
        item {
            Text("Security and privacy", style = MaterialTheme.typography.titleSmall, color = Ink)
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(LineBorder))
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Green100),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = Green700, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Biometric app lock", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Ink)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "Require fingerprint, face, or device PIN on launch",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = InkMuted
                                )
                            }
                        }

                        Switch(
                            checked = biometricEnabled,
                            onCheckedChange = { checked ->
                                val activity = context.findFragmentActivity()
                                if (checked) {
                                    if (activity != null && BiometricLockManager.canAuthenticate(context)) {
                                        BiometricLockManager.authenticate(
                                            activity = activity,
                                            title = "Enable App Lock",
                                            subtitle = "Verify fingerprint or PIN to enable lock",
                                            onSuccess = {
                                                feedback.success()
                                                repository.setBiometricLockEnabled(true)
                                                Toast.makeText(context, "App lock enabled", Toast.LENGTH_SHORT).show()
                                            },
                                            onError = { err ->
                                                Toast.makeText(context, "Authentication required: $err", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    } else {
                                        val reason = BiometricLockManager.getStatusMessage(context)
                                        Toast.makeText(context, reason, Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    feedback.toggle(false)
                                    repository.setBiometricLockEnabled(false)
                                    Toast.makeText(context, "App lock disabled", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = SurfaceColor, checkedTrackColor = Green600)
                        )
                    }

                    HorizontalDivider(color = LineBorder, thickness = 1.dp)

                    // Widget privacy: Always hide amounts on widget
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Always hide amounts on widget",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Ink
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Show 'Tap to view' on the home screen widget (automatic when app lock is on)",
                                style = MaterialTheme.typography.labelSmall,
                                color = InkMuted
                            )
                        }
                        Switch(
                            checked = widgetHideAmounts || biometricEnabled,
                            enabled = !biometricEnabled,
                            onCheckedChange = { checked ->
                                feedback.toggle(checked)
                                repository.setWidgetHideAmounts(checked)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = SurfaceColor, checkedTrackColor = Green600)
                        )
                    }

                    HorizontalDivider(color = LineBorder, thickness = 1.dp)

                    // Widget One-Tap Mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Widget one-tap preset mode",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Ink
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Preset buttons (₱20, ₱50, ₱100) log immediately using your most frequent category and show an Undo notification",
                                style = MaterialTheme.typography.labelSmall,
                                color = InkMuted
                            )
                        }
                        Switch(
                            checked = widgetOneTapMode,
                            onCheckedChange = { checked ->
                                feedback.toggle(checked)
                                repository.setWidgetOneTapMode(checked)
                                if (checked && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (!granted) {
                                        postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = SurfaceColor, checkedTrackColor = Green600)
                        )
                    }

                    HorizontalDivider(color = LineBorder, thickness = 1.dp)

                    // Bill Reminder Lead Time (N days before)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Bill reminders (8:00 AM Manila)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Ink
                        )
                        Text(
                            "Remind me N days before due date, on the due day, and weekly if overdue",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkMuted
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(1, 3, 5, 7).forEach { days ->
                                val isChosen = billReminderDaysBefore == days
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isChosen) Green100 else SurfaceColor)
                                        .border(1.dp, if (isChosen) Green600 else LineBorder, RoundedCornerShape(10.dp))
                                        .clickable {
                                            feedback.select()
                                            repository.setBillReminderDaysBefore(days)
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                                val granted = ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.POST_NOTIFICATIONS
                                                ) == PackageManager.PERMISSION_GRANTED
                                                if (!granted) {
                                                    postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                }
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${days}d before",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (isChosen) Green700 else Ink
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 4: Data, Storage & Native SAF File Backup
        item {
            Text("Data and storage", style = MaterialTheme.typography.titleSmall, color = Ink)
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(LineBorder))
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column {
                        Text("Stored on this phone (Offline-first)", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = Ink)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Zero internet access. All transactions and budgets reside safely in local storage on this device.", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                    }

                    HorizontalDivider(color = LineBorder, thickness = 1.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Last backup", style = MaterialTheme.typography.bodyMedium, color = Ink)
                            Text(lastBackupDate, style = MaterialTheme.typography.labelSmall, color = InkMuted)
                        }
                    }

                    // Native SAF Buttons: Export JSON File & Restore JSON File
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                exportJsonLauncher.launch("saannapunta_backup_$dateTag.json")
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Green600),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.FileUpload, contentDescription = null, tint = Green700, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export Backup", color = Green700, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
                        }

                        OutlinedButton(
                            onClick = {
                                importJsonLauncher.launch(arrayOf("application/json", "*/*"))
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, LineBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.FileDownload, contentDescription = null, tint = Ink, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restore Backup", color = Ink, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    // CSV Export Button
                    OutlinedButton(
                        onClick = {
                            exportCsvLauncher.launch("saannapunta_transactions_$dateTag.csv")
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, LineBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Export Transactions as CSV (Excel / Sheets)", color = Ink, style = MaterialTheme.typography.labelSmall)
                    }

                    HorizontalDivider(color = LineBorder, thickness = 1.dp)

                    // Start Fresh / Clear Data
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Start fresh (₱0 balances)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Ink)
                            Text("Clear all transactions and reset balances to ₱0", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { showStartFreshDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, StatusOver)
                        ) {
                            Text("Clear data", color = StatusOver, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // Section 5: Sounds and haptics
        item {
            Text("Sounds and feel", style = MaterialTheme.typography.titleSmall, color = Ink)
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(LineBorder))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sounds", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Ink)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Soft taps and chimes. Quiet when your phone is on silent or vibrate.",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkMuted
                        )
                    }
                    Switch(
                        checked = uiSoundsEnabled,
                        onCheckedChange = { checked ->
                            repository.setUiSoundsEnabled(checked)
                            SoundFx.enabled = checked
                            feedback.toggle(checked)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = SurfaceColor, checkedTrackColor = Green600)
                    )
                }
            }
        }

        // Section 6: About & Trademark Disclaimer
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Green50),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(LineBorder))
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("SaanNaPunta v0.2", style = MaterialTheme.typography.titleSmall, color = Ink)
                    Text(
                        text = "Bank and e-wallet names and logos belong to their owners. SaanNaPunta is not affiliated with them.",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted
                    )
                }
            }
        }
    }

    // Sheet: SMS Inbox Backfill
    if (showSmsBackfillSheet) {
        SmsBackfillSheet(
            repository = repository,
            onDismiss = { showSmsBackfillSheet = false }
        )
    }

    // Dialog: Start Fresh (Clear Data)
    if (showStartFreshDialog) {
        AlertDialog(
            onDismissRequest = { showStartFreshDialog = false },
            title = { Text("Clear All Transactions?", style = MaterialTheme.typography.titleMedium, color = StatusOver) },
            text = {
                Text(
                    "This will delete all transactions and reset balances to zero, giving you a clean slate to track your real money. Pay cycle and profile settings will be preserved.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        repository.startFreshPreserveSetup()
                        showStartFreshDialog = false
                        Toast.makeText(context, "Transactions cleared & balances reset to ₱0!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Clear All Data", color = StatusOver, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartFreshDialog = false }) {
                    Text("Cancel", color = InkMuted)
                }
            },
            containerColor = SurfaceColor
        )
    }

    // Dialog: Edit Name
    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Edit Display Name", style = MaterialTheme.typography.titleMedium, color = Ink) },
            text = {
                OutlinedTextField(
                    value = editNameInput,
                    onValueChange = { editNameInput = it },
                    label = { Text("Your name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editNameInput.isNotBlank()) {
                            repository.updateUserName(editNameInput.trim())
                            showEditNameDialog = false
                        }
                    }
                ) {
                    Text("Save", color = Green700, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancel", color = InkMuted)
                }
            },
            containerColor = SurfaceColor
        )
    }

    // Dialog: Edit Pay Cycle Schedule
    if (showEditPayCycleDialog) {
        var selectedType by remember(payCycleConfig) { mutableStateOf(payCycleConfig.type) }
        var firstDayInput by remember(payCycleConfig) { mutableStateOf(payCycleConfig.firstPayday.toString()) }
        var secondDayInput by remember(payCycleConfig) { mutableStateOf(payCycleConfig.secondPayday.toString()) }

        AlertDialog(
            onDismissRequest = { showEditPayCycleDialog = false },
            title = { Text("Pay Cycle Schedule", style = MaterialTheme.typography.titleMedium, color = Ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val presets = listOf(
                        Triple("15th & 30th (Semi-monthly)", PayCycleType.SEMI_MONTHLY_15_30, Pair(15, 30)),
                        Triple("10th & 25th (Semi-monthly)", PayCycleType.SEMI_MONTHLY_15_30, Pair(10, 25)),
                        Triple("5th & 20th (Semi-monthly)", PayCycleType.SEMI_MONTHLY_15_30, Pair(5, 20)),
                        Triple("End of Month (Monthly)", PayCycleType.MONTHLY_END, Pair(15, 30)),
                        Triple("Weekly (Baon / Allowance · 7 days)", PayCycleType.WEEKLY, Pair(2, 30)),
                        Triple("Flexible / No Fixed Income (Monthly pace)", PayCycleType.FLEXIBLE, Pair(1, 30))
                    )
                    presets.forEach { (label, type, days) ->
                        val isChosen = selectedType == type &&
                            (type != PayCycleType.SEMI_MONTHLY_15_30 || (firstDayInput == days.first.toString() && secondDayInput == days.second.toString()))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, if (isChosen) Green600 else LineBorder, RoundedCornerShape(12.dp))
                            .clickable {
                                selectedType = type
                                firstDayInput = days.first.toString()
                                secondDayInput = days.second.toString()
                            },
                            color = if (isChosen) Green50 else SurfaceColor
                        ) {
                            Text(
                                text = label,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isChosen) FontWeight.SemiBold else FontWeight.Normal),
                                color = if (isChosen) Green700 else Ink
                            )
                        }
                    }

                    if (selectedType == PayCycleType.SEMI_MONTHLY_15_30) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = firstDayInput,
                                onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) firstDayInput = it },
                                label = { Text("1st Payday") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = secondDayInput,
                                onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) secondDayInput = it },
                                label = { Text("2nd Payday") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val p1 = (firstDayInput.toIntOrNull() ?: 15).coerceIn(1, 28)
                        val p2 = (secondDayInput.toIntOrNull() ?: 30).coerceIn(p1 + 1, 31)
                        repository.updatePayCycleSettings(
                            firstDay = p1,
                            secondDay = p2,
                            weekendRule = weekendRule,
                            cycleType = selectedType
                        )
                        showEditPayCycleDialog = false
                        Toast.makeText(context, "Pay cycle updated!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Save", color = Green700, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditPayCycleDialog = false }) {
                    Text("Cancel", color = InkMuted)
                }
            },
            containerColor = SurfaceColor
        )
    }
}
