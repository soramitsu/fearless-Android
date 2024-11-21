package jp.co.soramitsu.tonconnect.impl.presentation.dappscreen

import androidx.collection.ArrayMap
import org.json.JSONArray
import org.json.JSONObject

abstract class JsBridge(
    val windowKey: String,
    val timeout: Long? = null
) {

    abstract val availableFunctions: Array<String>

    val keys = ArrayMap<String, Any>()

    /**
     * Don't use reflection because it's not safe and can be used for hacking
     * Use direct call of functions
     */
    abstract suspend fun invokeFunction(name: String, args: JSONArray): Any?

    open fun jsInjection(): String {
        val funcs = availableFunctions.joinToString(",") {"""
            $it: (...args) => {
                return new Promise((resolve, reject) => window.invokeRnFunc('${it}', args, resolve, reject))
            }
        """}

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
                            if (message.type === '${BridgeMessage.Type.FunctionResponse.value}') {
                                const promise = window.rnPromises[message.invocationId];
                                
                                if (!promise) {
                                    return;
                                }
                                
                                if (promise.timeoutId) {
                                    clearTimeout(promise.timeoutId);
                                }
                                
                                if (message.status === 'fulfilled') {
                                    promise.resolve(message.data);
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
                
                window.${windowKey} = Object.assign(${JSONObject(keys)},{ $funcs },{ listen });
            })();
        """
    }
}