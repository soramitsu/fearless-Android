package jp.co.soramitsu.feature_staking_api.di

import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.feature_account_api.domain.updaters.AccountUpdater

class StakingUpdaters(val globalUpdaters: Array<Updater>, val accountUpdaters: Array<AccountUpdater>)