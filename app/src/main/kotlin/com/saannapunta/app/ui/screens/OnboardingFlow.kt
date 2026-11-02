package com.saannapunta.app.ui.screens

import com.saannapunta.app.ui.components.PesoAmountTransformation
import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import com.saannapunta.app.data.ProviderInfo
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.saannapunta.app.R
import com.saannapunta.app.data.DataRepository
import com.saannapunta.app.data.OnboardingSetup
import com.saannapunta.app.data.ProviderCatalog
import com.saannapunta.app.data.model.*
import com.saannapunta.app.parser.PaymentNotificationListenerService
import com.saannapunta.app.ui.components.AccountLogoTile
import com.saannapunta.app.ui.components.SaanNaPuntaButton
import com.saannapunta.app.ui.feedback.SoundFx
import com.saannapunta.app.ui.feedback.UiFeedback
import com.saannapunta.app.ui.feedback.rememberUiFeedback
import com.saannapunta.app.ui.theme.*
import java.util.Calendar

private data class EditableBudgetRow(
    val category: String,
    val limitPesosStr: String,
    val examples: String
)

/** Onboarding steps in order. Steps between [CHITO] and [BUDGET] show the "n of 5" progress line. */
private enum class Step { WELCOME, CHITO, CYCLE, ACCOUNTS, PERMISSIONS, BUDGET, DONE }

private const val PROGRESS_STEPS = 5

private data class PermissionState(
    val sms: Boolean,
    val notificationAccess: Boolean,
    val postNotifications: Boolean
)

private fun readPermissionState(context: Context): PermissionState {
    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    return PermissionState(
        sms = granted(Manifest.permission.RECEIVE_SMS) && granted(Manifest.permission.READ_SMS),
        notificationAccess = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName),
        postNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            granted(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    )
}

private fun openNotificationAccessSettings(context: Context) {
    val component = ComponentName(context, PaymentNotificationListenerService::class.java)
    val direct = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
    } else null
    val fallback = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    runCatching { context.startActivity(direct ?: fallback) }
        .onFailure { runCatching { context.startActivity(fallback) } }
}

private fun openAppInfo(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        )
    }
}

