package jp.co.soramitsu.fearless_utils

import org.bouncycastle.util.encoders.Hex

object TestData {
    const val PUBLIC_KEY = "2f8c6129d816cf51c374bc7f08c3e63ed156cf78aefb4a6550d97b87997977ee"
    val PUBLIC_KEY_BYTES = Hex.decode(PUBLIC_KEY)

    const val PRIVATE_KEY = "f0106660c3dda23f16daa9ac5b811b963077f5bc0af89f85804f0de8e424f050"
    val PRIVATE_KEY_BYTES = Hex.decode(PRIVATE_KEY)

    const val SEED = "3132333435363738393031323334353637383930313233343536373839303132"
    val SEED_BYTES: ByteArray = Hex.decode(SEED)
}