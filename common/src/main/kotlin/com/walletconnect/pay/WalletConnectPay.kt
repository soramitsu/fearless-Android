package com.walletconnect.pay

import kotlin.coroutines.Continuation
import kotlin.jvm.JvmName

/**
 * Lightweight stub for the WalletConnect Pay SDK.
 *
 * The real implementation is excluded to avoid bundling native libraries that
 * do not satisfy the 16 KB page-size requirement. Providing a stub keeps the
 * WalletKit dependency happy while intentionally disabling Pay-specific flows.
 */
class Pay {
    data class SdkConfig(
        val projectId: String = "",
        val relayUrl: String = "",
        val walletName: String = "",
        val walletDescription: String = "",
        val walletIcon: String = ""
    )
}

@Suppress("UNUSED_PARAMETER")
object WalletConnectPay {

    private var initialized = false

    fun initialize(config: Pay.SdkConfig) {
        initialized = true
    }

    fun isInitialized(): Boolean = initialized

    @JvmName("getPaymentOptions-0E7RQCE")
    fun getPaymentOptions(
        requestId: String,
        routes: List<Any?>,
        continuation: Continuation<Any?>
    ): Any? = unavailable()

    @JvmName("getRequiredPaymentActions-0E7RQCE")
    fun getRequiredPaymentActions(
        requestId: String,
        optionId: String,
        continuation: Continuation<Any?>
    ): Any? = unavailable()

    @JvmName("confirmPayment-yxL6bBk")
    fun confirmPayment(
        requestId: String,
        optionId: String,
        actions: List<Any?>,
        signatures: List<Any?>,
        continuation: Continuation<Any?>
    ): Any? = unavailable()

    private fun unavailable(): Nothing =
        throw UnsupportedOperationException("WalletConnect Pay is disabled in this build")
}
