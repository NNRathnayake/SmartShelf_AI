package com.example.smartshelfai

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query


data class PredictRequest(
    val store_nbr: Int,
    val family: String,
    val date: String,
    val onpromotion: Int
)

data class PredictResponse(
    val predicted_sales: Double
)


data class SalesHistoryPoint(val date: String, val sales: Double)
data class SalesHistoryResponse(val store_nbr: Int, val family: String, val data: List<SalesHistoryPoint>)

data class ForecastPoint(val date: String, val predicted_sales: Double)
data class ForecastResponse(val store_nbr: Int, val family: String, val forecast: List<ForecastPoint>)

interface ApiService {

    @POST("/predict")
    suspend fun predictSales(@Body request: PredictRequest): PredictResponse

    @GET("/sales-history")
    suspend fun getSalesHistory(
        @Query("store_nbr") storeNbr: Int,
        @Query("family")    family: String
    ): SalesHistoryResponse

    @GET("/forecast")
    suspend fun getForecast(
        @Query("store_nbr")    storeNbr: Int,
        @Query("family")       family: String,
        @Query("days")         days: Int,
        @Query("onpromotion")  onPromotion: Int
    ): ForecastResponse
}