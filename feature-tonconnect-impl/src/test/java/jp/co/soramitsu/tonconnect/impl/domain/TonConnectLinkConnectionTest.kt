package jp.co.soramitsu.tonconnect.impl.domain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import jp.co.soramitsu.coredb.model.ConnectionSource
import jp.co.soramitsu.tonconnect.api.domain.TonConnectInteractor
import jp.co.soramitsu.tonconnect.api.domain.connectFromLink
import jp.co.soramitsu.tonconnect.api.model.AppEntity
import jp.co.soramitsu.tonconnect.api.model.ConnectRequest
import jp.co.soramitsu.tonconnect.api.model.TonConnectLink
import jp.co.soramitsu.tonconnect.api.model.TonDappConnection
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TonConnectLinkConnectionTest {
    private val interactor = mockk<TonConnectInteractor>()
    private val request = ConnectRequest(
        manifestUrl = "https://dapp.example/tonconnect-manifest.json",
        items = listOf(ConnectRequest.Item.TonAddress, ConnectRequest.Item.TonProof("challenge"))
    )
    private val link = TonConnectLink("c".repeat(64), request)
    private val app = AppEntity("https://dapp.example", "Example", "https://dapp.example/icon.png", null, null)
    private val reply = JSONObject().put("event", "connect")

    @Test
    fun theApprovedReplyGoesToTheLinksClientAfterTheManifestWasRead() = runTest {
        val connection = TonDappConnection(1L, link.clientId, "Example", "https://dapp.example/icon.png", app.url, ConnectionSource.QR)
        val approvals = mutableListOf<Pair<AppEntity, String?>>()
        coEvery { interactor.readManifest(request.manifestUrl) } returns app
        coEvery { interactor.respondDappConnectRequest(link.clientId, request, reply, app) } returns connection

        val result = interactor.connectFromLink(link) { approvedApp, proofPayload ->
            approvals += approvedApp to proofPayload
            reply
        }

        assertSame(connection, result)
        assertEquals(listOf(app to "challenge"), approvals)
        coVerifyOrder {
            interactor.readManifest(request.manifestUrl)
            interactor.respondDappConnectRequest(link.clientId, request, reply, app)
        }
    }

    @Test
    fun aDeclinedConnectionIsStillAnsweredAndStoresNothing() = runTest {
        val declined = JSONObject().put("event", "connect_error")
        coEvery { interactor.readManifest(any()) } returns app
        coEvery { interactor.respondDappConnectRequest(link.clientId, request, declined, app) } returns null

        assertNull(interactor.connectFromLink(link) { _, _ -> declined })
        coVerify(exactly = 1) { interactor.respondDappConnectRequest(link.clientId, request, declined, app) }
    }

    @Test
    fun anUnreadableManifestStopsBeforeTheApproval() = runTest {
        coEvery { interactor.readManifest(any()) } throws IllegalStateException("manifest unavailable")
        var approvalOpened = false

        val failure = runCatching {
            interactor.connectFromLink(link) { _, _ ->
                approvalOpened = true
                reply
            }
        }

        assertEquals("manifest unavailable", failure.exceptionOrNull()?.message)
        assertEquals(false, approvalOpened)
        coVerify(exactly = 0) { interactor.respondDappConnectRequest(any(), any(), any(), any()) }
    }

    @Test
    fun anApprovalThatCannotOpenSendsNoReply() = runTest {
        coEvery { interactor.readManifest(any()) } returns app

        val failure = runCatching { interactor.connectFromLink(link) { _, _ -> error("The wallet is locked") } }

        assertEquals("The wallet is locked", failure.exceptionOrNull()?.message)
        coVerify(exactly = 0) { interactor.respondDappConnectRequest(any(), any(), any(), any()) }
    }
}