@Composable
fun OnboardingScreen(
    repository: DataRepository,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val feedback = rememberUiFeedback()
    val uiSoundsEnabled by repository.uiSoundsEnabled.collectAsState()

    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    val step = Step.entries[stepIndex]
    var goingForward by remember { mutableStateOf(true) }

    fun goTo(target: Step) {
        if (target == step) return
        goingForward = target.ordinal > step.ordinal
        if (goingForward) feedback.next() else feedback.back()
        stepIndex = target.ordinal
    }

    // Profile
    var nameInput by rememberSaveable { mutableStateOf("") }

    // Pay cycle. 0 = May sahod, 1 = Baon / allowance, 2 = Flexible / no fixed income
    var lifestyleMode by rememberSaveable { mutableIntStateOf(0) }
    var cycleType by rememberSaveable { mutableStateOf(PayCycleType.SEMI_MONTHLY_15_30) }
    var firstPayday by rememberSaveable { mutableIntStateOf(15) }
    var secondPayday by rememberSaveable { mutableIntStateOf(30) }
    var weekendRule by rememberSaveable { mutableStateOf(true) }
    var useCustomPaydays by rememberSaveable { mutableStateOf(false) }

    val currentCycleConfig = remember(cycleType, firstPayday, secondPayday, weekendRule) {
        PayCycleConfig(cycleType, firstPayday, secondPayday, weekendRule)
    }
    val previewNextPayday = remember(currentCycleConfig) { repository.previewNextPayday(currentCycleConfig) }
    val previewDaysLeft = remember(currentCycleConfig) { repository.previewDaysLeft(currentCycleConfig) }

    // Accounts: provider name -> opening balance in pesos
    var accountSearchQuery by remember { mutableStateOf("") }
    val selectedAccountsMap = remember {
        mutableStateMapOf("Cash" to "", "GCash" to "", "Maya" to "")
    }

    // Permissions
    var permissions by remember { mutableStateOf(readPermissionState(context)) }

    // Budget
    var selectedPreset by remember { mutableStateOf("sahod") }
    var totalCeilingInput by remember { mutableStateOf("15000") }
    val budgetRows = remember {
        mutableStateListOf(
            EditableBudgetRow("Food & Dining", "6000", "Meals, groceries, snacks"),
            EditableBudgetRow("Utilities", "3500", "Electricity, water, internet, load"),
            EditableBudgetRow("Transportation", "2500", "Jeep, Grab, gas, commute"),
            EditableBudgetRow("Personal", "3000", "Essentials, shopping, medicine")
        )
    }
    var includeSavingsGoal by remember { mutableStateOf(true) }
    var goalNameInput by remember { mutableStateOf("Emergency Fund") }
    var goalTargetInput by remember { mutableStateOf("10000") }

    fun applyBudgetPreset(preset: String, ceilingPesos: Long) {
        selectedPreset = preset
        budgetRows.clear()
        when (preset) {
            "sahod" -> {
                val base = if (ceilingPesos > 0) ceilingPesos else 15000L
                totalCeilingInput = base.toString()
                budgetRows.add(EditableBudgetRow("Food & Dining", ((base * 40) / 100).toString(), "Groceries, meals, dining"))
                budgetRows.add(EditableBudgetRow("Utilities", ((base * 22) / 100).toString(), "Meralco, water, internet, load"))
                budgetRows.add(EditableBudgetRow("Transportation", ((base * 18) / 100).toString(), "Commute, gas, Grab"))
                budgetRows.add(EditableBudgetRow("Personal", ((base * 20) / 100).toString(), "Shopping, self-care, health"))
                includeSavingsGoal = true
                goalNameInput = "Emergency Fund"
                goalTargetInput = "10000"
            }
            "student" -> {
                val base = if (ceilingPesos > 0) ceilingPesos else 4000L
                totalCeilingInput = base.toString()
                budgetRows.add(EditableBudgetRow("Food & Baon", ((base * 45) / 100).toString(), "Canteen, meals, snacks"))
                budgetRows.add(EditableBudgetRow("Transportation", ((base * 25) / 100).toString(), "Jeep, tricycle, LRT/MRT"))
                budgetRows.add(EditableBudgetRow("School & Load", ((base * 20) / 100).toString(), "Printing, supplies, mobile data"))
                budgetRows.add(EditableBudgetRow("Personal", ((base * 10) / 100).toString(), "Hangouts, extras"))
                includeSavingsGoal = true
                goalNameInput = "Ipon / Savings"
                goalTargetInput = "3000"
            }
            "essentials" -> {
                val base = if (ceilingPesos > 0) ceilingPesos else 5000L
                totalCeilingInput = base.toString()
                budgetRows.add(EditableBudgetRow("Food & Groceries", ((base * 55) / 100).toString(), "Palengke, rice, daily meals"))
                budgetRows.add(EditableBudgetRow("Bills & Load", ((base * 25) / 100).toString(), "Electricity, water, data load"))
                budgetRows.add(EditableBudgetRow("Transport & Needs", ((base * 20) / 100).toString(), "Fare, medicine, essentials"))
                includeSavingsGoal = true
                goalNameInput = "Buffer / Emergency"
                goalTargetInput = "2000"
            }
            "wallet_pace" -> {
                // No category limits: the app paces directly from the wallet balance
                totalCeilingInput = "0"
            }
        }
    }

    fun redistributeTotalAcrossRows(newTotalPesos: Long) {
        if (budgetRows.isEmpty() || newTotalPesos <= 0L) return
        val currentSum = budgetRows.sumOf { it.limitPesosStr.toDoubleOrNull()?.toLong() ?: 0L }
        val updated = if (currentSum > 0L) {
            budgetRows.map { row ->
                val oldVal = row.limitPesosStr.toDoubleOrNull()?.toLong() ?: 0L
                row.copy(limitPesosStr = ((newTotalPesos * oldVal) / currentSum).toString())
            }
        } else {
            val perRow = newTotalPesos / budgetRows.size
            budgetRows.map { it.copy(limitPesosStr = perRow.toString()) }
        }
        budgetRows.clear()
        budgetRows.addAll(updated)
    }

    fun buildSetup(): OnboardingSetup {
        val accountsList = selectedAccountsMap.mapNotNull { (name, balanceStr) ->
            val info = ProviderCatalog.byName(name) ?: return@mapNotNull null
            Pair(info, Math.round((balanceStr.toDoubleOrNull() ?: 0.0) * 100.0))
        }.ifEmpty {
            listOf(Pair(ProviderCatalog.byName("Cash") ?: ProviderCatalog.all.first(), 0L))
        }
        val finalBudgets = budgetRows.mapNotNull { row ->
            val cat = row.category.trim()
            val centavos = Math.round((row.limitPesosStr.toDoubleOrNull() ?: 0.0) * 100.0)
            if (cat.isNotBlank() && centavos > 0L) {
                CategoryBudget(category = cat, limitCentavos = centavos, examples = row.examples.trim().ifBlank { "Custom budget" })
            } else null
        }
        val finalGoals = if (includeSavingsGoal && goalNameInput.isNotBlank()) {
            val targetCentavos = Math.round((goalTargetInput.toDoubleOrNull() ?: 0.0) * 100.0)
            if (targetCentavos > 0L) {
                listOf(SavingsGoal(name = goalNameInput.trim(), targetCentavos = targetCentavos, savedCentavos = 0L))
            } else emptyList()
        } else emptyList()

        return OnboardingSetup(
            name = nameInput.trim().ifBlank { "Friend" },
            cycleType = cycleType,
            firstPayday = firstPayday,
            secondPayday = secondPayday,
            weekendRule = weekendRule,
            accounts = accountsList,
            budgets = finalBudgets,
            goals = finalGoals
        )
    }

    BackHandler(enabled = step != Step.WELCOME) {
        goTo(Step.entries[step.ordinal - 1])
    }

    // Done step plays the completion chime once when it appears
    LaunchedEffect(step) {
        if (step == Step.DONE) feedback.complete()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        OnboardingTopBar(
            step = step,
            onBack = { goTo(Step.entries[step.ordinal - 1]) },
            onSkip = { goTo(Step.DONE) }
        )

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val dir = if (goingForward) 1 else -1
                (slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { dir * it / 5 } +
                    fadeIn(tween(220, delayMillis = 40))) togetherWith
                    (slideOutHorizontally(tween(200)) { -dir * it / 6 } + fadeOut(tween(140)))
            },
            label = "OnboardingStep",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { target ->
            Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                when (target) {
                    Step.WELCOME -> WelcomeStep()
                    Step.CHITO -> ChitoStep(
                        name = nameInput,
                        onNameChange = { nameInput = it },
                        onDone = { goTo(Step.CYCLE) }
                    )
                    Step.CYCLE -> CycleStep(
                        lifestyleMode = lifestyleMode,
                        cycleType = cycleType,
                        firstPayday = firstPayday,
                        secondPayday = secondPayday,
                        weekendRule = weekendRule,
                        useCustomPaydays = useCustomPaydays,
                        previewNextPayday = previewNextPayday,
                        previewDaysLeft = previewDaysLeft,
                        feedback = feedback,
                        onModeChange = { modeIdx ->
                            lifestyleMode = modeIdx
                            useCustomPaydays = false
                            when (modeIdx) {
                                0 -> {
                                    cycleType = PayCycleType.SEMI_MONTHLY_15_30
                                    firstPayday = 15
                                    secondPayday = 30
                                    applyBudgetPreset("sahod", 15000L)
                                }
                                1 -> {
                                    cycleType = PayCycleType.WEEKLY
                                    firstPayday = Calendar.MONDAY
                                    secondPayday = 30
                                    applyBudgetPreset("student", 2500L)
                                }
                                2 -> {
                                    cycleType = PayCycleType.FLEXIBLE
                                    firstPayday = 1
                                    secondPayday = 30
                                    weekendRule = false
                                    applyBudgetPreset("essentials", 5000L)
                                }
                            }
                        },
                        onCycleChange = { type, p1, p2, custom ->
                            cycleType = type
                            firstPayday = p1
                            secondPayday = p2
                            useCustomPaydays = custom
                        },
                        onWeekendRuleChange = { weekendRule = it }
                    )
                    Step.ACCOUNTS -> AccountsStep(
                        query = accountSearchQuery,
                        onQueryChange = { accountSearchQuery = it },
                        selected = selectedAccountsMap,
                        feedback = feedback
                    )
                    Step.PERMISSIONS -> PermissionsStep(
                        permissions = permissions,
                        onPermissionsChanged = { updated ->
                            val newlyGranted = (updated.sms && !permissions.sms) ||
                                (updated.notificationAccess && !permissions.notificationAccess) ||
                                (updated.postNotifications && !permissions.postNotifications)
                            if (newlyGranted) feedback.granted()
                            permissions = updated
                        },
                        feedback = feedback
                    )
                    Step.BUDGET -> BudgetStep(
                        selectedPreset = selectedPreset,
                        totalCeilingInput = totalCeilingInput,
                        onTotalChange = { totalCeilingInput = it },
                        budgetRows = budgetRows,
                        walletSumPesos = selectedAccountsMap.values.sumOf { (it.toDoubleOrNull() ?: 0.0).toLong() },
                        daysLeft = previewDaysLeft,
                        includeSavingsGoal = includeSavingsGoal,
                        onIncludeSavingsGoalChange = { includeSavingsGoal = it },
                        goalName = goalNameInput,
                        onGoalNameChange = { goalNameInput = it },
                        goalTarget = goalTargetInput,
                        onGoalTargetChange = { goalTargetInput = it },
                        feedback = feedback,
                        onPreset = { key -> applyBudgetPreset(key, totalCeilingInput.toLongOrNull() ?: 0L) },
                        onAutoSplit = {
                            val target = totalCeilingInput.toLongOrNull() ?: 0L
                            if (budgetRows.isEmpty() && target > 0L) applyBudgetPreset("essentials", target)
                            else redistributeTotalAcrossRows(target)
                        },
                        onAddCategory = {
                            selectedPreset = "custom"
                            budgetRows.add(EditableBudgetRow("New Category", "1000", "Custom spending"))
                        },
                        onRowsEdited = {
                            totalCeilingInput = budgetRows.sumOf { (it.limitPesosStr.toDoubleOrNull() ?: 0.0).toLong() }.toString()
                        }
                    )
                    Step.DONE -> DoneStep(
                        name = nameInput.trim().ifBlank { "Friend" },
                        cycleType = cycleType,
                        firstPayday = firstPayday,
                        secondPayday = secondPayday,
                        accountCount = selectedAccountsMap.size,
                        budgetRows = budgetRows,
                        permissions = permissions,
                        soundsEnabled = uiSoundsEnabled,
                        onSoundsChange = { enabled ->
                            repository.setUiSoundsEnabled(enabled)
                            SoundFx.enabled = enabled
                            feedback.toggle(enabled)
                        }
                    )
                }
            }
        }

        // Fixed bottom actions: the step content slides, the buttons stay put
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val primaryLabel = when (step) {
                Step.WELCOME -> "Get started"
                Step.CHITO -> "Continue"
                Step.CYCLE -> "Continue"
                Step.ACCOUNTS -> when (val n = selectedAccountsMap.size) {
                    0 -> "Continue with cash only"
                    1 -> "Add 1 account"
                    else -> "Add $n accounts"
                }
                Step.PERMISSIONS -> "Continue"
                Step.BUDGET -> "Review setup"
                Step.DONE -> "Go to Home"
            }
            SaanNaPuntaButton(
                text = primaryLabel,
                onClick = {
                    if (step == Step.DONE) {
                        feedback.success()
                        repository.completeOnboarding(buildSetup())
                        onComplete()
                    } else {
                        goTo(Step.entries[step.ordinal + 1])
                    }
                }
            )
            if (step == Step.PERMISSIONS && !permissions.sms && !permissions.notificationAccess) {
                TextButton(
                    onClick = { goTo(Step.BUDGET) },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("I'll add transactions myself", color = Green700, style = MaterialTheme.typography.labelLarge)
                }
            }
            if (step == Step.WELCOME || step == Step.PERMISSIONS) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = InkFaint, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Stored on this phone", style = MaterialTheme.typography.labelSmall, color = InkFaint)
                }
            }
        }
    }
}

