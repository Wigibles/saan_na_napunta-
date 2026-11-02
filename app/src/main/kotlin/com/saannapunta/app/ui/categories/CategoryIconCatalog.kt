package com.saannapunta.app.ui.categories

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class CategoryIconItem(
    val id: String,
    val label: String,
    val group: String,
    val icon: ImageVector
)

data class CategoryColorItem(
    val hex: String,
    val name: String,
    val color: Color
)

object CategoryIconCatalog {

    val GROUPS = listOf(
        "All",
        "Bills & Utilities",
        "Food & Dining",
        "Transportation",
        "Housing & Living",
        "Shopping & Personal",
        "Tech & Media",
        "Health & Fitness",
        "Education & Work",
        "Family & Pets",
        "Finance & Income"
    )

    val COLORS = listOf(
        CategoryColorItem("#1B5E20", "Forest Green", Color(0xFF1B5E20)),
        CategoryColorItem("#2E7D32", "Emerald Green", Color(0xFF2E7D32)),
        CategoryColorItem("#00897B", "Mint Green", Color(0xFF00897B)),
        CategoryColorItem("#00695C", "Deep Teal", Color(0xFF00695C)),
        CategoryColorItem("#0277BD", "Ocean Blue", Color(0xFF0277BD)),
        CategoryColorItem("#283593", "Indigo", Color(0xFF283593)),
        CategoryColorItem("#6A1B9A", "Royal Violet", Color(0xFF6A1B9A)),
        CategoryColorItem("#AD1457", "Deep Rose", Color(0xFFAD1457)),
        CategoryColorItem("#D84315", "Warm Coral", Color(0xFFD84315)),
        CategoryColorItem("#F57F17", "Golden Amber", Color(0xFFF57F17)),
        CategoryColorItem("#37474F", "Charcoal Slate", Color(0xFF37474F))
    )

