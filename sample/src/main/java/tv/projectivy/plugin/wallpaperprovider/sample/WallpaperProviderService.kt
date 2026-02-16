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
import tv.projectivy.plugin.wallpaperprovider.sample.ApiService

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

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    private val binder = object : IWallpaperProviderService.Stub() {
        override fun getWallpapers(event: Event?): List<Wallpaper> {
            Log.e("WallpaperService", "PROJECTIVY_LOG: getWallpapers | Event: ${event?.eventType}")

            var forceRefresh = false

            // Logic for handling Idle mode transitions
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

            // Execute API Call
            if (event is Event.TimeElapsed || forceRefresh) {
                try {
                    // 1. Hardcoded Base URL
                    val fixedBaseUrl = "http://192.168.2.50/"

                    val apiService = Retrofit.Builder()
                        .baseUrl(fixedBaseUrl)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()
                        .create(ApiService::class.java)

                    // 2. Simple call without parameters
                    val response = apiService.getWallpaperStatus().execute()

                    if (response.isSuccessful) {
                        val status = response.body()
                        if (status != null) {
                            var action = status.actionUrl

                            // Handle Jellyfin deep linking if necessary
                            if (!action.isNullOrBlank() && action.startsWith("jellyfin://items/")) {
                                val id = action.substringAfter("jellyfin://items/")
                                val preferredClient = PreferencesManager.preferredClient
                                val newAction = ClientManager.getClientActionUri(this@WallpaperProviderService, preferredClient, id)
                                if (newAction != null) action = newAction
                            }

                            // Save for persistence
                            PreferencesManager.lastWallpaperUri = status.imageUrl
                            PreferencesManager.lastWallpaperAuthor = status.title ?: ""

                            return listOf(
                                Wallpaper(
                                    uri = status.imageUrl,
                                    type = WallpaperType.IMAGE,
                                    displayMode = WallpaperDisplayMode.CROP,
                                    author = status.title,
                                    actionUri = action
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