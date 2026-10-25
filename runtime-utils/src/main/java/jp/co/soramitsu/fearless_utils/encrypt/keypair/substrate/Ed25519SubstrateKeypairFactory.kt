package jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate

import jp.co.soramitsu.fearless_utils.encrypt.SecurityProviders
import net.i2p.crypto.eddsa.EdDSAKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import java.security.KeyFactory

private const val ED25519_PRIVATE_KEY_PREFIX = "302e020100300506032b657004220420"
private const val ED25519_PUBLIC_KEY_PREFIX = "302a300506032b6570032100"

internal object Ed25519SubstrateKeypairFactory : OtherSubstrateKeypairFactory("Ed25519HDKD") {

    init {
        SecurityProviders.requireEdDSA
    }

    override fun deriveFromSeed(seed: ByteArray): KeypairWithSeed {
        val keyFac = KeyFactory.getInstance(EdDSAKey.KEY_ALGORITHM, "EdDSA")
        val spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519)
        val privKeySpec = EdDSAPrivateKeySpec(seed, spec)
        val private = keyFac.generatePrivate(privKeySpec).encoded
        val publicKeySpec = EdDSAPublicKeySpec(privKeySpec.a, spec)
        val public = keyFac.generatePublic(publicKeySpec).encoded

        return KeypairWithSeed(
            seed = seed,
            private.copyOfRange(
                ED25519_PRIVATE_KEY_PREFIX.length / 2,
                private.size
            ),
            public.copyOfRange(ED25519_PUBLIC_KEY_PREFIX.length / 2, public.size)
        )
    }
}
