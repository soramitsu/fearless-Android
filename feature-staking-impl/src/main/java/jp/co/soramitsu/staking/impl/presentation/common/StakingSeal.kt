package jp.co.soramitsu.staking.impl.presentation.common

import jp.co.soramitsu.common.feedback.TransferSeal
import jp.co.soramitsu.staking.impl.domain.payout.PayoutInteractor

/**
 * The seal (brand kit SPEC v4 part 3) for the relay-chain staking confirmations, whose own progress is a spinner in
 * their button: the seal is drawn over the whole screen on ink (TransferSealOverlayHost). Each runs at the hand-over,
 * after validation, around the one call that signs and submits: `ready` while it runs; `signed`, `sent` and `accepted`
 * together with its result (these screens cannot tell them apart); a definite failure breaks the seal, anything whose
 * fate is unknown clears it. The result is returned unchanged and at once.
 */
internal object StakingSeal {
    /** Bond and nominate, bond extra, rebond, redeem, reward destination, set controller. */
    suspend fun <T> submit(submit: suspend () -> Result<T>): Result<T> =
        TransferSeal.track(shownOver = true, submit = submit)

    /** Payout: a review refused because the staking network changed (nothing was signed) clears the seal. */
    suspend fun payout(submit: suspend () -> Result<String>): Result<String> =
        TransferSeal.track(definiteFailure = { it.message != PayoutInteractor.STALE_REVIEW }, shownOver = true, submit = submit)

    /** Unbond: a result for an attempt that is no longer current (the screen will not show it) clears the seal. */
    suspend fun <T> unbond(isCurrent: () -> Boolean, submit: suspend () -> Result<T>): Result<T> =
        TransferSeal.track(definiteFailure = { isCurrent() }, shownOver = true, submit = submit)
}
