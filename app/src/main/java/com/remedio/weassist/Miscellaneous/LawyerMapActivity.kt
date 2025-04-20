package com.remedio.weassist.Miscellaneous

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.maps.android.PolyUtil
import com.remedio.weassist.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

private const val TAG = "LawyerMapActivity"

class LawyerMapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var lawyerLocation: LatLng
    private lateinit var distanceTextView: TextView
    private lateinit var durationTextView: TextView
    private lateinit var backButton: ImageButton

    // Location-related variables
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest
    private var clientLocation: LatLng = LatLng(0.0, 0.0)
    private var locationUpdateRequested = false

    private var lawyerId: String = ""
    private var lawyerName: String = ""
    private var lawyerSpecialization: String = ""
    private var clientAddress: String = ""

    private val mapsApiKey = "AIzaSyBceN-dLuvJXdpGVpgZ1ckhfm4kCzuIjhM"
    private val defaultCebuLocation = LatLng(10.3157, 123.8854)

    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                // Precise location access granted
                getClientLocation()
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                // Only approximate location access granted
                getClientLocation()
            }
            else -> {
                // No location access granted
                Log.d(TAG, "Location permission denied by user")
                handleNoLocationPermission()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lawyer_map)

        // Initialize UI elements
        distanceTextView = findViewById(R.id.distanceTextView)
        durationTextView = findViewById(R.id.durationTextView)
        backButton = findViewById(R.id.back_button)

        backButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Get lawyer details from intent
        lawyerId = intent.getStringExtra("LAWYER_ID") ?: ""
        lawyerName = intent.getStringExtra("LAWYER_NAME") ?: ""
        lawyerSpecialization = intent.getStringExtra("LAWYER_SPECIALIZATION") ?: ""

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createLocationRequest()
        createLocationCallback()

        // Check if we should use the device location or the location from intent
        val useDeviceLocation = intent.getBooleanExtra("USE_DEVICE_LOCATION", true)

        if (useDeviceLocation) {
            checkLocationPermission()
        } else {
            // Get client information from intent (fallback)
            clientAddress = intent.getStringExtra("CLIENT_ADDRESS") ?: ""
            val clientLat = intent.getDoubleExtra("CLIENT_LATITUDE", 0.0)
            val clientLng = intent.getDoubleExtra("CLIENT_LONGITUDE", 0.0)

            // Use provided coordinates or geocode address
            if (clientLat != 0.0 && clientLng != 0.0) {
                clientLocation = LatLng(clientLat, clientLng)
                Log.d(TAG, "Using client coordinates from intent: $clientLat, $clientLng")
                processLawyerLocation()
            } else if (clientAddress.isNotEmpty()) {
                Log.d(TAG, "Geocoding client address: $clientAddress")
                geocodeAddress(clientAddress, isClient = true)
            } else {
                Log.e(TAG, "No client location information available")
                handleNoLocationPermission()
            }
        }
    }

    private fun createLocationRequest() {
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(5000)
            .setMaxUpdateDelayMillis(15000)
            .build()
    }

    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    if (!locationUpdateRequested) {
                        locationUpdateRequested = true
                        clientLocation = LatLng(location.latitude, location.longitude)
                        Log.d(TAG, "Got client location from device: ${location.latitude}, ${location.longitude}")

                        // Stop location updates once we get a location
                        stopLocationUpdates()

                        // Continue with lawyer location processing
                        processLawyerLocation()
                    }
                }
            }
        }
    }

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
                getClientLocation()
            }

            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                // Show rationale and request permission
                Toast.makeText(
                    this,
                    "Location permission is needed to show your position on the map",
                    Toast.LENGTH_LONG
                ).show()
                requestLocationPermissions()
            }

            else -> {
                // No explanation needed, request the permission
                requestLocationPermissions()
            }
        }
    }

    private fun requestLocationPermissions() {
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun getClientLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        // Try to get last known location first
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    clientLocation = LatLng(location.latitude, location.longitude)
                    Log.d(TAG, "Got client location from last known location: ${location.latitude}, ${location.longitude}")
                    processLawyerLocation()
                } else {
                    // If last location is null, request location updates
                    requestLocationUpdates()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error getting last location", e)
                requestLocationUpdates()
            }
    }

    private fun requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        // Set a timeout for location updates
        CoroutineScope(Dispatchers.Main).launch {
            kotlinx.coroutines.delay(15000) // 15 second timeout
            if (!locationUpdateRequested) {
                Log.d(TAG, "Location update timeout")
                stopLocationUpdates()
                handleNoLocationPermission()
            }
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun handleNoLocationPermission() {
        Toast.makeText(
            this,
            "Using default location since we couldn't access your current location",
            Toast.LENGTH_SHORT
        ).show()

        // Use default location or check for fallback in intent
        clientAddress = intent.getStringExtra("CLIENT_ADDRESS") ?: ""
        val clientLat = intent.getDoubleExtra("CLIENT_LATITUDE", 0.0)
        val clientLng = intent.getDoubleExtra("CLIENT_LONGITUDE", 0.0)

        if (clientLat != 0.0 && clientLng != 0.0) {
            clientLocation = LatLng(clientLat, clientLng)
            processLawyerLocation()
        } else if (clientAddress.isNotEmpty()) {
            geocodeAddress(clientAddress, isClient = true)
        } else {
            clientLocation = defaultCebuLocation
            processLawyerLocation()
        }
    }

    private fun processLawyerLocation() {
        val useAddressNotCoords = intent.getBooleanExtra("USE_ADDRESS_NOT_COORDS", false)

        if (useAddressNotCoords) {
            val lawyerAddress = intent.getStringExtra("LAWYER_ADDRESS") ?: ""
            if (lawyerAddress.isNotEmpty()) {
                Log.d(TAG, "Geocoding lawyer address: $lawyerAddress")
                geocodeAddress(lawyerAddress, isClient = false)
            } else {
                Log.e(TAG, "No lawyer address provided")
                getOfficeLocationFromFirebase()
            }
        } else {
            val lawyerLat = intent.getDoubleExtra("LAWYER_LATITUDE", 0.0)
            val lawyerLng = intent.getDoubleExtra("LAWYER_LONGITUDE", 0.0)
            if (lawyerLat != 0.0 && lawyerLng != 0.0) {
                lawyerLocation = LatLng(lawyerLat, lawyerLng)
                Log.d(TAG, "Using lawyer coordinates: $lawyerLat, $lawyerLng")
                initializeMap()
            } else {
                Log.e(TAG, "No lawyer coordinates provided")
                getOfficeLocationFromFirebase()
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Enable my location button if permission is granted
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            mMap.isMyLocationEnabled = true
        }

        // Set map to Philippines region first
        val phBounds = LatLngBounds(
            LatLng(4.6, 116.9),  // SW corner
            LatLng(21.2, 126.6)   // NE corner
        )
        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(phBounds, 0))

        // Add markers
        mMap.addMarker(MarkerOptions()
            .position(clientLocation)
            .title("Client Location")
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)))

        mMap.addMarker(MarkerOptions()
            .position(lawyerLocation)
            .title("$lawyerName ($lawyerSpecialization)")
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)))

        // Create bounds for both locations
        val bounds = LatLngBounds.Builder()
            .include(clientLocation)
            .include(lawyerLocation)
            .build()

        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))

        // Draw route
        drawRoute(clientLocation, lawyerLocation)
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    private fun geocodeAddress(address: String, isClient: Boolean) {
        val fullAddress = if (!address.contains("Philippines", ignoreCase = true)) {
            "$address, Philippines"
        } else {
            address
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val encodedAddress = URLEncoder.encode(fullAddress, "UTF-8")
                val geocodeUrl = "https://maps.googleapis.com/maps/api/geocode/json?" +
                        "address=$encodedAddress" +
                        "&components=country:PH" +
                        "&region=ph" +
                        "&key=$mapsApiKey"

                Log.d(TAG, "Geocoding URL: $geocodeUrl")
                val response = URL(geocodeUrl).readText()
                val jsonResponse = JSONObject(response)

                if (jsonResponse.getString("status") == "OK") {
                    val results = jsonResponse.getJSONArray("results")
                    if (results.length() > 0) {
                        val location = results.getJSONObject(0)
                            .getJSONObject("geometry")
                            .getJSONObject("location")

                        val lat = location.getDouble("lat")
                        val lng = location.getDouble("lng")
                        Log.d(TAG, "Geocoded location: $lat, $lng")

                        withContext(Dispatchers.Main) {
                            if (isClient) {
                                clientLocation = LatLng(lat, lng)
                                processLawyerLocation()
                            } else {
                                lawyerLocation = LatLng(lat, lng)
                                initializeMap()
                            }
                        }
                    } else {
                        handleGeocodingFailure("No results found", isClient)
                    }
                } else {
                    handleGeocodingFailure("API error: ${jsonResponse.getString("status")}", isClient)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Geocoding error", e)
                handleGeocodingFailure("Network error: ${e.message}", isClient)
            }
        }
    }

    private suspend fun handleGeocodingFailure(message: String, isClient: Boolean) {
        Log.e(TAG, "Geocoding failure ($isClient): $message")
        withContext(Dispatchers.Main) {
            Toast.makeText(this@LawyerMapActivity, "Location error: $message", Toast.LENGTH_SHORT).show()

            if (isClient) {
                clientLocation = defaultCebuLocation
                processLawyerLocation()
            } else {
                getOfficeLocationFromFirebase()
            }
        }
    }

    private fun getOfficeLocationFromFirebase() {
        val database = FirebaseDatabase.getInstance()
        val lawFirmRef = database.getReference("law_firm_admin").child(lawyerId)

        lawFirmRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val officeAddress = snapshot.child("officeAddress").getValue(String::class.java)
                    if (!officeAddress.isNullOrEmpty()) {
                        geocodeAddress(officeAddress, isClient = false)
                    } else {
                        useDefaultLawyerLocation()
                    }
                } else {
                    useDefaultLawyerLocation()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Firebase error", error.toException())
                useDefaultLawyerLocation()
            }
        })
    }

    private fun useDefaultLawyerLocation() {
        lawyerLocation = defaultCebuLocation
        initializeMap()
    }

    private fun initializeMap() {
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun drawRoute(origin: LatLng, destination: LatLng) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val directionsUrl = "https://maps.googleapis.com/maps/api/directions/json?" +
                        "origin=${origin.latitude},${origin.longitude}" +
                        "&destination=${destination.latitude},${destination.longitude}" +
                        "&key=$mapsApiKey" +
                        "&region=ph" +
                        "&mode=driving"

                val response = URL(directionsUrl).readText()
                val jsonResponse = JSONObject(response)

                when (jsonResponse.getString("status")) {
                    "OK" -> {
                        val route = jsonResponse.getJSONArray("routes").getJSONObject(0)
                        val leg = route.getJSONArray("legs").getJSONObject(0)
                        val distance = leg.getJSONObject("distance").getString("text")
                        val duration = leg.getJSONObject("duration").getString("text")
                        val polyline = route.getJSONObject("overview_polyline").getString("points")

                        withContext(Dispatchers.Main) {
                            updateRouteUI(PolyUtil.decode(polyline), distance, duration)
                        }
                    }
                    "ZERO_RESULTS" -> handleNoRouteFound(origin, destination)
                    else -> handleDirectionsFailure("API error")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Directions error", e)
                handleDirectionsFailure("Network error")
            }
        }
    }

    private suspend fun updateRouteUI(path: List<LatLng>, distance: String, duration: String) {
        withContext(Dispatchers.Main) {
            distanceTextView.text = "Distance: $distance"
            durationTextView.text = "Duration: $duration"
            distanceTextView.visibility = View.VISIBLE
            durationTextView.visibility = View.VISIBLE

            mMap.addPolyline(PolylineOptions()
                .addAll(path)
                .width(12f)
                .color(Color.BLUE)
                .geodesic(true))
        }
    }

    private suspend fun handleNoRouteFound(origin: LatLng, destination: LatLng) {
        withContext(Dispatchers.Main) {
            val distance = calculateDistance(origin, destination)
            distanceTextView.text = "Direct distance: ${formatDistance(distance)}"
            durationTextView.text = "No route available"

            mMap.addPolyline(PolylineOptions()
                .add(origin, destination)
                .width(8f)
                .color(Color.GRAY)
                .pattern(listOf(Dash(20f), Gap(10f))))
        }
    }

    private fun calculateDistance(start: LatLng, end: LatLng): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            start.latitude, start.longitude,
            end.latitude, end.longitude,
            results
        )
        return results[0] / 1000f
    }

    private fun formatDistance(km: Float): String {
        return if (km < 1) "${(km * 1000).toInt()} m" else "%.1f km".format(km)
    }

    private suspend fun handleDirectionsFailure(message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(this@LawyerMapActivity, "Route error: $message", Toast.LENGTH_SHORT).show()
            distanceTextView.text = "Distance: Unknown"
            durationTextView.text = "Duration: Unknown"
        }
    }
}