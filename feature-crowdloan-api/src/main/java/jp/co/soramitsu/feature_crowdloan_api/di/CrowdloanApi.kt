package jp.co.soramitsu.feature_crowdloan_api.di

import jp.co.soramitsu.feature_crowdloan_api.domain.repository.CrowdloanRepository

interface CrowdloanApi {

    fun repository() : CrowdloanRepository
}
