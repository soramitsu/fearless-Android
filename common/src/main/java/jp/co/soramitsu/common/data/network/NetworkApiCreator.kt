package jp.co.soramitsu.common.data.network

import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.lang.reflect.Type

class NetworkApiCreator(
    private val okHttpClient: OkHttpClient,
    private val baseUrl: String
) {

    fun <T> create(
        service: Class<T>,
        customBaseUrl: String = baseUrl,
        customFieldNamingPolicy: FieldNamingPolicy? = null,
        typeAdapters: Map<Type, JsonDeserializer<*>>? = null
    ): T {
        val gson = when {
            customFieldNamingPolicy == null && typeAdapters == null -> Gson()
            else -> {
                val builder = GsonBuilder()

                customFieldNamingPolicy?.let { namingPolicy ->
                    builder.setFieldNamingPolicy(namingPolicy)
                }

                typeAdapters?.forEach { (type, deserializer) ->
                    builder.registerTypeAdapter(type, deserializer)
                }

                builder.create()
            }
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
