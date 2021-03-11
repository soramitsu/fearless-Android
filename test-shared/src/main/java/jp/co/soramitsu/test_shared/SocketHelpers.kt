package jp.co.soramitsu.test_shared

import com.google.gson.Gson
import com.neovisionaries.ws.client.WebSocketFactory
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.recovery.Reconnector
import jp.co.soramitsu.fearless_utils.wsrpc.request.RequestExecutor

fun createTestSocket() = SocketService(Gson(), NoOpLogger, WebSocketFactory(), Reconnector(), RequestExecutor())