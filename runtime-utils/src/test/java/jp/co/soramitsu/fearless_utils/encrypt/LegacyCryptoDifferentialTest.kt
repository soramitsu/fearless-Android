package jp.co.soramitsu.fearless_utils.encrypt

import jp.co.soramitsu.fearless_utils.encrypt.CryptoTestVectors.ED25519_ORDER
import jp.co.soramitsu.fearless_utils.encrypt.CryptoTestVectors.flip
import jp.co.soramitsu.fearless_utils.encrypt.CryptoTestVectors.scalar
import jp.co.soramitsu.fearless_utils.encrypt.CryptoTestVectors.withScalar
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ECDSAUtils
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.seed.SeedCreator
import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAKey
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.EdDSASecurityProvider
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.spongycastle.crypto.digests.SHA512Digest
import org.spongycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.spongycastle.crypto.params.KeyParameter
import org.web3j.crypto.Sign
import java.math.BigInteger
import java.security.KeyFactory
import java.security.Security
import java.security.Signature
import java.text.Normalizer
import java.util.Random

/**
 * Old library against new code on random input, run while both were in the build: EdDSA-Java 0.3.0 against
 * BouncyCastle Ed25519, and SpongyCastle 1.58 against BouncyCastle for PBKDF2, Base64 and secp256k1 points.
 * Removed with the old libraries; the golden fixtures in legacy-crypto keep their outputs.
 */
class LegacyCryptoDifferentialTest {

