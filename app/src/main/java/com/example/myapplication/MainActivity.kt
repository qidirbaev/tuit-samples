@file:Suppress("DEPRECATION")
package com.example.myapplication

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import java.util.*

// App Constants
private const val PREFS_NAME = "AppPreferences"
private const val FIRST_LAUNCH_KEY = "isFirstLaunch"
private const val FAVORITE_LOCATIONS_KEY = "favoriteLocations"
private const val REQUEST_LOCATION_PERMISSION = 1

// Theme Constants
private val GradientBg = listOf(Color(0xFFF5F7FA), Color(0xFFE4E8F0))
private val WelcomeGradient = listOf(Color(0xFF2E3B4E), Color(0xFF1A2639))
private val Primary = Color(0xFF2C3E50)
private val Secondary = Color(0xFF313029)
private val Accent = Color(0xFFB8C5D9)

// Night mode map style
private const val NIGHT_MODE_STYLE = """[{"featureType":"all","elementType":"geometry","stylers":[{"color":"#242f3e"}]},{"featureType":"all","elementType":"labels.text.stroke","stylers":[{"lightness":-80}]},{"featureType":"administrative","elementType":"labels.text.fill","stylers":[{"color":"#746855"}]},{"featureType":"administrative.locality","elementType":"labels.text.fill","stylers":[{"color":"#d59563"}]},{"featureType":"poi","elementType":"labels.text.fill","stylers":[{"color":"#d59563"}]},{"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"#263c3f"}]},{"featureType":"poi.park","elementType":"labels.text.fill","stylers":[{"color":"#6b9a76"}]},{"featureType":"road","elementType":"geometry.fill","stylers":[{"color":"#2b3544"}]},{"featureType":"road","elementType":"labels.text.fill","stylers":[{"color":"#9ca5b3"}]},{"featureType":"road.arterial","elementType":"geometry.fill","stylers":[{"color":"#38414e"}]},{"featureType":"road.arterial","elementType":"geometry.stroke","stylers":[{"color":"#212a37"}]},{"featureType":"road.highway","elementType":"geometry.fill","stylers":[{"color":"#746855"}]},{"featureType":"road.highway","elementType":"geometry.stroke","stylers":[{"color":"#1f2835"}]},{"featureType":"road.highway","elementType":"labels.text.fill","stylers":[{"color":"#f3d19c"}]},{"featureType":"road.local","elementType":"geometry.fill","stylers":[{"color":"#38414e"}]},{"featureType":"road.local","elementType":"geometry.stroke","stylers":[{"color":"#212a37"}]},{"featureType":"transit","elementType":"geometry","stylers":[{"color":"#2f3948"}]},{"featureType":"transit.station","elementType":"labels.text.fill","stylers":[{"color":"#d59563"}]},{"featureType":"water","elementType":"geometry","stylers":[{"color":"#17263c"}]},{"featureType":"water","elementType":"labels.text.fill","stylers":[{"color":"#515c6d"}]},{"featureType":"water","elementType":"labels.text.stroke","stylers":[{"lightness":-20}]}]"""

// Quick access locations
private val QuickLocations = listOf(
    "\uD83C\uDFEB" to LatLng(41.34114558450147, 69.28677201284974),
    "\uD83C\uDDEC\uD83C\uDDE7" to LatLng(51.5074, -0.1278),
    "\uD83D\uDDFC" to LatLng(35.6762, 139.6503),
    "\uD83C\uDFC4" to LatLng(-33.8688, 151.2093)
)

class MainActivity : ComponentActivity() {
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isFirstLaunch = sharedPreferences.getBoolean(FIRST_LAUNCH_KEY, true)

        setContent {
            if (isFirstLaunch) {
                WelcomeScreen {
                    sharedPreferences.edit().putBoolean(FIRST_LAUNCH_KEY, false).apply()
                    setContent { LocationApp(sharedPreferences) }
                }
            } else {
                LocationApp(sharedPreferences)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSION &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setContent { LocationApp(sharedPreferences) }
        }
    }
}