@Composable
private fun OnboardingTopBar(step: Step, onBack: () -> Unit, onSkip: () -> Unit) {
    val showProgress = step.ordinal in Step.CHITO.ordinal..Step.BUDGET.ordinal
    val progress by animateFloatAsState(
        targetValue = if (showProgress) step.ordinal / PROGRESS_STEPS.toFloat() else if (step == Step.DONE) 1f else 0f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "OnboardingProgress"
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp)
        ) {
            if (step != Step.WELCOME) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
                }
            }
            if (showProgress) {
                Text(
                    text = "${step.ordinal} of $PROGRESS_STEPS",
                    style = MaterialTheme.typography.labelLarge,
                    color = InkMuted,
                    modifier = Modifier.align(Alignment.Center)
                )
                TextButton(onClick = onSkip, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Text("Skip", style = MaterialTheme.typography.labelLarge, color = InkMuted)
                }
            }
        }
        if (step != Step.WELCOME) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = Green600,
                trackColor = LineBorder,
                drawStopIndicator = {}
            )
        }
    }
}

/** Chito settles in with one soft spring when a step appears; no looping motion. */
@Composable
private fun ChitoImage(@DrawableRes res: Int, size: Int, description: String) {
    val scale = remember { Animatable(0.88f) }
    LaunchedEffect(res) {
        scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
    }
    Image(
        painter = painterResource(id = res),
        contentDescription = description,
        modifier = Modifier
            .size(size.dp)
            .scale(scale.value)
    )
}

