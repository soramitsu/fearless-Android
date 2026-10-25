package jp.co.soramitsu.account.impl.presentation.recovery

import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import jp.co.soramitsu.common.model.WalletEcosystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RecoveryTargetTest {
    @Test
    fun publicTargetRoundTripsRootAndNetworkScopes() {
        val scopes = listOf(
            WalletSecretScope.Substrate to WalletEcosystem.Substrate,
            WalletSecretScope.Ethereum to WalletEcosystem.Ethereum,
            WalletSecretScope.Ton to WalletEcosystem.Ton,
            WalletSecretScope.ChainAccount("12".repeat(20)) to WalletEcosystem.Ethereum,
            WalletSecretScope.ChainAccount("34".repeat(32)) to WalletEcosystem.Substrate
        )
        scopes.forEach { (scope, ecosystem) ->
            val target = RecoveryTarget.from(7, scope)
            assertEquals(scope, target.scope())
            assertEquals(ecosystem, target.ecosystem())
        }
    }

    @Test
    fun malformedTargetsAndDerivedBitcoinNamespacesCannotReachAKeyWrite() {
        for (invalid in listOf("", "pin_code", "chain:" + "12".repeat(33), "chain:GG", "chain:../secret")) {
            assertThrows(IllegalArgumentException::class.java) { RecoveryTarget(7, invalid).scope() }
        }
        for (id in listOf(-1L, 0L)) {
            assertThrows(IllegalArgumentException::class.java) { RecoveryTarget(id, "ethereum").scope() }
        }
    }
}
