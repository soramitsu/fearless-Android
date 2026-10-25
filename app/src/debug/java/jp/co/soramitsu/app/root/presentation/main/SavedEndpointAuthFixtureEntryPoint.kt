package jp.co.soramitsu.app.root.presentation.main

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.app.root.domain.WalletDatabaseStartupGate
import jp.co.soramitsu.coredb.AppDatabase

/** Debug-only access to the real graph for an explicitly owned disposable UID fixture. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SavedEndpointAuthFixtureEntryPoint {
    /** The production startup gate, including durable storage reconciliation. */
    fun startupGate(): WalletDatabaseStartupGate

    /** The production database; callers must pass the startup gate first. */
    fun database(): AppDatabase

    /** The production repository used to save and verify the public fixture PIN. */
    fun accountRepository(): AccountRepository
}
