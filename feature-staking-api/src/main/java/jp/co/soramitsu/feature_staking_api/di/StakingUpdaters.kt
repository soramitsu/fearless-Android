package jp.co.soramitsu.feature_staking_api.di

import jp.co.soramitsu.core.updater.ScopedUpdater
import jp.co.soramitsu.core.updater.Updater

class StakingUpdaters(val globalUpdaters: Array<Updater>, val accountUpdaters: Array<ScopedUpdater<String>>)