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

            // Logic for handling Idle mode transitions
            if (event is Event.LauncherIdleModeChanged) {
                if (!event.isIdle) {
                    if (PreferencesManager.refreshOnIdleExit) {
                        Log.e("WallpaperService", "PROJECTIVY_LOG: Refreshing on idle exit")
                        forceRefresh = true
                    } else {
                        val lastUri = PreferencesManager.lastWallpaperUri
                        val lastAuthor = PreferencesManager.lastWallpaperAuthor
                        if (lastUri.isNotBlank()) {
                            return listOf(
                                Wallpaper(
                                    uri = lastUri,
                                    type = WallpaperType.IMAGE,
                                    displayMode = WallpaperDisplayMode.CROP,
                                    author = lastAuthor.ifBlank { null },
                                    actionUri = null
                                )
                            )
                        }
                        return emptyList()
                    }
                } else {
                    return emptyList()
                }
            }

            // Execute API Call for TimeElapsed or forced Refresh
            if (event is Event.TimeElapsed || forceRefresh) {
                try {
                    // We use the root IP as the base URL
                    val fixedBaseUrl = "http://192.168.2.50/"

                    val apiService = Retrofit.Builder()
                        .baseUrl(fixedBaseUrl)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()
                        .create(ApiService::class.java)

                    // Call the fixed endpoint (tvdb) defined in ApiService
                    val response = apiService.getWallpaperStatus().execute()

                    if (response.isSuccessful) {
                        val status = response.body()
                        if (status != null) {
                            Log.e("WallpaperService", "PROJECTIVY_LOG: API Success: ${status.imageUrl}")

                            // Use the actionUrl and title directly from your JSON
                            val action = status.actionUrl
                            val displayTitle = status.title ?: ""

                            // Save for persistence so it shows up when offline or restarting
                            PreferencesManager.lastWallpaperUri = status.imageUrl
                            PreferencesManager.lastWallpaperAuthor = displayTitle

                            return listOf(
                                Wallpaper(
                                    uri = status.imageUrl,
                                    type = WallpaperType.IMAGE,
                                    displayMode = WallpaperDisplayMode.CROP,
                                    author = displayTitle,
                                    actionUri = action
                                )
                            )
                        }
                    } else {
                        Log.e("WallpaperService", "PROJECTIVY_LOG: API Response Unsuccessful: ${response.code()}")
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