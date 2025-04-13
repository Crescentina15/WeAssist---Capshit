package com.remedio.weassist.Miscellaneous

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.graphics.Typeface
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ai.client.generativeai.GenerativeModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.remedio.weassist.Lawyer.Lawyer
import com.remedio.weassist.Lawyer.LawyersListActivity
import com.remedio.weassist.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt



class ChatbotActivity : AppCompatActivity() {

    private lateinit var chatTextView: TextView
    private lateinit var userInputEditText: EditText
    private lateinit var sendButton: Button
    private lateinit var backButton: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var scrollView: ScrollView

    private val apiKey = "AIzaSyALy2fnaMCisp6UQUWO-VzcxWggalSWXfk"  // Replace with your Google Gemini API key
    private lateinit var generativeModel: GenerativeModel

    // Location-related fields
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geocoder: Geocoder
    private var userLocation: Location? = null
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    // Track conversation state
    private var conversationHistory = mutableListOf<Map<String, String>>()
    private var conversationState = ConversationState()

    private val USER_PREFIX_COLOR = Color.parseColor("#2eb4ea")  // or any color you prefer
    private val USER_BACKGROUND_COLOR = Color.argb(30, 0, 0, 255)
    private val ASSISTANT_PREFIX_COLOR = Color.parseColor("#008000")
    private val ASSISTANT_BACKGROUND_COLOR = Color.argb(30, 0, 255, 0)// or any color you prefer

    // Lawyer data - fetched from Firebase
    private val lawyersList = ArrayList<LawyerWithDistance>()
    // Cache for geocoded addresses
    private val geocodeCache = mutableMapOf<String, Pair<Double, Double>>()

    // Data class to store conversation state
    data class ConversationState(
        var isFirstMessage: Boolean = true,
        var locationRequested: Boolean = false,
        var intendedSpecialization: String = "",
        var showingLawyerInfo: Boolean = false,
        var currentLawyerId: String = "",
        var awaitingConfirmation: Boolean = false,
        var lastAction: String = ""
    )

    // Data class to track lawyer with distance from user
    data class LawyerWithDistance(
        val lawyer: Lawyer,
        var distance: Double = 0.0,
        var formattedDistance: String = ""
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chatbot)

        // Initialize views
        chatTextView = findViewById(R.id.chatTextView)
        userInputEditText = findViewById(R.id.userInputEditText)
        sendButton = findViewById(R.id.sendButton)
        backButton = findViewById(R.id.backButton)
        progressBar = findViewById(R.id.progressBar)
        scrollView = findViewById(R.id.scrollView)

        // Initialize location services and geocoder
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geocoder = Geocoder(this, Locale.getDefault())

        // Initialize Gemini model
        generativeModel = GenerativeModel(modelName = "gemini-1.5-pro-latest", apiKey = apiKey)

        // Hide progress bar initially
        progressBar.visibility = View.GONE

        // Set welcome message
        val welcomeMessage = "Hi there! I'm your legal assistant. How can I help you today?"
        chatTextView.text = "Legal Assistant: $welcomeMessage"

        // Add welcome message to conversation history
        conversationHistory.add(mapOf("role" to "assistant", "content" to welcomeMessage))

        // Fetch lawyer data in the background
        fetchLawyersFromFirebase()

        // Request location permission
        requestLocationPermission()

