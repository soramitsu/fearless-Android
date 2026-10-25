package jp.co.soramitsu.wallet.impl.data.repository

import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigInteger
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.core.network.JsonFactory
import jp.co.soramitsu.core.runtime.RuntimeFactory
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.keypair.BaseKeypair
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Extrinsic
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.multiAddressFromId
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u8
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.isCallMovedToAssetHub
import jp.co.soramitsu.fearless_utils.runtime.metadata.ExtrinsicMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.FunctionArgument
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.MetadataFunction
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Module
import jp.co.soramitsu.fearless_utils.runtime.metadata.moduleOrNull
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersion
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.wallet.impl.domain.WalletInteractorImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Since the 2025 Asset Hub migration Polkadot and Kusama still list Vesting.vest but filter it at dispatch after
 * charging the fee, and the pinned fearless-utils refuses to build it (CallMovedToAssetHubException). Offering Claim
 * there opened a screen whose eager fee lookup crashed the app.
 *
 * `resources/parity` holds the live metadata captured for fearless-utils' parity vectors (state_getMetadata on
 * 2026-10-10, fearless-utils src/test/resources/parity): Polkadot 2005000, Kusama 2003002 and Westend 1025001.
 */
class ClaimRewardsCallTest {

    @Test
    fun `Polkadot and Kusama do not offer Claim although they still list Vesting vest`() = runBlocking<Unit> {
        listOf(POLKADOT, KUSAMA).forEach { chain ->
            val runtime = runtime(chain)
            val metadata = runtime.metadata

            // The check before this fix offered Claim whenever the call was listed.
            assertNotNull(chain.name, metadata.moduleOrNull(Modules.VESTING)?.calls?.get("vest"))
            assertTrue(chain.name, metadata.isCallMovedToAssetHub(Modules.VESTING, "vest"))

            assertNull(chain.name, metadata.claimRewardsCall())
            assertFalse(chain.name, interactorWith(chain.name, runtime).checkClaimSupport(chain.name))
        }
    }

    @Test
    fun `building the claim on Polkadot and Kusama never throws`() {
        listOf(POLKADOT, KUSAMA).forEach { chain ->
            // Before this fix the builder threw CallMovedToAssetHubException from inside the screen's fee flow.
            builder(runtime(chain)).claimRewards()
        }
    }

    @Test
    fun `Westend still dispatches vesting and keeps Claim`() = runBlocking<Unit> {
        val runtime = runtime(WESTEND)

        assertEquals(Modules.VESTING to "vest", runtime.metadata.claimRewardsCall())
        assertTrue(interactorWith(WESTEND.name, runtime).checkClaimSupport(WESTEND.name))

        val call = Extrinsic.fromHex(runtime, builder(runtime).claimRewards().build()).call
        assertEquals("Vesting.vest", "${call.module.name}.${call.function.name}")
    }

    @Test
    fun `claim calls keep their order on runtimes without the migration`() {
        val vestingWithClaim = metadata(module(Modules.VESTING, 25, "claim" to 0, "vest" to 1))
        val vestingOnly = metadata(module(Modules.VESTING, 25, "vest" to 0))
        val vestedRewards = metadata(module(Modules.VESTED_REWARDS, 45, "claim_rewards" to 0))
        val none = metadata(module("Balances", 5, "transfer_keep_alive" to 3))

        assertEquals(Modules.VESTING to "claim", vestingWithClaim.claimRewardsCall())
        assertEquals(Modules.VESTING to "vest", vestingOnly.claimRewardsCall())
        assertEquals(Modules.VESTED_REWARDS to "claim_rewards", vestedRewards.claimRewardsCall())
        assertNull(none.claimRewardsCall())
    }

    @Test
    fun `a relay chain with RcMigrator offers no vesting claim`() {
        val migrated = metadata(module(Modules.VESTING, 25, "vest" to 0), module("RcMigrator", 255))

        assertNull(migrated.claimRewardsCall())
    }

    private class ParityChain(val name: String, val specVersion: Int, val metadataSha256: String)

    private fun interactorWith(chainId: String, runtime: RuntimeSnapshot): WalletInteractorImpl {
        val chainRegistry = mockk<ChainRegistry>(relaxed = true)
        coEvery { chainRegistry.getRuntimeOrNull(chainId) } returns runtime

        return WalletInteractorImpl(
            walletRepository = mockk(relaxed = true),
            addressBookRepository = mockk(relaxed = true),
            accountRepository = mockk(relaxed = true),
            historyRepository = mockk(relaxed = true),
            chainRegistry = chainRegistry,
            fileProvider = mockk(relaxed = true),
            preferences = mockk(relaxed = true),
            selectedFiat = mockk(relaxed = true),
            xcmEntitiesFetcher = mockk(relaxed = true),
            chainsRepository = mockk(relaxed = true),
            networkStateService = mockk(relaxed = true),
            tokenRepository = mockk(relaxed = true)
        )
    }

    private fun builder(runtime: RuntimeSnapshot) = ExtrinsicBuilder(
        runtime = runtime,
        keypair = KEYPAIR,
        nonce = BigInteger.ZERO,
        runtimeVersion = RuntimeVersion(1, 1),
        genesisHash = ByteArray(32),
        multiChainEncryption = MultiChainEncryption.Substrate(EncryptionType.ED25519),
        accountIdentifier = multiAddressFromId(KEYPAIR.publicKey)
    )

    private fun runtime(chain: ParityChain): RuntimeSnapshot {
        val metadataHex = resource("parity/${chain.name}_metadata_v14.hex.gz") { GZIPInputStream(it) }.trim()
        val types = resource("parity/${chain.name}_parity_types.json") { it }

        assertEquals(chain.name, chain.metadataSha256, MessageDigest.getInstance("SHA-256").digest(metadataHex.toByteArray()).toHexString())

        return RuntimeFactory(JsonFactory.create()).constructRuntime(metadataHex, types, chain.specVersion).runtime
    }

    private fun resource(path: String, decode: (java.io.InputStream) -> java.io.InputStream): String {
        val stream = requireNotNull(javaClass.classLoader!!.getResourceAsStream(path)) { path }

        return decode(stream).bufferedReader().use { it.readText() }
    }

    private companion object {
        val POLKADOT = ParityChain(
            "polkadot",
            2005000,
            "2986ea3657f25db11c1883d1f4c364b041323aefd33c20777632c276fe322b46"
        )
        val KUSAMA = ParityChain(
            "kusama",
            2003002,
            "d66cefa12abd92dd0b77597845c79ca43a6d2fc9a98f4855254b8c0963ef9ef0"
        )
        val WESTEND = ParityChain(
            "westend",
            1025001,
            "c784c84a19101eda63ea7e53d928f46b86cb5c804ab5f82dc2fc8dd49ca9348c"
        )

        val KEYPAIR = BaseKeypair(ByteArray(32) { 7 }, ByteArray(32) { 1 })

        fun module(name: String, index: Int, vararg calls: Pair<String, Int>) = Module(
            name,
            null,
            calls.associate { (call, callIndex) ->
                call to MetadataFunction(call, listOf(FunctionArgument("value", u8)), emptyList(), index to callIndex)
            },
            emptyMap(),
            emptyMap(),
            emptyMap(),
            index.toBigInteger()
        )

        fun metadata(vararg modules: Module) = RuntimeMetadata(
            BigInteger.valueOf(14),
            modules.associateBy { it.name },
            ExtrinsicMetadata(BigInteger.valueOf(4), emptyList())
        )
    }
}
