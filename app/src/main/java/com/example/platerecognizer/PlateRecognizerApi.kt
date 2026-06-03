package com.example.platerecognizer

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface PlateRecognizerApi {
    @Multipart
    @POST("v1/plate-reader/")
    suspend fun recognizePlate(
        @Header("Authorization") token: String,
        @Part image: MultipartBody.Part
    ): Response<PlateResponse>
}

data class PlateResponse(
    val results: List<PlateResult>
)

data class PlateResult(
    val plate: String,
    val score: Double
)
