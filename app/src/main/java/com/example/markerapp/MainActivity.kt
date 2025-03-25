package com.example.markerapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.markerapp.ui.theme.MarkerAppTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationRequest = LocationRequest.create().apply {
            interval = 10000
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }


        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    setContent {

                        MaterialTheme {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.background
                            ) {
                                MarkerMap(location)
                            }
                        }
                    }
                }
            }
        }



        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RequestPermissions(fusedLocationClient, locationRequest, locationCallback)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        }
    }

    override fun onPause() {
        super.onPause()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}

@Composable
fun RequestPermissions(
    fusedLocationClient: FusedLocationProviderClient,
    locationRequest: LocationRequest,
    locationCallback: LocationCallback
) {
    val context = LocalContext.current
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasLocationPermission = isGranted
        if (isGranted) {
            try {
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
            } catch (e: SecurityException) {
                Log.e("Location", "Security Exception: ${e.message}")
            }
        }
    }

    LaunchedEffect(hasLocationPermission) {
        if (!hasLocationPermission) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            try {
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
            } catch (e: SecurityException) {
                Log.e("Location", "Security Exception: ${e.message}")
            }
        }
    }
}


@Composable
fun MarkerMap(location: Location) { // location contains latitude and longitude
    val context = LocalContext.current // Get the current application context
    val scope = rememberCoroutineScope() // Create a coroutine scope for launching coroutines
    var reverseGeocodedAddress by remember { mutableStateOf<Address?>(null) } // State to store reverse geocoded Address result

    val markers = remember { mutableStateListOf<MarkerState>() }
    val userLocation = LatLng(location.latitude, location.longitude) // User current location
    val cameraPositionState = rememberCameraPositionState {
        position = com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(userLocation, 5f)
    }

    val duration = Toast.LENGTH_SHORT

    markers.add(MarkerState(userLocation)) // initially, add a marker where user is

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        onMapClick = {
            latLng -> markers.add(MarkerState(latLng))
        }
    ) {
        val userIcon: BitmapDescriptor = remember {
            BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
        }

        markers.forEach{ markerState ->
            Marker(
                state = markerState,
                title = "User",
                icon = userIcon,
                onClick = { // on user click of the location, we show a prompt of the address
                    scope.launch {
                        try {
                            val latitude = markerState.position.latitude // current coordinates
                            val longitude = markerState.position.longitude
                            reverseGeocodedAddress = reverseGeocode(context, latitude, longitude)

                            val toast = Toast.makeText(context, formatAddress(reverseGeocodedAddress), duration) // in Activity
                            toast.show() // display a short toast of the user's address
                        } catch (e: NumberFormatException) {
                            // Handle invalid input (non-numeric latitude or longitude)
                        }
                    }
                    true
                }
            )
        }
    }
}

// Format address function (converts Address object to formatted string)
fun formatAddress(address: Address?): String {
    return address?.let {
        val addressLines = (0..it.maxAddressLineIndex).mapNotNull { i ->
            it.getAddressLine(i)
        }
        addressLines.joinToString(separator = ", ") // Join address lines with commas
    } ?: "Address not found" // Return "Address not found" if the address is null
}

// Reverse geocoding function (converts latitude and longitude to Address object)
suspend fun reverseGeocode(context: Context, latitude: Double, longitude: Double): Address? =
    withContext(Dispatchers.IO) { // Run on IO thread for network operations
        val geocoder = Geocoder(context)
        try {
            val addresses: MutableList<Address>? = geocoder.getFromLocation(latitude, longitude, 1)
            addresses?.firstOrNull() // Get the first address from the result list
        } catch (e: Exception) {
            e.printStackTrace() // Print the exception stack trace for debugging
            null // Return null if reverse geocoding fails
        }
    }