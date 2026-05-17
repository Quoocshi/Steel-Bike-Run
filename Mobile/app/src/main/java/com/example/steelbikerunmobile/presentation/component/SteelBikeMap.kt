package com.example.steelbikerunmobile.presentation.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.steelbikerunmobile.data.repository.DemoMapData
import com.example.steelbikerunmobile.domain.model.LatLng
import com.example.steelbikerunmobile.domain.model.NearbyDriver
import com.example.steelbikerunmobile.presentation.theme.SteelBikeTheme

@Composable
fun SteelBikeMap(
    currentLocation: LatLng?,
    nearbyDrivers: List<NearbyDriver>,
    modifier: Modifier = Modifier
) {
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    Box(
        modifier = modifier.background(surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)

            nearbyDrivers.forEach { driver ->
                val point = Offset(
                    x = center.x + ((driver.location.longitude - 106.7009) * 18_000).toFloat(),
                    y = center.y - ((driver.location.latitude - 10.7769) * 18_000).toFloat()
                )
                drawCircle(color = tertiary, radius = 8.dp.toPx(), center = point)
                drawCircle(color = androidx.compose.ui.graphics.Color.White, radius = 3.dp.toPx(), center = point)
            }

            currentLocation?.let { location ->
                val point = Offset(
                    x = center.x + ((location.longitude - 106.7009) * 18_000).toFloat(),
                    y = center.y - ((location.latitude - 10.7769) * 18_000).toFloat()
                )
                drawCircle(color = primary, radius = 11.dp.toPx(), center = point)
                drawCircle(color = androidx.compose.ui.graphics.Color.White, radius = 4.dp.toPx(), center = point)
            }
        }
        if (currentLocation == null && nearbyDrivers.isEmpty()) {
            Text("Bản đồ SteelBike", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SteelBikeMapPreview() {
    SteelBikeTheme {
        SteelBikeMap(
            currentLocation = LatLng(10.7769, 106.7009),
            nearbyDrivers = DemoMapData.drivers,
            modifier = Modifier.height(320.dp)
        )
    }
}