@Composable
fun LocationApp(prefs: SharedPreferences) {
    var screen by remember { mutableStateOf("input") }
    var target by remember { mutableStateOf(LatLng(0.0, 0.0)) }
    var favorites by remember { mutableStateOf(loadFavorites(prefs)) }
    var mapMode by remember { mutableStateOf("standard") }

    when (screen) {
        "input" -> LocationInputScreen(
            onLocationSelected = { location ->
                target = location
                screen = "map"
            },
            onViewFavorites = { screen = "favorites" },
            favorites = favorites
        )
        "map" -> MapScreen(
            target = target,
            mapMode = mapMode,
            onBack = { screen = "input" },
            onChangeMapMode = { newMode -> mapMode = newMode },
            onAddFavorite = { name, location ->
                favorites = favorites.toMutableMap().apply { put(name, location) }
                saveFavorites(prefs, favorites)
            }
        )
        "favorites" -> FavoritesScreen(
            favorites = favorites,
            onBack = { screen = "input" },
            onSelect = { location ->
                target = location
                screen = "map"
            },
            onRemove = { name ->
                favorites = favorites.toMutableMap().apply { remove(name) }
                saveFavorites(prefs, favorites)
            }
        )
    }
}

@Composable
fun WelcomeScreen(onContinue: () -> Unit) {
    var animateIn by remember { mutableStateOf(false) }
    val pulse by rememberInfiniteTransition().animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        )
    )

    LaunchedEffect(true) {
        animateIn = true
        delay(500)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = WelcomeGradient)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            AnimatedVisibility(
                visible = animateIn,
                enter = scaleIn(spring(dampingRatio = 0.6f)) + fadeIn()
            ) {
                Text(
                    text = "First individual work",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            AnimatedVisibility(
                visible = animateIn,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn()
            ) {
                Text(
                    text = "Mobile development",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = Accent,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedVisibility(
                visible = animateIn,
                enter = scaleIn() + fadeIn(initialAlpha = 0.3f)
            ) {
                Button(
                    onClick = onContinue,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(24.dp),
                    elevation = ButtonDefaults.buttonElevation(4.dp),
                    modifier = Modifier
                        .padding(16.dp)
                        .width(200.dp)
                        .height(56.dp)
                        .scale(pulse)
                ) {
                    Text("Go on \uD83D\uDE80", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationInputScreen(
    onLocationSelected: (LatLng) -> Unit,
    onViewFavorites: () -> Unit,
    favorites: Map<String, LatLng>
) {
    var lat by remember { mutableStateOf(TextFieldValue("")) }
    var lng by remember { mutableStateOf(TextFieldValue("")) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var selectedQuick by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = GradientBg))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())  // Make content scrollable
                .imePadding()  // This pushes up content when keyboard appears
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Where would you like to go?",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Primary,
                modifier = Modifier
                    .padding(top = 12.dp)  // Additional top padding
                    .padding(vertical = 24.dp)  // Original padding
            )

            // Coordinates card
            Card(
                modifier = Modifier.padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Enter Coordinates",
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )

                    OutlinedTextField(
                        value = lat.text,
                        onValueChange = {
                            lat = TextFieldValue(it)
                            selectedQuick = null
                        },
                        label = { Text("Latitude") },
                        isError = errorMsg != null,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )

                    OutlinedTextField(
                        value = lng.text,
                        onValueChange = {
                            lng = TextFieldValue(it)
                            selectedQuick = null
                        },
                        label = { Text("Longitude") },
                        isError = errorMsg != null,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )

                    errorMsg?.let { Text(it, color = Color.Red) }
                }
            }

            // Quick locations card
            Card(
                modifier = Modifier.padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Quick Locations",
                        fontWeight = FontWeight.Bold,
                        color = Primary,
                        // margin bottom
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        QuickLocations.forEach { (name, latLng) ->
                            val isSelected = selectedQuick == name
                            val bgColor by animateColorAsState(
                                if (isSelected) Primary else Color(0xFFE4E8F0)
                            )
                            val scale by animateFloatAsState(
                                if (isSelected) 1.1f else 1f,
                                spring(dampingRatio = 0.4f)
                            )

                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .scale(scale)
                                    .background(bgColor)
                                    .clickable {
                                        selectedQuick = name
                                        lat = TextFieldValue(latLng.latitude.toString())
                                        lng = TextFieldValue(latLng.longitude.toString())
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = name,
                                    color = if (isSelected) Color.White else Primary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // Favorites button
            if (favorites.isNotEmpty()) {
                Button(
                    onClick = onViewFavorites,
                    colors = ButtonDefaults.buttonColors(containerColor = Secondary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View Favorite Locations (${favorites.size})")
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Explore button
            Button(
                onClick = {
                    try {
                        val latitude = lat.text.toDouble()
                        val longitude = lng.text.toDouble()

                        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
                            errorMsg = "Invalid coordinate ranges"
                            return@Button
                        }

                        errorMsg = null
                        onLocationSelected(LatLng(latitude, longitude))
                    } catch (e: NumberFormatException) {
                        errorMsg = "Invalid coordinate format"
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Secondary),
                shape = RoundedCornerShape(24.dp),
                elevation = ButtonDefaults.buttonElevation(6.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Explore Location", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun MapScreen(
    target: LatLng,
    mapMode: String,
    onBack: () -> Unit,
    onChangeMapMode: (String) -> Unit,
    onAddFavorite: (String, LatLng) -> Unit
) {
    val cameraPosition = rememberCameraPositionState {
        // Start with a zoomed-out view
        position = CameraPosition.fromLatLngZoom(target, 2f)  // Start zoomed out
    }
    var locationName by remember { mutableStateOf("Loading...") }
    var showInfo by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var favoriteName by remember { mutableStateOf("") }
    val context = LocalContext.current

    // Animation for zooming in to target location
    LaunchedEffect(target) {
        // Fetch location name
        locationName = getLocationName(context, target.latitude, target.longitude)

        // Animate camera to zoom in to the location
        // First, ensure we're looking at the right place but zoomed out
        cameraPosition.animate(
            update = CameraUpdateFactory.newCameraPosition(
                CameraPosition.fromLatLngZoom(target, 2f)
            ),
            durationMs = 10  // Quick adjustment
        )

        // Small delay for effect
        delay(300)

        // Then zoom in smoothly
        cameraPosition.animate(
            update = CameraUpdateFactory.newCameraPosition(
                CameraPosition.fromLatLngZoom(target, 15f)
            ),
            durationMs = 1500  // Slower zoom for dramatic effect
        )
    }

    // Save location dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Location") },
            text = {
                Column {
                    Text("Enter a name for this location:")
                    OutlinedTextField(
                        value = favoriteName,
                        onValueChange = { favoriteName = it },
                        label = { Text("Location Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (favoriteName.isNotEmpty()) {
                        onAddFavorite(favoriteName, target)
                        showSaveDialog = false
                        favoriteName = ""
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Map
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPosition,
            properties = MapProperties(
                mapStyleOptions = if (mapMode == "night") MapStyleOptions(NIGHT_MODE_STYLE) else null,
                mapType = if (mapMode == "satellite") MapType.SATELLITE else MapType.NORMAL
            ),
            uiSettings = MapUiSettings(
                compassEnabled = true,
                zoomControlsEnabled = false,  // Change this from true to false
                mapToolbarEnabled = true
            )
        ) {
            Marker(state = MarkerState(position = target), title = locationName)
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Location header
            Box(
                modifier = Modifier
                    .padding(start = 20.dp, end = 20.dp, bottom = 20.dp, top = 34.dp)
                    .align(Alignment.CenterHorizontally)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    .clickable { showInfo = !showInfo }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    "📍 $locationName",
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                )
            }

            // Location details
            AnimatedVisibility(
                visible = showInfo,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut(),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(Color.White.copy(alpha = 0.9f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Location Details", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Latitude: ${target.latitude}")
                        Text("Longitude: ${target.longitude}")
                        Text("Address: $locationName")
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom controls
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                // border

            ) {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(Color.Black.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("Back") }

                // Map type buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("standard" to "M", "satellite" to "S", "night" to "N").forEach { (mode, label) ->
                        FloatingActionButton(
                            onClick = { onChangeMapMode(mode) },
                            containerColor = if (mapMode == mode) Accent else Color.Gray.copy(alpha = 0.7f),
                            shape = CircleShape,
                            modifier = Modifier.size(40.dp)
                        ) { Text(label) }
                    }
                }

                Button(
                    onClick = { showSaveDialog = true },
                    colors = ButtonDefaults.buttonColors(Secondary.copy(alpha = 0.9f)),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("★ Save") }
            }
        }
    }
}

@Composable
fun FavoritesScreen(
    favorites: Map<String, LatLng>,
    onBack: () -> Unit,
    onSelect: (LatLng) -> Unit,
    onRemove: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = GradientBg))
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .shadow(4.dp, CircleShape)
                    .background(Color.White, CircleShape)
            ) {
                Text("←", fontSize = 36.sp, fontWeight = FontWeight.Bold)
            }

            Text(
                "Favorite Locations",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Primary
            )

            Box(modifier = Modifier.size(48.dp)) // Placeholder for alignment
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Content
        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "You haven't saved any locations yet.\nSave locations to view them here.",
                    textAlign = TextAlign.Center,
                    color = Color.Gray
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                favorites.forEach { (name, location) ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(location) },
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = Primary
                                )
                                Text(
                                    "Lat: ${location.latitude.toString().take(7)}",
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                                Text(
                                    "Lng: ${location.longitude.toString().take(7)}",
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                            }
                            IconButton(onClick = { onRemove(name) }) {
                                Text("×", fontSize = 24.sp, color = Color.Red)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Storage functions
fun saveFavorites(prefs: SharedPreferences, locations: Map<String, LatLng>) {
    val locationsSet = locations.map { (name, latLng) ->
        "$name:${latLng.latitude}:${latLng.longitude}"
    }.toSet()
    prefs.edit().putStringSet(FAVORITE_LOCATIONS_KEY, locationsSet).apply()
}

fun loadFavorites(prefs: SharedPreferences): Map<String, LatLng> {
    val locationsSet = prefs.getStringSet(FAVORITE_LOCATIONS_KEY, emptySet()) ?: emptySet()
    return locationsSet.mapNotNull { locationString ->
        val parts = locationString.split(":")
        if (parts.size == 3) {
            try {
                val name = parts[0]
                val lat = parts[1].toDouble()
                val lng = parts[2].toDouble()
                name to LatLng(lat, lng)
            } catch (e: NumberFormatException) {
                Log.e("LocationApp", "Failed to parse: $locationString", e)
                null
            }
        } else null
    }.toMap()
}

// Helper for location name
@SuppressLint("DefaultLocale")
fun getLocationName(context: Context, lat: Double, lng: Double): String {
    return try {
        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses = geocoder.getFromLocation(lat, lng, 1)
        if (!addresses.isNullOrEmpty()) {
            val address = addresses[0]
            when {
                address.thoroughfare != null -> {
                    StringBuilder().apply {
                        if (address.featureName != null && address.featureName != address.thoroughfare) {
                            append(address.featureName).append(", ")
                        }
                        append(address.thoroughfare)
                        if (address.locality != null) {
                            append(", ").append(address.locality)
                        }
                    }.toString()
                }
                address.locality != null -> {
                    StringBuilder(address.locality).apply {
                        if (address.adminArea != null) {
                            append(", ").append(address.adminArea)
                        }
                    }.toString()
                }
                address.adminArea != null -> address.adminArea
                else -> "Unknown location"
            }
        } else {
            "Location at ${String.format("%.4f", lat)}, ${String.format("%.4f", lng)}"
        }
    } catch (e: Exception) {
        Log.e("LocationApp", "Error getting location name", e)
        "Location at ${String.format("%.4f", lat)}, ${String.format("%.4f", lng)}"
    }
}
