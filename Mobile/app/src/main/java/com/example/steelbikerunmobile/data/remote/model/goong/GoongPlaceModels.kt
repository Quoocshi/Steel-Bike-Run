package com.example.steelbikerunmobile.data.remote.model.goong

data class GoongAutoCompleteResponse(
    val predictions: List<GoongPrediction>,
    val status: String
)

data class GoongPrediction(
    val description: String,
    val place_id: String
)

data class GoongPlaceDetailResponse(
    val result: GoongPlaceDetail,
    val status: String
)

data class GoongPlaceDetail(
    val place_id: String,
    val name: String,
    val formatted_address: String,
    val geometry: GoongGeometry
)

data class GoongGeometry(
    val location: GoongLocation
)

data class GoongLocation(
    val lat: Double,
    val lng: Double
)