@Composable
private fun ChitoSays(message: String, @DrawableRes pose: Int = R.drawable.chito_thinking, imageSize: Int = 96) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(LineBorder))
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ChitoImage(pose, imageSize, "Chito")
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Green50)
                    .padding(12.dp)
            ) {
                Text("Chito says", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Green700)
                Spacer(modifier = Modifier.height(4.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium, color = Ink)
            }
        }
    }
}

@Composable
private fun StepHeader(title: String, subtitle: String) {
    // Own Column: grid items stack loose children on top of each other
    Column {
        Text(text = title, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = Ink)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(LineBorder))
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Green50),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Green700, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = Ink)
                Text(body, style = MaterialTheme.typography.bodySmall, color = InkMuted)
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        ChitoImage(R.drawable.chito_waving, 150, "Chito waving")
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "SaanNaPunta",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold),
            color = Ink
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "See where your money goes across your banks, e-wallets, and cash.",
            style = MaterialTheme.typography.bodyLarge,
            color = InkMuted,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FeatureRow(Icons.Outlined.Lock, "Stored on this phone", "Your money data stays on this device.")
            FeatureRow(Icons.Outlined.AccountBalanceWallet, "Fits sahod or baon", "Paces your spending until your next payday or allowance.")
            FeatureRow(Icons.Outlined.Sms, "Reads payment alerts", "Logs GCash, Maya, and bank texts for you, if you allow it.")
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ChitoStep(name: String, onNameChange: (String) -> Unit, onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 20.dp)
    ) {
        StepHeader(
            title = "Meet Chito, your money guide",
            subtitle = "SaanNaPunta tracks your daily spending so you don't run out before the next payday or allowance."
        )
        Spacer(modifier = Modifier.height(18.dp))
        ChitoSays(
            message = "Kumusta. Sahod man o baon, I'll show you how much is safe to spend each day.",
            pose = R.drawable.chito_waving,
            imageSize = 92
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text("What should Chito call you?", style = MaterialTheme.typography.titleSmall, color = Ink)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            placeholder = { Text("e.g. Juan or Maria") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onDone() }),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text("Optional. You can change it later in Settings.", style = MaterialTheme.typography.labelSmall, color = InkFaint)
    }
}

