package com.velogappie.app.ui

import android.view.Gravity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.velogappie.app.ride.LocationPointEntity
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

@Composable
fun RouteMapCanvas(
    points: List<LocationPointEntity>,
    modifier: Modifier = Modifier,
    routeColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (points.size < 2) return

    val context = LocalContext.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val routeArgb = routeColor.toArgb()
    val startArgb = MaterialTheme.colorScheme.tertiary.toArgb()
    val endArgb = MaterialTheme.colorScheme.error.toArgb()

    val styleUrl = if (isDark)
        "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
    else
        "https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json"

    val bounds = remember(points) {
        LatLngBounds.Builder().apply {
            points.forEach { include(LatLng(it.latitude, it.longitude)) }
        }.build()
    }

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context)
    }

    DisposableEffect(mapView) {
        mapView.onCreate(null)
        mapView.onStart()
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = {
            mapView.apply {
                getMapAsync { map ->
                    map.uiSettings.apply {
                        isRotateGesturesEnabled = false
                        isTiltGesturesEnabled = false
                        isLogoEnabled = false
                        setAttributionGravity(Gravity.BOTTOM or Gravity.END)
                    }
                    map.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
                        val coords = points.map { p ->
                            Point.fromLngLat(p.longitude, p.latitude)
                        }

                        style.addSource(GeoJsonSource("route", Feature.fromGeometry(LineString.fromLngLats(coords))))
                        style.addLayer(LineLayer("route-line", "route").withProperties(
                            PropertyFactory.lineColor(routeArgb),
                            PropertyFactory.lineWidth(4f),
                            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                        ))

                        style.addSource(GeoJsonSource("start-pt", Feature.fromGeometry(coords.first())))
                        style.addLayer(CircleLayer("start-circle", "start-pt").withProperties(
                            PropertyFactory.circleRadius(6f),
                            PropertyFactory.circleColor(startArgb),
                            PropertyFactory.circleStrokeWidth(2f),
                            PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE)
                        ))

                        style.addSource(GeoJsonSource("end-pt", Feature.fromGeometry(coords.last())))
                        style.addLayer(CircleLayer("end-circle", "end-pt").withProperties(
                            PropertyFactory.circleRadius(6f),
                            PropertyFactory.circleColor(endArgb),
                            PropertyFactory.circleStrokeWidth(2f),
                            PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE)
                        ))

                        map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 50))
                    }
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(12.dp))
    )
}
