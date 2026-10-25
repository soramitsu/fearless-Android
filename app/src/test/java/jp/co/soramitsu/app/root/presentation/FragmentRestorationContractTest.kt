package jp.co.soramitsu.app.root.presentation

import android.os.Parcelable
import jp.co.soramitsu.account.impl.presentation.exporting.SecurityWarningBottomSheet
import jp.co.soramitsu.common.presentation.ErrorDialog
import jp.co.soramitsu.common.presentation.InfoDialog
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import jp.co.soramitsu.wallet.impl.presentation.PendingTransferValidationDialog
import jp.co.soramitsu.wallet.impl.presentation.TransferValidationDialogResult
import org.junit.Assert.assertTrue
import org.junit.Test

class FragmentRestorationContractTest {

    @Test
    fun `runtime-created dialog fragments expose a public empty constructor`() {
        val fragmentTypes = listOf(
            ErrorDialog::class.java,
            InfoDialog::class.java,
            SecurityWarningBottomSheet::class.java
        )

        val nonRestorableTypes = fragmentTypes.filterNot { fragmentType ->
            fragmentType.constructors.any { it.parameterCount == 0 }
        }

        assertTrue(
            "Android cannot restore fragments without a public empty constructor: " +
                nonRestorableTypes.joinToString { it.name },
            nonRestorableTypes.isEmpty()
        )
    }

    @Test
    fun `transfer warning payload remains process-restorable`() {
        assertTrue(
            "Transfer warning results carried by ErrorDialog must remain Parcelable",
            Parcelable::class.java.isAssignableFrom(
                TransferValidationResult::class.java
            ) &&
                Parcelable::class.java.isAssignableFrom(
                    PendingTransferValidationDialog::class.java
                ) &&
                Parcelable::class.java.isAssignableFrom(
                    TransferValidationDialogResult::class.java
                )
        )
    }
}
