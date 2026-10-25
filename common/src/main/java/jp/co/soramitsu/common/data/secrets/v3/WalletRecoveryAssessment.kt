package jp.co.soramitsu.common.data.secrets.v3

/**
 * Whether the recovery material stored next to a signing keypair (entropy,
 * seed, mnemonic text and derivation path) re-derives that exact keypair.
 *
 * Signing never depends on this. A keypair that matches its durable public
 * identity and proves ownership keeps signing whatever the assessment. Only
 * phrase or seed export and backup depend on it: material that re-derives
 * another key would restore a different, empty account.
 */
sealed interface RecoveryAssessment {

    /** No recovery material is stored, for example a JSON or private-key import. */
    data object None : RecoveryAssessment

    /** Every stored representation re-derives the signing keypair. */
    data object Verified : RecoveryAssessment

    /**
     * TON only: the stored text re-derives the keypair, but it is not in
     * canonical form (extra spaces or capitals). 4.x derived TON keys from the
     * text exactly as typed, so it must be exported byte for byte.
     */
    data class VerifiedAsTyped(val variant: TonPhraseVariant) : RecoveryAssessment

    /** At least one stored representation does not re-derive the signing keypair. */
    data class Unverified(val issues: Set<RecoveryIssue>) : RecoveryAssessment
}

enum class TonPhraseVariant {

    /** The exact stored text, spaces and capitals included, re-derives the key. */
    AS_TYPED,

    /** Only a whitespace- or case-normalized copy of the stored text re-derives the key. */
    NORMALIZED
}

enum class RecoveryIssue {
    ENTROPY_LENGTH,
    ENTROPY_MISMATCH,
    SEED_LENGTH,
    SEED_MISMATCH,
    PATH_MALFORMED,
    PATH_OVERSIZED,
    MNEMONIC_NONCANONICAL,
    MNEMONIC_INVALID,
    MNEMONIC_WORDCOUNT,
    MNEMONIC_NOT_UTF8,
    MNEMONIC_MISMATCH,
    ETH_ENTROPY_WITHOUT_PATH
}

/**
 * A root secret that passed signing integrity, re-encoded canonically in
 * memory, together with the assessment of its recovery material.
 */
data class ValidatedRootSecret(
    val canonical: String,
    val assessment: RecoveryAssessment
)

internal fun recoveryAssessment(
    hasRecoveryMaterial: Boolean,
    issues: Set<RecoveryIssue>
): RecoveryAssessment = when {
    issues.isNotEmpty() -> RecoveryAssessment.Unverified(issues)
    hasRecoveryMaterial -> RecoveryAssessment.Verified
    else -> RecoveryAssessment.None
}