    private val random = Random(4_320)
    private val spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519)

    init {
        if (Security.getProvider(EdDSASecurityProvider.PROVIDER_NAME) == null) {
            Security.addProvider(EdDSASecurityProvider())
        }
    }

    @Test
    fun `Ed25519 keys signatures and verdicts match EdDSA-Java on random input`() {
        val encryption = MultiChainEncryption.Substrate(EncryptionType.ED25519)
        var previousPublic = ByteArray(32)
        repeat(500) { index ->
            val seed = bytes(32)
            val message = bytes(random.nextInt(600))
            val (legacyPrivate, legacyPublic) = legacyKeypair(seed)
            val keypair = SubstrateKeypairFactory.generate(EncryptionType.ED25519, seed)
            assertArrayEquals("$index", legacyPrivate, keypair.privateKey)
            assertArrayEquals("$index", legacyPublic, keypair.publicKey)

            val signature = legacySign(seed, message)
            assertArrayEquals("$index", signature, Signer.sign(encryption, message, keypair).signature)

            val variants = listOf(
                Triple(message, signature, legacyPublic),
                Triple(message, withScalar(signature, scalar(signature) + ED25519_ORDER), legacyPublic),
                Triple(message, withScalar(signature, ED25519_ORDER - BigInteger.ONE), legacyPublic),
                Triple(message, flip(signature, random.nextInt(32)), legacyPublic),
                Triple(message, flip(signature, 32 + random.nextInt(32)), legacyPublic),
                Triple(message + byteArrayOf(1), signature, legacyPublic),
                Triple(message, signature, previousPublic),
                Triple(message, bytes(64), bytes(32))
            )
            variants.forEachIndexed { variant, (m, s, k) ->
                assertEquals("$index/$variant", legacyVerify(m, s, k) ?: false, Signer.verifyEd25519(m, s, k))
            }
            previousPublic = legacyPublic
        }
    }

    @Test
    fun `BIP39 PBKDF2 matches SpongyCastle on random entropy and passphrases`() {
        repeat(300) { index ->
            val entropy = bytes(16 + 4 * random.nextInt(5))
            val passphrase = if (index % 10 == 0) null else randomText()
            assertArrayEquals("$index", legacySeed(entropy, passphrase), SeedCreator.deriveSeed(entropy, passphrase))
        }
    }

    @Test
    fun `Base64 matches SpongyCastle on every encoding it produced`() {
        // SpongyCastle refuses to decode "", the encoding of no bytes (JsonBackupCompatibilityTest records it),
        // so the random strings here are never empty.
        repeat(2000) { index ->
            val data = bytes(1 + random.nextInt(300))
            val legacy = org.spongycastle.util.encoders.Base64.toBase64String(data)
            assertEquals("$index", legacy, org.bouncycastle.util.encoders.Base64.toBase64String(data))
            assertArrayEquals("$index", data, org.bouncycastle.util.encoders.Base64.decode(legacy))
            assertArrayEquals("$index", org.spongycastle.util.encoders.Base64.decode(legacy), org.bouncycastle.util.encoders.Base64.decode(legacy))
        }
    }

    @Test
    fun `secp256k1 point decompression matches SpongyCastle`() {
        val legacyCurve = org.spongycastle.jce.ECNamedCurveTable.getParameterSpec("secp256k1").curve
        repeat(300) { index ->
            val compressed = Sign.publicPointFromPrivate(BigInteger(1, bytes(32))).getEncoded(true)
            val legacyPoint = legacyCurve.decodePoint(compressed)
            val legacy = legacyPoint.xCoord.encoded + legacyPoint.yCoord.encoded
            assertArrayEquals("$index", legacy, ECDSAUtils.decompressed(compressed))
            assertEquals("$index", BigInteger(1, legacy), ECDSAUtils.decompressedAsInt(compressed))
        }
        repeat(300) { index ->
            val candidate = byteArrayOf((2 + random.nextInt(2)).toByte()) + bytes(32)
            val legacy = runCatching { legacyCurve.decodePoint(candidate).let { it.xCoord.encoded + it.yCoord.encoded } }
            val actual = runCatching { ECDSAUtils.decompressed(candidate) }
            assertEquals("$index", legacy.isSuccess, actual.isSuccess)
            if (legacy.isSuccess) assertArrayEquals(legacy.getOrThrow(), actual.getOrThrow())
            if (legacy.isFailure) assertEquals(legacy.exceptionOrNull()!!.javaClass, actual.exceptionOrNull()!!.javaClass)
        }
        for (candidate in listOf(ByteArray(0), byteArrayOf(0), ByteArray(33), bytes(32), byteArrayOf(4) + bytes(64))) {
            val legacy = runCatching { legacyCurve.decodePoint(candidate).let { it.xCoord.encoded + it.yCoord.encoded } }
            val actual = runCatching { ECDSAUtils.decompressed(candidate) }
            assertEquals(legacy.isSuccess, actual.isSuccess)
            assertEquals(legacy.exceptionOrNull()?.javaClass, actual.exceptionOrNull()?.javaClass)
        }
    }

    // The 4.3.1 Ed25519SubstrateKeypairFactory: PKCS#8 and X.509 encodings with their fixed prefixes cut off.
    private fun legacyKeypair(seed: ByteArray): Pair<ByteArray, ByteArray> {
        val keyFactory = KeyFactory.getInstance(EdDSAKey.KEY_ALGORITHM, "EdDSA")
        val privateSpec = EdDSAPrivateKeySpec(seed, spec)
        val private = keyFactory.generatePrivate(privateSpec).encoded
        val public = keyFactory.generatePublic(EdDSAPublicKeySpec(privateSpec.a, spec)).encoded
        return private.copyOfRange(16, private.size) to public.copyOfRange(12, public.size)
    }

    private fun legacySign(seed: ByteArray, message: ByteArray): ByteArray {
        val signature = Signature.getInstance(EdDSAEngine.SIGNATURE_ALGORITHM, EdDSASecurityProvider.PROVIDER_NAME)
        signature.initSign(EdDSAPrivateKey(EdDSAPrivateKeySpec(seed, spec)))
        signature.update(message)
        return signature.sign()
    }

    // The 4.3.1 Signer.verifyEd25519; null where EdDSA-Java threw.
    private fun legacyVerify(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean? = runCatching {
        if (signature.size != 64 || publicKey.size != 32) return@runCatching false
        if (scalar(signature) >= ED25519_ORDER) return@runCatching false
        val verifier = Signature.getInstance(EdDSAEngine.SIGNATURE_ALGORITHM, EdDSASecurityProvider.PROVIDER_NAME)
        verifier.initVerify(EdDSAPublicKey(EdDSAPublicKeySpec(publicKey, spec)))
        verifier.update(message)
        verifier.verify(signature)
    }.getOrNull()

    // The 4.3.1 SeedCreator on SpongyCastle.
    private fun legacySeed(entropy: ByteArray, passphrase: String?): ByteArray {
        val generator = PKCS5S2ParametersGenerator(SHA512Digest())
        generator.init(entropy, Normalizer.normalize("mnemonic${passphrase.orEmpty()}", Normalizer.Form.NFKD).toByteArray(), 2048)
        return (generator.generateDerivedMacParameters(64 * 8) as KeyParameter).key.copyOfRange(0, 64)
    }

    // Text NFKD changes: combining marks, Hangul, half-width kana, ligatures, roman numerals, squared words, emoji.
    private fun randomText(): String = buildString {
        repeat(random.nextInt(16)) {
            when (random.nextInt(9)) {
                0 -> append((0x20 + random.nextInt(0x5f)).toChar())
                1 -> append((0xc0 + random.nextInt(0x180)).toChar())
                2 -> append((0x300 + random.nextInt(0x70)).toChar())
                3 -> append((0xac00 + random.nextInt(0x2ba4)).toChar())
                4 -> append((0xff61 + random.nextInt(0x3f)).toChar())
                5 -> append((0xfb00 + random.nextInt(7)).toChar())
                6 -> append((0x2160 + random.nextInt(0x29)).toChar())
                7 -> append((0x3300 + random.nextInt(0x100)).toChar())
                else -> appendCodePoint(0x1f300 + random.nextInt(0x300))
            }
        }
    }

    private fun bytes(length: Int): ByteArray = ByteArray(length).also(random::nextBytes)
}
