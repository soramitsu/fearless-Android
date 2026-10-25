package jp.co.soramitsu.fearless_utils.integration.account

import jp.co.soramitsu.fearless_utils.integration.BaseIntegrationTest
import jp.co.soramitsu.fearless_utils.integration.WESTEND_URL
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.fearless_utils.wsrpc.executeAsync
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.account.AccountInfoRequest
import kotlinx.coroutines.runBlocking
import org.bouncycastle.util.encoders.Hex
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
@Ignore("Manual run only")
class AccountBalanceRequestTest : BaseIntegrationTest(WESTEND_URL) {

    @Test
    fun `should fetch null balance`() = runBlocking {
        val publicKey = "6c88e9f8a5b39f1ac58f74569a62fb5c4738e7a8e42a6e312486c24af6686369"
        val publicKeyBytes = Hex.decode(publicKey)

        val response = socketService.executeAsync(AccountInfoRequest(publicKeyBytes))

        assert(response.result == null)
    }

    @Test
    fun `should fetch existing balance`() = runBlocking {
        val address = "5DEwU2U97RnBHCpfwHMDfJC7pqAdfWaPFib9wiZcr2ephSfT"

        val publicKey = address.toAccountId()

        val response = socketService.executeAsync(AccountInfoRequest(publicKey))

        assert(response.result != null)
    }
}
