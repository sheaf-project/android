package systems.lupine.sheaf.di

import android.content.Context
import systems.lupine.sheaf.BuildConfig
import systems.lupine.sheaf.data.api.AuthInterceptor
import systems.lupine.sheaf.data.api.BaseUrlInterceptor
import systems.lupine.sheaf.data.api.FrontUpdateJsonAdapter
import systems.lupine.sheaf.data.model.FrontUpdate
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
        .add(FrontUpdate::class.java, FrontUpdateJsonAdapter())
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        baseUrlInterceptor: BaseUrlInterceptor,
        tokenAuthenticator: TokenAuthenticator,
        cookieJar: TrustedDeviceCookieJar,
        userAgentInterceptor: UserAgentInterceptor,
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(baseUrlInterceptor)
            .addInterceptor(authInterceptor)
            .authenticator(tokenAuthenticator)
            // Send "Sheaf Android/<version>" on every API call instead of
            // OkHttp's default "okhttp/<lib version>". The server records
            // User-Agent in the session row that powers the Trusted
            // Devices list, so without this the user's own device was
            // labelled "okhttp/4.12.0" — useless for telling sessions
            // apart from a browser session at a glance.
            .addInterceptor(userAgentInterceptor)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    // Request/response bodies carry bearer + refresh tokens
                    // (the /auth responses), watch-token activation codes,
                    // and member names. Logging them at BODY level in a
                    // release build would dump all of that into logcat,
                    // where adb / USB-debugging / a captured bug report can
                    // read it. Release logs nothing; debug keeps BODY for
                    // local network debugging but still redacts the
                    // credential-bearing headers so a shared debug log
                    // doesn't leak a live session.
                    level = if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BODY
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
                    redactHeader("Authorization")
                    redactHeader("Cookie")
                    redactHeader("Set-Cookie")
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
    ): ImageLoader {
        // Cloned from the API client so it keeps the debug SSL-trust config for
        // local dev servers. It also inherits AuthInterceptor, but that only
        // attaches credentials to the instance's own origins now, so an image
        // fetched from an external host carries none. (User-Agent and the
        // cookie jar are inherited from the clone, so we don't re-add them.)
        val imageClient = okHttpClient.newBuilder().build()
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
