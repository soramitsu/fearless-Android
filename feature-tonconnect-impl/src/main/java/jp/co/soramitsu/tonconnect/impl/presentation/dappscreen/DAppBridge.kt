package jp.co.soramitsu.tonconnect.impl.presentation.dappscreen

import co.jp.soramitsu.tonconnect.model.ConnectRequest
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
) : JsBridge("tonkeeper") {

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
                val response = tonapiFetch(args.getString(0), args.optString(1) ?: "")
                webAPIResponse(response).toString()
            }

            else -> null
        }
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
        return when (code) {
            0 -> "error"
            else -> "cors"
        }
    }

    override fun jsInjection(): String {
        val funcs = availableFunctions.joinToString(",") {
            """
            $it: (...args) => {
                return new Promise((resolve, reject) => window.invokeRnFunc('${it}', args, resolve, reject))
            }
        """
        }.replace("\n", "").replace("  ", "")

        return """
            (() => {
                if (!window.${windowKey}) {
                    window.rnPromises = {};
                    window.rnEventListeners = [];
                    window.invokeRnFunc = (name, args, resolve, reject) => {
                        const invocationId = btoa(Math.random()).substring(0, 12);
                        const timeoutMs = ${timeout};
                        const timeoutId = timeoutMs ? setTimeout(() => reject(new Error('bridge timeout for function with name: '+name+'')), timeoutMs) : null;
                        window.rnPromises[invocationId] = { resolve, reject, timeoutId }
                        window.ReactNativeWebView.postMessage(JSON.stringify({
                            type: '${BridgeMessage.Type.InvokeRnFunc.value}',
                            invocationId: invocationId,
                            name,
                            args,
                        }));
                    };
                    
                    window.addEventListener('message', ({ data }) => {
                        try {
                            const message = data;
                            console.log('message bridge', JSON.stringify(message));
                            if (message.type === '${BridgeMessage.Type.FunctionResponse.value}') {
                                const promise = window.rnPromises[message.invocationId];
                                
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
                                
                                delete window.rnPromises[message.invocationId];
                            }
                            
                            if (message.type === '${BridgeMessage.Type.Event.value}') {
                                window.rnEventListeners.forEach((listener) => listener(message.event));
                            }
                        } catch { }
                    });
                }
                
                const listen = (cb) => {
                    window.rnEventListeners.push(cb);
                    return () => {
                        const index = window.rnEventListeners.indexOf(cb);
                        if (index > -1) {
                            window.rnEventListeners.splice(index, 1);
                        }
                    };
                };
                
                window.${windowKey} = {
                    tonconnect: Object.assign(${JSONObject(keys)},{ $funcs },{ listen })
                };
                
                window.tonapi = {
                    fetch: async (url, options) => {
                        return new Promise((resolve, reject) => {
                            window.invokeRnFunc('tonapi.fetch', [url, options], (result) => {
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