package com.saannapunta.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saannapunta.app.data.model.CategoryBudget
import com.saannapunta.app.data.model.TransactionType
import com.saannapunta.app.ui.categories.CategoryIconCatalog
import com.saannapunta.app.ui.theme.*

/** Deep green gradient for hero cards. */
val HeroGradient: Brush = Brush.linearGradient(listOf(Green900, Color(0xFF0E5536), Green700))

/** Category budgets by lowercase name, so any row can draw a category's chosen icon and color. */
val LocalCategoryStyles = compositionLocalOf<Map<String, CategoryBudget>> { emptyMap() }

/** Fallback colors for categories without a chosen color; picked by name so each stays stable. */
private val CATEGORY_PALETTE = listOf(
    Color(0xFF14A869), Color(0xFF2F80ED), Color(0xFFF2994A), Color(0xFFEB5757),
    Color(0xFF9B51E0), Color(0xFF00A3A3), Color(0xFFD4A21C), Color(0xFFDB5C98)
)

@Composable
fun categoryColor(category: String): Color {
    val styles = LocalCategoryStyles.current
    val budget = styles[category.lowercase()]
    return remember(category, budget?.colorHex) {
        val fallback = CATEGORY_PALETTE[Math.floorMod(category.lowercase().hashCode(), CATEGORY_PALETTE.size)]
        CategoryIconCatalog.getColor(budget?.colorHex, fallback)
    }
}

@Composable
fun categoryIcon(category: String): ImageVector {
    val budget = LocalCategoryStyles.current[category.lowercase()]
    return remember(category, budget?.iconName) { CategoryIconCatalog.getIconForCategory(budget?.iconName, category) }
}

/** White card with a soft green-tinted shadow instead of a hairline border. */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    color: Color = SurfaceColor,
    elevation: Dp = 6.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .shadow(elevation, shape, ambientColor = Green900.copy(alpha = 0.10f), spotColor = Green900.copy(alpha = 0.14f))
            .clip(shape)
            .background(color)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        content = content
    )
}

/** Rounded square with a category's icon on a tint of its color. */
@Composable
fun CategoryIconChip(
    category: String,
    modifier: Modifier = Modifier,
    type: TransactionType = TransactionType.EXPENSE,
    size: Dp = 44.dp
) {
    val (icon, color) = when (type) {
        TransactionType.TRANSFER -> Icons.Outlined.CompareArrows to Color(0xFF2F80ED)
        TransactionType.INCOME -> Icons.Outlined.Payments to Green600
        TransactionType.EXPENSE -> categoryIcon(category) to categoryColor(category)
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(size * 0.5f))
    }
}

/** Big screen title with a small eyebrow line above it, and optional actions on the right. */
@Composable
fun ScreenHeader(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = eyebrow.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = Green700
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 28.sp),
                color = Ink
            )
        }
        actions()
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Ink)
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge, color = Green600)
            }
        }
    }
}

/** Small rounded label: "On track", "Near limit", "Due in 3d". */
@Composable
fun StatusChip(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = color)
    }
}

/** Translucent chip for use on the green hero cards. */
@Composable
fun HeroChip(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = OnHero, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = OnHero)
    }
}

/** Round action button with a tinted circle and a label underneath (Home quick actions). */
@Composable
fun CircleAction(label: String, icon: ImageVector, tint: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClickLabel = label) { onClick() }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = Ink)
    }
}

/** Brand accent for an account's provider, used as the stripe on account cards. */
fun providerAccent(provider: String): Color = when (provider.lowercase()) {
    "gcash" -> Color(0xFF0B6BF2)
    "maya" -> Color(0xFF00A86B)
    "spay" -> Color(0xFFEE4D2D)
    "grabpay" -> Color(0xFF00B14F)
    "coinsph" -> Color(0xFF1E4FD8)
    "palawanpay" -> Color(0xFF0F6FB4)
    "vybe" -> Color(0xFFB11116)
    "bdo" -> Color(0xFF0033A0)
    "bpi" -> Color(0xFFB11116)
    "metrobank" -> Color(0xFF1F4E9C)
    "landbank" -> Color(0xFF00843D)
    "securitybank" -> Color(0xFF00A3E0)
    "pnb" -> Color(0xFF1B3F8B)
    "rcbc" -> Color(0xFF0052A5)
    "unionbank" -> Color(0xFFF7931E)
    "chinabank" -> Color(0xFFC8102E)
    "eastwest" -> Color(0xFF6A1B9A)
    "psbank" -> Color(0xFF00629B)
    "gotyme" -> Color(0xFF00B5D8)
    "uniondigital" -> Color(0xFFF7931E)
    "maribank" -> Color(0xFFFF6B00)
    "cimb" -> Color(0xFFD6001C)
    "tonik" -> Color(0xFF7B2FF7)
    "unobank" -> Color(0xFF00A99D)
    "cash" -> Color(0xFF14A869)
    else -> Color(0xFF06653D)
}
