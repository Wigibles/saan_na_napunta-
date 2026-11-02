package com.saannapunta.app.ui.chito

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.saannapunta.app.R
import com.saannapunta.app.ui.feedback.rememberUiFeedback
import com.saannapunta.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Chito's poses. Each screen picks the one that matches what the user's money is doing. */
enum class ChitoMood(@DrawableRes val drawable: Int, val description: String) {
    DEFAULT(R.drawable.chito_default, "Chito with his coin sack"),
    WAVING(R.drawable.chito_waving, "Chito waving"),
    THINKING(R.drawable.chito_thinking, "Chito thinking"),
    THUMBSUP(R.drawable.chito_thumbsup, "Chito giving a thumbs up"),
    CELEBRATE(R.drawable.chito_celebrate, "Chito celebrating"),
    CHEER(R.drawable.chito_cheer, "Chito cheering"),
    CONFETTI(R.drawable.chito_confetti, "Chito celebrating with confetti"),
    CONFUSED(R.drawable.chito_confused, "Chito looking worried"),
    FACEPALM(R.drawable.chito_facepalm, "Chito facepalming"),
    BROKE(R.drawable.chito_broke, "Chito with empty pockets"),
    RICH(R.drawable.chito_rich, "Chito with a full coin sack"),
    HEARTEYES(R.drawable.chito_hearteyes, "Chito with heart eyes"),
    PAYING(R.drawable.chito_paying, "Chito handing over cash"),
    RUNNING(R.drawable.chito_running, "Chito running as money flies out of his wallet"),
    COUNTING(R.drawable.chito_counting, "Chito counting coins"),
    PIGGY(R.drawable.chito_piggy, "Chito hugging a piggy bank"),
    PHONE(R.drawable.chito_phone, "Chito checking his phone"),
    LAPTOP(R.drawable.chito_laptop, "Chito working on his laptop"),
    DETECTIVE(R.drawable.chito_detective, "Chito with a magnifying glass"),
    POINTING(R.drawable.chito_pointing, "Chito pointing"),
    SLEEPING(R.drawable.chito_sleeping, "Chito sleeping on his coin sack")
}

/** One thing Chito says, with the pose he says it in. */
data class ChitoLine(val mood: ChitoMood, val message: String)

/**
 * Chito's reactions to what the user just did (saved, deleted, paid). Screens and sheets post here;
 * [ChitoReactionHost] shows each one as Chito popping up from the bottom of the screen.
 */
object ChitoReactions {
    private val _events = MutableSharedFlow<ChitoLine>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    fun react(line: ChitoLine) {
        _events.tryEmit(line)
    }
}

/** Things Chito does on his own when Home is left alone for a while. */
private val IDLE_ACTIONS = listOf(
    ChitoLine(ChitoMood.COUNTING, "Binibilang ko lang ang ipon mo..."),
    ChitoLine(ChitoMood.PHONE, "Checking for new GCash and bank texts..."),
    ChitoLine(ChitoMood.LAPTOP, "Updating your budget sheet..."),
    ChitoLine(ChitoMood.DETECTIVE, "Looking for where the money went..."),
    ChitoLine(ChitoMood.PIGGY, "Feeding the piggy bank. Ipon is ipon.")
)
private val ASLEEP = ChitoLine(ChitoMood.SLEEPING, "Zzz... tap me if you need me.")
private val WOKEN = ChitoLine(ChitoMood.WAVING, "Gising na ako. What do you need?")
private const val SLEEP_AFTER_MS = 60_000L

/**
 * Chito with a speech bubble. He breathes gently while idle, hops and tilts when his line changes
 * (the user did something), and hops again when tapped. Motion scales with the system animation
 * setting, so "Remove animations" keeps him still.
 *
 * With [idleLife], Chito does little things on his own when nobody touches the screen and falls
 * asleep after a minute; [interactionKey] changes whenever the user touches the screen.
 */
