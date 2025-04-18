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
import com.google.android.gms.maps.model.LatLng
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

    private val recentMessages = mutableListOf<String>()
    private val MESSAGE_SIMILARITY_THRESHOLD = 0.7  // Adjust this value as needed

    private val mapsApiKey = "AIzaSyBceN-dLuvJXdpGVpgZ1ckhfm4kCzuIjhM"


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

    private fun isDuplicateOrSimilarMessage(message: String): Boolean {
        // Only check against the last few messages
        val messagesToCheck = recentMessages.takeLast(3)

        for (previousMessage in messagesToCheck) {
            // Calculate similarity ratio using Levenshtein distance
            val similarity = calculateSimilarity(previousMessage, message)
            if (similarity > MESSAGE_SIMILARITY_THRESHOLD) {
                return true
            }
        }
        return false
    }

    // Add this helper method to calculate string similarity
    private fun calculateSimilarity(s1: String, s2: String): Double {
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        // Calculate Levenshtein distance
        val distance = levenshteinDistance(s1.lowercase(), s2.lowercase())

        // Convert to similarity ratio (0.0 to 1.0)
        val maxLength = maxOf(s1.length, s2.length)
        return 1.0 - (distance.toDouble() / maxLength)
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) {
            dp[i][0] = i
        }

        for (j in 0..n) {
            dp[0][j] = j
        }

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,     // Delete
                    dp[i][j - 1] + 1,     // Insert
                    dp[i - 1][j - 1] + cost  // Substitute
                )
            }
        }

        return dp[m][n]
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
        for (lawyerWithDistance in lawyersList) {
            val lawyer = lawyerWithDistance.lawyer
            val address = lawyer.location

            if (address.isEmpty()) continue

            // Check cache first
            if (geocodeCache.containsKey(address)) {
                val (lat, lng) = geocodeCache[address]!!
                lawyer.location = "$address|$lat,$lng" // Store coordinates with address
                continue
            }

            try {
                // Use Google Maps Geocoding API for more reliable results
                val geocodingUrl = "https://maps.googleapis.com/maps/api/geocode/json?" +
                        "address=${address.replace(" ", "+")}" +
                        "&key=$mapsApiKey"

                val response = withContext(Dispatchers.IO) {
                    java.net.URL(geocodingUrl).readText()
                }

                val jsonResponse = JSONObject(response)
                if (jsonResponse.getString("status") == "OK") {
                    val results = jsonResponse.getJSONArray("results")
                    if (results.length() > 0) {
                        val location = results.getJSONObject(0)
                            .getJSONObject("geometry")
                            .getJSONObject("location")
                        val lat = location.getDouble("lat")
                        val lng = location.getDouble("lng")

                        // Cache the result
                        geocodeCache[address] = Pair(lat, lng)
                        lawyer.location = "$address|$lat,$lng" // Store coordinates with address
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatbotActivity", "Error geocoding with Google Maps API: ${e.message}")
            }
        }
    }



    private fun analyzeWithGemini(userMessage: String) {
        showTypingIndicator()
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
                        hideTypingIndicator()
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
                    hideTypingIndicator()
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
            val locationParts = lawyer.location.split("|")

            if (locationParts.size < 2) {
                lawyerWithDistance.distance = Double.MAX_VALUE
                lawyerWithDistance.formattedDistance = "Unknown distance"
                continue
            }

            try {
                val coords = locationParts[1].split(",")
                val lat = coords[0].toDouble()
                val lng = coords[1].toDouble()

                // Calculate distance using Haversine formula
                val distance = calculateHaversineDistance(
                    userLocation!!.latitude,
                    userLocation!!.longitude,
                    lat,
                    lng
                )

                lawyerWithDistance.distance = distance
                lawyerWithDistance.formattedDistance = formatDistance(distance)
            } catch (e: Exception) {
                lawyerWithDistance.distance = Double.MAX_VALUE
                lawyerWithDistance.formattedDistance = "Unknown distance"
            }
        }

        // Sort lawyers by distance
        lawyersList.sortBy { it.distance }
    }

    private fun calculateHaversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0 // Earth radius in kilometers
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    private fun formatDistance(distance: Double): String {
        return when {
            distance < 1.0 -> "${(distance * 1000).toInt()} m"
            else -> "%.1f km".format(distance)
        }
    }

    // Update the searchForLawyers function
    private fun searchForLawyers(specialization: String) {
        showTypingIndicator("Searching for $specialization lawyers...")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Ensure we have updated geocoding and distances
                geocodeLawyerAddresses()
                updateLawyerDistances()

                withContext(Dispatchers.Main) {
                    hideTypingIndicator()

                    val filteredLawyers = lawyersList.filter {
                        it.lawyer.specialization.equals(specialization, ignoreCase = true)
                    }.sortedBy { it.distance }

                    if (filteredLawyers.isNotEmpty()) {
                        val nearestLawyer = filteredLawyers.first()

                        // Show the nearest lawyer on map
                        showLawyerOnMap(nearestLawyer)

                        val distanceText = if (nearestLawyer.formattedDistance != "Unknown distance") {
                            "who is ${nearestLawyer.formattedDistance} away"
                        } else {
                            "in your area"
                        }

                        val resultsMessage = buildString {
                            append("I found ${filteredLawyers.size} $specialization lawyers near you. ")
                            append("The nearest is ${nearestLawyer.lawyer.name} ")
                            append("(${nearestLawyer.lawyer.lawFirm}). ")
                            append("I've shown their location on the map. ")
                            append("Would you like to see the full list or get directions?")
                        }

                        addAssistantMessage(resultsMessage)
                        conversationState.currentLawyerId = nearestLawyer.lawyer.id
                        conversationState.showingLawyerInfo = true
                    } else {
                        addAssistantMessage("I couldn't find any $specialization lawyers in our database. Would you like to try a different specialization?")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideTypingIndicator()
                    handleError("Sorry, I encountered an error while searching for lawyers. Please try again later.")
                }
            }
        }
    }

    private fun showLawyerOnMap(lawyerWithDistance: LawyerWithDistance) {
        val lawyer = lawyerWithDistance.lawyer
        val locationParts = lawyer.location.split("|")

        if (locationParts.size < 2 || userLocation == null) return

        try {
            val coords = locationParts[1].split(",")
            val lawyerLat = coords[0].toDouble()
            val lawyerLng = coords[1].toDouble()

            val clientLatLng = LatLng(userLocation!!.latitude, userLocation!!.longitude)
            val lawyerLatLng = LatLng(lawyerLat, lawyerLng)

            val intent = Intent(this, LawyerMapActivity::class.java).apply {
                putExtra("CLIENT_LOCATION", clientLatLng)
                putExtra("LAWYER_LOCATION", lawyerLatLng)
                putExtra("LAWYER", lawyer)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("ChatbotActivity", "Error showing lawyer on map: ${e.message}")
        }
    }

    private fun showLawyersList() {

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

    private fun showTypingIndicator(message: String = "Typing...") {
        progressBar.visibility = View.VISIBLE
        chatTextView.append("\nLegal Assistant: ")

        val typingText = SpannableString(message)
        // Apply the same styling as regular assistant messages
        typingText.setSpan(ForegroundColorSpan(ASSISTANT_PREFIX_COLOR), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        typingText.setSpan(BackgroundColorSpan(ASSISTANT_BACKGROUND_COLOR), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        typingText.setSpan(StyleSpan(Typeface.BOLD), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        chatTextView.append(typingText)

        // Scroll to bottom
        scrollView.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun hideTypingIndicator() {
        // Remove any typing/searching indicator
        val text = chatTextView.text.toString()
        if (text.endsWith("Legal Assistant: Typing...") ||
            text.endsWith("Legal Assistant: Searching for") ||
            text.contains("Legal Assistant: Searching for")) {
            // Find the last occurrence of "Legal Assistant:"
            val lastIndex = text.lastIndexOf("Legal Assistant:")
            if (lastIndex >= 0) {
                chatTextView.text = text.substring(0, lastIndex)
            }
        }
        progressBar.visibility = View.GONE
    }

    private fun addAssistantMessage(message: String) {
        // First show typing indicator
        showTypingIndicator()

        // Post delayed to simulate typing
        chatTextView.postDelayed({
            // Check if this message is too similar to recent messages
            if (isDuplicateOrSimilarMessage(message)) {
                Log.d("ChatbotActivity", "Prevented duplicate message: $message")
                hideTypingIndicator()
                return@postDelayed
            }

            // Remove typing indicator
            hideTypingIndicator()

            // Add to recent messages list
            recentMessages.add(message)

            // Keep only the last 5 messages to save memory
            if (recentMessages.size > 5) {
                recentMessages.removeAt(0)
            }

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
        }, 1500) // 1.5 second delay to simulate typing
    }

    private fun handleError(errorMessage: String) {
        addAssistantMessage(errorMessage)
        progressBar.visibility = View.GONE
    }
}