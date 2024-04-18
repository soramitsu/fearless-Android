package jp.co.soramitsu.wallet.impl.data.network.model.response

import org.web3j.protocol.websocket.events.Notification


class NewHeadExtended {
    var difficulty: String? = null
    var extraData: String? = null
    var gasLimit: String? = null
    var gasUsed: String? = null
    var hash: String? = null
    var logsBloom: String? = null
    var miner: String? = null
    var nonce: String? = null
    var number: String? = null
    var parentHash: String? = null
    var receiptRoot: String? = null
    var sha3Uncles: String? = null
    var stateRoot: String? = null
    var timestamp: String? = null
    var transactionRoot: String? = null
    var baseFeePerGas: String? = null

}
class NewHeadsNotificationExtended :
    Notification<NewHeadExtended?>()
