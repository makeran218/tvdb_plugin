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
                    val fixedBaseUrl = "http://192.168.2.50/"

                    val apiService = Retrofit.Builder()
                        .baseUrl(fixedBaseUrl)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()
                        .create(ApiService::class.java)

                    val response = apiService.getWallpaperStatus().execute()

                    if (response.isSuccessful) {
                        val status = response.body()
                        if (status != null) {
                            // KEEPING JELLYFIN ACTION LOGIC
                            // If the URL is jellyfin://items/123, it stays that way.
                            // Projectivy Launcher will handle the intent if the app is installed.
                            var action = status.actionUrl

                            if (!action.isNullOrBlank() && action.startsWith("jellyfin://items/")) {
                                val id = action.substringAfter("jellyfin://items/")
                                action = "embyatv://tv.emby.embyatv/play/$id"
                            }

                            Log.e("WallpaperService", "PROJECTIVY_LOG: API Success: ${status.imageUrl} | Action: $action")

                            PreferencesManager.lastWallpaperUri = status.imageUrl
                            PreferencesManager.lastWallpaperAuthor = status.title ?: ""

                            return listOf(
                                Wallpaper(
                                    uri = status.imageUrl,
                                    type = WallpaperType.IMAGE,
                                    displayMode = WallpaperDisplayMode.CROP,
                                    author = status.title,
                                    actionUri = action // This passes the Jellyfin link to the launcher
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