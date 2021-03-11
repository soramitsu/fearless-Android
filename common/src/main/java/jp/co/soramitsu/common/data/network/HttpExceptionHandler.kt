package jp.co.soramitsu.common.data.network

import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.BaseException
import jp.co.soramitsu.common.resources.ResourceManager
import retrofit2.HttpException
import java.io.IOException

class HttpExceptionHandler(
    private val resourceManager: ResourceManager
) {
    suspend fun <T> wrap(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: Throwable) {
            throw transformException(e)
        }
    }

    private fun transformException(exception: Throwable): BaseException {
        return when (exception) {
            is HttpException -> {
                val response = exception.response()!!

                val errorCode = response.code()
                response.errorBody()?.close()

                BaseException.httpError(errorCode, resourceManager.getString(R.string.common_error_general_message))
            }
            is IOException -> BaseException.networkError(resourceManager.getString(R.string.connection_error_message), exception)
            else -> BaseException.unexpectedError(exception)
        }
    }
}