package jp.co.soramitsu.staking.impl.data.repository

import com.google.gson.Gson
import com.google.gson.JsonObject
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.models.MultiAddress
import jp.co.soramitsu.core.runtime.storage.returnType
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.SignatureWrapper
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.multiAddressFromId
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.runtime.metadata.ExtrinsicMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersion
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.domain.model.RewardDestination
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.data.model.BondedPoolState
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindBondedPool
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindExposure
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindExposurePage
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindNominations
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindPoolMember
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindRewardDestination
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindValidatorPrefs
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.bond
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.bondExtra
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.bondMore
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.chill
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.claimPayout
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.createPoolBouncer
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.joinPool
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.nominate
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.nominatePool
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.payoutStakers
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.rebond
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.setPayee
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.setPoolMetadata
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.unbond
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.unbondFromPool
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.withdrawUnbonded
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.withdrawUnbondedFromPool
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Every staking and pool transaction the app builds on an Asset Hub, signed through the real builder over the
 * Hub's current runtime metadata (Polkadot and Kusama Asset Hub 2005001, Westend Asset Hub 1025002, fetched
 * 2026-10-30), must produce exactly the payload that independent polkadot-js vectors give for that call:
 * call bytes, then the signed and additional extensions.
 */
@RunWith(Parameterized::class)
class AssetHubStakingCallContractTest(private val network: String) {
    private val runtime: RuntimeSnapshot get() = AssetHubMetadataContractTest.runtimes.getValue(network)
    private val vectors = json("/asset-hub/$network-vectors.json")
    private val signing = json("/asset-hub/$network-signing-vectors.json")

    private val alice = ByteArray(32) { 0x11 }
    private val firstValidator = ByteArray(32) { 0x22 }
    private val secondValidator = ByteArray(32) { 0x33 }
    private val payoutValidator = ByteArray(32) { 0x44 }
    private val payee = ByteArray(32) { 0x55 }

    private fun json(path: String): JsonObject = javaClass.getResourceAsStream(path)!!.reader().use {
        Gson().fromJson(it, JsonObject::class.java)
    }

    private fun vector(name: String) = vectors[name].asString

    private val unreadable = object : Keypair {
        override val privateKey: ByteArray get() = error("private key accessed")
        override val publicKey: ByteArray get() = error("public key accessed")
    }

    private fun builder(runtime: RuntimeSnapshot = this.runtime) = ExtrinsicBuilder(
        runtime = runtime,
        keypair = unreadable,
        nonce = 34.toBigInteger(),
        runtimeVersion = RuntimeVersion(signing["specVersion"].asInt, signing["transactionVersion"].asInt),
        genesisHash = signing["genesis"].asString.fromHex(),
        multiChainEncryption = MultiChainEncryption.Substrate(EncryptionType.ED25519),
        accountIdentifier = multiAddressFromId(alice)
    )

    private fun payload(useBatchAll: Boolean = false, form: ExtrinsicBuilder.() -> Unit): String {
        val builder = builder()
        builder.form()
        var signed: String? = null
        builder.prepareSigning(useBatchAll).sign(unreadable) { _, message, _ ->
            signed = message.toHexString(withPrefix = true)
            SignatureWrapper.Ed25519(ByteArray(64))
        }
        return requireNotNull(signed)
    }

    private fun assertSigns(callVector: String, useBatchAll: Boolean = false, form: ExtrinsicBuilder.() -> Unit) {
        val expected = vector(callVector) + signing["signedExtras"].asString.removePrefix("0x") +
            signing["additionalExtras"].asString.removePrefix("0x")
        assertEquals(callVector, expected, payload(useBatchAll, form))
    }

    @Test fun `bond and nominate as setup submits them`() {
        assertSigns("setupBatchCall") {
            bond(MultiAddress.Id(alice), 12_345.toBigInteger(), RewardDestination.Restake)
            nominate(listOf(MultiAddress.Id(firstValidator)))
        }
        assertSigns("setupBatchAllCall", useBatchAll = true) {
            bond(MultiAddress.Id(alice), 12_345.toBigInteger(), RewardDestination.Restake)
            nominate(listOf(MultiAddress.Id(firstValidator)))
        }
    }

    @Test fun `bond has no controller and pays rewards where chosen`() {
        assertSigns("bondCall") { bond(MultiAddress.Id(alice), 12_345.toBigInteger(), RewardDestination.Restake) }
        assertSigns("bondAccountCall") { bond(MultiAddress.Id(alice), 12_345.toBigInteger(), RewardDestination.Payout(payee)) }
    }

    @Test fun `nominate, bond more, change reward destination and chill`() {
        assertSigns("nominateCall") { nominate(listOf(MultiAddress.Id(firstValidator), MultiAddress.Id(secondValidator))) }
        assertSigns("bondExtraCall") { bondMore(500.toBigInteger()) }
        assertSigns("setPayeeCall") { setPayee(RewardDestination.Restake) }
        assertSigns("setPayeeAccountCall") { setPayee(RewardDestination.Payout(payee)) }
        assertSigns("chillCall") { chill() }
    }

    @Test fun `unbond, rebond and withdraw`() {
        assertSigns("unbondCall") { unbond(345.toBigInteger()) }
        assertSigns("rebondCall") { rebond(100.toBigInteger()) }
        assertSigns("withdrawUnbondedCall") { withdrawUnbonded(BigInteger.ZERO) }
    }

