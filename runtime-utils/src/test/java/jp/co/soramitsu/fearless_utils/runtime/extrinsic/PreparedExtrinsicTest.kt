package jp.co.soramitsu.fearless_utils.runtime.extrinsic

import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.keypair.BaseKeypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.RealRuntimeProvider
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Era
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.multiAddressFromId
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersion
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class PreparedExtrinsicTest {
    private val runtime = RealRuntimeProvider.buildRuntime("westend")
    // Existing public test fixture from ExtrinsicBuilderTest; never a wallet key.
    private val key = BaseKeypair(
        publicKey = "fdc41550fb5186d71cae699c31731b3e1baa10680c7bd6b3831a6d222cf4d168".fromHex(),
        privateKey = "f3923eea431177cd21906d4308aea61c037055fb00575cae687217c6d8b2397f".fromHex()
    )
    @Test fun `prepared single and hashed batch payloads preserve legacy exact encoding`() {
        for (encryption in listOf(EncryptionType.ED25519, EncryptionType.ECDSA)) for (calls in listOf(1, 2, 20)) {
            val builder = builder(key, encryption = encryption)
            repeat(calls) { builder.transfer(ByteArray(32) { 3 }, BigInteger.TEN) }
            assertEquals(builder.build(), builder.prepareSigning().sign(key))
        }
    }
    @Test fun `preparation does not read key material and freezes subsequent caller mutations`() {
        val unreadable = object : Keypair {
            override val privateKey: ByteArray get() = error("private key accessed during preparation")
            override val publicKey: ByteArray get() = error("public key accessed during preparation")
        }
        val address = ByteArray(32) { 3 }
        val builder = builder(unreadable).transfer(address, BigInteger.TEN)
        val prepared = builder.prepareSigning()
        val expected = prepared.sign(key)
        val digestInput = prepared.intentBytes()
        address.fill(5)
        builder.transfer(ByteArray(32) { 6 }, BigInteger.ONE)
        digestInput.fill(0)
        assertEquals(expected, prepared.sign(key))
        assertFalse(digestInput.contentEquals(prepared.intentBytes()))
    }
    @Test fun `prepared intent binds nonce amount and deterministic encryption identity`() {
        fun prepared(nonce: Int, amount: Int) = builder(key, nonce).transfer(ByteArray(32), amount.toBigInteger()).prepareSigning()
        assertArrayEquals(prepared(1, 2).intentBytes(), prepared(1, 2).intentBytes())
        assertFalse(prepared(1, 2).intentBytes().contentEquals(prepared(2, 2).intentBytes()))
        assertFalse(prepared(1, 2).intentBytes().contentEquals(prepared(1, 3).intentBytes()))
    }
    private fun builder(keypair: Keypair, nonce: Int = 34, encryption: EncryptionType = EncryptionType.ED25519) = ExtrinsicBuilder(
        runtime = runtime, keypair = keypair, nonce = nonce.toBigInteger(),
        runtimeVersion = RuntimeVersion(48, 4), genesisHash = ByteArray(32) { 4 },
        multiChainEncryption = MultiChainEncryption.Substrate(encryption),
        accountIdentifier = multiAddressFromId(key.publicKey), era = Era.Mortal(64, 59), blockHash = ByteArray(32) { 5 }
    )
}
