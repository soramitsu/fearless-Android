package jp.co.soramitsu.walletconnect.impl.presentation

import jp.co.soramitsu.fearless_utils.extensions.fromHex
import org.junit.Assert.assertEquals
import org.junit.Test

class Eip191Test {

    @Test
    fun `personal sign matches the well known web3 vector`() {
        // web3.js documentation: web3.eth.accounts.sign('Some data', '0x4c0883a6…').
        val signature = Eip191.personalSign(
            message = "Some data",
            privateKey = "4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318".fromHex()
        )

        assertEquals(
            "0xb91467e570a6466aa9e9876cbcd013baba02900b8979d43fe208a4a4f339f5fd6007e74cd82e037b800186422fc2da167c747ef045e5d18a5f5d4300f8e1a0291c",
            signature
        )
    }
}