    val ICONS = listOf(
        // Bills & Utilities
        CategoryIconItem("electric_bolt", "Kuryente / Power", "Bills & Utilities", Icons.Outlined.ElectricBolt),
        CategoryIconItem("water_drop", "Tubig / Water", "Bills & Utilities", Icons.Outlined.WaterDrop),
        CategoryIconItem("wifi", "Wi-Fi / Internet", "Bills & Utilities", Icons.Outlined.Wifi),
        CategoryIconItem("phone_android", "Telepono / Mobile", "Bills & Utilities", Icons.Outlined.PhoneAndroid),
        CategoryIconItem("tv", "Cable / TV", "Bills & Utilities", Icons.Outlined.Tv),
        CategoryIconItem("receipt_long", "Billing / SOA", "Bills & Utilities", Icons.Outlined.ReceiptLong),
        CategoryIconItem("local_gas_station", "LPG / Gas Tank", "Bills & Utilities", Icons.Outlined.LocalGasStation),
        CategoryIconItem("cleaning_services", "Assoc / Condo Dues", "Bills & Utilities", Icons.Outlined.CleaningServices),

        // Food & Dining
        CategoryIconItem("restaurant", "Restaurant / Dine Out", "Food & Dining", Icons.Outlined.Restaurant),
        CategoryIconItem("fastfood", "Fast Food / Karinderya", "Food & Dining", Icons.Outlined.Fastfood),
        CategoryIconItem("local_cafe", "Coffee / Milk Tea", "Food & Dining", Icons.Outlined.LocalCafe),
        CategoryIconItem("local_grocery_store", "Groceries / Palengke", "Food & Dining", Icons.Outlined.LocalGroceryStore),
        CategoryIconItem("shopping_cart", "Supermarket Cart", "Food & Dining", Icons.Outlined.ShoppingCart),
        CategoryIconItem("cake", "Bakery / Desserts", "Food & Dining", Icons.Outlined.Cake),
        CategoryIconItem("lunch_dining", "Packed Lunch / Baon", "Food & Dining", Icons.Outlined.LunchDining),

        // Transportation
        CategoryIconItem("two_wheeler", "Motor / Angkas / Joyride", "Transportation", Icons.Outlined.TwoWheeler),
        CategoryIconItem("directions_car", "Car / Grab / Taxi", "Transportation", Icons.Outlined.DirectionsCar),
        CategoryIconItem("directions_bus", "Bus / Jeepney", "Transportation", Icons.Outlined.DirectionsBus),
        CategoryIconItem("train", "MRT / LRT / Train", "Transportation", Icons.Outlined.Train),
        CategoryIconItem("commute", "Commute / Transit", "Transportation", Icons.Outlined.Commute),
        CategoryIconItem("directions_bike", "Bicycle / Bike", "Transportation", Icons.Outlined.DirectionsBike),
        CategoryIconItem("flight", "Flight / Travel", "Transportation", Icons.Outlined.Flight),
        CategoryIconItem("local_parking", "Parking / Toll RFID", "Transportation", Icons.Outlined.LocalParking),

        // Housing & Living
        CategoryIconItem("home", "Home / Bahay", "Housing & Living", Icons.Outlined.Home),
        CategoryIconItem("apartment", "Condo / Apartment", "Housing & Living", Icons.Outlined.Apartment),
        CategoryIconItem("house", "Rent / Housing", "Housing & Living", Icons.Outlined.House),
        CategoryIconItem("key", "Lease / Deposit", "Housing & Living", Icons.Outlined.Key),
        CategoryIconItem("handyman", "Repairs / Hardware", "Housing & Living", Icons.Outlined.Handyman),
        CategoryIconItem("build", "Maintenance", "Housing & Living", Icons.Outlined.Build),

        // Shopping & Personal
        CategoryIconItem("shopping_bag", "Shopping / Retail", "Shopping & Personal", Icons.Outlined.ShoppingBag),
        CategoryIconItem("checkroom", "Apparel / Clothes", "Shopping & Personal", Icons.Outlined.Checkroom),
        CategoryIconItem("storefront", "Tiangge / Bazaar", "Shopping & Personal", Icons.Outlined.Storefront),
        CategoryIconItem("content_cut", "Haircut / Salon", "Shopping & Personal", Icons.Outlined.ContentCut),
        CategoryIconItem("spa", "Skincare / Spa", "Shopping & Personal", Icons.Outlined.Spa),
        CategoryIconItem("card_giftcard", "Gift / Regalo", "Shopping & Personal", Icons.Outlined.CardGiftcard),

        // Tech & Media
        CategoryIconItem("play_circle", "Streaming / Netflix", "Tech & Media", Icons.Outlined.PlayCircle),
        CategoryIconItem("music_note", "Music / Spotify", "Tech & Media", Icons.Outlined.MusicNote),
        CategoryIconItem("sports_esports", "Gaming / Steam", "Tech & Media", Icons.Outlined.SportsEsports),
        CategoryIconItem("laptop", "Gadgets / Electronics", "Tech & Media", Icons.Outlined.Laptop),
        CategoryIconItem("movie", "Cinema / Movies", "Tech & Media", Icons.Outlined.Movie),
        CategoryIconItem("headphones", "Audio / Podcasts", "Tech & Media", Icons.Outlined.Headphones),

        // Health & Fitness
        CategoryIconItem("medication", "Pharmacy / Mercury Drug", "Health & Fitness", Icons.Outlined.Medication),
        CategoryIconItem("medical_services", "Clinic / Checkup", "Health & Fitness", Icons.Outlined.MedicalServices),
        CategoryIconItem("fitness_center", "Gym / Fitness", "Health & Fitness", Icons.Outlined.FitnessCenter),
        CategoryIconItem("favorite", "Health & Wellness", "Health & Fitness", Icons.Outlined.Favorite),
        CategoryIconItem("local_hospital", "Hospital / Emergency", "Health & Fitness", Icons.Outlined.LocalHospital),
        CategoryIconItem("psychology", "Mental Health", "Health & Fitness", Icons.Outlined.Psychology),

        // Education & Work
        CategoryIconItem("school", "Tuition / School", "Education & Work", Icons.Outlined.School),
        CategoryIconItem("menu_book", "Books & Reviewers", "Education & Work", Icons.Outlined.MenuBook),
        CategoryIconItem("work", "Office / Work Expenses", "Education & Work", Icons.Outlined.Work),
        CategoryIconItem("business_center", "Business / Side Gig", "Education & Work", Icons.Outlined.BusinessCenter),
        CategoryIconItem("badge", "Work ID / Clearance", "Education & Work", Icons.Outlined.Badge),

        // Family & Pets
        CategoryIconItem("pets", "Pets / Vet & Dog Food", "Family & Pets", Icons.Outlined.Pets),
        CategoryIconItem("child_care", "Baby / Toddler Care", "Family & Pets", Icons.Outlined.ChildCare),
        CategoryIconItem("celebration", "Family Event / Birthday", "Family & Pets", Icons.Outlined.Celebration),
        CategoryIconItem("volunteer_activism", "Padala / Family Help", "Family & Pets", Icons.Outlined.VolunteerActivism),

        // Finance & Income
        CategoryIconItem("savings", "Ipon / Savings Goal", "Finance & Income", Icons.Outlined.Savings),
        CategoryIconItem("payments", "Salary / Sweldo", "Finance & Income", Icons.Outlined.Payments),
        CategoryIconItem("account_balance", "Bank / Bangko", "Finance & Income", Icons.Outlined.AccountBalance),
        CategoryIconItem("credit_card", "Credit Card / Loan", "Finance & Income", Icons.Outlined.CreditCard),
        CategoryIconItem("monetization_on", "Cash / Allowance", "Finance & Income", Icons.Outlined.MonetizationOn),
        CategoryIconItem("security", "Insurance / PhilHealth", "Finance & Income", Icons.Outlined.Security)
    )

