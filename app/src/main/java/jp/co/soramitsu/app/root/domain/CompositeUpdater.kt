package jp.co.soramitsu.app.root.domain

import jp.co.soramitsu.core.updater.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext

class CompositeUpdater(
    private val updaters: List<Updater>
) : Updater {

    constructor(vararg updaters: Updater) : this(updaters.toList())

    override suspend fun listenForUpdates() = withContext(Dispatchers.IO) {
        val flows = updaters.map { it.listenForUpdates() }

        flows.merge()
    }
}