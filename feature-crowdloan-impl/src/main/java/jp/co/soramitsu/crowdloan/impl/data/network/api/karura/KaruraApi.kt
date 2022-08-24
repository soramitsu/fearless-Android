package jp.co.soramitsu.crowdloan.impl.data.network.api.karura

import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.kusamaChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.rococoChainId
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface KaruraApi {

    companion object {
        private val URL_BY_CHAIN_ID = mapOf(
            rococoChainId to "crowdloan-api.laminar.codes",
            kusamaChainId to "api.aca-staging.network"
        )

        fun getBaseUrl(chainId: ChainId) = URL_BY_CHAIN_ID[chainId]
            ?: throw UnsupportedOperationException("Network with genesis($chainId) is not supported for Karura")
    }

    @GET("//{baseUrl}/referral/{referral}")
    suspend fun isReferralValid(
        @Path("baseUrl") baseUrl: String,
        @Path("referral") referral: String
    ): ReferralCheck

    @GET("//{baseUrl}/statement")
    suspend fun getStatement(
        @Path("baseUrl") baseUrl: String
    ): KaruraStatement

    @POST("//{baseUrl}/verify")
    suspend fun applyForBonus(
        @Path("baseUrl") baseUrl: String,
        @Body body: VerifyKaruraParticipationRequest
    ): Any?
}
