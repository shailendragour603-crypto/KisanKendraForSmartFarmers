package shop.sabjiwala.kisankendra.api

import shop.sabjiwala.kisankendra.model.MandiResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface MandiApiService {
    @GET("resource/9ef84268-d588-465a-a308-a864a43d0070")
    suspend fun getMandiPrices(
        @Query("api-key") apiKey: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 10000
    ): MandiResponse
}