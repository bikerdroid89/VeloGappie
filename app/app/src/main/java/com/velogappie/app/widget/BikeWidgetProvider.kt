package com.velogappie.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.velogappie.app.MainActivity
import com.velogappie.app.R

class BikeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) updateWidget(context, manager, id)
    }

    companion object {
        private const val PREFS = "widget_state"
        private const val KEY_BATTERY = "battery"
        private const val KEY_BIKE_NAME = "bike_name"
        private const val KEY_SPEED = "speed"
        private const val KEY_ODOMETER = "odometer"
        private const val KEY_CONNECTED = "connected"

        fun pushState(context: Context, bikeName: String?, battery: Int?, speedKmh: Double?, odometer: Int? = null) {
            val connected = bikeName != null
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
                putBoolean(KEY_CONNECTED, connected)
                battery?.let { putInt(KEY_BATTERY, it) }
                bikeName?.let { putString(KEY_BIKE_NAME, it) }
                speedKmh?.let { putFloat(KEY_SPEED, it.toFloat()) }
                odometer?.let { putInt(KEY_ODOMETER, it) }
                apply()
            }
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BikeWidgetProvider::class.java))
            for (id in ids) updateWidget(context, manager, id)
        }

        fun pushDisconnected(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
                putBoolean(KEY_CONNECTED, false)
                apply()
            }
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BikeWidgetProvider::class.java))
            for (id in ids) updateWidget(context, manager, id)
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val connected = prefs.getBoolean(KEY_CONNECTED, false)
            val battery = prefs.getInt(KEY_BATTERY, -1)
            val bikeName = prefs.getString(KEY_BIKE_NAME, null)
            val speed = prefs.getFloat(KEY_SPEED, 0f)
            val odometer = prefs.getInt(KEY_ODOMETER, -1)

            val views = RemoteViews(context.packageName, R.layout.widget_bike)

            val intent = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, pending)

            if (connected && bikeName != null) {
                views.setTextViewText(R.id.widget_bike_name, bikeName.uppercase())
                views.setViewVisibility(R.id.widget_stats_row, View.VISIBLE)

                views.setImageViewResource(R.id.widget_battery_icon, R.drawable.ic_widget_battery)
                views.setTextViewText(
                    R.id.widget_battery,
                    if (battery >= 0) "$battery%" else "—"
                )

                views.setImageViewResource(R.id.widget_speed_icon, R.drawable.ic_widget_speed)
                views.setTextViewText(
                    R.id.widget_speed,
                    if (speed > 0.5f) "%.0f".format(speed) else "0"
                )

                views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_tap_to_open))
            } else if (battery >= 0 || odometer >= 0) {
                views.setTextViewText(R.id.widget_bike_name, context.getString(R.string.widget_no_bike))
                views.setViewVisibility(R.id.widget_stats_row, View.VISIBLE)

                views.setImageViewResource(R.id.widget_battery_icon, R.drawable.ic_widget_battery)
                views.setTextViewText(
                    R.id.widget_battery,
                    if (battery >= 0) "$battery%" else "—"
                )

                views.setImageViewResource(R.id.widget_speed_icon, R.drawable.ic_widget_odometer)
                views.setTextViewText(
                    R.id.widget_speed,
                    if (odometer >= 0) "$odometer" else "—"
                )

                views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_tap_to_open))
            } else {
                views.setTextViewText(R.id.widget_bike_name, context.getString(R.string.widget_no_bike))
                views.setViewVisibility(R.id.widget_stats_row, View.GONE)
                views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_tap_to_open))
            }

            manager.updateAppWidget(id, views)
        }
    }
}
