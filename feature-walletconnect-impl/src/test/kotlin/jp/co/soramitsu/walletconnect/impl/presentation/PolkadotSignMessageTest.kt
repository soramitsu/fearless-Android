package jp.co.soramitsu.walletconnect.impl.presentation

import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.SignatureWrapper
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256
import jp.co.soramitsu.fearless_utils.hash.Hasher.keccak256
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.web3j.crypto.Sign
import java.math.BigInteger

class PolkadotSignMessageTest {

    // balances.transferAll(dest, keepAlive) followed by era, nonce, tip, versions and two 32 byte hashes:
    // the shape of an extrinsic signing payload a dApp could pass off as a "message".
    private val extrinsicPayload = "0x0504" + "00" + "d43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d" + "00" +
        "f500" + "04" + "00" + "c8240000" + "18000000" +
        "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3" +
        "554d682a74099d05e8b7852d19c93b527b5fae1e9e1969f6e1b82a2f09a14cc9"

    @Test
    fun `text message is signed inside Bytes tags`() {
        assertEquals("<Bytes>hello</Bytes>", wrapPolkadotMessageBytes(polkadotMessageBytes("hello")).decodeToString())
    }

    @Test
    fun `prefixed hex message is decoded before wrapping`() {
        assertEquals("<Bytes>hello</Bytes>", wrapPolkadotMessageBytes(polkadotMessageBytes("0x68656c6c6f")).decodeToString())
        assertEquals("<Bytes></Bytes>", wrapPolkadotMessageBytes(polkadotMessageBytes("0x")).decodeToString())
    }

    @Test
    fun `unprefixed or malformed hex is read as text like polkadot js does`() {
        assertArrayEquals("68656c6c6f".encodeToByteArray(), polkadotMessageBytes("68656c6c6f"))
        assertArrayEquals("0xzz".encodeToByteArray(), polkadotMessageBytes("0xzz"))
        assertArrayEquals("0xabc".encodeToByteArray(), polkadotMessageBytes("0xabc"))
    }

    @Test
    fun `extrinsic signing payload is wrapped instead of signed raw`() {
        val raw = extrinsicPayload.fromHex()
        val signed = wrapPolkadotMessageBytes(polkadotMessageBytes(extrinsicPayload))

        assertArrayEquals("<Bytes>".encodeToByteArray() + raw + "</Bytes>".encodeToByteArray(), signed)
    }

    @Test
    fun `already wrapped input is not wrapped twice`() {
        val wrapped = "<Bytes>hello</Bytes>"

        assertTrue(isWrappedPolkadotMessage(wrapped.encodeToByteArray()))
        assertEquals(wrapped, wrapPolkadotMessageBytes(polkadotMessageBytes(wrapped)).decodeToString())
        assertEquals(
            wrapped,
            wrapPolkadotMessageBytes(polkadotMessageBytes(wrapped.encodeToByteArray().toHexString(withPrefix = true))).decodeToString()
        )
    }

    @Test
    fun `partially wrapped or Ethereum prefixed input is still wrapped`() {
        assertFalse(isWrappedPolkadotMessage("<Bytes>hello".encodeToByteArray()))
        assertFalse(isWrappedPolkadotMessage("<Bytes></Bytes".encodeToByteArray()))
        assertEquals("<Bytes><Bytes>hello</Bytes>", wrapPolkadotMessageBytes("<Bytes>hello".encodeToByteArray()).decodeToString())

        val ethereumMessage = "\u0019Ethereum Signed Message:\n5hello"
        assertEquals(
            "<Bytes>$ethereumMessage</Bytes>",
            wrapPolkadotMessageBytes(polkadotMessageBytes(ethereumMessage)).decodeToString()
        )
    }

    @Test
    fun `ed25519 signature verifies against the wrapped message only`() {
        val keypair = SubstrateKeypairFactory.generate(EncryptionType.ED25519, seed(1))

        val signature = signPolkadotMessage(extrinsicPayload, MultiChainEncryption.Substrate(EncryptionType.ED25519), keypair)

        assertTrue(Signer.verifyEd25519(wrapped(extrinsicPayload), signature.signature, keypair.publicKey))
        assertFalse(Signer.verifyEd25519(extrinsicPayload.fromHex(), signature.signature, keypair.publicKey))
        assertFalse(Signer.verifyEd25519(extrinsicPayload.fromHex().blake2b256(), signature.signature, keypair.publicKey))
    }

    @Test
    fun `substrate ecdsa signature verifies against the wrapped message only`() {
        val keypair = SubstrateKeypairFactory.generate(EncryptionType.ECDSA, seed(2))

        val signature = signPolkadotMessage(extrinsicPayload, MultiChainEncryption.Substrate(EncryptionType.ECDSA), keypair)

        assertEquals(publicKey(keypair), recover(wrapped(extrinsicPayload).blake2b256(), signature))
        assertNotEquals(publicKey(keypair), recover(extrinsicPayload.fromHex().blake2b256(), signature))
    }

    @Test
    fun `ethereum based signature verifies against the wrapped message only`() {
        val keypair = EthereumKeypairFactory.createWithPrivateKey(seed(3))
        // An RLP legacy transaction a dApp could submit as the "message" on Moonbeam.
        val transaction = "0xec098504a817c800825208943535353535353535353535353535353535353535880de0b6b3a764000080018080"

        val signature = signPolkadotMessage(transaction, MultiChainEncryption.Ethereum, keypair)

        assertEquals(publicKey(keypair), recover(wrapped(transaction).keccak256(), signature))
        assertNotEquals(publicKey(keypair), recover(transaction.fromHex().keccak256(), signature))
    }

    private fun wrapped(hex: String) = "<Bytes>".encodeToByteArray() + hex.fromHex() + "</Bytes>".encodeToByteArray()

    private fun seed(fill: Int) = ByteArray(32) { (it + fill).toByte() }

    private fun publicKey(keypair: Keypair): BigInteger = Sign.publicKeyFromPrivate(BigInteger(1, keypair.privateKey))

    private fun recover(hash: ByteArray, signature: SignatureWrapper): BigInteger? {
        val ecdsa = signature as SignatureWrapper.Ecdsa
        return runCatching { Sign.signedMessageHashToKey(hash, Sign.SignatureData(ecdsa.v, ecdsa.r, ecdsa.s)) }.getOrNull()
    }
}