@Composable
fun ChitoCompanion(
    line: ChitoLine,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    onTap: (() -> Unit)? = null,
    bubbleColor: Color = Green50,
    idleLife: Boolean = false,
    interactionKey: Int = 0
) {
    val feedback = rememberUiFeedback()
    val scope = rememberCoroutineScope()
    val hop = remember { Animatable(0f) }
    val tilt = remember { Animatable(0f) }

    var idleLine by remember { mutableStateOf<ChitoLine?>(null) }
    var wakeKey by remember { mutableIntStateOf(0) }

    // Idle life: a small activity every so often, sleep after a minute of no touches
    if (idleLife) {
        LaunchedEffect(interactionKey, line, wakeKey) {
            if (idleLine == ASLEEP) idleLine = null
            val start = System.currentTimeMillis()
            while (true) {
                delay(Random.nextLong(16_000L, 26_000L))
                if (System.currentTimeMillis() - start >= SLEEP_AFTER_MS) {
                    idleLine = ASLEEP
                    break
                }
                idleLine = IDLE_ACTIONS.random()
                delay(5_000L)
                idleLine = null
            }
        }
    }

    val shown = idleLine ?: line

    // React whenever what Chito shows changes, but not on first appearance
    var lastShown by remember { mutableStateOf(shown) }
    LaunchedEffect(shown) {
        if (shown != lastShown) {
            lastShown = shown
            if (shown != ASLEEP) launchHop(hop, tilt)
        }
    }

    val breathing = rememberInfiniteTransition(label = "ChitoIdle")
    val breathe by breathing.animateFloat(
        initialValue = 1f,
        targetValue = if (shown == ASLEEP) 1.06f else 1.035f,
        animationSpec = infiniteRepeatable(tween(if (shown == ASLEEP) 2600 else 1800), RepeatMode.Reverse),
        label = "ChitoBreathe"
    )

    val tappable = onTap != null || idleLife
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedContent(
            targetState = shown.mood,
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
            label = "ChitoPose"
        ) { mood ->
            Image(
                painter = painterResource(mood.drawable),
                contentDescription = mood.description,
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        translationY = hop.value
                        rotationZ = tilt.value
                        scaleX = breathe
                        scaleY = breathe
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .then(
                        if (tappable) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                                onClickLabel = "Ask Chito"
                            ) {
                                feedback.select()
                                if (idleLine == ASLEEP) {
                                    // Wake up, say hi, then go back to the screen's line
                                    idleLine = WOKEN
                                    scope.launch {
                                        delay(3_000L)
                                        if (idleLine == WOKEN) idleLine = null
                                        wakeKey++
                                    }
                                } else {
                                    if (idleLine != null) idleLine = null
                                    scope.launch { launchHop(hop, tilt) }
                                    onTap?.invoke()
                                }
                            }
                        } else Modifier
                    )
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        ChitoBubble(shown.message, bubbleColor, Modifier.weight(1f))
    }
}

@Composable
private fun ChitoBubble(message: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp))
            .background(color)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text("Chito", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Green700)
        AnimatedContent(
            targetState = message,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
            label = "ChitoMessage"
        ) { text ->
            Text(text, style = MaterialTheme.typography.bodyMedium, color = Ink)
        }
    }
}

/**
 * Shows each [ChitoReactions] event as Chito popping up from the bottom edge with a bubble, for a
 * few seconds or until tapped. Place it in a Box over the screen content, aligned to the bottom.
 */
@Composable
fun ChitoReactionHost(modifier: Modifier = Modifier) {
    var current by remember { mutableStateOf<ChitoLine?>(null) }
    var visible by remember { mutableStateOf(false) }
    var dismissKey by remember { mutableIntStateOf(0) }
    val hop = remember { Animatable(0f) }
    val tilt = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        ChitoReactions.events.collect { line ->
            current = line
            visible = true
            dismissKey++
        }
    }
    LaunchedEffect(dismissKey) {
        if (!visible) return@LaunchedEffect
        delay(350L)
        launchHop(hop, tilt)
        delay(3_000L)
        visible = false
    }

    AnimatedVisibility(
        visible = visible && current != null,
        modifier = modifier,
        enter = slideInVertically(spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(),
        exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(180))
    ) {
        val line = current ?: return@AnimatedVisibility
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "Dismiss"
                ) { visible = false },
            verticalAlignment = Alignment.Bottom
        ) {
            Image(
                painter = painterResource(line.mood.drawable),
                contentDescription = line.mood.description,
                modifier = Modifier
                    .size(96.dp)
                    .graphicsLayer {
                        translationY = hop.value
                        rotationZ = tilt.value
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
            )
            Spacer(modifier = Modifier.width(6.dp))
            ChitoBubble(
                message = line.message,
                color = SurfaceColor,
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = 28.dp)
                    .shadow(8.dp, RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp))
            )
        }
    }
}

private suspend fun launchHop(hop: Animatable<Float, *>, tilt: Animatable<Float, *>) {
    kotlinx.coroutines.coroutineScope {
        launch {
            hop.animateTo(-14f, tween(110))
            hop.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow))
        }
        launch {
            tilt.animateTo(-7f, tween(90))
            tilt.animateTo(5f, tween(110))
            tilt.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow))
        }
    }
}
