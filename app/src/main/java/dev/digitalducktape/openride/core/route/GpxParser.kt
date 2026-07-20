package dev.digitalducktape.openride.core.route

import android.util.Xml
import java.io.InputStream
import org.xmlpull.v1.XmlPullParser

/**
 * Parses a GPX 1.1 file into a [Route] (PRD #21/T21) using Android's built-in [XmlPullParser]
 * (via [Xml.newPullParser]) — no XML library dependency, same approach as
 * [dev.digitalducktape.openride.core.content.AtomFeedParser].
 *
 * GPX can carry geometry three ways; this prefers a recorded **track** (`<trkpt>`, the common
 * case for a ride export), falling back to a **route** (`<rtept>`) and then loose **waypoints**
 * (`<wpt>`) if there's no track. `lat`/`lon` are attributes; `<ele>` (metres) is an optional
 * child — a point without one reads as elevation 0 (see [RoutePoint]).
 *
 * ```
 * <gpx>
 *   <metadata><name>My climb</name></metadata>
 *   <trk>
 *     <name>My climb</name>
 *     <trkseg>
 *       <trkpt lat="37.80" lon="-122.42"><ele>10.0</ele></trkpt>
 *       ...
 *     </trkseg>
 *   </trk>
 * </gpx>
 * ```
 *
 * As with the namespace-aware Atom parser, elements are matched on their bare local name
 * (`trkpt`, not any prefixed form).
 */
class GpxParser {

    fun parse(input: InputStream): Route {
        val parser = Xml.newPullParser()
        parser.setInput(input, null)

        val trackPoints = mutableListOf<RoutePoint>()
        val routePoints = mutableListOf<RoutePoint>()
        val wayPoints = mutableListOf<RoutePoint>()

        var routeName: String? = null

        // The point currently being built (between a *pt start tag and its end tag), plus which
        // list it belongs to. `null` when not inside a point element.
        var currentLat: Double? = null
        var currentLon: Double? = null
        var currentEle = 0.0
        var currentTarget: MutableList<RoutePoint>? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    TAG_TRKPT, TAG_RTEPT, TAG_WPT -> {
                        currentLat = parser.getAttributeValue(null, ATTR_LAT)?.toDoubleOrNull()
                        currentLon = parser.getAttributeValue(null, ATTR_LON)?.toDoubleOrNull()
                        currentEle = 0.0
                        currentTarget = when (parser.name) {
                            TAG_TRKPT -> trackPoints
                            TAG_RTEPT -> routePoints
                            else -> wayPoints
                        }
                    }
                    TAG_ELE -> if (currentTarget != null) {
                        currentEle = parser.nextText().trim().toDoubleOrNull() ?: 0.0
                    }
                    TAG_NAME -> if (currentTarget == null && routeName == null) {
                        // First name outside any point (metadata/trk/rte) names the route.
                        routeName = parser.nextText().trim().ifEmpty { null }
                    }
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    TAG_TRKPT, TAG_RTEPT, TAG_WPT -> {
                        val lat = currentLat
                        val lon = currentLon
                        if (lat != null && lon != null) {
                            currentTarget?.add(RoutePoint(lat = lat, lon = lon, elevationMeters = currentEle))
                        }
                        currentLat = null
                        currentLon = null
                        currentTarget = null
                    }
                }
            }
            eventType = parser.next()
        }

        val points = when {
            trackPoints.isNotEmpty() -> trackPoints
            routePoints.isNotEmpty() -> routePoints
            else -> wayPoints
        }
        return Route.fromPoints(name = routeName, points = points)
    }

    private companion object {
        const val TAG_TRKPT = "trkpt"
        const val TAG_RTEPT = "rtept"
        const val TAG_WPT = "wpt"
        const val TAG_ELE = "ele"
        const val TAG_NAME = "name"
        const val ATTR_LAT = "lat"
        const val ATTR_LON = "lon"
    }
}
