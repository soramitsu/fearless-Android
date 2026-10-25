package jp.co.soramitsu.fearless_utils.integration

import com.google.gson.Gson
import com.neovisionaries.ws.client.WebSocketFactory
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.StdoutLogger
import jp.co.soramitsu.fearless_utils.wsrpc.recovery.ConstantReconnectStrategy
import jp.co.soramitsu.fearless_utils.wsrpc.recovery.Reconnector
import jp.co.soramitsu.fearless_utils.wsrpc.request.RequestExecutor
import org.junit.After
import org.junit.Before

abstract class BaseIntegrationTest(private val networkUrl: String = KUSAMA_URL) {

    protected val socketService = SocketService(
        Gson(),
        StdoutLogger,
        WebSocketFactory(),
        Reconnector(strategy = ConstantReconnectStrategy(1000L)),
        RequestExecutor()
    )

    @Before
    open fun setup() {
        socketService.start(networkUrl)
    }

    @After
    open fun tearDown() {
        socketService.stop()
    }
}
