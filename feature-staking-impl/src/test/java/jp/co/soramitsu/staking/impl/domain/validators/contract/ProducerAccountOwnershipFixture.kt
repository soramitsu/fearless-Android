package jp.co.soramitsu.staking.impl.domain.validators.contract

import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.staking.api.domain.model.Delegation
import jp.co.soramitsu.staking.api.domain.model.DelegatorState
import jp.co.soramitsu.staking.api.domain.model.DelegatorStateStatus
import jp.co.soramitsu.staking.impl.domain.validators.current.search.SearchCustomBlockProducerInteractor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigInteger

internal class ProducerAccountOwnershipFixture {
    val data = ProducerSearchFixture(true)
    val para = data.para
    val secondAccount = ByteArray(20) { 8 }
    val selectedWallet = MutableStateFlow(wallet(11, para.accountId))
    val interactor = SearchCustomBlockProducerInteractor(
        para.provider, data.validatorProvider, data.shared, data.cache, mock(), para.accounts
    )
    val started = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()

    fun wallet(id: Long, accountId: ByteArray?) = mock<MetaAccount> {
        on { this.id } doReturn id
        on { ethereumAddress } doReturn accountId
        on { chainAccounts } doReturn emptyMap()
    }

    suspend fun start() {
        data.defaults()
        para.defaults()
        whenever(para.accounts.getSelectedMetaAccount()).thenAnswer { selectedWallet.value }
        whenever(para.repository.getDelegatorState(any(), any())).thenAnswer {
            val account = it.getArgument<ByteArray>(1)
            val usedIndex = if (account.contentEquals(para.accountId)) 0 else 1
            DelegatorState(
                account, listOf(Delegation(para.ids[usedIndex], BigInteger("500"))),
                BigInteger("500"), DelegatorStateStatus.ACTIVE
            )
        }
    }

    suspend fun holdCandidateResponse() {
        whenever(para.repository.getCandidateInfos(any(), any())).doSuspendableAnswer {
            val requested = it.getArgument<List<ByteArray>>(1)
            started.complete(Unit)
            release.await()
            requested.associate { account -> account.toHexString() to para.candidate }
        }
    }

    suspend fun rows(owner: SearchContractLifecycle = data.lifecycle) = interactor.getBlockProducers(owner)

    suspend fun <T> await(value: Deferred<T>): T = withContext(Dispatchers.Default) {
        withTimeout(3_000) { value.await() }
    }

    fun close() {
        release.complete(Unit)
        data.lifecycle.destroy()
    }
}
