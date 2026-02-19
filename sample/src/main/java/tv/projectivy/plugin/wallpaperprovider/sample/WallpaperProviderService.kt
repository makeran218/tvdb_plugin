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

class WallpaperProviderService : Service() {

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
            val eventType = event?.eventType ?: -1
            Log.e("WallpaperService", "PROJECTIVY_LOG: getWallpapers | Event: $eventType")

            var forceRefresh = false

            // Handle Idle Mode changes
            if (event is Event.LauncherIdleModeChanged) {
                if (!event.isIdle) {
                    if (PreferencesManager.refreshOnIdleExit) {
                        forceRefresh = true
                    } else {
                        return getLastSavedWallpaper()
                    }
                } else {
                    return emptyList()
                }
            }

            // Time Guard Logic: Avoid fast flickering on service restarts
            val currentTime = System.currentTimeMillis()
            val lastUpdate = PreferencesManager.lastUpdateTimestamp // Ensure this exists in your PreferencesManager
            val oneMinuteInMillis = 60 * 1000

            // If it's a standard elapsed event (1) but not enough time has passed, return last wallpaper
            if (event is Event.TimeElapsed && !forceRefresh) {
                if (currentTime - lastUpdate < oneMinuteInMillis) {
                    Log.e("WallpaperService", "PROJECTIVY_LOG: Skipping API call - too soon (${(currentTime - lastUpdate) / 1000}s since last change)")
                    return getLastSavedWallpaper()
                }
            }

            if (event is Event.TimeElapsed || forceRefresh) {
                try {
                    // Get the URL from user input/preferences
                    var dynamicBaseUrl = PreferencesManager.serverUrl

                    // Safety check: Retrofit requires the URL to end with a /
                    if (!dynamicBaseUrl.endsWith("/")) {
                        dynamicBaseUrl += "/"
                    }

                    val apiService = Retrofit.Builder()
                        .baseUrl(dynamicBaseUrl) // Use the dynamic URL here
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()
                        .create(ApiService::class.java)

                    val response = apiService.getWallpaperStatus().execute()
                    if (response.isSuccessful) {
                        val wallpaperList: List<WallpaperStatus>? = response.body()

                        if (wallpaperList != null && wallpaperList.isNotEmpty()) {
                            // Pick random
                            val randomIndex = (Math.random() * wallpaperList.size).toInt()
                            val status = wallpaperList[randomIndex]

                            // Deep Link Logic
                            val targetApp = PreferencesManager.appTarget
                            val rawAction = status.actionUrl ?: ""
                            var finalAction: String? = null

                            if (rawAction.contains("_tmdb:")) {
                                val parts = rawAction.split("_tmdb:")
                                if (parts.size == 2) {
                                    val type = parts[0] // "movie" or "tv"
                                    val id = parts[1]

                                    finalAction = when (targetApp) {
                                        "Stremio" -> {
                                            val stremioType = if (type == "tv") "series" else "movie"
                                            "stremio:///detail/$stremioType/tmdb:$id"
                                        }
                                        "Kodi" -> {
                                            // 1. Map 'tv' to 'episode' and 'movie' to 'movie' for POV's playback mode
                                            val mediaType = if (type == "tv") "episode" else "movie"

                                            // 2. Build the playback URL using ONLY the ID
                                            // We add autoplay=false to ensure it shows the source list instead of just picking one
                                            var kodiUrl = "plugin://plugin.video.pov/?mode=playback.media&media_type=$mediaType&tmdb_id=$id&autoplay=false"

                                            // 3. For TV, POV REQUIRES a season and episode to start the scraper.
                                            // If your ID doesn't include them, we default to S1E1.
                                            if (type == "tv") {
                                                kodiUrl += "&season=1&episode=1"
                                            }

                                            // 4. Wrap in the explicit Intent for Projectivy
                                            "intent:$kodiUrl#Intent;action=android.intent.action.VIEW;package=org.xbmc.kodi;component=org.xbmc.kodi/.Main;end"
                                        }
                                        "Plex", "Emby" -> {
                                            // Placeholder: These usually require a web search or specific server item IDs
                                            // For now, we'll just log it
                                            null
                                        }
                                        else -> null
                                    }
                                }
                            }

                            Log.e("WallpaperService", "PROJECTIVY_LOG: Selected ${status.title} | Action: $finalAction")

                            // Save to Preferences
                            PreferencesManager.lastWallpaperUri = status.imageUrl
                            PreferencesManager.lastWallpaperAuthor = status.title ?: ""
                            PreferencesManager.lastActionUri = finalAction ?: "" // Store this so it persists
                            PreferencesManager.lastUpdateTimestamp = currentTime

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

            // Fallback to last known wallpaper if API fails or logic falls through
            return getLastSavedWallpaper()
        }

        // Helper function to keep code clean
        private fun getLastSavedWallpaper(): List<Wallpaper> {
            val lastUri = PreferencesManager.lastWallpaperUri
            return if (lastUri.isNotBlank()) {
                listOf(
                    Wallpaper(
                        uri = lastUri,
                        type = WallpaperType.IMAGE,
                        displayMode = WallpaperDisplayMode.CROP,
                        author = PreferencesManager.lastWallpaperAuthor,
                        actionUri = PreferencesManager.lastActionUri
                    )
                )
            } else {
                emptyList()
            }
        }

        override fun getPreferences(): String = PreferencesManager.export()
        override fun setPreferences(params: String) { PreferencesManager.import(params) }
    }
}