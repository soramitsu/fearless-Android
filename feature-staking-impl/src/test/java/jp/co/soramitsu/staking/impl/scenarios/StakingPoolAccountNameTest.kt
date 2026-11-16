package jp.co.soramitsu.staking.impl.scenarios

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.domain.api.IdentityRepository
import jp.co.soramitsu.staking.api.domain.model.RootIdentity
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Pool screens (create pool, edit roles) show an account's on-chain identity in place of its address when it is
 * not one of the user's wallets. The identities come back keyed by hex account id and were looked up by the SS58
 * address, so the name never appeared.
 */
class StakingPoolAccountNameTest {

    private val accountId = ByteArray(32) { 4 }
    private val address = accountId.toAddress(0)
    private val chain = mock<Chain> {
        on { ecosystem } doReturn Ecosystem.Substrate
        on { isEthereumBased } doReturn false
    }

    @Test
    fun `an account with an on-chain identity is shown by its display name`() = runTest {
        val identity = RootIdentity("Pool operator", null, null, null, null, null, null, null)

        assertEquals("Pool operator", interactor(mapOf(accountId.toHexString(false) to identity)).getAccountName(address))
    }

    @Test
    fun `an account without an identity has no name`() = runTest {
        assertNull(interactor(mapOf(accountId.toHexString(false) to null)).getAccountName(address))
    }

    private suspend fun interactor(identities: Map<String, RootIdentity?>): StakingPoolInteractor {
        val staking = mock<StakingInteractor> { on { selectedChainFlow() } doReturn flowOf(chain) }
        val accounts = mock<AccountRepository>()
        whenever(accounts.findMetaAccount(any())).thenReturn(null)
        val identityRepository = mock<IdentityRepository>()
        whenever(identityRepository.getIdentitiesFromIds(chain, listOf(accountId.toHexString(false)))).thenReturn(identities)

        return StakingPoolInteractor(mock(), mock(), staking, mock(), accounts, identityRepository, mock(), mock())
    }
}