@Composable
private fun SegmentedTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Green50)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { idx, label ->
            val isSelected = idx == selected
            val bg by animateColorAsState(if (isSelected) SurfaceColor else Green50, tween(180), label = "TabBg")
            val fg by animateColorAsState(if (isSelected) Green700 else InkMuted, tween(180), label = "TabFg")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(bg)
                    .then(if (isSelected) Modifier.border(1.dp, LineBorder, RoundedCornerShape(11.dp)) else Modifier)
                    .clickable { onSelect(idx) }
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
                    color = fg,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun CycleStep(
    lifestyleMode: Int,
    cycleType: PayCycleType,
    firstPayday: Int,
    secondPayday: Int,
    weekendRule: Boolean,
    useCustomPaydays: Boolean,
    previewNextPayday: String,
    previewDaysLeft: Int,
    feedback: UiFeedback,
    onModeChange: (Int) -> Unit,
    onCycleChange: (PayCycleType, Int, Int, Boolean) -> Unit,
    onWeekendRuleChange: (Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 20.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            StepHeader(
                title = "When do you get your money?",
                subtitle = "Chito paces your spending until the next one. You can change this in Settings."
            )
            Spacer(modifier = Modifier.height(14.dp))
            SegmentedTabs(
                options = listOf("May sahod", "Baon / allowance", "Flexible"),
                selected = lifestyleMode,
                onSelect = { idx ->
                    if (idx != lifestyleMode) {
                        feedback.select()
                        onModeChange(idx)
                    }
                }
            )
        }

        when (lifestyleMode) {
            0 -> {
                val options = listOf(
                    Triple("15th and 30th", 15 to 30, PayCycleType.SEMI_MONTHLY_15_30),
                    Triple("10th and 25th", 10 to 25, PayCycleType.SEMI_MONTHLY_15_30),
                    Triple("5th and 20th", 5 to 20, PayCycleType.SEMI_MONTHLY_15_30),
                    Triple("Monthly (end of month)", 1 to 30, PayCycleType.MONTHLY_END)
                )
                items(options) { (label, pair, type) ->
                    val isSelected = !useCustomPaydays && cycleType == type &&
                        (type == PayCycleType.MONTHLY_END || (firstPayday == pair.first && secondPayday == pair.second))
                    CycleOptionCard(
                        title = label,
                        subtitle = if (type == PayCycleType.MONTHLY_END) "One payday a month" else "Twice a month",
                        badge = if (pair.first == 15 && type == PayCycleType.SEMI_MONTHLY_15_30) "Common" else null,
                        isSelected = isSelected,
                        onClick = {
                            feedback.select()
                            onCycleChange(type, pair.first, pair.second, false)
                        }
                    )
                }
                item {
                    CycleOptionCard(
                        title = "Other dates",
                        subtitle = "Pick your own 1st and 2nd payday",
                        isSelected = useCustomPaydays,
                        onClick = {
                            feedback.select()
                            onCycleChange(PayCycleType.SEMI_MONTHLY_15_30, firstPayday, secondPayday, true)
                        }
                    )
                    AnimatedVisibility(visible = useCustomPaydays, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = firstPayday.toString(),
                                onValueChange = { v -> v.toIntOrNull()?.let { if (it in 1..28) onCycleChange(cycleType, it, secondPayday, true) } },
                                label = { Text("1st payday (1-28)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = secondPayday.toString(),
                                onValueChange = { v -> v.toIntOrNull()?.let { if (it in 2..31) onCycleChange(cycleType, firstPayday, it, true) } },
                                label = { Text("2nd payday (2-31)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(LineBorder))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Weekend rule", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Ink)
                                Text("If payday falls on a weekend, count the Friday before", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                            }
                            Switch(
                                checked = weekendRule,
                                onCheckedChange = {
                                    feedback.toggle(it)
                                    onWeekendRuleChange(it)
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = SurfaceColor, checkedTrackColor = Green600)
                            )
                        }
                    }
                }
            }
            1 -> {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CycleOptionCard(
                            title = "Every week",
                            subtitle = "Resets every Monday. Good for weekly baon or padala.",
                            isSelected = cycleType == PayCycleType.WEEKLY,
                            onClick = {
                                feedback.select()
                                onCycleChange(PayCycleType.WEEKLY, Calendar.MONDAY, secondPayday, false)
                            }
                        )
                        CycleOptionCard(
                            title = "Twice a month (15th and 30th)",
                            subtitle = "Semi-monthly allowance",
                            isSelected = cycleType == PayCycleType.SEMI_MONTHLY_15_30,
                            onClick = {
                                feedback.select()
                                onCycleChange(PayCycleType.SEMI_MONTHLY_15_30, 15, 30, false)
                            }
                        )
                        CycleOptionCard(
                            title = "Once a month",
                            subtitle = "One allowance at the end of the month",
                            isSelected = cycleType == PayCycleType.MONTHLY_END,
                            onClick = {
                                feedback.select()
                                onCycleChange(PayCycleType.MONTHLY_END, firstPayday, secondPayday, false)
                            }
                        )
                    }
                }
            }
            else -> {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ChitoSays(
                            message = "No fixed payday? Freelance, raket, or living on savings, I'll pace your money over a rolling cycle.",
                            pose = R.drawable.chito_thinking,
                            imageSize = 64
                        )
                        CycleOptionCard(
                            title = "Monthly pace",
                            subtitle = "Tracks spending month by month",
                            isSelected = cycleType == PayCycleType.FLEXIBLE,
                            onClick = {
                                feedback.select()
                                onCycleChange(PayCycleType.FLEXIBLE, 1, secondPayday, false)
                            }
                        )
                        CycleOptionCard(
                            title = "Weekly pace",
                            subtitle = "One week at a time, resets every Monday",
                            isSelected = cycleType == PayCycleType.WEEKLY,
                            onClick = {
                                feedback.select()
                                onCycleChange(PayCycleType.WEEKLY, Calendar.MONDAY, secondPayday, false)
                            }
                        )
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Green50),
                border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(LineBorder))
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = Green700, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Next money day", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                        AnimatedContent(targetState = "$previewNextPayday · $previewDaysLeft days away", label = "NextPayday") { text ->
                            Text(text, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = Ink)
                        }
                    }
                }
            }
        }
    }
}

private val PICKER_TABS = listOf("Popular", "E-wallets", "Banks", "Digital banks")

