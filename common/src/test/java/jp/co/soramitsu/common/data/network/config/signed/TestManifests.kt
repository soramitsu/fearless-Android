package jp.co.soramitsu.common.data.network.config.signed

import com.google.gson.Gson
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.io.File
import java.security.MessageDigest
import java.util.TreeMap

/** Builds signed manifests the way scripts/sign-remote-config.py does, with a synthetic test key. */
object TestManifests {
    private val seed = ByteArray(32) { (it + 1).toByte() }
    private val privateKey = Ed25519PrivateKeyParameters(seed, 0)
    val publicKey: ByteArray = privateKey.generatePublicKey().encoded
    val keyId: String = sha256(publicKey).take(16)

    fun sha256(content: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }

    /** The canonical manifest JSON: sorted keys, no spaces, ASCII. */
    fun canonical(files: Map<String, String>, sequence: Long, keyId: String = this.keyId, repository: String = "soramitsu/fearless-Android"): String {
        val document = TreeMap<String, Any>()
        document["files"] = TreeMap(files)
        document["keyId"] = keyId
        document["repository"] = repository
        document["schema"] = "fearless-remote-config-manifest/1"
        document["sequence"] = sequence
        document["signedAt"] = "2026-11-07T00:00:00Z"
        return Gson().toJson(document)
    }

    fun envelope(manifest: String, signature: ByteArray = sign(manifest.toByteArray(Charsets.US_ASCII))): ByteArray =
        Gson().toJson(mapOf("algorithm" to "ed25519", "manifest" to manifest, "signature" to signature.joinToString("") { "%02x".format(it) }))
            .toByteArray()

    fun signed(files: Map<String, ByteArray>, sequence: Long): ByteArray =
        envelope(canonical(files.mapValues { sha256(it.value) }, sequence))

    fun sign(message: ByteArray): ByteArray = Ed25519Signer().run {
        init(true, privateKey)
        update(message, 0, message.size)
        generateSignature()
    }

    fun verify(envelope: ByteArray) = SignedRemoteConfigManifest.verify(envelope, publicKey, keyId)

    fun repositoryRoot(): File = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "settings.gradle").isFile }
}
