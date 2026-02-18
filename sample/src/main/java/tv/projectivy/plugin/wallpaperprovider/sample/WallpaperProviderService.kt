package tv.projectivy.plugin.wallpaperprovider.sample

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import tv.projectivy.plugin.wallpaperprovider.api.Event
import tv.projectivy.plugin.wallpaperprovider.api.IWallpaperProviderService
import tv.projectivy.plugin.wallpaperprovider.api.Wallpaper
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperDisplayMode
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperType

class WallpaperProviderService: Service() {

    override fun onCreate() {
        super.onCreate()
        Log.e("WallpaperService", "PROJECTIVY_LOG: Service onCreate")
        PreferencesManager.init(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        Log.e("WallpaperService", "PROJECTIVY_LOG: Service onBind")
        return binder
    }

    private val binder = object : IWallpaperProviderService.Stub() {
        override fun getWallpapers(event: Event?): List<Wallpaper> {
            Log.e("WallpaperService", "PROJECTIVY_LOG: getWallpapers | Event: ${event?.eventType}")

            var forceRefresh = false

            if (event is Event.LauncherIdleModeChanged) {
                if (!event.isIdle) {
                    if (PreferencesManager.refreshOnIdleExit) {
                        forceRefresh = true
                    } else {
                        val lastUri = PreferencesManager.lastWallpaperUri
                        if (lastUri.isNotBlank()) {
                            return listOf(Wallpaper(uri = lastUri, type = WallpaperType.IMAGE, displayMode = WallpaperDisplayMode.CROP, author = PreferencesManager.lastWallpaperAuthor, actionUri = null))
                        }
                        return emptyList()
                    }
                } else {
                    return emptyList()
                }
            }

            if (event is Event.TimeElapsed || forceRefresh) {
                try {
                    val fixedBaseUrl = "https://makeran218.github.io/projectivity-background-source/"

                    val apiService = Retrofit.Builder()
                        .baseUrl(fixedBaseUrl)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()
                        .create(ApiService::class.java)

                    val response = apiService.getWallpaperStatus().execute()

                    if (response.isSuccessful) {
                        val status = response.body()
                        if (status != null) {
                            val rawAction = status.actionUrl ?: ""
                            var finalAction: String? = null

                            // Format: "movies_tmdb:967941" -> "stremio:///detail/movie/tmdb:967941/tmdb:967941"
                            if (rawAction.contains("_tmdb:")) {
                                val parts = rawAction.split("_tmdb:")
                                if (parts.size == 2) {
                                    val typeRaw = parts[0] // "movies" or "series"
                                    val id = parts[1]      // "967941"

                                    val stremioType = when (typeRaw) {
                                        "movies" -> "movie"
                                        "series" -> "series"
                                        else -> "movie" // fallback
                                    }

                                    // Constructing the triple-slash URI with duplicated ID for Android TV compatibility
                                    finalAction = "stremio:///detail/$stremioType/tmdb:$id/tmdb:$id"
                                }
                            } else {
                                finalAction = rawAction // Fallback to raw if format differs
                            }

                            Log.e("WallpaperService", "PROJECTIVY_LOG: Action Mapped: $finalAction")

                            PreferencesManager.lastWallpaperUri = status.imageUrl
                            PreferencesManager.lastWallpaperAuthor = status.title ?: ""

                            return listOf(
                                Wallpaper(
                                    uri = status.imageUrl,
                                    type = WallpaperType.IMAGE,
                                    displayMode = WallpaperDisplayMode.CROP,
                                    author = status.title,
                                    actionUri = finalAction
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WallpaperService", "PROJECTIVY_LOG: API Error", e)
                }
            }
            return emptyList()
        }

        override fun getPreferences(): String = PreferencesManager.export()
        override fun setPreferences(params: String) { PreferencesManager.import(params) }
    }
}