        // Send button click listener
        sendButton.setOnClickListener {
            val userMessage = userInputEditText.text.toString().trim()
            if (userMessage.isNotEmpty()) {
                val fullMessage = "You: $userMessage"
                val spannable = SpannableString(fullMessage)

                // Style the "You:" prefix
                spannable.setSpan(ForegroundColorSpan(USER_PREFIX_COLOR), 0, 4, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(BackgroundColorSpan(USER_BACKGROUND_COLOR), 0, 4, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, 4, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                chatTextView.append("\n")
                chatTextView.append(spannable)


                // Add to conversation history
                conversationHistory.add(mapOf("role" to "user", "content" to userMessage))

                // Clear input field
                userInputEditText.text.clear()

                // Show typing indicator
                progressBar.visibility = View.VISIBLE

                // Process user message
                processUserMessage(userMessage)

                // Scroll to bottom
                scrollView.post {
                    scrollView.fullScroll(View.FOCUS_DOWN)
                }
            }
        }

        // Back button click listener
        backButton.setOnClickListener {
            finish()
        }
    }

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            // Permission already granted, get location
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
                // Permission granted, get location
                getCurrentLocation()
            } else {
                // Permission denied
                Toast.makeText(
                    this,
                    "Location permission denied. Using default location.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val cancellationTokenSource = CancellationTokenSource()

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cancellationTokenSource.token
        ).addOnSuccessListener { location ->
            userLocation = location

            // If location is received, geocode lawyer addresses and calculate distances
            if (location != null && lawyersList.isNotEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    geocodeLawyerAddresses()
                    updateLawyerDistances()
                }
            }

            Log.d("ChatbotActivity", "User location: ${location?.latitude}, ${location?.longitude}")
        }.addOnFailureListener { e ->
            Log.e("ChatbotActivity", "Error getting location: ${e.message}")
        }
    }

    private fun processUserMessage(userMessage: String) {
        // First interaction handling (special case)
        if (conversationState.isFirstMessage) {
            conversationState.isFirstMessage = false
            respondWithInitialQuestion()
            return
        }

        // Check for immediate action phrases that should bypass Gemini
        when {
            // Direct command to show list when specialization is known
            (userMessage.contains("show", ignoreCase = true) &&
                    userMessage.contains("list", ignoreCase = true) &&
                    conversationState.intendedSpecialization.isNotEmpty()) -> {
                showLawyersList()
                return
            }

            // Clear yes to a search confirmation
            (conversationState.awaitingConfirmation &&
                    (userMessage.equals("yes", ignoreCase = true) ||
                            userMessage.equals("sure", ignoreCase = true) ||
                            userMessage.equals("ok", ignoreCase = true))) -> {

                if (conversationState.lastAction == "suggest_search" &&
                    conversationState.intendedSpecialization.isNotEmpty()) {
                    conversationState.awaitingConfirmation = false

                    // Check if we have location
                    if (userLocation == null && !conversationState.locationRequested) {
                        conversationState.locationRequested = true
                        requestLocationForSearch()
                    } else {
                        searchForLawyers(conversationState.intendedSpecialization)
                    }
                    return
                }
            }
        }

        // For all other cases, use Gemini for understanding and response generation
        analyzeWithGemini(userMessage)
    }

    private fun requestLocationForSearch() {
        addAssistantMessage("I'll need to access your location to find lawyers near you. Getting your location...")

        // Request location again if not already available
        if (userLocation == null) {
            getCurrentLocation()

            // Add a delay to wait for location
            CoroutineScope(Dispatchers.IO).launch {
                // Wait 2 seconds for location
                Thread.sleep(2000)

                withContext(Dispatchers.Main) {
                    if (userLocation != null) {
                        searchForLawyers(conversationState.intendedSpecialization)
                    } else {
                        addAssistantMessage("I couldn't get your location. I'll show you lawyers without distance information.")
                        searchForLawyers(conversationState.intendedSpecialization)
                    }
                }
            }
        } else {
            searchForLawyers(conversationState.intendedSpecialization)
        }
    }

    private suspend fun geocodeLawyerAddresses() {
        // Create a list to collect all updates we need to make
        val updates = mutableListOf<Pair<Int, LawyerWithDistance>>()

        // Iterate using indices to safely handle potential modifications
        for (index in lawyersList.indices) {
            val lawyerWithDistance = lawyersList[index]
            val lawyer = lawyerWithDistance.lawyer

            // Get the address to geocode
            var address = lawyer.location

            if (address.isEmpty()) {
                // Try to get address from law firm admin reference
                val lawFirm = lawyer.lawFirm
                if (lawFirm.isNotEmpty()) {
                    try {
                        // Look up the law firm admin database to find the office address
                        val adminSnapshot = withContext(Dispatchers.IO) {
                            FirebaseDatabase.getInstance().getReference("law_firm_admin")
                                .orderByChild("lawFirm")
                                .equalTo(lawFirm)
                                .get()
                                .await()
                        }

                        if (adminSnapshot.exists() && adminSnapshot.childrenCount > 0) {
                            // Get the first matching admin entry
                            val adminEntry = adminSnapshot.children.first()
                            address = adminEntry.child("officeAddress").getValue(String::class.java) ?: ""
                        }
                    } catch (e: Exception) {
                        Log.e("ChatbotActivity", "Error fetching law firm admin: ${e.message}")
                    }
                }
            }

            // Skip if no valid address
            if (address.isEmpty()) continue

            // Check cache first
            if (geocodeCache.containsKey(address)) {
                val (lat, lng) = geocodeCache[address]!!
                updates.add(index to createUpdatedLawyer(lawyerWithDistance, lat, lng))
                continue
            }

            // Geocode the address
            try {
                val results = withContext(Dispatchers.IO) {
                    geocoder.getFromLocationName(address, 1)
                }

                if (results != null && results.isNotEmpty()) {
                    val location = results[0]
                    val latitude = location.latitude
                    val longitude = location.longitude

                    // Cache the result
                    geocodeCache[address] = Pair(latitude, longitude)

                    // Collect the update to apply later
                    updates.add(index to createUpdatedLawyer(lawyerWithDistance, latitude, longitude))
                } else {
                    Log.d("ChatbotActivity", "No geocoding results found for address: $address")
                }
            } catch (e: IOException) {
                Log.e("ChatbotActivity", "Error geocoding address: ${e.message}")
            } catch (e: Exception) {
                Log.e("ChatbotActivity", "Unexpected error during geocoding: ${e.message}")
            }
        }

        // Apply all collected updates to the original list
        updates.forEach { (index, updated) ->
            lawyersList[index] = updated
        }
    }

    private fun createUpdatedLawyer(original: LawyerWithDistance, lat: Double, lng: Double): LawyerWithDistance {
        // Create updated lawyer with coordinates in location field
        val updatedLawyer = original.lawyer.copy(location = "${original.lawyer.location} [$lat,$lng]")

        // Calculate distance if we have user location
        val distance = if (userLocation != null) {
            calculateDistance(
                userLocation!!.latitude, userLocation!!.longitude,
                lat, lng
            )
        } else {
            original.distance
        }

        // Format distance for display
        val formattedDistance = when {
            distance < 1.0 -> "${(distance * 1000).toInt()} m"
            else -> "${String.format("%.1f", distance)} km"
        }

        return LawyerWithDistance(updatedLawyer, distance, formattedDistance)
    }

    // Helper to update lawyer coordinates and update distances
    private fun updateLawyerCoordinates(lawyer: Lawyer, latitude: Double, longitude: Double) {
        // Store coordinates in temporary variable
        val locationWithCoords = "${lawyer.location} [${latitude},${longitude}]"

        // Find the LawyerWithDistance for this lawyer and update
        val lawyerWithDist = lawyersList.find { it.lawyer.id == lawyer.id }
        lawyerWithDist?.let {
            // We need to create a new lawyer instance since Lawyer is val in LawyerWithDistance
            val updatedLawyer = it.lawyer.copy()
            // Store coordinates in the location field for later use
            updatedLawyer.location = locationWithCoords

            // Create a new LawyerWithDistance with the updated lawyer
            val updatedLawyerWithDist = LawyerWithDistance(updatedLawyer, it.distance, it.formattedDistance)

            // Replace the old instance in the list
            val index = lawyersList.indexOf(it)
            if (index != -1) {
                lawyersList[index] = updatedLawyerWithDist
            }

            if (userLocation != null) {
                // Calculate distance
                val distance = calculateDistance(
                    userLocation!!.latitude, userLocation!!.longitude,
                    latitude, longitude
                )

                updatedLawyerWithDist.distance = distance

                // Format distance for display
                updatedLawyerWithDist.formattedDistance = when {
                    distance < 1.0 -> "${(distance * 1000).toInt()} m"
                    else -> "${String.format("%.1f", distance)} km"
                }
            }
        }
    }

    private fun analyzeWithGemini(userMessage: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Create structured context about the conversation
                val hasLocation = userLocation != null

                val contextJson = JSONObject().apply {
                    put("hasUserLocation", hasLocation)
                    put("suggestedSpecialization", conversationState.intendedSpecialization)
                    put("showingLawyerInfo", conversationState.showingLawyerInfo)
                    put("awaitingConfirmation", conversationState.awaitingConfirmation)
                    put("lastAction", conversationState.lastAction)
                    put("availableSpecializations", getAvailableSpecializations().joinToString(", "))
                }.toString()

                // System prompt with very specific instructions
                val systemPrompt = """
                You are a helpful legal assistant chatbot for a legal app called WeAssist. Your goal is to help users find lawyers based on their needs in a conversational manner.

                CONVERSATION CONTEXT:
                $contextJson

                AVAILABLE COMMANDS (include these in your JSON response):
                - "suggest_specialization": When you identify what type of lawyer the user needs
                - "search_lawyers": When confirming a search for a specific lawyer type
                - "show_lawyers_list": When the user wants to see the full list of lawyers
                - "request_more_info": When you need more information about the user's legal situation
                - "provide_info": When explaining legal concepts (but always bring back to lawyer search)

                RESPONSE RULES:
                1. Keep responses conversational, friendly, and under 3 sentences.
                2. Always steer towards helping find a lawyer, not giving legal advice.
                3. For "I don't know" or vague statements from users, ask about their legal issue.
                4. Recognize common legal situations and match to lawyer specializations.
                5. For questions about specific lawyers, suggest viewing the full list.
                6. Mimic this example flow:
                   - Initial greeting -> Ask what type of lawyer they need
                   - Recognize legal issue -> Suggest specialization and offer to search
                   - Confirm search -> Show results with nearest lawyer
                   - Handle "tell me more" by encouraging full list view
                   - For "show list" requests -> Confirm showing list

                REQUIRED RESPONSE FORMAT:
                Return a JSON object with these fields:
                {
                    "message": "Your response text here",
                    "command": "one_of_the_available_commands",
                    "specialization": "lawyer_specialization_if_detected",
                    "requires_confirmation": true/false
                }
                """

                // Create a conversation history string for Gemini
                val historyString = conversationHistory.joinToString("\n") {
                    "${it["role"]}: ${it["content"]}"
                }

                // Final prompt with context and current message
                val fullPrompt = "$systemPrompt\n\nCurrent conversation history:\n$historyString\n\nAnalyze the user's message and respond appropriately."

                // Get response from Gemini
                val response = generativeModel.generateContent(fullPrompt)

                // Extract the text response
                val responseText = response.text?.trim() ?: ""

                try {
                    // Find JSON in response - Gemini sometimes adds text before/after
                    val jsonMatch = Regex("\\{[\\s\\S]*\\}").find(responseText)
                    val jsonString = jsonMatch?.value ?:
                    throw Exception("No valid JSON found in response")

                    // Parse JSON response
                    val jsonResponse = JSONObject(jsonString)
                    val message = jsonResponse.getString("message")
                    val command = jsonResponse.optString("command", "")
                    val specialization = jsonResponse.optString("specialization", "")
                    val requiresConfirmation = jsonResponse.optBoolean("requires_confirmation", false)

                    // Update conversation state based on JSON response
                    if (specialization.isNotEmpty()) {
                        conversationState.intendedSpecialization = specialization
                    }

                    conversationState.awaitingConfirmation = requiresConfirmation
                    conversationState.lastAction = command

                    withContext(Dispatchers.Main) {
                        // Add the assistant's response to the UI
                        addAssistantMessage(message)

                        // Execute commands if present
                        when (command) {
                            "search_lawyers" -> {
                                if (!requiresConfirmation) {
                                    // Check if we have location first
                                    if (userLocation == null && !conversationState.locationRequested) {
                                        conversationState.locationRequested = true
                                        requestLocationForSearch()
                                    } else {
                                        searchForLawyers(conversationState.intendedSpecialization)
                                    }
                                }
                            }
                            "show_lawyers_list" -> {
                                if (!requiresConfirmation) {
                                    showLawyersList()
                                }
                            }
                        }

                        progressBar.visibility = View.GONE
                    }

                } catch (e: Exception) {
                    // If JSON parsing fails, still display the text response
                    Log.e("ChatbotActivity", "JSON parsing error: ${e.message}")

                    withContext(Dispatchers.Main) {
                        addAssistantMessage(responseText)
                        progressBar.visibility = View.GONE
                    }
                }

            } catch (e: Exception) {
                Log.e("ChatbotActivity", "Gemini API error: ${e.message}")
                withContext(Dispatchers.Main) {
                    handleError("I'm having trouble connecting right now. Would you like me to help you find a specific type of lawyer?")
                }
            }
        }
    }

    private fun getAvailableSpecializations(): List<String> {
        val specializations = mutableSetOf<String>()

        // Extract unique specializations from lawyersList
        for (lawyerWithDistance in lawyersList) {
            if (lawyerWithDistance.lawyer.specialization.isNotEmpty()) {
                specializations.add(lawyerWithDistance.lawyer.specialization)
            }
        }

        // If no specializations found in DB, provide common ones
        if (specializations.isEmpty()) {
            return listOf(
                "Family Law",
                "Criminal Law",
                "Personal Injury",
                "Estate Planning",
                "Corporate Law",
                "Real Estate Law",
                "Immigration Law",
                "Tax Law",
                "Intellectual Property Law",
                "Employment Law",
                "Notarial Services"
            )
        }

        return specializations.toList()
    }

    private fun respondWithInitialQuestion() {
        val initialQuestion = "I'm here to help you find legal assistance near your location. What type of lawyer are you looking for?"
        addAssistantMessage(initialQuestion)
        progressBar.visibility = View.GONE
    }

    private fun updateLawyerDistances() {
        if (userLocation == null || lawyersList.isEmpty()) return

        for (lawyerWithDistance in lawyersList) {
            val lawyer = lawyerWithDistance.lawyer

            // Extract coordinates from the location string if available
            // Format: "Original Address [latitude,longitude]"
            val coordsRegex = "\\[([-\\d.]+),([-\\d.]+)\\]".toRegex()
            val matchResult = coordsRegex.find(lawyer.location)

            if (matchResult != null) {
                val (latStr, lngStr) = matchResult.destructured
                try {
                    val lat = latStr.toDouble()
                    val lng = lngStr.toDouble()

                    // Calculate distance
                    val distance = calculateDistance(
                        userLocation!!.latitude, userLocation!!.longitude,
                        lat, lng
                    )

                    lawyerWithDistance.distance = distance

                    // Format distance for display
                    lawyerWithDistance.formattedDistance = when {
                        distance < 1.0 -> "${(distance * 1000).toInt()} m"
                        else -> "${String.format("%.1f", distance)} km"
                    }
                } catch (e: NumberFormatException) {
                    // If conversion fails, use default values
                    lawyerWithDistance.distance = Double.MAX_VALUE
                    lawyerWithDistance.formattedDistance = "Unknown distance"
                }
            } else {
                // No coordinates available
                lawyerWithDistance.distance = Double.MAX_VALUE
                lawyerWithDistance.formattedDistance = "Unknown distance"
            }
        }

        // Sort lawyers by distance
        lawyersList.sortBy { it.distance }
    }

    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val radius = 6371.0 // Earth radius in kilometers

        val latDistance = Math.toRadians(lat2 - lat1)
        val lonDistance = Math.toRadians(lon2 - lon1)

        val a = sin(latDistance / 2) * sin(latDistance / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(lonDistance / 2) * sin(lonDistance / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return radius * c
    }

    private fun searchForLawyers(specialization: String) {
        val searchingMessage = "Great! I'll search for $specialization lawyers near your location. One moment please..."
        addAssistantMessage(searchingMessage)

        // Simulate search delay
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Short delay for better UX
                Thread.sleep(1000)

                // Ensure we've geocoded addresses for distance calculations
                geocodeLawyerAddresses()

                // Sort by distance if needed
                if (userLocation != null) {
                    updateLawyerDistances()
                }

                withContext(Dispatchers.Main) {
                    // Display search results
                    if (lawyersList.isNotEmpty()) {
                        val filteredLawyers = lawyersList.filter {
                            it.lawyer.specialization.equals(specialization, ignoreCase = true)
                        }

                        if (filteredLawyers.isNotEmpty()) {
                            val count = filteredLawyers.size
                            val nearestLawyer = filteredLawyers.first()

                            val distanceText = if (userLocation != null && nearestLawyer.formattedDistance.isNotEmpty() &&
                                nearestLawyer.formattedDistance != "Unknown distance") {
                                "who is ${nearestLawyer.formattedDistance} away"
                            } else {
                                "in your area"
                            }

                            val resultsMessage = "I found $count $specialization lawyers near your location. The closest is ${nearestLawyer.lawyer.name}, who specializes in $specialization and is $distanceText. Would you like to see the full list?"
                            addAssistantMessage(resultsMessage)

                            conversationState.currentLawyerId = nearestLawyer.lawyer.id
                            conversationState.showingLawyerInfo = true
                        } else {
                            addAssistantMessage("I couldn't find any $specialization lawyers in our database. Would you like to try a different specialization?")
                        }
                    } else {
                        // Fallback if no lawyers found
                        val distanceText = if (userLocation != null) "1.8 km away" else "in your area"
                        addAssistantMessage("I found 4 $specialization lawyers near your location. The closest is Michael Garcia, who specializes in $specialization and is $distanceText. Would you like to see the full list?")
                    }
                    progressBar.visibility = View.GONE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    handleError("Sorry, I encountered an error while searching for lawyers. Please try again later.")
                }
            }
        }
    }

    private fun showLawyersList() {
        val listMessage = "Great! I'll open the full list of ${conversationState.intendedSpecialization} lawyers for you now."
        addAssistantMessage(listMessage)
        progressBar.visibility = View.GONE

        // Launch the lawyers list activity
        CoroutineScope(Dispatchers.Main).launch {
            // Small delay for natural feel
            Thread.sleep(500)

            val intent = Intent(this@ChatbotActivity, LawyersListActivity::class.java)
            intent.putExtra("SPECIALIZATION", conversationState.intendedSpecialization)

            // Pass user location if available
            if (userLocation != null) {
                intent.putExtra("USER_LATITUDE", userLocation!!.latitude)
                intent.putExtra("USER_LONGITUDE", userLocation!!.longitude)
            }

            startActivity(intent)
        }
    }

    private fun fetchLawyersFromFirebase() {
        val database = FirebaseDatabase.getInstance().getReference("lawyers")

        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                lawyersList.clear()

                for (lawyerSnapshot in snapshot.children) {
                    try {
                        val lawyer = lawyerSnapshot.getValue(Lawyer::class.java)
                        lawyer?.let {
                            it.id = lawyerSnapshot.key ?: ""

                            // Add lawyer to list with default distance
                            lawyersList.add(LawyerWithDistance(it))
                        }
                    } catch (e: Exception) {
                        Log.e("ChatbotActivity", "Error parsing lawyer data: ${e.message}")
                    }
                }

                Log.d("ChatbotActivity", "Loaded ${lawyersList.size} lawyers from database")

                // Start geocoding addresses in the background
                if (lawyersList.isNotEmpty()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        geocodeLawyerAddresses()

                        // Update distances if location is available
                        if (userLocation != null) {
                            updateLawyerDistances()
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatbotActivity", "Failed to load lawyers: ${error.message}")
            }
        })
    }

    private fun addAssistantMessage(message: String) {
        val fullMessage = "Legal Assistant: $message"
        val spannable = SpannableString(fullMessage)

        // Style the "Legal Assistant:" prefix
        spannable.setSpan(ForegroundColorSpan(ASSISTANT_PREFIX_COLOR), 0, 16, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(BackgroundColorSpan(ASSISTANT_BACKGROUND_COLOR), 0, 16, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, 16, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        chatTextView.append("\n")
        chatTextView.append(spannable)

        // Add to conversation history
        conversationHistory.add(mapOf("role" to "assistant", "content" to message))

        // Scroll to the bottom of the conversation
        scrollView.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun handleError(errorMessage: String) {
        addAssistantMessage(errorMessage)
        progressBar.visibility = View.GONE
    }
}