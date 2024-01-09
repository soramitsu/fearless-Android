package jp.co.soramitsu.common.utils

import jp.co.soramitsu.shared_utils.encrypt.Base58

object Base58Ext {

    // TODO make Base58 static in fearless utils
    private val base58 = Base58()

    fun String.fromBase58Check() = base58.decodeChecked(this)
}
