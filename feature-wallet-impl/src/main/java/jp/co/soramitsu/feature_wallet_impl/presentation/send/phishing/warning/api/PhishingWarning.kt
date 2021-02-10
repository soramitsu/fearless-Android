package jp.co.soramitsu.feature_wallet_impl.presentation.send.phishing.warning.api

import androidx.lifecycle.LiveData
import jp.co.soramitsu.common.utils.Event

interface PhishingWarning {

    val showPhishingWarning: LiveData<Event<String>>

    suspend fun checkAddressForPhishing(address: String)

    fun proceedAddress(address: String)
}