package jp.co.soramitsu.staking.impl.data.network.blockhain.updaters.base

import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.core.updater.Updater

interface StakingUpdater : Updater {

    override val requiredModules: List<String>
        get() = listOf(Modules.STAKING)
}

interface ParachainStakingUpdater : Updater {
    override val requiredModules: List<String>
        get() = listOf(Modules.PARACHAIN_STAKING)
}
