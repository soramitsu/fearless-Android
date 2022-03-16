package jp.co.soramitsu.common.model

import jp.co.soramitsu.fearless_utils.runtime.AccountId

data class AssetKey(
    val metaId: Long,
    val chainId: String,
    val accountId: AccountId,
    val tokenSymbol: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AssetKey

        if (metaId != other.metaId) return false
        if (chainId != other.chainId) return false
        if (!accountId.contentEquals(other.accountId)) return false
        if (tokenSymbol != other.tokenSymbol) return false

        return true
    }

    override fun hashCode(): Int {
        var result = metaId.hashCode()
        result = 31 * result + chainId.hashCode()
        result = 31 * result + accountId.contentHashCode()
        result = 31 * result + tokenSymbol.hashCode()
        return result
    }
}
