package jp.co.soramitsu.wallet.impl.presentation

import jp.co.soramitsu.runtime.multiNetwork.tokengroups.PortfolioVisibility
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.SharedPortfolioConfigSource
import java.io.File

/** The shared portfolio files exactly as the app bundles them in `runtime/src/main/assets`. */
internal fun bundledPortfolioVisibility(): PortfolioVisibility = PortfolioVisibility(
    SharedPortfolioConfigSource(
        readAsset = { name ->
            listOf(File("../runtime/src/main/assets/$name"), File("runtime/src/main/assets/$name"))
                .first(File::isFile)
                .readBytes()
        },
        onLoadFailure = { name, error -> throw AssertionError("Bundled $name failed to load", error) }
    )
)
