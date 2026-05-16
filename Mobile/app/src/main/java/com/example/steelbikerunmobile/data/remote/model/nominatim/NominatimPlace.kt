package com.example.steelbikerunmobile.data.remote.model.nominatim

data class NominatimPlace(
    val place_id: Long,
    val lat: String,
    val lon: String,
    val display_name: String,
    val name: String
)
