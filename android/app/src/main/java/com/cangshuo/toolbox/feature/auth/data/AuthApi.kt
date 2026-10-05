package com.cangshuo.toolbox.feature.auth.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.net.URI
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT

internal data class AuthRequestDto(val email: String, val password: String, val nickname: String? = null) {
    override fun toString() = "AuthRequestDto[redacted]"
}
internal data class UserDto(val id: Long, val email: String, val nickname: String)
internal data class AuthDataDto(val accessToken: String, val tokenType: String, val expiresIn: Long,
    val expiresAt: String, val user: UserDto, val refreshToken: String, val refreshExpiresAt: String) {
    override fun toString() = "AuthDataDto[redacted]"
}
internal data class AuthEnvelope<T>(val code: Int, val message: String, val data: T?, val traceId: String) {
    override fun toString() = "AuthEnvelope[redacted]"
}

internal interface AuthApi {
    @retrofit2.http.HTTP(method="DELETE", path="auth/me", hasBody=true)
    suspend fun deleteAccount(@Header("Authorization") bearer: String,
        @Body request: DeleteAccountDto): Response<AuthEnvelope<Unit>>
    @POST("auth/register") suspend fun register(@Body request: AuthRequestDto): Response<AuthEnvelope<AuthDataDto>>
    @POST("auth/login") suspend fun login(@Body request: AuthRequestDto): Response<AuthEnvelope<AuthDataDto>>
    @GET("auth/me") suspend fun me(@Header("Authorization") bearer: String): Response<AuthEnvelope<UserDto>>
    @PUT("auth/me") suspend fun updateProfile(@Header("Authorization") bearer: String,
        @Body request: ProfileRequestDto): Response<AuthEnvelope<UserDto>>
    @POST("auth/refresh") suspend fun refresh(@Body request: RefreshRequestDto): Response<AuthEnvelope<AuthDataDto>>
    @POST("auth/logout") suspend fun logout(@Body request: RefreshRequestDto): Response<AuthEnvelope<Unit>>
}

internal data class DeleteAccountDto(val email: String, val password: String) {
    override fun toString() = "DeleteAccountDto[redacted]"
}

internal data class ProfileRequestDto(val nickname: String) {
    override fun toString() = "ProfileRequestDto[redacted]"
}

internal data class RefreshRequestDto(val refreshToken: String) {
    override fun toString() = "RefreshRequestDto[redacted]"
}

internal fun createAuthApi(baseUrl: String): AuthApi = createRemoteApi(baseUrl,AuthApi::class.java,16_384)

internal fun <T> createRemoteApi(baseUrl: String, type: Class<T>, budget: Long, timeoutSeconds: Long = 15): T {
    require(timeoutSeconds in 1..70)
    val uri = URI(baseUrl)
    require(uri.scheme == "https" || (uri.scheme == "http" && uri.host in setOf("localhost", "127.0.0.1", "10.0.2.2")))
    require(uri.userInfo == null && uri.query == null && uri.fragment == null && uri.path.trimEnd('/') == "/api/v1")
    val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(if (timeoutSeconds == 15L) 10 else timeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS).callTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            val body = response.body() ?: return@addInterceptor response
            body.use {
                if (it.contentLength() > budget) throw java.io.IOException("API response exceeds budget")
                val source = it.source()
                source.request(budget+1)
                if (source.buffer.size > budget) throw java.io.IOException("API response exceeds budget")
                val bytes = source.buffer.readByteArray()
                response.newBuilder().body(ResponseBody.create(it.contentType(), bytes)).build()
            }
        }
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    return Retrofit.Builder().baseUrl(baseUrl.trimEnd('/') + "/").client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi)).build().create(type)
}
