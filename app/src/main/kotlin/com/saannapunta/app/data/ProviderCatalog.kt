package com.saannapunta.app.data

import com.saannapunta.app.data.model.AccountType

/**
 * Single source of truth for supported Philippine banks and e-wallets (design spec section 5.2).
 * [provider] is the lowercase key the SMS/notification parser matches against. [aliases] are the
 * other names people search by ("LBP", "Union Bank", "Shopee").
 */
data class ProviderInfo(
    val name: String,
    val provider: String,
    val type: AccountType,
    val iconResName: String?,
    val group: String,
    val aliases: List<String> = emptyList()
) {
    fun matches(query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        val compact = q.replace(" ", "")
        return name.contains(q, ignoreCase = true) ||
            name.replace(" ", "").contains(compact, ignoreCase = true) ||
            group.contains(q, ignoreCase = true) ||
            aliases.any { it.contains(q, ignoreCase = true) || it.replace(" ", "").contains(compact, ignoreCase = true) }
    }
}

object ProviderCatalog {
    val all: List<ProviderInfo> = listOf(
        // E-wallets
        ProviderInfo("GCash", "gcash", AccountType.E_WALLET, "ic_gcash", "E-wallets", listOf("Globe", "G-Cash")),
        ProviderInfo("Maya", "maya", AccountType.E_WALLET, "ic_maya", "E-wallets", listOf("PayMaya", "Maya Bank")),
        ProviderInfo("ShopeePay", "spay", AccountType.E_WALLET, "ic_spay", "E-wallets", listOf("Shopee", "SPay", "SeaMoney")),
        ProviderInfo("GrabPay", "grabpay", AccountType.E_WALLET, "ic_grabpay", "E-wallets", listOf("Grab")),
        ProviderInfo("Coins.ph", "coinsph", AccountType.E_WALLET, "ic_coinsph", "E-wallets", listOf("Coins", "Crypto")),
        ProviderInfo("Palawan Pay", "palawanpay", AccountType.E_WALLET, "ic_palawanpay", "E-wallets", listOf("Palawan Express", "Pera Padala")),
        ProviderInfo("Vybe", "vybe", AccountType.E_WALLET, "ic_vybe", "E-wallets", listOf("BPI Vybe")),
        // Traditional banks
        ProviderInfo("BDO", "bdo", AccountType.BANK, "ic_bdo", "Banks", listOf("Banco de Oro", "BDO Unibank")),
        ProviderInfo("BPI", "bpi", AccountType.BANK, "ic_bpi", "Banks", listOf("Bank of the Philippine Islands")),
        ProviderInfo("Metrobank", "metrobank", AccountType.BANK, "ic_metrobank", "Banks", listOf("Metropolitan Bank", "MBTC")),
        ProviderInfo("Landbank", "landbank", AccountType.BANK, null, "Banks", listOf("Land Bank", "LBP", "Land Bank of the Philippines")),
        ProviderInfo("Security Bank", "securitybank", AccountType.BANK, "ic_security_bank", "Banks", listOf("SecBank")),
        ProviderInfo("PNB", "pnb", AccountType.BANK, "ic_pnb", "Banks", listOf("Philippine National Bank")),
        ProviderInfo("RCBC", "rcbc", AccountType.BANK, "ic_rcbc", "Banks", listOf("Rizal Commercial", "DiskarTech")),
        ProviderInfo("UnionBank", "unionbank", AccountType.BANK, "ic_unionbank", "Banks", listOf("Union Bank", "UB")),
        ProviderInfo("China Bank", "chinabank", AccountType.BANK, "ic_chinabank", "Banks", listOf("Chinabank", "China Banking")),
        ProviderInfo("EastWest", "eastwest", AccountType.BANK, "ic_eastwest", "Banks", listOf("East West", "EW")),
        ProviderInfo("PSBank", "psbank", AccountType.BANK, "ic_psbank", "Banks", listOf("Philippine Savings Bank")),
        // Digital banks
        ProviderInfo("GoTyme", "gotyme", AccountType.BANK, "ic_gotyme", "Digital banks", listOf("Go Tyme", "GoTyme Bank")),
        ProviderInfo("UnionDigital", "uniondigital", AccountType.BANK, "ic_uniondigital", "Digital banks", listOf("Union Digital")),
        ProviderInfo("MariBank", "maribank", AccountType.BANK, "ic_maribank", "Digital banks", listOf("SeaBank", "Sea Bank", "Mari Bank")),
        ProviderInfo("CIMB", "cimb", AccountType.BANK, "ic_cimb", "Digital banks", listOf("CIMB Bank")),
        ProviderInfo("Tonik", "tonik", AccountType.BANK, "ic_tonik", "Digital banks", listOf("Tonik Bank")),
        ProviderInfo("UNO Digital Bank", "unobank", AccountType.BANK, "ic_unobank", "Digital banks", listOf("UNO", "UNOBank")),
        // Other
        ProviderInfo("Cash", "cash", AccountType.CASH, "ic_cash", "Other", listOf("Cash on hand", "Pera", "Wallet")),
        ProviderInfo("Other bank", "other", AccountType.BANK, null, "Other", listOf("Other", "Cooperative", "Rural bank"))
    )

    /** The accounts most people in the Philippines have, shown first in the picker. */
    val popularNames: List<String> = listOf("Cash", "GCash", "Maya", "BDO", "BPI", "GoTyme", "Metrobank", "UnionBank")

    val popular: List<ProviderInfo> get() = popularNames.mapNotNull { byName(it) }

    fun byName(name: String): ProviderInfo? = all.firstOrNull { it.name.equals(name, ignoreCase = true) }
}
