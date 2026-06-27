package com.velogappie.app.ride

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object GpxExporter {

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val fileNameFormat = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US)

    fun export(context: Context, ride: RideEntity, points: List<LocationPointEntity>) {
        val gpx = buildGpx(ride, points)
        val fileName = "velogappie-ride-${fileNameFormat.format(Date(ride.startTime))}.gpx"
        val file = File(context.cacheDir, fileName)
        file.writeText(gpx)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    private fun buildGpx(ride: RideEntity, points: List<LocationPointEntity>): String {
        val rideName = "VeloGappie ride ${fileNameFormat.format(Date(ride.startTime))}"
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<gpx version="1.1" creator="VeloGappie"""")
        sb.appendLine("""  xmlns="http://www.topografix.com/GPX/1/1">""")
        sb.appendLine("  <metadata>")
        sb.appendLine("    <name>$rideName</name>")
        sb.appendLine("    <time>${isoFormat.format(Date(ride.startTime))}</time>")
        sb.appendLine("  </metadata>")
        sb.appendLine("  <trk>")
        sb.appendLine("    <name>$rideName</name>")
        sb.appendLine("    <trkseg>")
        for (pt in points) {
            sb.append("      <trkpt lat=\"${pt.latitude}\" lon=\"${pt.longitude}\">")
            pt.altitude?.let { sb.append("<ele>$it</ele>") }
            sb.append("<time>${isoFormat.format(Date(pt.timestamp))}</time>")
            sb.appendLine("</trkpt>")
        }
        sb.appendLine("    </trkseg>")
        sb.appendLine("  </trk>")
        sb.appendLine("</gpx>")
        return sb.toString()
    }
}
