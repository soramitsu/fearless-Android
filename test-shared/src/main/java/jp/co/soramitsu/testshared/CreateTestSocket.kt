package jp.co.soramitsu.testshared

import com.google.gson.Gson
import com.neovisionaries.ws.client.WebSocketFactory
import jp.co.soramitsu.shared_utils.wsrpc.SocketService
import jp.co.soramitsu.shared_utils.wsrpc.recovery.Reconnector
import jp.co.soramitsu.shared_utils.wsrpc.request.CoroutinesRequestExecutor

fun createTestSocket() = SocketService(Gson(), NoOpLogger, WebSocketFactory(), Reconnector(), CoroutinesRequestExecutor())
