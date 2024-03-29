package jp.co.soramitsu.walletconnect.impl.presentation

import androidx.core.net.toUri
import com.walletconnect.android.Core
import com.walletconnect.web3.wallet.client.Wallet
import io.ipfs.multibase.CharEncoding
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.shared_utils.extensions.fromHex
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.Charset

enum class Caip2Namespace(val value: String) {
    EIP155("eip155"),
    POLKADOT("polkadot")
}

// https://github.com/ChainAgnostic/CAIPs/blob/master/CAIPs/caip-2.md#syntax
@Suppress("MagicNumber")
val Chain.caip2id: String
    get() {
        val namespace = if (isEthereumChain) Caip2Namespace.EIP155 else Caip2Namespace.POLKADOT
        val chainId = id.substring(0, Integer.min(id.length, 32))
        return namespace.value + ":" + chainId
    }

val Core.Model.AppMetaData?.dappUrl: String?
    get() = runCatching {
        this?.url?.toUri()?.schemeSpecificPart?.removePrefix("//")?.removeSuffix("/")
    }.getOrNull() ?: this?.url

val Wallet.Model.SessionRequest.JSONRPCRequest.message: String
    get() = when (method) {
        WalletConnectMethod.PolkadotSignMessage.method -> JSONObject(params).get("message").toString()
        WalletConnectMethod.PolkadotSignTransaction.method -> JSONObject(params).get("transactionPayload").toString()
        WalletConnectMethod.EthereumSendTransaction.method -> JSONArray(params).get(0).toString()
        WalletConnectMethod.EthereumSignTransaction.method -> JSONArray(params).get(0).toString()
        WalletConnectMethod.EthereumSignTypedData.method -> JSONArray(params).get(1).toString()
        WalletConnectMethod.EthereumSignTypedDataV4.method -> JSONArray(params).get(1).toString()
        WalletConnectMethod.EthereumPersonalSign.method -> JSONArray(params).get(0).toString().fromHex().toString(Charset.forName(CharEncoding.UTF_8))
        WalletConnectMethod.EthereumSign.method -> JSONArray(params).get(1).toString().fromHex().toString(Charset.forName(CharEncoding.UTF_8))
        else -> "$method's params: $params"
    }

val Wallet.Model.SessionRequest.JSONRPCRequest.address: String?
    get() = when (method) {
        WalletConnectMethod.PolkadotSignMessage.method -> JSONObject(params).get("address")
        WalletConnectMethod.PolkadotSignTransaction.method -> JSONObject(params).get("address")
        WalletConnectMethod.EthereumPersonalSign.method -> JSONArray(params).get(1)
        WalletConnectMethod.EthereumSignTransaction.method -> (JSONArray(params).get(0) as? JSONObject)?.get("from")
        WalletConnectMethod.EthereumSendTransaction.method -> (JSONArray(params).get(0) as? JSONObject)?.get("from")
        WalletConnectMethod.EthereumSignTypedData.method -> JSONArray(params).get(0)
        WalletConnectMethod.EthereumSignTypedDataV4.method -> JSONArray(params).get(0)
        WalletConnectMethod.EthereumSign.method -> JSONArray(params).get(0)
        else -> null
    } as? String
