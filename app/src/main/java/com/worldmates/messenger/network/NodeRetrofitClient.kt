package com.worldmates.messenger.network

import android.util.Log
import com.worldmates.messenger.data.Constants
import com.worldmates.messenger.data.UserSession
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Retrofit клієнт для Node.js API (приватні чати).
 *
 * Відрізняється від [RetrofitClient] тим, що:
 *  1. Базовий URL → Constants.NODE_BASE_URL  (порт 449, той самий сервер)
 *  2. Немає ApiKeyInterceptor (Node.js не вимагає server_key / s)
 *  3. Auth через заголовок `access-token` ([NodeAuthInterceptor])
 *  4. Окремий OkHttpClient — не заважає PHP-клієнту
 *
 * Використання:
 *   val api = NodeRetrofitClient.api
 *   val resp = api.getMessages(recipientId = 42, limit = 30)
 */
object NodeRetrofitClient {

    private const val TAG = "NodeRetrofitClient"

    // ── DNS: IPv4 першим ─────────────────────────────────────────────────────
    private val ipv4Dns = object : okhttp3.Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return try {
                okhttp3.Dns.SYSTEM.lookup(hostname)
                    .sortedBy { if (it is Inet4Address) 0 else 1 }
            } catch (e: UnknownHostException) {
                Thread.sleep(500)
                try {
                    okhttp3.Dns.SYSTEM.lookup(hostname)
                        .sortedBy { if (it is Inet4Address) 0 else 1 }
                } catch (e2: UnknownHostException) {
                    Log.e(TAG, "DNS failed for $hostname")
                    throw e2
                }
            }
        }
    }

    // ── Auth interceptor ─────────────────────────────────────────────────────
    // Node.js читає access-token з хедера, query-параметра або тіла POST
    private val authInterceptor = Interceptor { chain ->
        val token = UserSession.accessToken ?: ""
        val req   = chain.request().newBuilder()
            .addHeader("access-token", token)
            .build()
        chain.proceed(req)
    }

    // ── Logging ──────────────────────────────────────────────────────────────
    private val loggingInterceptor = HttpLoggingInterceptor { msg ->
        Log.d("NODE_API", msg)
    }.apply { level = HttpLoggingInterceptor.Level.BODY }

    // ── OkHttpClient ─────────────────────────────────────────────────────────
    private val client = OkHttpClient.Builder()
        .dns(ipv4Dns)
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(Constants.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(Constants.READ_TIMEOUT_SECONDS,    TimeUnit.SECONDS)
        .writeTimeout(Constants.WRITE_TIMEOUT_SECONDS,  TimeUnit.SECONDS)
        .build()

    // ── Retrofit ─────────────────────────────────────────────────────────────
    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(Constants.NODE_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    /** Singleton NodeApi сервіс. Використовуй скрізь у проекті. */
    val api: NodeApi = retrofit.create(NodeApi::class.java)

    /** Channel API via Node.js. */
    val channelApi: NodeChannelApi = retrofit.create(NodeChannelApi::class.java)

    // ── Upload client with longer timeouts for file uploads ────────────────
    private val uploadClient = client.newBuilder()
        .writeTimeout(Constants.MEDIA_UPLOAD_TIMEOUT.toLong(), TimeUnit.SECONDS)
        .readTimeout(Constants.MEDIA_UPLOAD_TIMEOUT.toLong(), TimeUnit.SECONDS)
        .build()

    private val uploadRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl(Constants.NODE_BASE_URL)
        .client(uploadClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    /** Stories API with longer upload timeouts. */
    val storiesApi: NodeStoriesApi = uploadRetrofit.create(NodeStoriesApi::class.java)

    /** Channel API with longer upload timeouts (for media/avatar uploads). */
    val channelUploadApi: NodeChannelApi = uploadRetrofit.create(NodeChannelApi::class.java)

    /** Group chat API via Node.js. */
    val groupApi: NodeGroupApi = retrofit.create(NodeGroupApi::class.java)

    /** Group API with longer upload timeouts (for avatar uploads). */
    val groupUploadApi: NodeGroupApi = uploadRetrofit.create(NodeGroupApi::class.java)
}