    fun getIcon(id: String?): ImageVector {
        if (id.isNullOrBlank()) return Icons.Outlined.Category
        return ICONS.firstOrNull { it.id.equals(id, ignoreCase = true) }?.icon ?: Icons.Outlined.Category
    }

    fun getIconForCategory(id: String?, categoryName: String = ""): ImageVector {
        if (!id.isNullOrBlank()) {
            val found = ICONS.firstOrNull { it.id.equals(id, ignoreCase = true) }
            if (found != null) return found.icon
        }
        val lower = categoryName.lowercase()
        return when {
            lower.contains("food") || lower.contains("din") -> Icons.Outlined.Restaurant
            lower.contains("util") || lower.contains("bill") -> Icons.Outlined.ElectricBolt
            lower.contains("trans") || lower.contains("commute") -> Icons.Outlined.TwoWheeler
            lower.contains("shop") || lower.contains("personal") -> Icons.Outlined.ShoppingBag
            lower.contains("rent") || lower.contains("hous") -> Icons.Outlined.House
            lower.contains("health") || lower.contains("med") -> Icons.Outlined.MedicalServices
            lower.contains("edu") || lower.contains("school") -> Icons.Outlined.School
            lower.contains("tech") || lower.contains("media") || lower.contains("subscript") -> Icons.Outlined.PlayCircle
            lower.contains("income") || lower.contains("salary") || lower.contains("sweldo") -> Icons.Outlined.Payments
            else -> Icons.Outlined.Category
        }
    }

    fun getColor(colorHex: String?, fallback: Color = Color(0xFF2E7D32)): Color {
        if (colorHex.isNullOrBlank()) return fallback
        return runCatching {
            val clean = colorHex.removePrefix("#")
            val colorLong = clean.toLong(16)
            if (clean.length == 6) {
                Color(colorLong or 0x00000000FF000000)
            } else {
                Color(colorLong)
            }
        }.getOrDefault(fallback)
    }

    fun getDefaultSubcategoriesForPreset(categoryName: String): List<String> {
        val lower = categoryName.lowercase()
        return when {
            lower.contains("util") || lower.contains("bill") ->
                listOf("Kuryente", "Tubig", "Internet", "Telepono / Load", "Gas / LPG", "Condo / Assoc Dues")
            lower.contains("food") || lower.contains("din") ->
                listOf("Groceries", "Fast Food", "Coffee & Milk Tea", "Restaurants", "Karinderya & Street Food", "Snacks")
            lower.contains("trans") || lower.contains("commute") ->
                listOf("Commute / Jeep / Bus", "Grab / Angkas / Taxi", "Gas / Fuel", "Toll / RFID", "Parking", "Maintenance")
            lower.contains("shop") || lower.contains("personal") ->
                listOf("Clothes & Apparel", "Skincare & Toiletries", "Online Shopping", "Haircut / Salon", "Gadgets")
            lower.contains("rent") || lower.contains("hous") ->
                listOf("House Rent", "Condo Rent", "Bedspace", "Repairs")
            lower.contains("health") || lower.contains("med") ->
                listOf("Medicine / Pharmacy", "Doctor / Checkup", "Dentist", "Vitamins & Supplements", "Hospital")
            lower.contains("edu") || lower.contains("school") ->
                listOf("Tuition", "School Supplies", "Books", "Allowance / Baon", "Uniform")
            lower.contains("entertain") || lower.contains("subscript") ->
                listOf("Streaming (Netflix)", "Gaming / Steam", "Movies / Cinema", "Outing & Vacation", "Concerts")
            lower.contains("fam") || lower.contains("pet") ->
                listOf("Pet Food / Vet", "Family Allowance", "Baby Care", "Gifts / Padala")
            lower.contains("sal") || lower.contains("income") || lower.contains("sahod") ->
                listOf("Payroll", "Side Hustle / Raket", "Allowance / Baon", "Business Sales", "Bonus / 13th Month")
            else -> emptyList()
        }
    }

    fun getSuggestedSubcategories(categoryName: String, currentSubcategories: List<String>): List<String> {
        val defaults = getDefaultSubcategoriesForPreset(categoryName)
        val currentSet = currentSubcategories.map { it.trim().lowercase() }.toSet()
        return defaults.filter { it.trim().lowercase() !in currentSet }
    }
}
