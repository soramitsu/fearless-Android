package jp.co.soramitsu.walletconnect.impl.presentation

import co.jp.soramitsu.walletconnect.model.WalletConnectValidation
import co.jp.soramitsu.walletconnect.model.WalletConnectVerifyContext
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.resources.ResourceManager

/**
 * The one short warning a screen shows about where a dApp runs: none when Verify vouches for the
 * URL the dApp claims, a mismatch warning when the dApp runs elsewhere, and an unverified warning
 * when no attestation came with it.
 */
internal fun WalletConnectVerifyContext.originWarning(resourceManager: ResourceManager): String? = when (validation) {
    WalletConnectValidation.VALID -> null
    WalletConnectValidation.INVALID -> resourceManager.getString(
        R.string.walletconnect_origin_mismatch,
        verifiedOrigin?.let(::hostOf).orEmpty(),
        claimedUrl?.let(::hostOf).orEmpty()
    )
    WalletConnectValidation.UNKNOWN -> resourceManager.getString(R.string.walletconnect_origin_unverified)
}

/** The notice for a dApp Verify lists as a scam; the site is named by the origin Verify saw. */
internal fun WalletConnectVerifyContext.scamNotice(resourceManager: ResourceManager): String =
    resourceManager.getString(R.string.walletconnect_scam_blocked, (verifiedOrigin ?: claimedUrl)?.let(::hostOf).orEmpty())
