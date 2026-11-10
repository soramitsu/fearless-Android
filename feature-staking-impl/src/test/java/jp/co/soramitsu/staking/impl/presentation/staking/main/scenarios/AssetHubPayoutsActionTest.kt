package jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios

import com.google.gson.Gson
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.assethub.AssetHubStaking
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.toChain
import jp.co.soramitsu.staking.impl.presentation.staking.main.ManageStakeAction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File

/**
 * Pending payouts on the Asset Hubs are found on chain, and the catalogue lists no staking indexer for them. The
 * Manage menu still offers Payouts there, to nominators, validators and stashes without a role.
 */
class AssetHubPayoutsActionTest {
    private val catalogue: List<Chain> =
        Gson().fromJson(File("../remote-config/production/chains/v13/chains.json").readText(), Array<ChainRemote>::class.java)
            .filter { !it.disabled }
            .mapNotNull { runCatching { it.toChain() }.getOrNull() }

    @Test
    fun `both production Hubs offer Payouts without a staking indexer`() {
        listOf(AssetHubStaking.POLKADOT, AssetHubStaking.KUSAMA).forEach { id ->
            val hub = catalogue.single { it.id == id }
            assertNull(hub.externalApi?.staking)
            assertTrue(hub.assets.any { it.staking == Asset.StakingType.RELAYCHAIN })
            assertTrue(relayPayoutsSupported(hub))
            RelayStakeKind.entries.forEach { kind ->
                assertTrue("$id $kind", ManageStakeAction.PAYOUTS in relayManageActions(kind, relayPayoutsSupported(hub)))
            }
        }
    }

    @Test
    fun `a network without a staking indexer still hides Payouts`() {
        val chain = mock<Chain> { on { id } doReturn "no-indexer" }
        assertFalse(relayPayoutsSupported(chain))
    }
}
