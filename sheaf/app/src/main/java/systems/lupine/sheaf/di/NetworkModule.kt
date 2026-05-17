package systems.lupine.sheaf.di

import android.content.Context
import systems.lupine.sheaf.BuildConfig
import systems.lupine.sheaf.data.api.AuthInterceptor
import systems.lupine.sheaf.data.api.BaseUrlInterceptor
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.api.TokenAuthenticator
import systems.lupine.sheaf.data.api.TrustedDeviceCookieJar
import systems.lupine.sheaf.data.api.UserAgentInterceptor
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import systems.lupine.sheaf.data.api.RelativeUrlInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.security.cert.X509Certificate
import java.util.Date
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .add(Date::class.java, Rfc3339DateJsonAdapter().nullSafe())
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        baseUrlInterceptor: BaseUrlInterceptor,
        tokenAuthenticator: TokenAuthenticator,
        cookieJar: TrustedDeviceCookieJar,
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(baseUrlInterceptor)
            .addInterceptor(authInterceptor)
            .authenticator(tokenAuthenticator)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
            )

        if (BuildConfig.DEBUG) {
            val trustAll = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(trustAll), null)
            }
            builder
                .sslSocketFactory(sslContext.socketFactory, trustAll)
                .hostnameVerifier { _, _ -> true }
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            // Placeholder — overridden at runtime by BaseUrlInterceptor
            .baseUrl("http://localhost/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideSheafApiService(retrofit: Retrofit): SheafApiService =
        retrofit.create(SheafApiService::class.java)

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        relativeUrlInterceptor: RelativeUrlInterceptor,
        userAgentInterceptor: UserAgentInterceptor,
    ): ImageLoader {
        val imageClient = okHttpClient.newBuilder()
            .addInterceptor(userAgentInterceptor)
            .build()
        return ImageLoader.Builder(context)
            .okHttpClient(imageClient)
            .components {
                add(relativeUrlInterceptor)
            }
            .crossfade(true)
            // Explicit cache config. The default 2% disk allocation on a
            // freshly-installed device with low free space ends up small
            // enough that avatar reloads become visible on history scroll
            // (every row in the front-history list is an avatar of a
            // member who likely also fronts in nearby rows). Avatars are
            // tiny on the wire so we can afford to be generous; 100MB
            // hard cap keeps the cache from squatting on an SSD's worth
            // of disk on a system with terabytes free.
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.30)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("sheaf_image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            // Avatars never change at a given URL (the backend mints a new
            // URL when an avatar is replaced), so ignore upstream cache
            // headers and rely on URL change for invalidation. Saves the
            // 304-validation round trips that otherwise fire on every
            // recomposition where Coil thinks the entry might be stale.
            .respectCacheHeaders(false)
            .build()
    }
}
