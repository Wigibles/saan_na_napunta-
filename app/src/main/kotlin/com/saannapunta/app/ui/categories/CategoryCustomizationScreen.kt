package com.saannapunta.app.ui.categories

import com.saannapunta.app.ui.chito.ChitoCompanion
import com.saannapunta.app.ui.chito.ChitoLines
import com.saannapunta.app.ui.components.PesoAmountTransformation
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saannapunta.app.data.DataRepository
import com.saannapunta.app.data.model.CategoryBudget
import com.saannapunta.app.data.model.MoneyFormatter
import com.saannapunta.app.ui.components.SaanNaPuntaButton
import com.saannapunta.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryCustomizationScreen(
    repository: DataRepository,
    onBack: () -> Unit
) {
    val budgets by repository.budgets.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var editingCategory by remember { mutableStateOf<CategoryBudget?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }

    // Pre-populate system categories with rich default subcategories so they are fully customizable
    val allDisplayCategories = remember(budgets) {
        val list = budgets.toMutableList()
        val defaultCategories = listOf(
            "Utilities" to "Electric, water, internet bills",
            "Food & Dining" to "Groceries, fast food, snacks",
            "Transportation" to "Grab, commute, fuel, toll",
            "Shopping & Personal" to "Clothes, haircut, self-care",
            "Rent & Housing" to "Rent, condo dues, maintenance",
            "Health & Medical" to "Pharmacy, checkup, vitamins",
            "Education & School" to "Tuition, supplies, books",
            "Entertainment" to "Streaming, gaming, cinema",
            "Family & Pets" to "Family support, pet food, vet"
        )
        for ((catName, examples) in defaultCategories) {
            if (list.none { it.category.equals(catName, ignoreCase = true) }) {
                list.add(
                    CategoryBudget(
                        id = 0L,
                        category = catName,
                        limitCentavos = 0L,
                        examples = examples,
                        iconName = when {
                            catName.contains("Util") -> "electric_bolt"
                            catName.contains("Food") -> "restaurant"
                            catName.contains("Trans") -> "two_wheeler"
                            catName.contains("Shop") -> "shopping_bag"
                            catName.contains("Rent") -> "house"
                            catName.contains("Health") -> "medical_services"
                            catName.contains("Edu") -> "school"
                            catName.contains("Entertain") -> "play_circle"
                            catName.contains("Pet") -> "pets"
                            else -> null
                        },
                        subCategories = CategoryIconCatalog.getDefaultSubcategoriesForPreset(catName)
                    )
                )
            }
        }
        list.toList()
    }

    val filteredList = remember(allDisplayCategories, searchQuery, selectedFilter) {
        var base: List<CategoryBudget> = allDisplayCategories
        if (searchQuery.isNotBlank()) {
            base = base.filter { cat ->
                cat.category.contains(searchQuery, ignoreCase = true) ||
                    cat.examples.contains(searchQuery, ignoreCase = true) ||
                    cat.subCategories.any { it.contains(searchQuery, ignoreCase = true) }
            }
        }
        when (selectedFilter) {
            "With Limit" -> base.filter { it.limitCentavos > 0L }
            "No Limit" -> base.filter { it.limitCentavos == 0L }
            else -> base
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Categories & Subcategories",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Ink
                        )
                        Text(
                            text = "Personalize icons, colors & budget caps",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkMuted
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Ink
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            isCreatingNew = true
                            editingCategory = CategoryBudget(
                                id = 0L,
                                category = "",
                                limitCentavos = 0L,
                                examples = "",
                                iconName = "category",
                                colorHex = "#2E7D32",
                                subCategories = emptyList()
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Green600),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Category", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Canvas)
            )
        },
        containerColor = Canvas
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ChitoCompanion(line = ChitoLines.categories(), size = 60.dp)
            }

            // Search Input with Clear Button
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search categories (e.g. Kuryente, Grab, Tuition)", color = InkFaint) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = InkMuted) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Clear search", tint = InkMuted)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceColor,
                        unfocusedContainerColor = SurfaceColor,
                        focusedBorderColor = Green600,
                        unfocusedBorderColor = LineBorder
                    ),
                    singleLine = true
                )
            }

            // Quick Filter Tabs (All, With Limit, No Limit)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filterOptions = listOf(
                        "All" to "All (${allDisplayCategories.size})",
                        "With Limit" to "With Budget (${allDisplayCategories.count { it.limitCentavos > 0L }})",
                        "No Limit" to "No Limit (${allDisplayCategories.count { it.limitCentavos == 0L }})"
                    )

                    filterOptions.forEach { (key, label) ->
                        val isSelected = selectedFilter == key
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isSelected) Green100 else SurfaceColor)
                                .border(1.dp, if (isSelected) Green600 else LineBorder, CircleShape)
                                .clickable { selectedFilter = key }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isSelected) Green700 else InkMuted
                            )
                        }
                    }
                }
            }

            // Overview Info Tip Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Green50),
                    border = BorderStroke(1.dp, Green100)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Green100),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = Green700, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "1-Tap Subcategories",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Green700
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Tag bills and transactions instantly (e.g. Kuryente or Tubig under Utilities). Tap any category to edit.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Ink
                            )
                        }
                    }
                }
            }

            // Categories List
            if (filteredList.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No matching categories found", style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Try searching another name or create a new category above", style = MaterialTheme.typography.labelSmall, color = InkFaint)
                    }
                }
            } else {
                items(filteredList) { cat ->
                    CategoryCustomizationCard(
                        category = cat,
                        onClick = {
                            isCreatingNew = false
                            editingCategory = cat
                        }
                    )
                }
            }
        }
    }

    // Edit / Create Category Sheet
    editingCategory?.let { currentBudget ->
        EditCategoryCustomizationSheet(
            initialCategory = currentBudget,
            isNew = isCreatingNew,
            onDismiss = {
                editingCategory = null
                isCreatingNew = false
            },
            onSave = { updated ->
                repository.addCategoryBudget(updated)
                editingCategory = null
                isCreatingNew = false
            },
            onDelete = { id ->
                if (id > 0) {
                    repository.deleteCategoryBudget(id)
                }
                editingCategory = null
                isCreatingNew = false
            }
        )
    }
}

