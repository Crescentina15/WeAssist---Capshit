package com.remedio.weassist.Lawyer

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import com.remedio.weassist.Miscellaneous.AddBalanceActivity
import com.remedio.weassist.R
import com.remedio.weassist.Secretary.AddAvailabilityActivity
import com.remedio.weassist.Secretary.AddBackgroundActivity
import com.remedio.weassist.Secretary.SetAppointmentActivity

class LawyersListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var lawyerAdapter: LawyerAdapter
    private lateinit var databaseReference: DatabaseReference
    private lateinit var lawFirmAdminReference: DatabaseReference
    private var lawyerList = ArrayList<Lawyer>()

    private var fromManageAvailability = false
    private var fromAddBackgroundActivity = false
    private var fromAddBalanceActivity = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lawyers_list)

        val specialization = intent.getStringExtra("SPECIALIZATION")
        val lawFirm = intent.getStringExtra("LAW_FIRM")

        // Get user location if available from intent
        val userLatitude = intent.getDoubleExtra("USER_LATITUDE", 0.0)
        val userLongitude = intent.getDoubleExtra("USER_LONGITUDE", 0.0)
        val hasLocation = userLatitude != 0.0 && userLongitude != 0.0

        // Retrieve intent flags
        fromManageAvailability = intent.getBooleanExtra("FROM_MANAGE_AVAILABILITY", false)
        fromAddBackgroundActivity = intent.getBooleanExtra("FROM_ADD_BACKGROUND", false)
        fromAddBalanceActivity = intent.getBooleanExtra("FROM_ADD_BALANCE", false)

        recyclerView = findViewById(R.id.lawyerlist)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Initialize empty adapter first to show loading state
        lawyerAdapter = LawyerAdapter(
            lawyersList = emptyList(),
            onLawyerClick = { /* Will be replaced */ }
        )
        recyclerView.adapter = lawyerAdapter

        // Initialize Firebase
        databaseReference = FirebaseDatabase.getInstance().getReference("lawyers")
        lawFirmAdminReference = FirebaseDatabase.getInstance().getReference("law_firm_admin")

        // Load lawyers from Firebase with firm locations
        loadLawyers(specialization, lawFirm, userLatitude, userLongitude, hasLocation)

        // Handle back button click
        findViewById<ImageButton>(R.id.back_button).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun loadLawyers(
        specialization: String?,
        lawFirm: String?,
        userLatitude: Double = 0.0,
        userLongitude: Double = 0.0,
        hasLocation: Boolean = false
    ) {
        lawFirmAdminReference.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(adminSnapshot: DataSnapshot) {
                val firmLocationMap = HashMap<String, String>()

                for (adminData in adminSnapshot.children) {
                    val admin = adminData.value as? Map<String, Any> ?: continue
                    val firmName = admin["lawFirm"]?.toString() ?: continue
                    val officeAddress = admin["officeAddress"]?.toString() ?: ""

                    firmLocationMap[firmName] = officeAddress
                }

                loadLawyersWithFirmLocations(specialization, lawFirm, firmLocationMap, userLatitude, userLongitude, hasLocation)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(applicationContext, "Failed to load firm data.", Toast.LENGTH_SHORT)
                    .show()
            }
        })
    }

    private fun loadLawyersWithFirmLocations(
        specialization: String?,
        lawFirm: String?,
        firmLocationMap: Map<String, String>,
        userLatitude: Double = 0.0,
        userLongitude: Double = 0.0,
        hasLocation: Boolean = false
    ) {
        databaseReference.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tempLawyerList = ArrayList<Lawyer>()

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
                    lawyerList.addAll(tempLawyerList.sortedWith(compareBy(
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

                lawyerAdapter = LawyerAdapter(
                    lawyersList = lawyerList,
                    onLawyerClick = { selectedLawyer ->
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
                            startActivity(intent)
                        }
                    }
                )

                recyclerView.adapter = lawyerAdapter

                // Check if list is empty
                if (lawyerList.isEmpty()) {
                    Toast.makeText(
                        applicationContext,
                        "No ${specialization ?: "matching"} lawyers found",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(applicationContext, "Failed to load lawyers.", Toast.LENGTH_SHORT)
                    .show()
            }
        })
    }

    // Calculate distance using Haversine formula
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