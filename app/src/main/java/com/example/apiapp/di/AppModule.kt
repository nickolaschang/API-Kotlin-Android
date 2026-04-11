package com.example.apiapp.di

import com.example.apiapp.data.api.ApiService
import com.example.apiapp.data.repository.AppRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt module that wires up the networking stack.
 *
 * Installed in [SingletonComponent] so everything here lives for the full
 * application lifetime — there is never a reason to recreate the HTTP
 * client, Retrofit instance, or repository.
 *
 * The provider chain is: `OkHttpClient → Retrofit → ApiService → AppRepository`.
 * Each depends on the one before it, and Hilt stitches them together
 * automatically via constructor injection.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Builds the shared [OkHttpClient] used by Retrofit.
     *
     * Two important tweaks over the default:
     *
     * 1. **Dispatcher concurrency** — OkHttp's default `Dispatcher` caps
     *    `maxRequestsPerHost` at 5, which would throttle the vuln demo's
     *    enumeration scan (which fires hundreds of probes at the same
     *    host). We raise both limits to 256 so the worker pool in
     *    [com.example.apiapp.ui.vulndemo.VulnDemoViewModel] can actually
     *    run in parallel.
     *
     * 2. **Generous timeouts** — the NIT3213 API is hosted on Render's
     *    free tier which cold-starts after idle periods. A 60-second
     *    timeout lets the first request wake the server up instead of
     *    failing immediately.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val dispatcher = Dispatcher().apply {
            maxRequests = 256
            maxRequestsPerHost = 256
        }
        return OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Configures Retrofit with the NIT3213 dummy API base URL and Gson
     * for JSON (de)serialization.
     */
    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://nit3213api.onrender.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /** Asks Retrofit to generate the [ApiService] implementation at runtime. */
    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService {
        return retrofit.create(ApiService::class.java)
    }

    /**
     * Repository is `@Inject`-annotated so Hilt could wire it up automatically,
     * but we provide it explicitly here to keep the whole networking graph
     * visible in one file.
     */
    @Provides
    @Singleton
    fun provideAppRepository(apiService: ApiService): AppRepository {
        return AppRepository(apiService)
    }
}
