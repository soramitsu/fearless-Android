package jp.co.soramitsu.wallet.impl.presentation.model

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_wallet_api.R
import jp.co.soramitsu.wallet.impl.domain.model.ControllerDeprecationWarning
import org.junit.Assert.assertEquals
import org.junit.Test

class ControllerDeprecationWarningModelTest {
    private val resources = mockk<ResourceManager> {
        every { getString(R.string.common_important) } returns "Important"
        every { getString(R.string.controller_account_issue_action) } returns "Change controller"
        every { getString(R.string.stash_account_issue_action) } returns "Import stash"
        every { getString(R.string.controller_account_issue_message, "Polkadot") } returns "Controller message"
        every { getString(R.string.stash_account_issue_message, "abcdefgh...stuvwxyz") } returns "Stash message"
    }

    @Test
    fun controllerWarningUsesChainNameAndChangeAction() {
        val model = ControllerDeprecationWarning.ChangeController("dot", "Polkadot").toModel(resources)
        assertEquals("Important", model.title)
        assertEquals("Controller message", model.message)
        assertEquals("Change controller", model.buttonText)
        assertEquals(ControllerDeprecationWarningModel.Action.ChangeController, model.action)
        assertEquals("dot", model.chainId)
        verify { resources.getString(R.string.controller_account_issue_message, "Polkadot") }
    }

    @Test
    fun stashWarningShortensAddressAndUsesImportAction() {
        val model = ControllerDeprecationWarning.ImportStash("dot", "abcdefghijklmnopqrstuvwxyz").toModel(resources)
        assertEquals("Stash message", model.message)
        assertEquals("Import stash", model.buttonText)
        assertEquals(ControllerDeprecationWarningModel.Action.ImportStash, model.action)
        assertEquals("dot", model.chainId)
        verify { resources.getString(R.string.stash_account_issue_message, "abcdefgh...stuvwxyz") }
    }
}
