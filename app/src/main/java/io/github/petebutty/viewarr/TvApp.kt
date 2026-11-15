package io.github.petebutty.viewarr

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TvApp : Application(), ImageLoaderFactory {
    /** Posters and backdrops on a 2 GB box: 16-bit bitmaps where possible, a capped memory cache, no crossfade. */
    override fun newImageLoader(): ImageLoader {
        val am = getSystemService(ACTIVITY_SERVICE) as? android.app.ActivityManager
        io.github.petebutty.viewarr.data.net.SmallerImages.weak = am?.isLowRamDevice == true
        return buildLoader()
    }

    /**
     * Gone to the background (the Home button, another app): let go of what can be fetched again, so Android keeps
     * Viewarr when memory is short instead of killing it, and it opens where you left it rather than starting again
     * (Peter, 2026-11-15: on the 2 GB Shield it was killed two seconds after Projectivy came back). Pictures are kept
     * on disk, so they come straight back.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            runCatching { coil.Coil.imageLoader(this).memoryCache?.clear() }
            android.util.Log.i("TvApp", "in the background (trim $level): picture memory let go")
        }
    }

    private fun buildLoader(): ImageLoader = ImageLoader.Builder(this)
        .allowRgb565(true)
        .crossfade(false)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.18).build() }
        .respectCacheHeaders(false)
        // Pictures at the size they're shown (smaller still on weak boxes): smaller downloads and less memory.
        .components { add(io.github.petebutty.viewarr.data.net.SmallerImages) }
        // Posters and logos from a home server: its away address when away.
        .okHttpClient { okhttp3.OkHttpClient.Builder().addInterceptor(io.github.petebutty.viewarr.data.net.Reach.Interceptor).build() }
        .build()
}