    @Test fun `payouts claim one exposure page at a time`() {
        assertSigns("payoutByPageCall") { payoutStakers(2310.toBigInteger(), payoutValidator, BigInteger.ONE) }
    }

    @Test fun `pool join, bond extra, claim, unbond and withdraw`() {
        assertSigns("poolJoinCall") { joinPool(1_000_000_000_000L.toBigInteger(), 7.toBigInteger()) }
        assertSigns("poolBondExtraCall") { bondExtra(5_000.toBigInteger()) }
        assertSigns("poolClaimPayoutCall") { claimPayout() }
        assertSigns("poolUnbondCall") { unbondFromPool(alice, MultiAddress.Id(alice), 777.toBigInteger()) }
        assertSigns("poolWithdrawUnbondedCall") { withdrawUnbondedFromPool(alice, MultiAddress.Id(alice)) }
    }

    @Test fun `pool creation, nomination and metadata`() {
        assertSigns("poolCreateCall") {
            createPoolBouncer(1_000_000_000_000L.toBigInteger(), MultiAddress.Id(alice), MultiAddress.Id(alice), MultiAddress.Id(alice))
        }
        assertSigns("poolNominateCall") { nominatePool(7.toBigInteger(), listOf(firstValidator)) }
        assertSigns("poolSetMetadataCall") { setPoolMetadata(7.toBigInteger(), "Fearless".encodeToByteArray()) }
    }

    @Test fun `Hub staking and pool calls refuse a runtime without the complete signing context`() {
        val original = runtime.metadata.extrinsic
        listOf("CheckGenesis", "CheckMortality", "CheckNonce", "CheckMetadataHash", "ChargeAssetTxPayment").forEach { removed ->
            val incomplete = RuntimeSnapshot(
                runtime.typeRegistry,
                RuntimeMetadata(
                    runtime.metadata.runtimeVersion, runtime.metadata.modules,
                    ExtrinsicMetadata(original.version, original.signedExtensions - removed, original.signedExtensionTypes)
                )
            )
            assertTrue(removed, runCatching { builder(incomplete).bond(MultiAddress.Id(alice), BigInteger.ONE, RewardDestination.Restake) }.isFailure)
            assertTrue(removed, runCatching { builder(incomplete).joinPool(BigInteger.ONE, BigInteger.ONE) }.isFailure)
        }
        assertFalse(runCatching { builder().joinPool(BigInteger.ONE, BigInteger.ONE) }.isFailure)
    }

    @Test fun `nominations, reward destinations and exposures decode from current storage`() {
        val nominations = bindNominations(vector("nominations"), runtime)
        assertEquals(listOf(firstValidator.toList(), secondValidator.toList()), nominations.targets.map { it.toList() })
        assertEquals(2300.toBigInteger(), nominations.submittedInEra)
        assertFalse(nominations.suppressed)

        val chain = mock<Chain> {
            on { id } doReturn signing["genesis"].asString.removePrefix("0x")
            on { ecosystem } doReturn Ecosystem.Substrate
            on { assets } doReturn emptyList()
        }
        val stash = StakingState.Stash.None(chain, alice, alice, alice)
        assertEquals(RewardDestination.Restake, bindRewardDestination(vector("payeeStaked"), runtime, stash))
        val paid = bindRewardDestination(vector("payeeAccount"), runtime, stash) as RewardDestination.Payout
        assertArrayEquals(payee, paid.targetAccountId)

        val overview = requireNotNull(bindExposure(vector("exposureOverview"), runtime))
        assertEquals(1_000.toBigInteger(), overview.total)
        assertEquals(100.toBigInteger(), overview.own)
        assertEquals(BigInteger.ONE, overview.pageCount)
        val page = requireNotNull(bindExposurePage(vector("exposurePage"), runtime))
        assertEquals(900.toBigInteger(), page.pageTotal)
        assertArrayEquals(alice, page.others.single().who)
        assertEquals(900.toBigInteger(), page.others.single().value)

        val prefsType = runtime.metadata.staking().storage("ErasValidatorPrefs").returnType()
        val prefs = bindValidatorPrefs(vector("validatorPrefs"), runtime, prefsType)
        assertEquals(0, prefs.commission.compareTo(BigDecimal("0.1")))
    }

    @Test fun `pool member and bonded pool decode from current storage`() {
        val member = requireNotNull(bindPoolMember(vector("poolMember"), runtime))
        assertEquals(7.toBigInteger(), member.poolId)
        assertEquals(1_000.toBigInteger(), member.points)
        assertEquals(BigInteger.ZERO, member.lastRecordedRewardCounter)
        assertEquals(2320.toBigInteger(), member.unbondingEras.single().era)
        assertEquals(345.toBigInteger(), member.unbondingEras.single().amount)

        val pool = requireNotNull(bindBondedPool(vector("bondedPool"), runtime))
        assertEquals(5_000.toBigInteger(), pool.points)
        assertEquals(3.toBigInteger(), pool.memberCounter)
        assertEquals(BondedPoolState.Open, pool.state)
        assertArrayEquals(alice, pool.depositor)
        assertArrayEquals(alice, pool.bouncer)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun networks() = listOf("polkadot", "kusama", "westend")
    }
}