@Composable
fun CategoryCustomizationCard(
    category: CategoryBudget,
    onClick: () -> Unit
) {
    val icon = remember(category.iconName, category.category) {
        CategoryIconCatalog.getIconForCategory(category.iconName, category.category)
    }
    val color = remember(category.colorHex) {
        CategoryIconCatalog.getColor(category.colorHex)
    }

    val effectiveSubcategories = remember(category.subCategories, category.category) {
        if (category.subCategories.isNotEmpty()) {
            category.subCategories
        } else {
            CategoryIconCatalog.getDefaultSubcategoriesForPreset(category.category)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(LineBorder))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(color.copy(alpha = 0.12f))
                            .border(1.dp, color.copy(alpha = 0.28f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = category.category,
                            tint = color,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = category.category,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (effectiveSubcategories.isNotEmpty()) {
                                "${effectiveSubcategories.size} subcategories"
                            } else {
                                "Tap to add subcategories"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = InkMuted
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (category.limitCentavos > 0L) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Green100)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${MoneyFormatter.format(category.limitCentavos, false)} / cycle",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                                color = Green700
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Canvas)
                                .border(1.dp, LineBorder, CircleShape)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "No limit",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = InkFaint
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Edit category",
                        tint = InkMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Subcategory chips preview row
            if (effectiveSubcategories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    effectiveSubcategories.take(5).forEach { sub ->
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Green50)
                                .border(1.dp, LineBorder, CircleShape)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = sub,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = Green700
                            )
                        }
                    }

                    if (effectiveSubcategories.size > 5) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Canvas)
                                .border(1.dp, LineBorder, CircleShape)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "+${effectiveSubcategories.size - 5} more",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = InkMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCategoryCustomizationSheet(
    initialCategory: CategoryBudget,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (CategoryBudget) -> Unit,
    onDelete: (Long) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var categoryName by remember { mutableStateOf(initialCategory.category) }
    var limitInput by remember {
        mutableStateOf(
            if (initialCategory.limitCentavos > 0L) {
                (initialCategory.limitCentavos / 100L).toString()
            } else ""
        )
    }
    var examplesInput by remember { mutableStateOf(initialCategory.examples) }
    var selectedIconId by remember { mutableStateOf(initialCategory.iconName ?: "category") }
    var selectedColorHex by remember { mutableStateOf(initialCategory.colorHex ?: "#2E7D32") }

    val subCategories = remember {
        val initialList = if (initialCategory.subCategories.isNotEmpty()) {
            initialCategory.subCategories
        } else {
            CategoryIconCatalog.getDefaultSubcategoriesForPreset(initialCategory.category)
        }
        mutableStateListOf<String>().apply { addAll(initialList) }
    }

    var newSubCategoryInput by remember { mutableStateOf("") }
    var showIconPickerDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val currentColor = CategoryIconCatalog.getColor(selectedColorHex)
    val currentIcon = CategoryIconCatalog.getIcon(selectedIconId)

    // Unadded suggested presets
    val suggestedMissing = remember(categoryName, subCategories.toList()) {
        CategoryIconCatalog.getSuggestedSubcategories(categoryName, subCategories.toList())
    }

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
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isNew) "Add Category" else "Customize Category",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Ink
                    )
                    Text(
                        text = "Customize theme, subcategories, and spending limit",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", tint = InkMuted)
                }
            }

            // Live Hero Mockup Preview Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Canvas),
                border = BorderStroke(1.dp, LineBorder)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(currentColor.copy(alpha = 0.14f))
                                .border(1.5.dp, currentColor, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = currentIcon,
                                contentDescription = null,
                                tint = currentColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = categoryName.ifBlank { "Category Name" },
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (categoryName.isBlank()) InkFaint else Ink
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${subCategories.size} subcategories attached",
                                style = MaterialTheme.typography.labelSmall,
                                color = InkMuted
                            )
                        }
                    }

                    val limitDouble = limitInput.toDoubleOrNull() ?: 0.0
                    if (limitDouble > 0) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Green100)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "₱${String.format(java.util.Locale.US, "%,.0f", limitDouble)} / cycle",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Green700
                            )
                        }
                    } else {
                        Text(
                            text = "No limit",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkFaint
                        )
                    }
                }
            }

            // Category Name Field
            Column {
                Text("Category name", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = Ink)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = categoryName,
                    onValueChange = { categoryName = it },
                    placeholder = { Text("e.g. Utilities, Pet Care, Streaming", color = InkFaint) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceColor,
                        unfocusedContainerColor = SurfaceColor,
                        focusedBorderColor = Green600,
                        unfocusedBorderColor = LineBorder
                    ),
                    singleLine = true
                )
            }

            // Icon & Color Row
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Theme Icon & Color", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = Ink)
                    TextButton(onClick = { showIconPickerDialog = true }) {
                        Text("Browse 60+ Icons ›", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Green600)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Color Swatches Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CategoryIconCatalog.COLORS.forEach { colorItem ->
                        val isSelected = selectedColorHex.equals(colorItem.hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(colorItem.color)
                                .then(if (isSelected) Modifier.border(2.5.dp, Ink, CircleShape) else Modifier)
                                .clickable { selectedColorHex = colorItem.hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // Subcategories Management Section
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Subcategories", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = Ink)
                        Text("Tag specific bills & expenses (e.g. Kuryente, Tubig)", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Active Subcategory Chips
                if (subCategories.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        subCategories.forEach { sub ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Green50)
                                    .border(1.dp, LineBorder, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = sub, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium), color = Green700)
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove $sub",
                                    tint = InkMuted,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { subCategories.remove(sub) }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Quick suggestions (missing presets)
                if (suggestedMissing.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        suggestedMissing.take(5).forEach { suggestion ->
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Canvas)
                                    .border(1.dp, Green600.copy(alpha = 0.4f), CircleShape)
                                    .clickable {
                                        if (subCategories.none { it.equals(suggestion, ignoreCase = true) }) {
                                            subCategories.add(suggestion)
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "+ $suggestion",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                                    color = Green700
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Add Subcategory Input Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newSubCategoryInput,
                        onValueChange = { newSubCategoryInput = it },
                        placeholder = { Text("Add custom subcategory (e.g. Meralco)", color = InkFaint) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                val trimmed = newSubCategoryInput.trim()
                                if (trimmed.isNotBlank() && subCategories.none { it.equals(trimmed, ignoreCase = true) }) {
                                    subCategories.add(trimmed)
                                    newSubCategoryInput = ""
                                }
                                focusManager.clearFocus()
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceColor,
                            unfocusedContainerColor = SurfaceColor,
                            focusedBorderColor = Green600,
                            unfocusedBorderColor = LineBorder
                        )
                    )

                    Button(
                        onClick = {
                            val trimmed = newSubCategoryInput.trim()
                            if (trimmed.isNotBlank() && subCategories.none { it.equals(trimmed, ignoreCase = true) }) {
                                subCategories.add(trimmed)
                                newSubCategoryInput = ""
                            }
                            focusManager.clearFocus()
                        },
                        enabled = newSubCategoryInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Green600),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text("Add")
                    }
                }
            }

            // Spending Limit Section
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Cycle Spending Limit (Optional)", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = Ink)
                    if (limitInput.isNotBlank()) {
                        Text(
                            text = "Clear limit",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusOver,
                            modifier = Modifier.clickable { limitInput = "" }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Quick Amount Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presets = listOf("1000", "3000", "5000", "10000")
                    presets.forEach { presetVal ->
                        val isChosen = limitInput == presetVal
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isChosen) Green100 else Canvas)
                                .border(1.dp, if (isChosen) Green600 else LineBorder, CircleShape)
                                .clickable { limitInput = presetVal }
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "₱${String.format(java.util.Locale.US, "%,d", presetVal.toInt())}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal),
                                color = if (isChosen) Green700 else InkMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = limitInput,
                    onValueChange = { input ->
                        if (input.matches(Regex("^\\d*(\\.\\d{0,2})?$"))) {
                            limitInput = input
                        }
                    },
                    placeholder = { Text("0.00 (Leave empty for no limit)", color = InkFaint) },
                    prefix = { Text("₱ ", color = Ink, fontWeight = FontWeight.Bold) },
                    visualTransformation = PesoAmountTransformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceColor,
                        unfocusedContainerColor = SurfaceColor,
                        focusedBorderColor = Green600,
                        unfocusedBorderColor = LineBorder
                    )
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Action Buttons
            SaanNaPuntaButton(
                text = if (isNew) "Create category" else "Save category changes",
                enabled = categoryName.isNotBlank(),
                onClick = {
                    val limitPesos = limitInput.toDoubleOrNull() ?: 0.0
                    val limitCentavos = Math.round(limitPesos * 100.0)
                    onSave(
                        CategoryBudget(
                            id = initialCategory.id,
                            category = categoryName.trim(),
                            limitCentavos = limitCentavos,
                            examples = examplesInput.trim(),
                            iconName = selectedIconId,
                            colorHex = selectedColorHex,
                            subCategories = subCategories.toList()
                        )
                    )
                }
            )

            if (!isNew && initialCategory.id > 0) {
                OutlinedButton(
                    onClick = { showDeleteConfirmDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, LineBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusOver)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = StatusOver, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete custom category", color = StatusOver, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Category?", style = MaterialTheme.typography.titleMedium, color = StatusOver) },
            text = {
                Text(
                    "Are you sure you want to delete '${categoryName}'? Existing transactions with this category will be preserved.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete(initialCategory.id)
                    }
                ) {
                    Text("Delete", color = StatusOver, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = InkMuted)
                }
            },
            containerColor = SurfaceColor
        )
    }

    // Modal Icon Picker Sheet
    if (showIconPickerDialog) {
        CategoryIconPickerSheet(
            selectedIconId = selectedIconId,
            onSelect = {
                selectedIconId = it
                showIconPickerDialog = false
            },
            onDismiss = { showIconPickerDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryIconPickerSheet(
    selectedIconId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var iconSearch by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf("All") }

    val filteredIcons = remember(iconSearch, selectedGroup) {
        CategoryIconCatalog.ICONS.filter { item ->
            val matchGroup = selectedGroup == "All" || item.group == selectedGroup
            val matchSearch = iconSearch.isBlank() ||
                item.label.contains(iconSearch, ignoreCase = true) ||
                item.id.contains(iconSearch, ignoreCase = true) ||
                item.group.contains(iconSearch, ignoreCase = true)
            matchGroup && matchSearch
        }
    }

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
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Choose an Icon",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Ink
                    )
                    Text(
                        text = "${filteredIcons.size} icons available",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", tint = InkMuted)
                }
            }

            // Search Bar with Clear Button
            OutlinedTextField(
                value = iconSearch,
                onValueChange = { iconSearch = it },
                placeholder = { Text("Search icon (e.g. kuryente, food, grab, bill)", color = InkFaint) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = InkMuted) },
                trailingIcon = {
                    if (iconSearch.isNotBlank()) {
                        IconButton(onClick = { iconSearch = "" }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear", tint = InkMuted)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceColor,
                    unfocusedContainerColor = SurfaceColor,
                    focusedBorderColor = Green600,
                    unfocusedBorderColor = LineBorder
                )
            )

            // Category Group Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryIconCatalog.GROUPS.forEach { group ->
                    val isSel = selectedGroup == group
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isSel) Green100 else Canvas)
                            .border(1.dp, if (isSel) Green600 else LineBorder, CircleShape)
                            .clickable { selectedGroup = group }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = group,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal),
                            color = if (isSel) Green700 else InkMuted
                        )
                    }
                }
            }

            // Icon Grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 74.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(filteredIcons) { item ->
                    val isSelected = selectedIconId.equals(item.id, ignoreCase = true)
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) Green100 else Canvas)
                            .border(1.dp, if (isSelected) Green600 else LineBorder, RoundedCornerShape(14.dp))
                            .clickable { onSelect(item.id) }
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Green600 else SurfaceColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = if (isSelected) Color.White else Ink,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.label.split("/").first().trim(),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = if (isSelected) Green700 else InkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
