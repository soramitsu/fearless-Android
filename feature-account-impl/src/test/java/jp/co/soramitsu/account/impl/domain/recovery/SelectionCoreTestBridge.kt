package jp.co.soramitsu.account.impl.domain.recovery

import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.core.runtime.EndpointRuntimeStatus
import jp.co.soramitsu.coredb.recovery.EndpointRecordRef
import jp.co.soramitsu.coredb.recovery.decision.EndpointDecisionCandidate
import jp.co.soramitsu.coredb.recovery.decision.EndpointDecisionSourceBinding
import jp.co.soramitsu.coredb.recovery.decision.EndpointDecisionTarget
import jp.co.soramitsu.coredb.recovery.decision.EndpointRecoveryDecision
import jp.co.soramitsu.coredb.recovery.decision.EndpointSelectionAuthority
import jp.co.soramitsu.coredb.recovery.decision.EndpointSelectionCapture
import jp.co.soramitsu.coredb.recovery.decision.EndpointSelectionRequest
import jp.co.soramitsu.coredb.recovery.decision.EndpointSelectionResult
import jp.co.soramitsu.coredb.recovery.selection.EndpointMutationLane
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

/**
 * Unit-fixture bridge to exact JVM signatures in a separately compiled core-db module.
 * No production factory or authority is widened. Storage remains synthetic; the invoked lane,
 * capture, authority and final-grant implementations are real. SQL/native tests live in core-db.
 */
internal object SelectionCoreTestBridge {
    private fun method(
        type: Class<*>,
        name: String,
        vararg parameters: Class<*>
    ): Method = type.declaredMethods.single {
            it.name.substringBefore('$') == name && it.parameterTypes.contentEquals(parameters)
        }

    private fun Method.call(receiver: Any, vararg arguments: Any?): Any? = try {
        invoke(receiver, *arguments)
    } catch (error: InvocationTargetException) {
        throw error.targetException
    }

    suspend fun <T> readRuntime(
        lane: EndpointMutationLane,
        chainId: String,
        read: suspend () -> T
    ): EndpointMutationLane.RuntimeRead<T> = suspendCoroutineUninterceptedOrReturn { continuation ->
        method(
            EndpointMutationLane::class.java, "readRuntime", String::class.java,
            Function1::class.java, Continuation::class.java
        ).call(lane, chainId, read, continuation)
    }

    fun sourceBinding(ref: EndpointRecordRef, fingerprint: String): EndpointDecisionSourceBinding =
        EndpointDecisionSourceBinding::class.java.getDeclaredConstructor(EndpointRecordRef::class.java, String::class.java)
            .newInstance(ref, fingerprint)

    fun target(
        chainId: String,
        fingerprint: String,
        family: EndpointDecisionTarget.Family,
        genesis: String
    ): EndpointDecisionTarget = EndpointDecisionTarget::class.java.getDeclaredConstructor(
            String::class.java, String::class.java,
            EndpointDecisionTarget.Family::class.java, String::class.java
        ).newInstance(chainId, fingerprint, family, genesis)

    fun decision(
        id: Long,
        revision: Long,
        source: EndpointDecisionSourceBinding,
        candidate: EndpointDecisionCandidate,
        target: EndpointDecisionTarget
    ): EndpointRecoveryDecision = EndpointRecoveryDecision::class.java.getDeclaredConstructor(
            Long::class.javaPrimitiveType, Long::class.javaPrimitiveType,
            EndpointDecisionSourceBinding::class.java, EndpointDecisionCandidate::class.java, EndpointDecisionTarget::class.java
        )
            .newInstance(id, revision, source, candidate, target)

    fun runtimeStatus(
        phase: EndpointRuntimeStatus.Phase,
        applied: Boolean,
        logical: ChainNode,
        current: () -> Boolean
    ): EndpointRuntimeStatus = EndpointRuntimeStatus::class.java.getDeclaredConstructor(
            EndpointRuntimeStatus.Phase::class.java,
            Boolean::class.javaPrimitiveType, ChainNode::class.java, Function0::class.java
        ).newInstance(phase, applied, logical, current)

    fun capture(
        request: EndpointSelectionRequest,
        operation: EndpointMutationLane.Operation
    ): EndpointSelectionCapture = EndpointSelectionCapture::class.java.getDeclaredConstructor(
            EndpointSelectionRequest::class.java, EndpointMutationLane.Operation::class.java,
            Long::class.javaPrimitiveType,
            Class.forName("jp.co.soramitsu.coredb.recovery.decision.EndpointSelectionSql\$Selected")
        ).newInstance(request, operation, 8L, null)

    fun operation(capture: EndpointSelectionCapture): EndpointMutationLane.Operation =
        method(EndpointSelectionCapture::class.java, "getOperation").call(capture) as EndpointMutationLane.Operation

    fun commitOnce(
        authority: EndpointSelectionAuthority,
        attempt: EndpointSelectionAuthority.Attempt,
        capture: EndpointSelectionCapture,
        commit: () -> Unit
    ) {
        method(
            EndpointSelectionAuthority::class.java, "commitOnce", EndpointSelectionAuthority.Attempt::class.java,
            EndpointSelectionCapture::class.java, Function0::class.java
        ).call(authority, attempt, capture, commit)
    }

    fun result(capture: EndpointSelectionCapture): EndpointSelectionResult =
        EndpointSelectionResult::class.java.getDeclaredConstructor(
            EndpointSelectionResult.Kind::class.java, EndpointSelectionCapture::class.java
        ).newInstance(EndpointSelectionResult.Kind.SELECTED, capture)
}
