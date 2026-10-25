package jp.co.soramitsu.staking.impl.data.repository

import jp.co.soramitsu.core.extrinsic.keypair_provider.GuardedKeypairProvider
import jp.co.soramitsu.core.extrinsic.keypair_provider.WalletBoundKeypairProvider
import jp.co.soramitsu.core.extrinsic.mortality.Mortality
import jp.co.soramitsu.core.extrinsic.mortality.MortalityConstructor
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.core.runtime.IChainRegistry
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.DynamicTypeResolver
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.v13Preset
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.TypeReference
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Era
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Extrinsic
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.SignedExtras
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.FixedByteArray
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u8
import jp.co.soramitsu.fearless_utils.runtime.metadata.ExtrinsicMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.FunctionArgument
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.MetadataFunction
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Module
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersion
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigInteger

import jp.co.soramitsu.core.extrinsic.ExtrinsicBuilderFactory
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.data.StakingSharedState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.mockito.kotlin.doSuspendableAnswer

import jp.co.soramitsu.fearless_utils.encrypt.keypair.BaseKeypair
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Alias
import org.mockito.kotlin.times
/** Actual API/service/SCALE with local public fixtures and mocked RPC; no real endpoint. */
class StakingPoolMemberEncodingTest {
    @Test fun `unbond fee uses MultiAddress runtime shape`() = runBlocking<Unit> { check(true, true, false) }
    @Test fun `withdraw fee uses MultiAddress runtime shape`() = runBlocking<Unit> { check(false, true, false) }
    @Test fun `unbond fee uses raw account runtime shape`() = runBlocking<Unit> { check(true, false, false) }
    @Test fun `withdraw fee uses raw account runtime shape`() = runBlocking<Unit> { check(false, false, false) }
    @Test fun `unbond submit uses MultiAddress runtime shape`() = runBlocking<Unit> { check(true, true, true) }
    @Test fun `withdraw submit uses MultiAddress runtime shape`() = runBlocking<Unit> { check(false, true, true) }
    @Test fun `unbond submit uses raw account runtime shape`() = runBlocking<Unit> { check(true, false, true) }
    @Test fun `withdraw submit uses raw account runtime shape`() = runBlocking<Unit> { check(false, false, true) }

    @Test fun `20 byte member shape supports both pool operations without retry`() = runBlocking<Unit> {
        for (unbond in listOf(false, true)) for (submit in listOf(false, true)) check(unbond, true, submit, width = 20)
    }
    @Test fun `20 byte raw account supports both pool operations without retry`() = runBlocking<Unit> {
        for (unbond in listOf(false, true)) for (submit in listOf(false, true)) check(unbond, false, submit, width = 20)
    }
    @Test fun `aliased member types preserve original account and call`() = runBlocking<Unit> {
        for (multi in listOf(false, true)) for (unbond in listOf(false, true)) check(unbond, multi, false, aliased = true)
    }
    @Test fun `unsupported member type stops before fee or output RPC`() = runBlocking<Unit> { rejected("unsupported") }
    @Test fun `unresolved member type stops before fee or output RPC`() = runBlocking<Unit> { rejected("unresolved") }
    @Test fun `wrong length member metadata stops before fee or output RPC`() = runBlocking<Unit> { rejected("wrong-length") }

    private suspend fun rejected(mode: String) {
        for (unbond in listOf(false, true)) for (submit in listOf(false, true)) {
            val h = Harness(true, mode = mode)
            val address = h.account.toAddress(42)
            if (submit) {
                val result = if (unbond) h.api.unbond(address, BigInteger.TEN) else h.api.withdrawUnbonded(address)
                assertTrue(result.isFailure)
            } else {
                val result = runCatching { if (unbond) h.api.estimateUnbondFee(address, BigInteger.TEN) else h.api.estimateWithdrawUnbondedFee(address) }
                assertTrue(result.isFailure)
                verify(h.provider, never()).getKeypairFor(any(), any())
            }
            verify(h.registry, times(1)).getRuntime(GENESIS)
            verify(h.rpc, never()).submitExtrinsic(any(), any())
            verify(h.rpc, never()).estimateExtrinsicFee(any(), any())
        }
    }

    private suspend fun check(unbond: Boolean, multi: Boolean, submit: Boolean, width: Int = 32, aliased: Boolean = false) {
        val h = Harness(multi, width, aliased)
        val address = if (width == 20) h.account.toHexString(withPrefix = true) else h.account.toAddress(42)
        if (submit) {
            val r = if (unbond) h.api.unbond(address, BigInteger.TEN) else h.api.withdrawUnbonded(address)
            assertEquals("0xfixture", r.getOrThrow())
        } else {
            val r = if (unbond) h.api.estimateUnbondFee(address, BigInteger.TEN) else h.api.estimateWithdrawUnbondedFee(address)
            assertEquals(FEE, r)
            verify(h.provider, never()).getKeypairFor(any(), any())
        }
        val capture = argumentCaptor<String>()
        if (submit) verify(h.rpc, times(1)).submitExtrinsic(eq(GENESIS), capture.capture())
        else verify(h.rpc, times(1)).estimateExtrinsicFee(eq(GENESIS), capture.capture())
        val decoded = Extrinsic.fromHex(h.runtime, capture.firstValue)
        assertEquals(if (unbond) "unbond" else "withdraw_unbonded", decoded.call.function.name)
        val member = decoded.call.arguments.getValue("member_account")
        if (multi) {
            assertEquals(if (width == 20) "Address20" else "Id", (member as DictEnum.Entry<*>).name)
            assertArrayEquals(h.account, member.value as ByteArray)
        } else assertArrayEquals(h.account, member as ByteArray)
        assertEquals(if (unbond) BigInteger.TEN else BigInteger.ZERO, decoded.call.arguments[if (unbond) "unbonding_points" else "num_slashing_spans"])
    }

