package com.aprireader.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.aprireader.app.data.prefs.LocaleStore
import okhttp3.OkHttpClient
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Точка старта приложения с инициализацией локали и единого оптимизированного
 * загрузчика изображений (Coil ImageLoader) для всех источников обложек.
 */
class ApriApplication : Application(), ImageLoaderFactory {

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        LocaleStore.read(this)?.takeIf { it.isNotBlank() }?.let { tag ->
            runCatching {
                val locale = Locale.forLanguageTag(tag)
                Locale.setDefault(locale)
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient {
                OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .retryOnConnectionFailure(true)
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36 ApriReader/1.0")
                            .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                            .build()
                        chain.proceed(request)
                    }
                    .build()
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(80L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
    }
}
