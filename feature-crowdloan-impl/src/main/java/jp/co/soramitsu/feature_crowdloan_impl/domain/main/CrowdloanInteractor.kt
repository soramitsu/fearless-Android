package jp.co.soramitsu.feature_crowdloan_impl.domain.main

import jp.co.soramitsu.feature_crowdloan_api.data.repository.CrowdloanRepository

class CrowdloanInteractor(
    private val crowdloanRepository: CrowdloanRepository
) {

    suspend fun getAllCrowdloans() = crowdloanRepository.allFundInfos()
}
