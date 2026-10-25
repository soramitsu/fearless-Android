package jp.co.soramitsu.account.api.domain.model

import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryItem
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope

/**
 * What needs attention in one wallet, key by key. A wallet keeps signing in
 * every scope that has no blocking item (addendum U0.2).
 */
data class WalletRecoveryStatus(
    val metaId: Long,
    val items: List<WalletRecoveryItem>
) {
    /** Any key of this wallet cannot be used: the banner and portable export. */
    val isRecoveryRequired: Boolean
        get() = items.any(WalletRecoveryItem::isBlocking)

    val blockedScopes: Set<WalletSecretScope>
        get() = items.filter(WalletRecoveryItem::isBlocking).mapTo(linkedSetOf()) { it.scope }

    fun isBlocked(scope: WalletSecretScope): Boolean = scope in blockedScopes
}