@Composable
private fun AccountsStep(
    query: String,
    onQueryChange: (String) -> Unit,
    selected: MutableMap<String, String>,
    feedback: UiFeedback
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val shown = remember(query, tab) {
        when {
            query.isNotBlank() -> ProviderCatalog.all.filter { it.matches(query) }
            tab == 0 -> ProviderCatalog.popular
            else -> ProviderCatalog.all.filter { it.group == PICKER_TABS[tab] }
        }
    }
    // Popular first (Cash leads), then catalog order: stable as accounts are added and removed
    val selectedInfos = (ProviderCatalog.popular + ProviderCatalog.all).distinct().filter { selected.containsKey(it.name) }

    fun toggle(info: ProviderInfo) {
        if (selected.containsKey(info.name)) {
            selected.remove(info.name)
            feedback.toggle(false)
        } else {
            selected[info.name] = ""
            feedback.toggle(true)
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 20.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            StepHeader(
                title = "Where do you keep your money?",
                subtitle = "Tap the wallets and banks you use, then enter what's in each one now."
            )
        }

        if (selectedInfos.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "selected-title") {
                Text(
                    text = "Your accounts (${selectedInfos.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = Ink,
                    modifier = Modifier.animateItem()
                )
            }
            items(selectedInfos, key = { "selected-${it.name}" }, span = { GridItemSpan(maxLineSpan) }) { info ->
                SelectedAccountRow(
                    info = info,
                    balance = selected[info.name] ?: "",
                    onBalanceChange = { input ->
                        if (input.matches(Regex("^\\d*(\\.\\d{0,2})?$"))) selected[info.name] = input
                    },
                    onRemove = { toggle(info) },
                    modifier = Modifier.animateItem()
                )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }, key = "search") {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search, e.g. GCash, BDO, Landbank") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = InkMuted) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search", tint = InkMuted)
                        }
                    }
                } else null,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem(),
                shape = RoundedCornerShape(14.dp)
            )
        }

        if (query.isBlank()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "tabs") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .animateItem(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PICKER_TABS.forEachIndexed { idx, label ->
                        val isSel = tab == idx
                        val bg by animateColorAsState(if (isSel) Green600 else SurfaceColor, tween(180), label = "PickerTabBg")
                        val fg by animateColorAsState(if (isSel) SurfaceColor else Ink, tween(180), label = "PickerTabFg")
                        Box(
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .clip(CircleShape)
                                .background(bg)
                                .border(1.dp, if (isSel) Green600 else LineBorder, CircleShape)
                                .clickable {
                                    if (!isSel) {
                                        feedback.select()
                                        tab = idx
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
                        }
                    }
                }
            }
        }

        items(shown, key = { "tile-${it.name}" }) { info ->
            ProviderTile(
                info = info,
                isSelected = selected.containsKey(info.name),
                onClick = { toggle(info) },
                modifier = Modifier.animateItem()
            )
        }

        if (shown.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "no-match") {
                Text(
                    text = "No match for \"$query\". Pick Other bank under Banks and rename it later.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

/** One logo in the picker grid: tap to add, tap again to remove. Pops a little when picked. */
@Composable
private fun ProviderTile(
    info: ProviderInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pop = remember { Animatable(1f) }
    LaunchedEffect(isSelected) {
        if (isSelected) {
            pop.snapTo(0.86f)
            pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    val container by animateColorAsState(if (isSelected) Green50 else SurfaceColor, tween(180), label = "TileBg")
    val borderColor by animateColorAsState(if (isSelected) Green600 else LineBorder, tween(180), label = "TileBorder")

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClickLabel = if (isSelected) "Remove ${info.name}" else "Add ${info.name}") { onClick() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .size(64.dp)
                    .scale(pop.value)
                    .clip(RoundedCornerShape(18.dp))
                    .background(container)
                    .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                AccountLogoTile(iconResName = info.iconResName, fallbackName = info.name, size = 44)
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = isSelected,
                enter = scaleIn(spring(dampingRatio = 0.5f)) + fadeIn(),
                exit = fadeOut(tween(120))
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(Green600, CircleShape)
                        .border(2.dp, SurfaceColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = SurfaceColor, modifier = Modifier.size(13.dp))
                }
            }
        }
        Text(
            text = info.name,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal),
            color = if (isSelected) Green700 else Ink,
            textAlign = TextAlign.Center,
            maxLines = 2,
            minLines = 2
        )
    }
}