    private class Harness(multi: Boolean, width: Int = 32, aliased: Boolean = false, mode: String = "valid") {
        val chain = mock<Chain> {
            on { id } doReturn GENESIS
            on { ecosystem } doReturn (if (width == 20) Ecosystem.EthereumBased else Ecosystem.Substrate)
            on { isEthereumBased } doReturn (width == 20)
        }
        val account = ByteArray(width) { 5 }
        val rpc = mock<RpcCalls>()
        val provider = mock<GuardedKeypairProvider>()
        val baseMemberType = if (multi) DictEnum("MultiAddress", listOf(DictEnum.Entry(if (width == 20) "Address20" else "Id", TypeReference(FixedByteArray("AccountId", width))))) else FixedByteArray("AccountId", width)
        val memberType = when (mode) {
            "unsupported" -> u8
            "wrong-length" -> FixedByteArray("WrongMemberLength", width - 1)
            "unresolved" -> Alias("UnresolvedMember", TypeReference(null))
            else -> if (aliased) Alias("MemberAlias", TypeReference(baseMemberType)) else baseMemberType
        }
        val pool = Module("NominationPools", null, listOf("unbond", "withdraw_unbonded").mapIndexed { index, name ->
            name to MetadataFunction(name, listOf(FunctionArgument("member_account", memberType), FunctionArgument(if (name == "unbond") "unbonding_points" else "num_slashing_spans", u8)), emptyList(), 39 to index)
        }.toMap(), emptyMap(), emptyMap(), emptyMap(), BigInteger.valueOf(39))
        val runtime = runtime(POLKADOT_EXTENSIONS, listOf(pool), width)
        val registry = mock<IChainRegistry>()
        val mortality = mock<MortalityConstructor>()
        val service = ExtrinsicService(rpc, provider, ExtrinsicBuilderFactory(rpc, registry, mortality))
        val selection = mock<StakingSharedState>()
        val api = StakingPoolApi(service, selection, mock(), PoolJoinPreferencesFixture().journal(), mock())
        init {
            runBlocking {
                whenever(selection.chain()).thenReturn(chain)
                whenever(registry.getRuntime(GENESIS)).thenReturn(runtime)
                whenever(rpc.getRuntimeVersion(GENESIS)).thenReturn(RuntimeVersion(1_005_000, 26))
                whenever(rpc.getAccountNonce(any(), any())).thenReturn(BigInteger.valueOf(3))
                whenever(rpc.estimateExtrinsicFee(eq(GENESIS), any())).thenReturn(FEE)
                whenever(rpc.submitExtrinsic(eq(GENESIS), any())).thenReturn("0xfixture")
                whenever(mortality.construct(chain)).thenReturn(Mortality(Era.Mortal(64, 12), BLOCK_HASH))
                whenever(provider.getCryptoTypeFor(any(), any())).thenReturn(CryptoType.ED25519)
                whenever(provider.getKeypairFor(any(), any())).thenReturn(BaseKeypair(ByteArray(32) { 7 }, account))
            }
        }
    }
    private companion object {
        val GENESIS = "91".repeat(32)
        val BLOCK_HASH = ByteArray(32) { 0x11 }
        val FEE: BigInteger = BigInteger.valueOf(15_000_000)

        // Order and names as Polkadot 2005000 and Avail list them; unmapped ones encode nothing.
        val POLKADOT_EXTENSIONS = listOf(
            "CheckNonZeroSender", "CheckSpecVersion", "CheckTxVersion", "CheckGenesis", "CheckMortality",
            "CheckNonce", "CheckWeight", "ChargeTransactionPayment", "PrevalidateAttests", "CheckMetadataHash"
        )
        fun runtime(signedExtensions: List<String>, modules: List<Module>, width: Int = 32): RuntimeSnapshot {
            val signature = DictEnum(
                "ExtrinsicSignature",
                listOf(
                    DictEnum.Entry("Ed25519", TypeReference(FixedByteArray("Ed25519", 64))),
                    DictEnum.Entry("Sr25519", TypeReference(FixedByteArray("Sr25519", 64))),
                    DictEnum.Entry("Ecdsa", TypeReference(FixedByteArray("Ecdsa", 65)))
                )
            )
            val registry = TypeRegistry(
                v13Preset() + mapOf(
                    "Address" to TypeReference(FixedByteArray("Address", width)),
                    "ExtrinsicSignature" to TypeReference(signature)
                ),
                DynamicTypeResolver.defaultCompoundResolver()
            )
            return RuntimeSnapshot(
                registry,
                RuntimeMetadata(
                    BigInteger.valueOf(14),
                    modules.associateBy { it.name },
                    ExtrinsicMetadata(BigInteger.valueOf(4), signedExtensions)
                )
            )
        }
    }
}
