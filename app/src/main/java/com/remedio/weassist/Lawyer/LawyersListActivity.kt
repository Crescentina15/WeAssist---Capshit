package com.remedio.weassist.Lawyer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.database.*
import com.remedio.weassist.Miscellaneous.AddBalanceActivity
import com.remedio.weassist.Miscellaneous.LawyerMapActivity
import com.remedio.weassist.R
import com.remedio.weassist.Secretary.AddAvailabilityActivity
import com.remedio.weassist.Secretary.AddBackgroundActivity
import com.remedio.weassist.Secretary.SetAppointmentActivity
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.recyclerview.widget.DividerItemDecoration

class LawyersListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var lawyerAdapter: LawyerAdapter
    private lateinit var databaseReference: DatabaseReference
    private lateinit var lawFirmAdminReference: DatabaseReference
    private lateinit var searchEditText: EditText
    private var lawyerList = ArrayList<Lawyer>()
    private var filteredLawyerList = ArrayList<Lawyer>()
    private var userLatitude: Double = 0.0
    private var userLongitude: Double = 0.0
    private var hasLocation: Boolean = false

    private var fromManageAvailability = false
    private var fromAddBackgroundActivity = false
    private var fromAddBalanceActivity = false

    // Location related variables
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lawyers_list)

        val specialization = intent.getStringExtra("SPECIALIZATION")
        val lawFirm = intent.getStringExtra("LAW_FIRM")

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Get user location if available from intent
        userLatitude = intent.getDoubleExtra("USER_LATITUDE", 0.0)
        userLongitude = intent.getDoubleExtra("USER_LONGITUDE", 0.0)

        // If location not provided in intent, try to get current location
        if (userLatitude == 0.0 || userLongitude == 0.0) {
            requestLocationPermission()
        } else {
            hasLocation = true
        }

        // Retrieve intent flags
        fromManageAvailability = intent.getBooleanExtra("FROM_MANAGE_AVAILABILITY", false)
        fromAddBackgroundActivity = intent.getBooleanExtra("FROM_ADD_BACKGROUND", false)
        fromAddBalanceActivity = intent.getBooleanExtra("FROM_ADD_BALANCE", false)

        recyclerView = findViewById(R.id.lawyerlist)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        // Setup empty state view
        val emptyStateView = findViewById<View>(R.id.empty_state)
        val progressBar = findViewById<View>(R.id.progressBar)
        progressBar.visibility = View.VISIBLE

        // Initialize search functionality
        searchEditText = findViewById(R.id.search_edit_text)
        setupSearchFunctionality()

        // Initialize empty adapter first to show loading state
        lawyerAdapter = LawyerAdapter(
            lawyersList = emptyList(),
            onLawyerClick = { /* Will be replaced */ },
            onDirectionsClick = { lawyer -> showDirections(lawyer) }
        )
        recyclerView.adapter = lawyerAdapter

        // Initialize Firebase
        databaseReference = FirebaseDatabase.getInstance().getReference("lawyers")
        lawFirmAdminReference = FirebaseDatabase.getInstance().getReference("law_firm_admin")

        // Load lawyers from Firebase with firm locations
        // We'll load after location is obtained
        if (hasLocation) {
            loadLawyers(specialization, lawFirm)
        }

        // Handle back button click
        findViewById<ImageButton>(R.id.back_button).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupSearchFunctionality() {
        searchEditText.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                performSearch(searchEditText.text.toString())
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                filterLawyers(s.toString())
            }
        })
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) {
            // If search is empty, just show all lawyers
            updateAdapterWithFilteredList(lawyerList)
            return
        }

        val trimmedQuery = query.trim().lowercase()

        // Find exact match first
        val exactMatch = lawyerList.find {
            it.name.lowercase() == trimmedQuery
        }

        if (exactMatch != null) {
            // Direct to lawyer details if exact match
            navigateToLawyerDetails(exactMatch)
        } else {
            // Filter and show results
            filterLawyers(trimmedQuery)

            // Show message if no results
            if (filteredLawyerList.isEmpty()) {
                Toast.makeText(
                    this,
                    "No lawyer found with name \"$query\"",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun filterLawyers(query: String) {
        if (query.isBlank()) {
            // If filter is empty, show all lawyers
            filteredLawyerList = ArrayList(lawyerList)
        } else {
            // Filter lawyers by name (case insensitive)
            filteredLawyerList = ArrayList(lawyerList.filter {
                it.name.lowercase().contains(query.lowercase())
            })
        }

        updateAdapterWithFilteredList(filteredLawyerList)
    }

    private fun updateAdapterWithFilteredList(lawyers: List<Lawyer>) {
        // Show/hide empty state based on filtered results
        val emptyStateView = findViewById<View>(R.id.empty_state)
        emptyStateView.visibility = if (lawyers.isEmpty()) View.VISIBLE else View.GONE

        lawyerAdapter = LawyerAdapter(
            lawyersList = lawyers,
            onLawyerClick = { selectedLawyer -> navigateToLawyerDetails(selectedLawyer) },
            onDirectionsClick = { lawyer -> showDirections(lawyer) }
        )
        recyclerView.adapter = lawyerAdapter
    }

    private fun navigateToLawyerDetails(selectedLawyer: Lawyer) {
        val context = this@LawyersListActivity
        if (!fromManageAvailability && !fromAddBackgroundActivity && !fromAddBalanceActivity) {
            val intent = Intent(context, LawyerBackgroundActivity::class.java)
            intent.putExtra("LAWYER_ID", selectedLawyer.id)
            startActivity(intent)
        } else {
            val intent = when {
                fromManageAvailability -> Intent(
                    context,
                    AddAvailabilityActivity::class.java
                )
                fromAddBackgroundActivity -> Intent(
                    context,
                    AddBackgroundActivity::class.java
                )
                fromAddBalanceActivity -> Intent(
                    context,
                    AddBalanceActivity::class.java
                )
                else -> Intent(context, SetAppointmentActivity::class.java)
            }
            intent.putExtra("LAWYER_ID", selectedLawyer.id)
            intent.putExtra("LAWYER_NAME", selectedLawyer.name)
            intent.putExtra("LAW_FIRM", selectedLawyer.lawFirm)
            intent.putExtra("LOCATION", selectedLawyer.location)

            // Also add client location to intent if available
            if (hasLocation) {
                intent.putExtra("USER_LATITUDE", userLatitude)
                intent.putExtra("USER_LONGITUDE", userLongitude)
            }

            startActivity(intent)
        }
    }

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            getCurrentLocation()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation()
            } else {
                Toast.makeText(
                    this,
                    "Location permission denied. Distance-based sorting disabled.",
                    Toast.LENGTH_SHORT
                ).show()

                // Even without location, we can still load lawyers
                val specialization = intent.getStringExtra("SPECIALIZATION")
                val lawFirm = intent.getStringExtra("LAW_FIRM")
                loadLawyers(specialization, lawFirm)
            }
        }
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    location?.let {
                        userLatitude = it.latitude
                        userLongitude = it.longitude
                        hasLocation = true

                        val specialization = intent.getStringExtra("SPECIALIZATION")
                        val lawFirm = intent.getStringExtra("LAW_FIRM")
                        loadLawyers(specialization, lawFirm)
                    } ?: run {
                        Toast.makeText(
                            this,
                            "Could not get current location. Try again later.",
                            Toast.LENGTH_SHORT
                        ).show()

                        // Even without location, we can still load lawyers
                        val specialization = intent.getStringExtra("SPECIALIZATION")
                        val lawFirm = intent.getStringExtra("LAW_FIRM")
                        loadLawyers(specialization, lawFirm)
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(
                        this,
                        "Failed to get location. Distance-based sorting disabled.",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Even without location, we can still load lawyers
                    val specialization = intent.getStringExtra("SPECIALIZATION")
                    val lawFirm = intent.getStringExtra("LAW_FIRM")
                    loadLawyers(specialization, lawFirm)
                }
        }
    }

    private fun showDirections(lawyer: Lawyer) {
        if (!hasLocation) {
            // If we don't have location yet, try to get it first
            getCurrentLocation()
            Toast.makeText(this, "Getting your location first...", Toast.LENGTH_SHORT).show()
            return
        }

        // First try to extract coordinates from the existing location string
        val coordsRegex = "\\[([-\\d.]+),([-\\d.]+)\\]".toRegex()
        val locationCoords = coordsRegex.find(lawyer.location)

        if (locationCoords != null) {
            val (latStr, lngStr) = locationCoords.destructured
            try {
                val lat = latStr.toDouble()
                val lng = lngStr.toDouble()
                navigateToLawyerLocation(lawyer, lat, lng)
            } catch (e: NumberFormatException) {
                // If parsing fails, use a default location for the law firm or geocode the address
                handleAddressWithoutCoordinates(lawyer)
            }
        } else {
            // If no coordinates in the location string, use a default location or geocode
            handleAddressWithoutCoordinates(lawyer)
        }
    }

    private fun handleAddressWithoutCoordinates(lawyer: Lawyer) {
        // For Cebu City locations, you might want to use default coordinates as a fallback
        // These are approximate coordinates for Cebu City
        val defaultCebuLat = 10.3157
        val defaultCebuLng = 123.8854

        // Check if the location contains "Cebu" to use default Cebu coordinates
        if (lawyer.location.contains("Cebu", ignoreCase = true)) {
            // Navigate using default Cebu coordinates
            navigateToLawyerLocation(lawyer, defaultCebuLat, defaultCebuLng)
            Toast.makeText(this, "Using approximate location for directions", Toast.LENGTH_SHORT).show()
        } else {
            // For addresses outside Cebu or if you want to be more precise,
            // you would ideally implement geocoding here to convert the address to coordinates
            // For now, we'll show a more descriptive message
            Toast.makeText(this,
                "Location coordinates not available. Please add coordinates to the lawyer's office address in format: 'address [lat,lng]'",
                Toast.LENGTH_LONG).show()
        }

        // Alternatively, you can pass the text address to the map activity and let it handle geocoding
        val intent = Intent(this, LawyerMapActivity::class.java).apply {
            putExtra("CLIENT_LATITUDE", userLatitude)
            putExtra("CLIENT_LONGITUDE", userLongitude)
            putExtra("LAWYER", lawyer)
            putExtra("LAWYER_ADDRESS", lawyer.location) // Pass the text address
            // Flag to indicate we're passing an address, not coordinates
            putExtra("USE_ADDRESS_NOT_COORDS", true)
        }
        startActivity(intent)
    }

    private fun navigateToLawyerLocation(lawyer: Lawyer, lawyerLat: Double, lawyerLng: Double) {
        val intent = Intent(this, LawyerMapActivity::class.java).apply {
            putExtra("CLIENT_LATITUDE", userLatitude)
            putExtra("CLIENT_LONGITUDE", userLongitude)
            putExtra("LAWYER_LATITUDE", lawyerLat)
            putExtra("LAWYER_LONGITUDE", lawyerLng)
            putExtra("LAWYER", lawyer)
            putExtra("USE_ADDRESS_NOT_COORDS", false)
        }
        startActivity(intent)
    }

    private fun loadLawyers(specialization: String?, lawFirm: String?) {
        // Show progress bar while loading
        findViewById<View>(R.id.progressBar).visibility = View.VISIBLE
        findViewById<View>(R.id.empty_state).visibility = View.GONE

        lawFirmAdminReference.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(adminSnapshot: DataSnapshot) {
                val firmLocationMap = HashMap<String, String>()

                for (adminData in adminSnapshot.children) {
                    val admin = adminData.value as? Map<String, Any> ?: continue
                    val firmName = admin["lawFirm"]?.toString() ?: continue
                    val officeAddress = admin["officeAddress"]?.toString() ?: ""

                    firmLocationMap[firmName] = officeAddress
                }

                loadLawyersWithFirmLocations(specialization, lawFirm, firmLocationMap)
            }

            override fun onCancelled(error: DatabaseError) {
                findViewById<View>(R.id.progressBar).visibility = View.GONE
                Toast.makeText(applicationContext, "Failed to load firm data.", Toast.LENGTH_SHORT)
                    .show()
            }
        })
    }

    private fun loadLawyersWithFirmLocations(
        specialization: String?,
        lawFirm: String?,
        firmLocationMap: Map<String, String>
    ) {
        databaseReference.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tempLawyerList = ArrayList<Lawyer>()

                // Hide progress bar
                findViewById<View>(R.id.progressBar).visibility = View.GONE

                for (lawyerSnapshot in snapshot.children) {
                    val lawyerId = lawyerSnapshot.key ?: continue
                    val lawyerData = lawyerSnapshot.value as? Map<String, Any> ?: continue

                    val contactData = lawyerData["contact"]
                    val contact = when (contactData) {
                        is Map<*, *> -> Contact(
                            phone = contactData["phone"]?.toString() ?: "",
                            email = contactData["email"]?.toString() ?: "",
                            address = contactData["address"]?.toString() ?: ""
                        )
                        is String -> Contact(phone = contactData)
                        else -> Contact()
                    }

                    val lawyerFirm = lawyerData["lawFirm"]?.toString() ?: ""
                    val firmLocation = firmLocationMap[lawyerFirm] ?: ""

                    val ratingsString = lawyerData["ratings"]?.toString() ?: "0"
                    val ratings = try {
                        ratingsString.toInt()
                    } catch (e: NumberFormatException) {
                        0
                    }

                    val averageRating = lawyerData["averageRating"]?.let {
                        when (it) {
                            is Double -> it
                            is Long -> it.toDouble()
                            is Float -> it.toDouble()
                            is String -> it.toDoubleOrNull()
                            else -> null
                        }
                    }

                    // Extract coordinates from location if available (format: "address [lat,lng]")
                    val coordsRegex = "\\[([-\\d.]+),([-\\d.]+)\\]".toRegex()
                    val locationCoords = coordsRegex.find(firmLocation)
                    var distance: Double? = null

                    // Calculate distance if user location is available and lawyer location has coordinates
                    if (hasLocation && locationCoords != null) {
                        val (latStr, lngStr) = locationCoords.destructured
                        try {
                            val lat = latStr.toDouble()
                            val lng = lngStr.toDouble()

                            // Calculate distance using Haversine formula
                            distance = calculateDistance(userLatitude, userLongitude, lat, lng)
                        } catch (e: NumberFormatException) {
                            // Skip distance calculation if conversion fails
                        }
                    }

                    val lawyer = Lawyer(
                        id = lawyerId,
                        name = lawyerData["name"]?.toString() ?: "",
                        specialization = lawyerData["specialization"]?.toString() ?: "",
                        lawFirm = lawyerFirm,
                        licenseNumber = lawyerData["licenseNumber"]?.toString() ?: "",
                        experience = lawyerData["experience"]?.toString() ?: "",
                        profileImageUrl = lawyerData["profileImageUrl"]?.toString(),
                        location = firmLocation,
                        rate = ratings.toString(),
                        averageRating = averageRating,
                        contact = contact,
                        distance = distance
                    )

                    if ((specialization == null || lawyer.specialization == specialization) &&
                        (lawFirm == null || lawyer.lawFirm == lawFirm)
                    ) {
                        tempLawyerList.add(lawyer)
                    }
                }

                // Sort lawyers based on distance if user location is available, otherwise by rating
                lawyerList.clear()
                if (hasLocation) {
                    // Sort by distance first, then by rating for lawyers without distance
                    lawyerList.addAll(tempLawyerList.sortedWith(compareBy<Lawyer>(
                        // Sort by distance (null distances come last)
                        { it.distance ?: Double.MAX_VALUE },
                        // Then by rating (higher rating first)
                        { -(it.averageRating ?: 0.0) }
                    )))
                } else {
                    // Sort by rating only (if no location available)
                    lawyerList.addAll(tempLawyerList.sortedByDescending {
                        it.averageRating ?: 0.0
                    })
                }

                // Start with full list
                filteredLawyerList = ArrayList(lawyerList)

                // Apply any existing search filter
                if (searchEditText.text.isNotEmpty()) {
                    filterLawyers(searchEditText.text.toString())
                } else {
                    updateAdapterWithFilteredList(lawyerList)
                }

                // Check if list is empty
                if (lawyerList.isEmpty()) {
                    Toast.makeText(
                        applicationContext,
                        "No ${specialization ?: "matching"} lawyers found",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Show empty state
                    findViewById<View>(R.id.empty_state).visibility = View.VISIBLE
                }
            }

            override fun onCancelled(error: DatabaseError) {
                findViewById<View>(R.id.progressBar).visibility = View.GONE
                Toast.makeText(applicationContext, "Failed to load lawyers.", Toast.LENGTH_SHORT)
                    .show()
            }
        })
    }

    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val radius = 6371.0 // Earth radius in kilometers

        val latDistance = Math.toRadians(lat2 - lat1)
        val lonDistance = Math.toRadians(lon2 - lon1)

        val a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2)

        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return radius * c
    }
}