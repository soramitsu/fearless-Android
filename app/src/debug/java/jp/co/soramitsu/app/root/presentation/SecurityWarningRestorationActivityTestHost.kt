package jp.co.soramitsu.app.root.presentation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.atomic.AtomicInteger
import jp.co.soramitsu.account.impl.presentation.exporting.SecurityWarningBottomSheet

/** Debug-only host for exercising nested FragmentManager save and restore. */
class SecurityWarningRestorationActivityTestHost : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            SecurityWarningRestorationTestController.restoredActivityCount
                .incrementAndGet()
        }
        setContentView(FrameLayout(this))

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(
                    android.R.id.content,
                    SecurityWarningRestorationParentTestFragment(),
                    PARENT_FRAGMENT_TAG
                )
                .commitNow()
            currentParent().showWarning()
        }
    }

    fun currentSheet(): SecurityWarningBottomSheet? =
        currentParent().currentSheet()

    fun activeSheetCount(): Int = currentParent().activeSheetCount()

    fun cancelCurrentSheet() {
        currentParent().cancelCurrentSheet()
    }

    private fun currentParent():
        SecurityWarningRestorationParentTestFragment {
        supportFragmentManager.executePendingTransactions()
        return checkNotNull(
            supportFragmentManager.findFragmentByTag(PARENT_FRAGMENT_TAG)
                as? SecurityWarningRestorationParentTestFragment
        ) {
            "The security-warning restoration parent is not attached"
        }
    }

    companion object {
        private const val PARENT_FRAGMENT_TAG =
            "security_warning_restoration_parent"
    }
}

/** Mirrors ExportFragment's child FragmentManager and result listener. */
class SecurityWarningRestorationParentTestFragment : Fragment() {

    private var cancellationDelivered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cancellationDelivered =
            savedInstanceState?.getBoolean(CANCELLATION_DELIVERED_KEY) == true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(CANCELLATION_DELIVERED_KEY, cancellationDelivered)
        super.onSaveInstanceState(outState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FrameLayout(requireContext())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        childFragmentManager.setFragmentResultListener(
            SecurityWarningBottomSheet.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            SecurityWarningRestorationTestController.resultCount
                .incrementAndGet()
            if (
                result.getString(
                    SecurityWarningBottomSheet.RESULT_ACTION_KEY
                ) != SecurityWarningBottomSheet.ACTION_CONFIRM &&
                cancellationDelivered.not()
            ) {
                cancellationDelivered = true
                SecurityWarningRestorationTestController.cancellationCount
                    .incrementAndGet()
                requireActivity().finish()
            }
        }
    }

    fun showWarning() {
        SecurityWarningBottomSheet().showNow(
            childFragmentManager,
            SecurityWarningBottomSheet.TAG
        )
    }

    fun currentSheet(): SecurityWarningBottomSheet? {
        childFragmentManager.executePendingTransactions()
        return childFragmentManager.findFragmentByTag(
            SecurityWarningBottomSheet.TAG
        ) as? SecurityWarningBottomSheet
    }

    fun activeSheetCount(): Int =
        childFragmentManager.fragments.count {
            it is SecurityWarningBottomSheet && it.isRemoving.not()
        }

    fun cancelCurrentSheet() {
        checkNotNull(currentSheet()?.dialog).cancel()
    }

    private companion object {
        const val CANCELLATION_DELIVERED_KEY =
            "security_warning_test_cancellation_delivered"
    }
}

object SecurityWarningRestorationTestController {
    val restoredActivityCount = AtomicInteger()
    val resultCount = AtomicInteger()
    val cancellationCount = AtomicInteger()

    fun reset() {
        restoredActivityCount.set(0)
        resultCount.set(0)
        cancellationCount.set(0)
    }
}
