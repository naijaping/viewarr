package io.github.petebutty.viewarr.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.petebutty.viewarr.data.net.Reach
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        // (No overall call timeout here: this client also fetches update APKs, M3U playlists, Xtream lists and the guide,
        // which can rightly take minutes. HttpJson puts a 30 s one on its own calls.)
        // Home or away: a server's away address when its home one can't be reached.
        .addInterceptor(Reach.Interceptor)
        .build()

    // The guide's databases (one per Live TV source): see GuideDatabases.
}
