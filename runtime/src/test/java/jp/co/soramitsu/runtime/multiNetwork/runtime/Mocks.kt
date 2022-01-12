package jp.co.soramitsu.runtime.multiNetwork.runtime

import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.test_shared.whenever
import org.mockito.Mockito

object Mocks {
    fun chain(id: String) : Chain {
        val chain = Mockito.mock(Chain::class.java)

        whenever(chain.id).thenReturn(id)

        return chain
    }
}
