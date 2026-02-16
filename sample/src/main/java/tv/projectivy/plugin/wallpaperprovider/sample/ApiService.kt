package tv.projectivy.plugin.wallpaperprovider.sample

import retrofit2.Call
import retrofit2.http.GET

// 1. Updated Data Class to match your JSON
data class WallpaperStatus(
    val imageUrl: String,
    val actionUrl: String?,
    val title: String?
)

interface ApiService {
    // 2. Updated to your fixed endpoint /tvdb/api
    // Removed all @Query parameters as they are no longer needed
    @GET("tvdb/api.json")
    fun getWallpaperStatus(): Call<WallpaperStatus>

    // You can keep these if you still need them,
    // but based on your request, they are likely no longer used.
    @GET("/api/layouts/list")
    fun getLayouts(): Call<List<String>>

    @GET("/api/genres/list")
    fun getGenres(): Call<List<String>>

    @GET("/api/ages/list")
    fun getAgeRatings(): Call<List<String>>

    @GET("/api/year/list")
    fun getYears(): Call<List<String>>
}