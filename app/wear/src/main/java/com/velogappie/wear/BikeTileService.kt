package com.velogappie.wear

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

private const val RESOURCES_VERSION = "1"

class BikeTileService : TileService() {

    override fun onTileRequest(request: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val state = BikeStateRepository.state.value

        val batteryText = state.batteryPercent?.let { "$it%" } ?: "—"
        val speedText = state.speedKmh?.let { "%.0f km/h".format(it) } ?: "—"
        val statusText = if (state.connected) "Connected" else "Not connected"

        val layout = LayoutElementBuilders.Column.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setId("open_app")
                            .setOnClick(
                                ActionBuilders.LaunchAction.Builder()
                                    .setAndroidActivity(
                                        ActionBuilders.AndroidActivity.Builder()
                                            .setPackageName(packageName)
                                            .setClassName("com.velogappie.wear.MainActivity")
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setAll(DimensionBuilders.dp(16f))
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Spacer.Builder()
                    .setHeight(DimensionBuilders.dp(24f))
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(batteryText)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(DimensionBuilders.sp(36f))
                            .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                            .setColor(ColorBuilders.argb(batteryColor(state.batteryPercent)))
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Spacer.Builder()
                    .setHeight(DimensionBuilders.dp(4f))
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(speedText)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(DimensionBuilders.sp(20f))
                            .setColor(ColorBuilders.argb(0xFFE0E0E0.toInt()))
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Spacer.Builder()
                    .setHeight(DimensionBuilders.dp(8f))
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(statusText)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(DimensionBuilders.sp(12f))
                            .setColor(
                                ColorBuilders.argb(
                                    if (state.connected) 0xFF4CAF50.toInt() else 0xFF607D8B.toInt()
                                )
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        val entry = TimelineBuilders.TimelineEntry.Builder()
            .setLayout(
                LayoutElementBuilders.Layout.Builder()
                    .setRoot(layout)
                    .build()
            )
            .build()

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(10_000)
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(entry)
                    .build()
            )
            .build()

        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(request: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        return Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .build()
        )
    }

    companion object {
        fun requestUpdate(context: Context) {
            try {
                TileService.getUpdater(context).requestUpdate(BikeTileService::class.java)
            } catch (_: Exception) { }
        }
    }

    private fun batteryColor(pct: Int?): Int = when {
        pct == null -> 0xFF607D8B.toInt()
        pct > 50 -> 0xFF4CAF50.toInt()
        pct > 20 -> 0xFFFFB300.toInt()
        else -> 0xFFEF5350.toInt()
    }
}
