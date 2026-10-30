package jp.co.soramitsu.staking.impl.data.repository

import com.google.gson.Gson
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.core.runtime.storage.returnType
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toHex
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import jp.co.soramitsu.runtime.assethub.AssetHubStaking
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.ChainService
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeProvider
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.api.domain.model.ValidatorPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.math.BigInteger

class AssetHubPayoutContractTest {
    private class Fixture {
        val runtime = AssetHubMetadataContractTest.runtimes.getValue("polkadot")
        val staking = runtime.metadata.staking()
        val registry = mock<ChainRegistry>()
        val socket = mock<SocketService>()
        val provider = mock<RuntimeProvider>()
        val connection = mock<ChainConnection> { on { socketService } doReturn socket }
        val chain = mock<Chain> {
            on { id } doReturn AssetHubStaking.POLKADOT
            on { addressPrefix } doReturn 0
            on { ecosystem } doReturn Ecosystem.Substrate
        }
        val owner = ByteArray(32) { 1 }
        val formerValidator = ByteArray(32) { 2 }
        val state = StakingState.Stash.None(chain, owner, owner, owner)
        val values = mutableMapOf<String, String?>()
        val pageKeys = mutableListOf<String>()
        var omitResponse = false
        var wrongBlock = false
        var duplicateChange = false
        var silentMethod: String? = null
        val cancellable = mock<SocketService.Cancellable>()
        val queriedBlocks = mutableListOf<String?>()
        val queriedPrefixes = mutableListOf<String>()
        val repository = AssetHubPayoutRepository(registry)

        @Suppress("UNCHECKED_CAST")
        fun put(
            name: String,
            value: Any,
            vararg arguments: Any
        ): String {
            val storage = staking.storage(name)
            val key = storage.storageKey(runtime, *arguments)
            values[key] = (storage.returnType() as Type<Any>).toHex(runtime, value)
            return key
        }

        suspend fun payouts(stash: StakingState.Stash = state) =
            withContext(Dispatchers.Default) { repository.payouts(stash) }
        suspend fun warnings(accounts: List<String>) = withContext(Dispatchers.Default) {
            AssetHubSlashWarnings(registry).get(chain.id, accounts)
        }

        suspend fun setup() {
            whenever(registry.getService(chain.id)).thenReturn(ChainService(provider, connection))
            whenever(provider.get()).thenReturn(runtime)
            whenever(socket.jsonMapper).thenReturn(Gson())
            whenever(socket.executeRequest(any(), any(), any())).thenAnswer { invocation ->
                val request = invocation.getArgument<RuntimeRequest>(0)
                if (request.method == silentMethod) return@thenAnswer cancellable
                val response: Any = when (request.method) {
                    "chain_getFinalizedHead" -> "finalized-block"
                    "state_getKeysPaged" -> {
                        queriedBlocks += request.params[3] as String
                        val prefix = request.params[0] as String
                        queriedPrefixes += prefix
                        val paged = staking.storage("ErasStakersPaged").storageKey()
                        if (prefix.startsWith(paged)) {
                            // Malformed entries are served to every page query so that the scan must reject them.
                            pageKeys.filter { it.startsWith(prefix) || !it.startsWith(paged) }
                        } else {
                            values.keys.filter { it.startsWith(prefix) }.sorted()
                        }
                    }
                    "state_queryStorageAt" -> {
                        queriedBlocks += request.params[1] as String
                        val keys = request.params[0] as List<String>
                        val changes = if (omitResponse) emptyList() else keys.map { listOf(it, values[it]) }
                        listOf(
                            mapOf(
                                "block" to if (wrongBlock) "other-block" else "finalized-block",
                            "changes" to if (duplicateChange) changes + changes.take(1) else changes
                            )
                        )
                    }
                    else -> error("Unexpected RPC ${request.method}")
                }
                invocation.getArgument<SocketService.ResponseListener<RpcResponse>>(2).onNext(RpcResponse("2.0", response, 1, null))
                mock<SocketService.Cancellable>()
            }
            put("CurrentEra", 101.toBigInteger())
            put("ActiveEra", Struct.Instance(mapOf("index" to 100.toBigInteger(), "start" to 1000.toBigInteger())))
            val era = 99.toBigInteger()
            pageKeys += put(
                "ErasStakersPaged",
                Struct.Instance(
                    mapOf(
                        "pageTotal" to 900.toBigInteger(),
                        "others" to listOf(
                Struct.Instance(mapOf("who" to owner, "value" to 900.toBigInteger()))
            )
                    )
                ),
                era, formerValidator, BigInteger.ZERO
            )
            put("ErasStakersOverview", Struct.Instance(mapOf("total" to 1000.toBigInteger(), "own" to 100.toBigInteger(), "nominatorCount" to BigInteger.ONE, "pageCount" to BigInteger.ONE)), era, formerValidator)
            put("ErasValidatorPrefs", Struct.Instance(mapOf("commission" to 100_000_000.toBigInteger(), "blocked" to false)), era, formerValidator)
            put("ErasValidatorReward", 10_000.toBigInteger(), era)
            put("ErasRewardPoints", Struct.Instance(mapOf("total" to 100.toBigInteger(), "individual" to listOf(listOf(formerValidator, 100.toBigInteger())))), era)
        }
    }

