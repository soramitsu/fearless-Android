package jp.co.soramitsu.core.extrinsic

import jp.co.soramitsu.core.extrinsic.mortality.ExtrinsicValidity

/** Durable retention surrounds RPC submission; implementations must not hold UI locks during I/O. */
interface ExtrinsicSubmissionTracking {
    /** The blocks the signed transaction can be included in; reported before [guard] when they are known. */
    fun validity(window: ExtrinsicValidity) = Unit

    fun guard(signedExtrinsic: String, intent: String, authority: MutationExecutionGuard): MutationExecutionGuard
    fun acknowledged(transactionHash: String)
    fun finished()
}
