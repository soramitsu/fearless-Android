package jp.co.soramitsu.feature_crowdloan_api.di

import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.updaters.CrowdloanUpdaters
import jp.co.soramitsu.feature_crowdloan_api.data.repository.CrowdloanRepository

interface CrowdloanFeatureApi {

    fun repository(): CrowdloanRepository

    fun updaters(): CrowdloanUpdaters
}
