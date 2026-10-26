package dev.kevin.trail.ui.map

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.kevin.trail.R
import dev.kevin.trail.data.db.PointEntity
import dev.kevin.trail.data.db.VisitEntity
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Composable
fun RouteMap(
    points: List<PointEntity>,
    visits: List<VisitEntity>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val map = remember {
        MapView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setTilesScaledToDpi(true)
            controller.setZoom(15.0)
        }
    }
    DisposableEffect(lifecycle, map) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            map.onDetach()
        }
    }
    AndroidView(
        factory = { map },
        modifier = modifier,
        update = { view ->
            view.overlays.clear()
            if (points.isNotEmpty()) {
                val line = Polyline().apply {
                    setPoints(points.map { GeoPoint(it.latitude, it.longitude) })
                    outlinePaint.color = 0xFF1B4332.toInt()
                    outlinePaint.strokeWidth = 8f
                }
                view.overlays.add(line)
            }
            val icon = ContextCompat.getDrawable(context, R.drawable.ic_stop)
            visits.forEach { visit ->
                view.overlays.add(
                    Marker(view).apply {
                        position = GeoPoint(visit.latitude, visit.longitude)
                        title = visit.arrivalTst.toString()
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        this.icon = icon
                    },
                )
            }
            val signature = points.lastOrNull()?.tst to visits.size
            if (view.tag != signature && points.isNotEmpty()) {
                view.tag = signature
                view.post {
                    if (points.size == 1) {
                        view.controller.setZoom(16.0)
                        view.controller.setCenter(GeoPoint(points.first().latitude, points.first().longitude))
                    } else {
                        val box = BoundingBox.fromGeoPoints(points.map { GeoPoint(it.latitude, it.longitude) })
                        view.zoomToBoundingBox(box, true, 64)
                    }
                }
            }
            view.invalidate()
        },
    )
}
