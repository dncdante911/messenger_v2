package com.worldmates.messenger.network

import android.util.Log
import com.worldmates.messenger.data.UserSession
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * OkHttp interceptor that transparently refreshes the access token when:
 *  1. The server returns HTTP 401 with error_id = "401" (token expired), OR
 *  2. The local token is within 60 s of expiry before the request is sent.
 *
 * On success the new tokens are persisted in [UserSession] and the original
 * request is retried once with the new access token.
 * On failure (refresh token also expired) the session is cleared so the app
 * can redirect the user to the login screen.
 *
 * NOTE: This interceptor uses a synchronous (blocking) Retrofit call to avoid
 * coroutine complexities inside OkHttp interceptors.
 */
class TokenRefreshInterceptor(private val baseUrl: String) : Interceptor {

    companion object {
        private const val TAG = "TokenRefreshInterceptor"
        // Paths that must never trigger a refresh attempt (auth endpoints)
        private val SKIP_PATHS = setOf(
            "api/node/auth/login",
            "api/node/auth/refresh",
            "api/node/auth/register",
            "api/node/auth/quick-register",
            "api/node/auth/quick-verify",
            "api/node/auth/verify-code",
            "api/node/auth/send-code",
            "api/node/auth/request-password-reset",
            "api/node/auth/reset-password",
        )
    }

    // Minimal Retrofit interface used only inside this interceptor (blocking call)
    private interface RefreshApi {
        @FormUrlEncoded
        @POST("api/node/auth/refresh")
        fun refreshTokenSync(
            @Field("refresh_token") refreshToken: String
        ): retrofit2.Call<TokenRefreshResponse>
    }

    private val refreshApi: RefreshApi by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RefreshApi::class.java)
    }

    @Volatile private var isRefreshing = false

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Skip auth endpoints
        val path = request.url.encodedPath.trimStart('/')
        if (SKIP_PATHS.any { path.startsWith(it) }) {
            return chain.proceed(request)
        }

        // Proactive refresh: if the token is within 60 s of expiry, refresh now
        if (UserSession.isTokenExpiringSoon && !UserSession.refreshToken.isNullOrEmpty()) {
            val refreshed = tryRefresh()
            if (refreshed) {
                return chain.proceed(withFreshToken(request))
            }
        }

        // Proceed with the original request
        val response = chain.proceed(request)

        // Reactive refresh: server returned 401 token-expired
        if (response.code == 401 || isTokenExpiredBody(response)) {
            val refreshToken = UserSession.refreshToken
            if (refreshToken.isNullOrEmpty()) {
                Log.w(TAG, "401 but no refresh token stored — logging out")
                UserSession.clearSession()
                return response
            }

            response.close()

            val refreshed = tryRefresh()
            return if (refreshed) {
                chain.proceed(withFreshToken(request))
            } else {
                Log.w(TAG, "Refresh failed — logging out")
                UserSession.clearSession()
                chain.proceed(request) // return original failed response
            }
        }

        return response
    }

    private fun withFreshToken(original: Request): Request {
        return original.newBuilder()
            .header("access-token", UserSession.accessToken ?: "")
            .build()
    }

    /**
     * Attempt to refresh the access token synchronously.
     * Returns true on success, false on failure.
     */
    @Synchronized
    private fun tryRefresh(): Boolean {
        // Double-check: another thread may have already refreshed
        if (!UserSession.isTokenExpiringSoon &&
            !UserSession.accessToken.isNullOrEmpty() &&
            UserSession.tokenExpiresAt > System.currentTimeMillis() / 1000 + 60) {
            return true
        }

        val rt = UserSession.refreshToken ?: return false

        return try {
            val call     = refreshApi.refreshTokenSync(rt)
            val response = call.execute()
            val body     = response.body()

            if (body != null && body.apiStatus == 200 &&
                !body.accessToken.isNullOrEmpty() &&
                !body.refreshToken.isNullOrEmpty()) {
                UserSession.updateTokens(
                    newAccessToken  = body.accessToken,
                    newRefreshToken = body.refreshToken,
                    newExpiresAt    = body.expiresAt ?: 0L
                )
                Log.d(TAG, "Token refreshed successfully for user ${body.userId}")
                true
            } else {
                Log.w(TAG, "Refresh endpoint returned error: ${body?.errorMessage}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh network error: ${e.message}")
            false
        }
    }

    /**
     * Peek at the response body to detect a JSON error_id = "401".
     * Returns false without consuming the body if it's not an expired-token response.
     * NOTE: peeking is safe in OkHttp — it doesn't close the response.
     */
    private fun isTokenExpiredBody(response: Response): Boolean {
        return try {
            val source = response.peekBody(512).string()
            source.contains("\"error_id\":\"401\"") || source.contains("\"error_id\": \"401\"")
        } catch (_: Exception) {
            false
        }
    }
}
