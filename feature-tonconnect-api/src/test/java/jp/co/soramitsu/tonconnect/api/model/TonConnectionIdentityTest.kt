package jp.co.soramitsu.tonconnect.api.model

import jp.co.soramitsu.coredb.model.ConnectionSource
import jp.co.soramitsu.coredb.model.TonConnectionLocal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TonConnectionIdentityTest {

    @Test
    fun localAndDomainModelsPreserveCompleteRoomPrimaryKey() {
        val local = TonConnectionLocal(
            metaId = 7L,
            clientId = "abcdef0123456789abcdef0123456789" +
                "abcdef0123456789abcdef0123456789",
            name = "Dapp",
            icon = "https://example.com/icon.png",
            url = "https://example.com",
            source = ConnectionSource.QR
        )
        val expected = TonConnectionIdentity(
            metaId = local.metaId,
            url = local.url,
            source = local.source
        )

        assertEquals(expected, TonConnectionIdentity(local))
        assertEquals(expected, TonDappConnection(local).identity())
        assertEquals(
            expected,
            DappModel(TonDappConnection(local)).connectionIdentityOrNull()
        )
    }

    @Test
    fun remoteDappWithoutWalletAndSourceCannotBecomeDeletionIdentity() {
        val remoteStyle = DappModel(
            identifier = "public-id",
            chains = emptyList(),
            name = "Dapp",
            url = "https://example.com",
            description = null,
            background = null,
            icon = null,
            metaId = null
        )

        assertNull(remoteStyle.connectionIdentityOrNull())
    }
}
