package jp.co.soramitsu.core.extrinsic

/** Serializes a final operation boundary with changes to its mutation authorization. */
interface MutationExecutionGuard {
    fun <T> runIfAuthorized(intentSha256: String, operation: () -> T): T

    fun check(intentSha256: String) = runIfAuthorized(intentSha256) { Unit }
}
