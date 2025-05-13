package jp.co.soramitsu.common.data.network.ton

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

data class JettonsBalances(

    @SerializedName("balances")
    val balances: List<JettonBalance>

)

data class JettonBalance(

    @SerializedName("balance")
    val balance: String,

    @SerializedName("wallet_address")
    val walletAddress: AccountAddress,

    @SerializedName("jetton")
    val jetton: JettonPreview,

    @SerializedName("price")
    val price: TokenRates? = null,

    @SerializedName("extensions")
    val extensions: List<String>? = null,

    @SerializedName("lock")
    val lock: JettonBalanceLock? = null

)

data class JettonPreview(

    @SerializedName("address")
    val address: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("symbol")
    val symbol: String,

    @SerializedName("decimals")
    val decimals: Int,

    @SerializedName("image")
    val image: String,

    @SerializedName("verification")
    val verification: String

)

data class TokenRates(

    @SerializedName("prices")
    val prices: Map<String, BigDecimal>? = null,

    @SerializedName("diff_24h")
    val diff24h: Map<String, String>? = null,

    @SerializedName("diff_7d")
    val diff7d: Map<String, String>? = null,

    @SerializedName("diff_30d")
    val diff30d: Map<String, String>? = null

)

data class JettonBalanceLock(

    @SerializedName("amount")
    val amount: String,

    @SerializedName("till")
    val till: Long

)

data class AccountAddress(

    @SerializedName("address")
    val address: String,

    /* Is this account was marked as part of scammers activity */
    @SerializedName("is_scam")
    val isScam: Boolean,

    @SerializedName("is_wallet")
    val isWallet: Boolean,

    /* Display name. Data collected from different sources like moderation lists, dns, collections names and over. */
    @SerializedName("name")
    val name: String? = null,

    @SerializedName("icon")
    val icon: String? = null

)