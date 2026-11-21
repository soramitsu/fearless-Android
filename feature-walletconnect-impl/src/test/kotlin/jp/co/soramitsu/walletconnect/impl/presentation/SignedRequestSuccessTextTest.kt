package jp.co.soramitsu.walletconnect.impl.presentation

import co.jp.soramitsu.feature_walletconnect_impl.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignedRequestSuccessTextTest {

    @Test
    fun `only a transaction this wallet broadcast keeps the transaction sent text`() {
        assertNull(signedRequestSuccessText(WalletConnectMethod.EthereumSendTransaction.method))
    }

    @Test
    fun `message signatures say a message was signed`() {
        val messageMethods = listOf(
            WalletConnectMethod.EthereumPersonalSign,
            WalletConnectMethod.EthereumSign,
            WalletConnectMethod.EthereumSignTypedData,
            WalletConnectMethod.EthereumSignTypedDataV4,
            WalletConnectMethod.PolkadotSignMessage
        )

        for (method in messageMethods) {
            assertEquals(
                method.method,
                SignedRequestSuccessText(R.string.walletconnect_message_signed_title, R.string.walletconnect_message_signed_text),
                signedRequestSuccessText(method.method)
            )
        }
    }

    @Test
    fun `a transaction that was only signed does not claim it was sent`() {
        for (method in listOf(WalletConnectMethod.EthereumSignTransaction, WalletConnectMethod.PolkadotSignTransaction)) {
            assertEquals(
                method.method,
                SignedRequestSuccessText(R.string.walletconnect_transaction_signed_title, R.string.walletconnect_transaction_signed_text),
                signedRequestSuccessText(method.method)
            )
        }
    }
}
