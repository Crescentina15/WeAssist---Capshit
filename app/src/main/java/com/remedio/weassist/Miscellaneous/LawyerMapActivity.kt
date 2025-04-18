package com.remedio.weassist.Miscellaneous

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
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

class LawyerMapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var clientLocation: LatLng
    private lateinit var lawyerLocation: LatLng
    private lateinit var distanceTextView: TextView
    private lateinit var durationTextView: TextView
    private lateinit var backButton: ImageButton

    // Lawyer details as separate fields instead of Lawyer object
    private var lawyerId: String = ""
    private var lawyerName: String = ""
    private var lawyerSpecialization: String = ""

    private val mapsApiKey = "AIzaSyBceN-dLuvJXdpGVpgZ1ckhfm4kCzuIjhM"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lawyer_map)

        // Initialize UI elements
        distanceTextView = findViewById(R.id.distanceTextView)
        durationTextView = findViewById(R.id.durationTextView)
        backButton = findViewById(R.id.back_button)

        // Set back button listener
        backButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Get client location from intent
        val clientLat = intent.getDoubleExtra("CLIENT_LATITUDE", 0.0)
        val clientLng = intent.getDoubleExtra("CLIENT_LONGITUDE", 0.0)
        clientLocation = LatLng(clientLat, clientLng)

        // Get lawyer details from intent
        lawyerId = intent.getStringExtra("LAWYER_ID") ?: ""
        lawyerName = intent.getStringExtra("LAWYER_NAME") ?: ""
        lawyerSpecialization = intent.getStringExtra("LAWYER_SPECIALIZATION") ?: ""

        // Check if we're using an address or coordinates for lawyer location
        val useAddressNotCoords = intent.getBooleanExtra("USE_ADDRESS_NOT_COORDS", false)

        if (useAddressNotCoords) {
            // Get the address from intent and use Places API to find location
            val lawyerAddress = intent.getStringExtra("LAWYER_ADDRESS") ?: ""
            getLocationFromAddress(lawyerAddress)
        } else {
            // Get lawyer location from coordinates
            val lawyerLat = intent.getDoubleExtra("LAWYER_LATITUDE", 0.0)
            val lawyerLng = intent.getDoubleExtra("LAWYER_LONGITUDE", 0.0)
            lawyerLocation = LatLng(lawyerLat, lawyerLng)
            initializeMap()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Add markers for client and lawyer
        mMap.addMarker(MarkerOptions()
            .position(clientLocation)
            .title("Your Location"))

        mMap.addMarker(MarkerOptions()
            .position(lawyerLocation)
            .title("$lawyerName ($lawyerSpecialization)"))

        // Move camera to show both locations
        val bounds = LatLngBounds.Builder()
            .include(clientLocation)
            .include(lawyerLocation)
            .build()

        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))

        // Draw route between locations
        drawRoute(clientLocation, lawyerLocation)
    }

    // Modified LawyerMapActivity to use Google Maps Places API instead of geocoding
    private fun getLocationFromAddress(address: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Use Google Maps Places API instead of Geocoding API
                val encodedAddress = java.net.URLEncoder.encode(address, "UTF-8")
                val placesUrl = "https://maps.googleapis.com/maps/api/place/findplacefromtext/json?" +
                        "input=$encodedAddress" +
                        "&inputtype=textquery" +
                        "&fields=geometry" +
                        "&key=$mapsApiKey"

                val response = URL(placesUrl).readText()
                val jsonResponse = JSONObject(response)

                if (jsonResponse.getString("status") == "OK") {
                    val candidates = jsonResponse.getJSONArray("candidates")
                    if (candidates.length() > 0) {
                        val location = candidates.getJSONObject(0)
                            .getJSONObject("geometry")
                            .getJSONObject("location")

                        val lat = location.getDouble("lat")
                        val lng = location.getDouble("lng")

                        withContext(Dispatchers.Main) {
                            lawyerLocation = LatLng(lat, lng)
                            initializeMap()
                        }
                    } else {
                        handleLocationFailure("No results found for address")
                    }
                } else {
                    handleLocationFailure("Finding place failed: ${jsonResponse.getString("status")}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                handleLocationFailure("Error finding address: ${e.message}")
            }
        }
    }

    private fun initializeMap() {
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this@LawyerMapActivity)
    }

    private suspend fun handleLocationFailure(message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(this@LawyerMapActivity, message, Toast.LENGTH_SHORT).show()

            // Use the office address from Firebase as fallback
            getOfficeLocationFromFirebase()
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
                        getLocationFromAddress(officeAddress)
                    } else {
                        // Use a default location for Cebu City as final fallback
                        lawyerLocation = LatLng(10.3157, 123.8854)
                        initializeMap()
                    }
                } else {
                    // Use a default location for Cebu City as final fallback
                    lawyerLocation = LatLng(10.3157, 123.8854)
                    initializeMap()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Use a default location for Cebu City as final fallback
                lawyerLocation = LatLng(10.3157, 123.8854)
                initializeMap()
            }
        })
    }

    private fun geocodeAddress(address: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val encodedAddress = java.net.URLEncoder.encode(address, "UTF-8")
                val geocodeUrl = "https://maps.googleapis.com/maps/api/geocode/json?" +
                        "address=$encodedAddress" +
                        "&key=$mapsApiKey"

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

                        withContext(Dispatchers.Main) {
                            lawyerLocation = LatLng(lat, lng)

                            // Now that we have the lawyer location, initialize the map
                            val mapFragment = supportFragmentManager
                                .findFragmentById(R.id.map) as SupportMapFragment
                            mapFragment.getMapAsync(this@LawyerMapActivity)
                        }
                    } else {
                        handleGeocodingFailure("No results found for address")
                    }
                } else {
                    handleGeocodingFailure("Geocoding failed: ${jsonResponse.getString("status")}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                handleGeocodingFailure("Error geocoding address: ${e.message}")
            }
        }
    }

    private suspend fun handleGeocodingFailure(message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(this@LawyerMapActivity, message, Toast.LENGTH_SHORT).show()

            // Use a default location for Cebu City as fallback
            lawyerLocation = LatLng(10.3157, 123.8854)

            // Initialize the map anyway
            val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
            mapFragment.getMapAsync(this@LawyerMapActivity)
        }
    }

    private fun drawRoute(origin: LatLng, destination: LatLng) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val directionsUrl = "https://maps.googleapis.com/maps/api/directions/json?" +
                        "origin=${origin.latitude},${origin.longitude}" +
                        "&destination=${destination.latitude},${destination.longitude}" +
                        "&key=$mapsApiKey"

                val response = URL(directionsUrl).readText()
                val jsonResponse = JSONObject(response)

                if (jsonResponse.getString("status") == "OK") {
                    val route = jsonResponse.getJSONArray("routes").getJSONObject(0)
                    val leg = route.getJSONArray("legs").getJSONObject(0)
                    val distance = leg.getJSONObject("distance").getString("text")
                    val duration = leg.getJSONObject("duration").getString("text")
                    val polyline = route.getJSONObject("overview_polyline").getString("points")
                    val decodedPath = PolyUtil.decode(polyline)

                    withContext(Dispatchers.Main) {
                        // Display distance and duration
                        distanceTextView.text = "Distance: $distance"
                        durationTextView.text = "Duration: $duration"
                        distanceTextView.visibility = View.VISIBLE
                        durationTextView.visibility = View.VISIBLE

                        // Draw the route
                        mMap.addPolyline(PolylineOptions()
                            .addAll(decodedPath)
                            .width(10f)
                            .color(Color.BLUE))
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@LawyerMapActivity,
                            "Could not get directions",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@LawyerMapActivity,
                        "Error getting directions: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}