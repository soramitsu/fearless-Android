package jp.co.soramitsu.core.extrinsic

/** Serializes a final operation boundary with changes to its mutation authorization. */
interface MutationExecutionGuard {
    fun <T> runIfAuthorized(intentSha256: String, operation: () -> T): T

    fun check(intentSha256: String) = runIfAuthorized(intentSha256) { Unit }

    /**
     * Invoked at the final writer callback, after queuing and encoding. A guard may atomically
     * admit the write and release UI/selection locks before blocking output. Once admitted, the
     * write may finish after revocation; admission is neither acknowledgement nor safe retry.
     * Existing guards retain their synchronous operation policy unless they opt into this hook.
     */
    fun <T> runSubmissionIfAuthorized(intentSha256: String, operation: () -> T): T =
        runIfAuthorized(intentSha256, operation)
}
