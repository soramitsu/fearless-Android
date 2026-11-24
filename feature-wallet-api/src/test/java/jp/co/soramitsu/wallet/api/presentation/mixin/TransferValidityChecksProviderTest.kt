package jp.co.soramitsu.wallet.api.presentation.mixin

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.wallet.impl.domain.model.TransferValidityLevel
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

@Suppress("DEPRECATION")
class TransferValidityChecksProviderTest {
    @get:Rule val executor = InstantTaskExecutorRule()

    @Test
    fun warningIsPublishedAsConsumableEventWithoutAnError() {
        val provider = TransferValidityChecksProvider()
        provider.showTransferWarning(TransferValidityLevel.Warning.Status.WillRemoveAccount)
        val event = provider.showTransferWarning.value!!
        assertSame(TransferValidityLevel.Warning.Status.WillRemoveAccount, event.getContentIfNotHandled())
        assertNull(event.getContentIfNotHandled())
        assertNull(provider.showTransferError.value)
    }

    @Test
    fun errorEventIsReplacedForEachFailureWithoutWarning() {
        val provider = TransferValidityChecksProvider()
        provider.showTransferError(TransferValidityLevel.Error.Status.NotEnoughFunds)
        assertSame(TransferValidityLevel.Error.Status.NotEnoughFunds, provider.showTransferError.value?.peekContent())
        provider.showTransferError(TransferValidityLevel.Error.Status.DeadRecipient)
        assertSame(TransferValidityLevel.Error.Status.DeadRecipient, provider.showTransferError.value?.peekContent())
        assertNull(provider.showTransferWarning.value)
    }
}
