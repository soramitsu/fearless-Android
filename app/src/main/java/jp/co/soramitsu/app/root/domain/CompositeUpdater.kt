package jp.co.soramitsu.app.root.domain

import jp.co.soramitsu.core_api.data.network.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

class CompositeUpdater(
    private val updaters: List<Updater>
) : Updater {

    constructor(vararg updaters: Updater) : this(updaters.toList())

    override suspend fun listenForUpdates(): Unit = withContext(Dispatchers.IO) {
        val coroutines = updaters.map {
            async { it.listenForUpdates() }
        }

        coroutines.awaitAll()
    }
}