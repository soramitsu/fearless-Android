package jp.co.soramitsu.app.root.presentation

import dagger.hilt.android.AndroidEntryPoint

/**
 * Compatibility entry point for tasks and PendingIntents created by versions
 * where RootActivity owned the navigation graph.
 *
 * Keep this class name stable. It must stay lightweight and inherit the same
 * startup gate as the launcher so an in-place upgrade cannot restore the old
 * NavHost before wallet migrations complete.
 */
@AndroidEntryPoint
class RootActivity : WalletGateActivity()
