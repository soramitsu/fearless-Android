package jp.co.soramitsu.tonconnect.impl.presentation.dappscreen

import jp.co.soramitsu.tonconnect.api.model.ConnectRequest
import jp.co.soramitsu.tonconnect.api.model.TonConnectUrlValidator
import okhttp3.Headers
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

class DAppBridge(
    val deviceInfo: String,
    val isWalletBrowser: Boolean = true,
    val protocolVersion: Int = 2,
    val send: suspend (array: JSONArray) -> JSONObject,
    val connect: suspend (protocolVersion: Int, request: ConnectRequest) -> JSONObject,
    val restoreConnection: suspend () -> JSONObject,
    val disconnect: suspend () -> Unit,
    val tonapiFetch: suspend (url: String, options: String) -> Response
) : JsBridge("fearless") {

    override val availableFunctions = arrayOf("send", "connect", "restoreConnection", "disconnect")

    init {
        keys["deviceInfo"] = deviceInfo
        keys["protocolVersion"] = protocolVersion
        keys["isWalletBrowser"] = isWalletBrowser
    }

    override suspend fun invokeFunction(name: String, args: JSONArray): Any? {
        return when (name) {
            "connect" -> connect(protocolVersion, ConnectRequest.parse(args.getJSONObject(1))).toString()
            "send" -> send(args).toString()
            "restoreConnection" -> restoreConnection().toString()
            "disconnect" -> disconnect()
            "tonapi.fetch" -> {
                val tonApiUrl = validateTonApiUrl(args.getString(0))
                val response = tonapiFetch(tonApiUrl, args.optString(1) ?: "")
                webAPIResponse(response).toString()
            }

            else -> null
        }
    }

    private fun validateTonApiUrl(url: String): String {
        return TonConnectUrlValidator.validateTonApiFetchUrl(url)
    }

    private fun webAPIResponse(response: Response): JSONObject {
        val body = response.body?.string() ?: ""
        val json = JSONObject()
        json.put("body", body)
        json.put("ok", response.isSuccessful)
        json.put("status", response.code)
        json.put("statusText", response.message)
        json.put("type", webAPIResponseType(response.code))
        json.put("headers", webAPIResponseHeaders(response.headers))
        json.put("redirected", response.isRedirect)
        json.put("url", response.request.url.toString())
        return json
    }

    private fun webAPIResponseHeaders(headers: Headers): JSONObject {
        val json = JSONObject()
        for (i in 0 until headers.size) {
            json.put(headers.name(i), headers.value(i))
        }
        return json
    }

    private fun webAPIResponseType(code: Int): String {
        return if (code == 0) {
            "error"
        } else {
            "cors"
        }
    }

    override fun jsInjection(token: String): String {
        require(DappBridgeAccess.isWellFormedToken(token)) { "Invalid bridge token" }
        val funcs = availableFunctions.joinToString(",") {
            """
            $it: (...args) => {
                return new Promise((resolve, reject) => invokeRnFunc('$it', args, resolve, reject))
            }
        """
        }.replace("\n", "").replace("  ", "")

        // The token lives only in this closure. Every call carries it, and a response or
        // event without it, such as one posted by another frame, is ignored.
        return """
            (() => {
                const token = '$token';
                const rnPromises = {};
                const rnEventListeners = [];
                const invokeRnFunc = (name, args, resolve, reject) => {
                    const invocationId = btoa(Math.random()).substring(0, 12);
                    const timeoutMs = $timeout;
                    const timeoutId = timeoutMs ? setTimeout(() => reject(new Error('bridge timeout for function with name: '+name+'')), timeoutMs) : null;
                    rnPromises[invocationId] = { resolve, reject, timeoutId }
                    window.ReactNativeWebView.postMessage(JSON.stringify({
                        type: '${BridgeMessage.Type.InvokeRnFunc.value}',
                        ${JsBridge.TOKEN_KEY}: token,
                        invocationId: invocationId,
                        name,
                        args,
                    }));
                };

                window.addEventListener('message', ({ data }) => {
                    try {
                        const message = data;
                        if (!message || message.${JsBridge.TOKEN_KEY} !== token) {
                            return;
                        }
                        if (message.type === '${BridgeMessage.Type.FunctionResponse.value}') {
                            const promise = rnPromises[message.invocationId];

                            if (!promise) {
                                return;
                            }

                            if (promise.timeoutId) {
                                clearTimeout(promise.timeoutId);
                            }

                            if (message.status === 'fulfilled') {
                                promise.resolve(JSON.parse(message.data));
                            } else {
                                promise.reject(new Error(message.data));
                            }

                            delete rnPromises[message.invocationId];
                        }

                        if (message.type === '${BridgeMessage.Type.Event.value}') {
                            rnEventListeners.forEach((listener) => listener(message.event));
                        }
                    } catch { }
                });

                const listen = (cb) => {
                    rnEventListeners.push(cb);
                    return () => {
                        const index = rnEventListeners.indexOf(cb);
                        if (index > -1) {
                            rnEventListeners.splice(index, 1);
                        }
                    };
                };

                window.$windowKey = {
                    tonconnect: Object.assign(${JSONObject(keys)},{ $funcs },{ listen })
                };

                window.tonapi = {
                    fetch: async (url, options) => {
                        return new Promise((resolve, reject) => {
                            invokeRnFunc('tonapi.fetch', [url, options], (result) => {
                                try {
                                    const headers = new Headers(result.headers);
                                    const response = new Response(result.body, {
                                        status: result.status,
                                        statusText: result.statusText,
                                        headers: headers
                                    });
                                    resolve(response);
                                } catch (e) {
                                    reject(e);
                                }
                            }, reject)
                        });
                    }
                };
            })();
        """
    }
}
