package jp.co.soramitsu.coredb.recovery.decision

/**
 * Process-local revocation mechanism for one already authenticated owner/lease.
 *
 * Construction is not authentication. The trusted feature adapter owns this
 * object, creates it only after a successful lease claim, and never exposes it
 * through navigation or saved state. It grants only a final offline SQL commit.
 * RPC, attachment, selection and UI publication require independent authority.
 */
class EndpointDecisionAuthority {
    private val coordinator = EndpointDecisionCommitCoordinator()

    /** Atomic revocation never waits for hashing, a database lock or physical commit. */
    fun revoke() = coordinator.revoke()

    /** A snapshot for admission, not a publication fence or a reusable commit grant. */
    fun isCurrent(): Boolean = coordinator.isCurrent()

    internal fun newAttempt(): EndpointDecisionCommitCoordinator.Attempt = coordinator.newAttempt()
    internal fun abandon(attempt: EndpointDecisionCommitCoordinator.Attempt) = coordinator.abandon(attempt)
    internal fun <T> commitOnce(attempt: EndpointDecisionCommitCoordinator.Attempt, commit: () -> T): T =
        coordinator.commitOnce(attempt, commit)

    override fun toString(): String = "EndpointDecisionAuthority"
}