@Composable
private fun SelectedAccountRow(
    info: ProviderInfo,
    balance: String,
    onBalanceChange: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Green50),
        border = BorderStroke(1.dp, Green600)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AccountLogoTile(iconResName = info.iconResName, fallbackName = info.name, size = 36)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = info.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Ink,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = balance,
                onValueChange = onBalanceChange,
                placeholder = { Text("0", color = InkFaint) },
                prefix = { Text("₱", color = InkMuted) },
                visualTransformation = PesoAmountTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.width(140.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceColor,
                    unfocusedContainerColor = SurfaceColor
                )
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove ${info.name}", tint = InkMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun PermissionsStep(
    permissions: PermissionState,
    onPermissionsChanged: (PermissionState) -> Unit,
    feedback: UiFeedback
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showRestrictedHelp by rememberSaveable { mutableStateOf(false) }

    // Re-read when coming back from system settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onPermissionsChanged(readPermissionState(context))
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val updated = readPermissionState(context)
        if (!updated.sms) {
            // Sideloaded apps on Android 13+ get a silent denial until restricted settings are allowed
            showRestrictedHelp = true
            feedback.error()
        }
        onPermissionsChanged(updated)
    }
    val postLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onPermissionsChanged(readPermissionState(context))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 20.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StepHeader(
            title = "Log payments for you",
            subtitle = "SaanNaPunta can read texts and notifications from your banks and e-wallets to record transactions. Personal messages are ignored, and nothing leaves your phone."
        )
        Spacer(modifier = Modifier.height(2.dp))

        PermissionCard(
            icon = Icons.Outlined.Sms,
            title = "Bank and e-wallet texts",
            body = "Reads SMS from GCash, Maya, and banks only.",
            granted = permissions.sms,
            actionLabel = "Allow",
            onAction = {
                feedback.tap()
                smsLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS))
            }
        )
        PermissionCard(
            icon = Icons.Outlined.NotificationsActive,
            title = "Payment app notifications",
            body = "Reads alerts from payment apps. Turn on SaanNaPunta on the next screen.",
            granted = permissions.notificationAccess,
            actionLabel = "Open",
            onAction = {
                feedback.tap()
                openNotificationAccessSettings(context)
            }
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionCard(
                icon = Icons.Outlined.Notifications,
                title = "Bill reminders",
                body = "Lets the app remind you before a bill is due.",
                granted = permissions.postNotifications,
                actionLabel = "Allow",
                onAction = {
                    feedback.tap()
                    postLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            )
        }

        if (!permissions.sms || !permissions.notificationAccess) {
            TextButton(
                onClick = {
                    feedback.select()
                    showRestrictedHelp = !showRestrictedHelp
                },
                contentPadding = PaddingValues(horizontal = 0.dp)
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = Green700, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Button not working or setting greyed out?", color = Green700, style = MaterialTheme.typography.labelLarge)
            }
        }
        AnimatedVisibility(
            visible = showRestrictedHelp && (!permissions.sms || !permissions.notificationAccess),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Green50),
                border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(LineBorder))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Allow restricted settings", style = MaterialTheme.typography.titleSmall, color = Ink)
                    Text(
                        "Android blocks these permissions for apps installed outside the Play Store until you allow them:",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted
                    )
                    Text("1. Tap Open App info below", style = MaterialTheme.typography.bodySmall, color = Ink)
                    Text("2. Tap ⋮ at the top right", style = MaterialTheme.typography.bodySmall, color = Ink)
                    Text("3. Tap Allow restricted settings and confirm", style = MaterialTheme.typography.bodySmall, color = Ink)
                    Text("4. Come back here and tap Allow again", style = MaterialTheme.typography.bodySmall, color = Ink)
                    Text("The exact words differ a little between phones.", style = MaterialTheme.typography.labelSmall, color = InkFaint)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = {
                            feedback.tap()
                            openAppInfo(context)
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Green600),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Open App info", color = Green700, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        Text(
            "You can change these anytime in Settings.",
            style = MaterialTheme.typography.labelSmall,
            color = InkFaint
        )
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    body: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    val borderColor by animateColorAsState(if (granted) Green600 else LineBorder, tween(220), label = "PermBorder")
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Green50),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Green700, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = Ink)
                Text(body, style = MaterialTheme.typography.bodySmall, color = InkMuted)
            }
            Spacer(modifier = Modifier.width(8.dp))
            AnimatedContent(targetState = granted, label = "PermStatus") { isGranted ->
                if (isGranted) {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Green100)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Green700, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("On", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Green700)
                    }
                } else {
                    Button(
                        onClick = onAction,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Green600, contentColor = SurfaceColor),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.heightIn(min = 40.dp)
                    ) {
                        Text(actionLabel, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetStep(
    selectedPreset: String,
    totalCeilingInput: String,
    onTotalChange: (String) -> Unit,
    budgetRows: MutableList<EditableBudgetRow>,
    walletSumPesos: Long,
    daysLeft: Int,
    includeSavingsGoal: Boolean,
    onIncludeSavingsGoalChange: (Boolean) -> Unit,
    goalName: String,
    onGoalNameChange: (String) -> Unit,
    goalTarget: String,
    onGoalTargetChange: (String) -> Unit,
    feedback: UiFeedback,
    onPreset: (String) -> Unit,
    onAutoSplit: () -> Unit,
    onAddCategory: () -> Unit,
    onRowsEdited: () -> Unit
) {
    val allocatedSumPesos = budgetRows.sumOf { (it.limitPesosStr.toDoubleOrNull() ?: 0.0).toLong() }
    val effectivePool = if (allocatedSumPesos > 0L) allocatedSumPesos else walletSumPesos
    val safePerDay = if (daysLeft > 0) effectivePool / daysLeft else effectivePool
    val animatedSafe by animateFloatAsState(safePerDay.toFloat(), tween(300), label = "SafePerDay")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 20.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            StepHeader(
                title = "Your first budget",
                subtitle = "Start from a template and adjust, or skip limits and pace from your wallet balance."
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "sahod" to "Sahod starter",
                    "student" to "Student baon",
                    "essentials" to "Essentials only",
                    "wallet_pace" to "No limits"
                ).forEach { (key, label) ->
                    val isSel = selectedPreset == key
                    val bg by animateColorAsState(if (isSel) Green100 else SurfaceColor, tween(180), label = "ChipBg")
                    Box(
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .clip(CircleShape)
                            .background(bg)
                            .border(1.dp, if (isSel) Green600 else LineBorder, CircleShape)
                            .clickable {
                                feedback.select()
                                onPreset(key)
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium),
                            color = if (isSel) Green700 else InkMuted
                        )
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Green900)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Safe to spend per day", style = MaterialTheme.typography.labelMedium, color = OnHeroMuted)
                    Text(
                        text = "₱${"%,d".format(animatedSafe.toLong())}",
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                        color = OnHero
                    )
                    Text(
                        text = if (allocatedSumPesos > 0L) "₱${"%,d".format(allocatedSumPesos)} over $daysLeft days" else "Paced from your wallets over $daysLeft days",
                        style = MaterialTheme.typography.labelMedium,
                        color = OnHeroMuted
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = totalCeilingInput,
                    onValueChange = onTotalChange,
                    label = { Text("Total for this cycle") },
                    prefix = { Text("₱", color = Ink) },
                    visualTransformation = PesoAmountTransformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedButton(
                    onClick = {
                        feedback.tap()
                        onAutoSplit()
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Green600),
                    modifier = Modifier.heightIn(min = 52.dp)
                ) {
                    Text("Split", color = Green700, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Category budgets (${budgetRows.size})", style = MaterialTheme.typography.titleSmall, color = Ink)
                TextButton(onClick = {
                    feedback.tap()
                    onAddCategory()
                }) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Green700, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add category", color = Green700, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        if (budgetRows.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Green50),
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(LineBorder))
                ) {
                    Text(
                        text = "No category limits. Your safe amount per day comes from your wallet balance and the days left in your cycle.",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink
                    )
                }
            }
        }

        itemsIndexed(budgetRows) { index, row ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(LineBorder))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = row.category,
                        onValueChange = { budgetRows[index] = row.copy(category = it) },
                        label = { Text("Category") },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = row.limitPesosStr,
                        onValueChange = {
                            budgetRows[index] = row.copy(limitPesosStr = it)
                            onRowsEdited()
                        },
                        label = { Text("Limit") },
                        prefix = { Text("₱") },
                        visualTransformation = PesoAmountTransformation,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(0.9f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    IconButton(onClick = {
                        feedback.delete()
                        budgetRows.removeAt(index)
                        onRowsEdited()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove ${row.category}", tint = InkMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(LineBorder))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Savings goal", style = MaterialTheme.typography.titleSmall, color = Ink)
                            Text("An emergency fund or ipon target, at your own pace", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                        }
                        Switch(
                            checked = includeSavingsGoal,
                            onCheckedChange = {
                                feedback.toggle(it)
                                onIncludeSavingsGoalChange(it)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = SurfaceColor, checkedTrackColor = Green600)
                        )
                    }
                    AnimatedVisibility(visible = includeSavingsGoal, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = goalName,
                                onValueChange = onGoalNameChange,
                                label = { Text("Goal name") },
                                singleLine = true,
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            OutlinedTextField(
                                value = goalTarget,
                                onValueChange = onGoalTargetChange,
                                label = { Text("Target") },
                                prefix = { Text("₱") },
                                visualTransformation = PesoAmountTransformation,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(0.9f),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DoneStep(
    name: String,
    cycleType: PayCycleType,
    firstPayday: Int,
    secondPayday: Int,
    accountCount: Int,
    budgetRows: List<EditableBudgetRow>,
    permissions: PermissionState,
    soundsEnabled: Boolean,
    onSoundsChange: (Boolean) -> Unit
) {
    val allocatedSumPesos = budgetRows.sumOf { (it.limitPesosStr.toDoubleOrNull() ?: 0.0).toLong() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ChitoImage(R.drawable.chito_thumbsup, 130, "Chito giving a thumbs up")
        Spacer(modifier = Modifier.height(14.dp))
        Text("You're all set, $name", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = Ink, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(6.dp))
        Text("Here's what Chito will use. You can change any of it later.", style = MaterialTheme.typography.bodyMedium, color = InkMuted, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceColor),
            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(LineBorder))
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val cycleDesc = when (cycleType) {
                    PayCycleType.MONTHLY_END -> "Monthly, end of month"
                    PayCycleType.WEEKLY -> "Weekly"
                    PayCycleType.FLEXIBLE -> "Flexible, monthly pace"
                    PayCycleType.SEMI_MONTHLY_15_30 -> "${ordinal(firstPayday)} and ${ordinal(secondPayday)}"
                }
                val budgetDesc = if (allocatedSumPesos > 0L) {
                    "₱${"%,d".format(allocatedSumPesos)} in ${budgetRows.size} categories"
                } else "Paced from wallets"
                val alertsDesc = when {
                    permissions.sms && permissions.notificationAccess -> "Texts and notifications"
                    permissions.sms -> "Texts"
                    permissions.notificationAccess -> "Notifications"
                    else -> "Off, you'll add them yourself"
                }
                SummaryLine("Pay cycle", cycleDesc)
                SummaryLine("Accounts", if (accountCount == 1) "1 account" else "$accountCount accounts")
                SummaryLine("Budget", budgetDesc)
                SummaryLine("Payment alerts", alertsDesc)
                SummaryLine("Data", "Stored on this phone")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceColor),
            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(LineBorder))
        ) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sounds", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Ink)
                    Text("Soft taps and chimes. Quiet when your phone is on silent.", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                }
                Switch(
                    checked = soundsEnabled,
                    onCheckedChange = onSoundsChange,
                    colors = SwitchDefaults.colors(checkedThumbColor = SurfaceColor, checkedTrackColor = Green600)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

private fun ordinal(day: Int): String {
    val suffix = if (day in 11..13) "th" else when (day % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
    return "$day$suffix"
}

@Composable
private fun CycleOptionCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    badge: String? = null
) {
    val container by animateColorAsState(if (isSelected) Green50 else SurfaceColor, tween(180), label = "CycleBg")
    val borderColor by animateColorAsState(if (isSelected) Green600 else LineBorder, tween(180), label = "CycleBorder")
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = title, style = MaterialTheme.typography.titleSmall, color = Ink)
                    if (badge != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Green700,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Green100)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = InkMuted)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Green600 else Green50)
                    .border(1.dp, if (isSelected) Green600 else LineBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.animation.AnimatedVisibility(visible = isSelected, enter = scaleIn(spring(dampingRatio = 0.5f)) + fadeIn(), exit = fadeOut()) {
                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = SurfaceColor, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun FactRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(Green600, CircleShape)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = Ink)
    }
}

@Composable
fun SummaryLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = InkMuted)
        Spacer(modifier = Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Ink, textAlign = TextAlign.End)
    }
}
