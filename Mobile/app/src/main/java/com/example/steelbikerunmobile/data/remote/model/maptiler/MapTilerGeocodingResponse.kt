package com.example.steelbikerunmobile.data.remote.model.maptiler

import com.google.gson.annotations.SerializedName

data class MapTilerGeocodingResponse(
    val type: String,
    val features: List<GeocodingFeature>
)

data class GeocodingFeature(
    val id: String,
    @SerializedName("place_name")
    val placeName: String,
    val text: String,
    val center: List<Double>?, // [longitude, latitude]
    val geometry: GeocodingGeometry
)

data class GeocodingGeometry(
    val type: String,
    val coordinates: List<Double> // [longitude, latitude]
)