    private fun Fixture.nominate(targets: List<ByteArray>, submittedIn: Int) {
        put("Nominators", Struct.Instance(mapOf("targets" to targets, "submittedIn" to submittedIn.toBigInteger(), "suppressed" to false)), owner)
    }

    private fun Fixture.pagePrefixes() = queriedPrefixes.filter { it.startsWith(staking.storage("ErasStakersPaged").storageKey()) }

    @Test fun `eras elected after the nominations read only the current targets' pages`() = runTest {
        val f = Fixture()
        f.setup()
        f.nominate(listOf(f.formerValidator), submittedIn = 10)
        val payout = f.payouts(f.state).single()
        assertEquals(8100.toBigInteger(), payout.amount)
        assertEquals(99.toBigInteger(), payout.era)
        assertTrue(f.pagePrefixes().isEmpty())
    }

    @Test fun `eras the nominations may not have reached are scanned page by page`() = runTest {
        val f = Fixture()
        f.setup()
        val other = ByteArray(32) { 3 }
        f.nominate(listOf(other), submittedIn = 97)
        // Era 99 is read through the current target only; eras up to 98 are scanned.
        assertTrue(f.payouts(f.state).isEmpty())
        val scanned = f.pagePrefixes().map { prefix -> (17..98).single { prefix == f.staking.storage("ErasStakersPaged").storageKey(f.runtime, it.toBigInteger()) } }
        assertEquals((17..98).toList(), scanned)
        f.nominate(listOf(other), submittedIn = 98)
        assertEquals(8100.toBigInteger(), f.payouts(f.state).single().amount)
    }

    @Test fun `a target page the overview counts but storage lacks is an error`() = runTest {
        val f = Fixture()
        f.setup()
        f.nominate(listOf(f.formerValidator), submittedIn = 10)
        f.put("ErasStakersOverview", Struct.Instance(mapOf("total" to 1000.toBigInteger(), "own" to 100.toBigInteger(), "nominatorCount" to BigInteger.ONE, "pageCount" to 2.toBigInteger())), 99.toBigInteger(), f.formerValidator)
        assertTrue(runCatching { f.payouts(f.state) }.isFailure)
    }

    @Test fun `historical nomination is discovered even after account stops nominating`() = runTest {
        val f = Fixture()
        f.setup()
        val payouts = f.payouts(f.state)
        assertEquals(1, payouts.size)
        assertEquals(8100.toBigInteger(), payouts.single().amount)
        assertEquals(BigInteger.ZERO, payouts.single().page)
        assertTrue(f.queriedBlocks.all { it == "finalized-block" })
    }

    @Test fun `already claimed page is excluded without hiding other pages`() = runTest {
        val f = Fixture()
        f.setup()
        f.put("ClaimedRewards", listOf(BigInteger.ZERO), 99.toBigInteger(), f.formerValidator)
        assertTrue(f.payouts(f.state).isEmpty())
    }

    @Test fun `unclaimed page remains independently payable after another page is claimed`() = runTest {
        val f = Fixture()
        f.setup()
        val era = 99.toBigInteger()
        f.pageKeys += f.put(
            "ErasStakersPaged",
            Struct.Instance(
                mapOf(
                    "pageTotal" to 900.toBigInteger(),
                    "others" to listOf(
            Struct.Instance(mapOf("who" to f.owner, "value" to 900.toBigInteger()))
        )
                )
            ),
            era, f.formerValidator, BigInteger.ONE
        )
        f.pageKeys.sort()
        f.put("ErasStakersOverview", Struct.Instance(mapOf("total" to 1900.toBigInteger(), "own" to 100.toBigInteger(), "nominatorCount" to 2.toBigInteger(), "pageCount" to 2.toBigInteger())), era, f.formerValidator)
        f.put("ClaimedRewards", listOf(BigInteger.ZERO), era, f.formerValidator)
        val payout = f.payouts(f.state).single()
        assertEquals(BigInteger.ONE, payout.page)
        assertEquals(4263.toBigInteger(), payout.amount)
    }

    @Test fun `incomplete RPC response is an error rather than zero pending rewards`() = runTest {
        val f = Fixture()
        f.setup()
        f.omitResponse = true
        assertTrue(runCatching { f.payouts(f.state) }.isFailure)
    }

    @Test fun `mixed block and duplicate storage responses are rejected`() = runTest {
        val f = Fixture()
        f.setup()
        f.wrongBlock = true
        assertTrue(runCatching { f.payouts(f.state) }.isFailure)
        f.wrongBlock = false
        f.duplicateChange = true
        assertTrue(runCatching { f.payouts(f.state) }.isFailure)
    }

