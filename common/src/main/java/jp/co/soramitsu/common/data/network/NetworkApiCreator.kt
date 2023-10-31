package jp.co.soramitsu.common.data.network

import android.util.Log
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import javax.inject.Named
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory

class NetworkApiCreator(
    @Named("FearlessOkHttp") private val okHttpClient: OkHttpClient,
    private val baseUrl: String
) {

    init {
        Log.d("&&&", "okHttpInstance is ${okHttpClient.hashCode()} $okHttpClient")

    }
    fun <T> create(
        service: Class<T>,
        customBaseUrl: String = baseUrl,
        customFieldNamingPolicy: FieldNamingPolicy? = null
    ): T {
        val gson = when (customFieldNamingPolicy) {
            null -> Gson()
            else -> GsonBuilder()
                .setFieldNamingPolicy(customFieldNamingPolicy)
                .create()
        }
        val retrofit = Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl(customBaseUrl)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        return retrofit.create(service)
    }
}