    @Test fun `unresponsive finalized head query times out and cancels its RPC request`() = runTest {
        val f = Fixture()
        f.setup()
        f.silentMethod = "chain_getFinalizedHead"
        val failure = runCatching { f.repository.payouts(f.state) }.exceptionOrNull()
        assertTrue(failure is TimeoutCancellationException)
        verify(f.cancellable).cancel()
    }

    @Test fun `duplicate and out of prefix pages are rejected before reward lookup`() = runTest {
        val f = Fixture()
        f.setup()
        f.pageKeys += f.pageKeys.single()
        assertTrue(runCatching { f.payouts(f.state) }.isFailure)
        f.pageKeys.clear()
        f.pageKeys += "0x00"
        assertTrue(runCatching { f.payouts(f.state) }.isFailure)
    }

    @Test fun `own stake only validator has a claimable virtual page zero`() = runTest {
        val f = Fixture()
        f.setup()
        f.pageKeys.clear()
        val era = 99.toBigInteger()
        f.put("ErasStakersOverview", Struct.Instance(mapOf("total" to 1000.toBigInteger(), "own" to 1000.toBigInteger(), "nominatorCount" to BigInteger.ZERO, "pageCount" to BigInteger.ZERO)), era, f.formerValidator)
        val validator = StakingState.Stash.Validator(f.chain, f.formerValidator, f.formerValidator, f.formerValidator, ValidatorPrefs(BigDecimal("0.1"), false))
        val payouts = f.payouts(validator)
        assertEquals(10_000.toBigInteger(), payouts.single().amount)
        assertEquals(BigInteger.ZERO, payouts.single().page)
        val former = StakingState.Stash.None(f.chain, f.formerValidator, f.formerValidator, f.formerValidator)
        assertEquals(10_000.toBigInteger(), f.payouts(former).single().amount)
    }

    @Test fun `validator bonus honors missing weighted cutoff and older legacy eras`() = runTest {
        val f = Fixture()
        f.setup()
        val era = 99.toBigInteger()
        f.put("ErasValidatorIncentiveBudget", 5000.toBigInteger(), era)
        f.put("ErasValidatorIncentiveWeight", 2.toBigInteger(), era, f.formerValidator)
        f.put("ErasSumValidatorIncentiveWeight", 4.toBigInteger(), era)
        f.put("ErasSumWeightedPoints", 800.toBigInteger(), era)
        val validator = StakingState.Stash.Validator(f.chain, f.formerValidator, f.formerValidator, f.formerValidator, ValidatorPrefs(BigDecimal("0.1"), false))
        assertEquals(3150.toBigInteger(), f.payouts(validator).single().amount)
        f.put("WeightedPointsFormulaStartEra", 100.toBigInteger())
        assertEquals(4400.toBigInteger(), f.payouts(validator).single().amount)
        f.put("WeightedPointsFormulaStartEra", era)
        assertEquals(3150.toBigInteger(), f.payouts(validator).single().amount)
    }

    @Test fun `recent nonzero slash warns only the affected validator`() = runTest {
        val f = Fixture()
        f.setup()
        f.put("ValidatorSlashInEra", listOf(BigInteger.ONE, 10.toBigInteger()), 99.toBigInteger(), f.formerValidator)
        val warnings = f.warnings(listOf(f.formerValidator.toHexString(), f.owner.toHexString()))
        assertEquals(mapOf(f.formerValidator.toHexString() to true, f.owner.toHexString() to false), warnings)
    }

    @Test fun `expired and zero slash histories do not warn`() = runTest {
        val f = Fixture()
        f.setup()
        f.put("ValidatorSlashInEra", listOf(BigInteger.ONE, BigInteger.TEN), BigInteger.ZERO, f.formerValidator)
        f.put("ValidatorSlashInEra", listOf(BigInteger.ZERO, BigInteger.ZERO), 99.toBigInteger(), f.owner)
        assertTrue(f.warnings(listOf(f.formerValidator.toHexString(), f.owner.toHexString())).values.none { it })
    }

    @Test fun `pending slash outside recent history still warns using modern NMap identity`() = runTest {
        val f = Fixture()
        f.setup()
        f.put(
            "UnappliedSlashes",
            Struct.Instance(
                mapOf(
                    "validator" to f.formerValidator, "own" to BigInteger.TEN,
            "others" to emptyList<Any>(), "reporter" to null, "payout" to BigInteger.ZERO
                )
            ),
                110.toBigInteger(), listOf(f.formerValidator, BigInteger.ONE, BigInteger.ZERO)
        )
        assertEquals(true, f.warnings(listOf(f.formerValidator.toHexString()))[f.formerValidator.toHexString()])
    }

    @Test fun `invalid slash storage cannot hide a warning as a clean validator`() = runTest {
        val f = Fixture()
        f.setup()
        val key = f.put("ValidatorSlashInEra", listOf(BigInteger.ONE, BigInteger.TEN), 99.toBigInteger(), f.formerValidator)
        f.values[key] = "0x00"
        assertTrue(runCatching { f.warnings(listOf(f.formerValidator.toHexString())) }.isFailure)
    }
}
