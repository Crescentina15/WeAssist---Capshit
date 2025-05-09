package com.remedio.weassist.Secretary

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.format.Formatter.formatFileSize
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.Models.FileAttachmentAdapter
import com.remedio.weassist.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import com.remedio.weassist.CloudinaryManager


class SetAppointmentActivity : AppCompatActivity() {

    private lateinit var databaseReference: DatabaseReference
    private lateinit var dateDropdown: AutoCompleteTextView
    private lateinit var timeDropdown: AutoCompleteTextView
    private lateinit var editFullName: TextInputEditText
    private lateinit var editProblem: TextInputEditText
    private lateinit var btnSetAppointment: MaterialButton
    private lateinit var backArrow: ImageButton

    private val uploadedFileUrls = mutableListOf<String>()
    private var filesToUploadCount = AtomicInteger(0)
    private var successfullyUploadedFiles = AtomicInteger(0)

    private lateinit var btnAddFile: MaterialButton
    private lateinit var fileAttachmentRecyclerView: RecyclerView
    private lateinit var noAttachmentsView: LinearLayout
    private val attachedFiles = mutableListOf<FileAttachment>()
    private lateinit var fileAdapter: FileAttachmentAdapter


    // Add a constant for file picker request code
    companion object {
        private const val PICK_FILE_REQUEST_CODE = 123
    }
    // Add a data class for file attachments
    data class FileAttachment(
        val uri: Uri,
        val fileName: String,
        val fileSize: String
    )

    private var lawyerId: String? = null
    private var selectedDate: String? = null
    private var selectedTime: String? = null
    private var clientId: String? = null

    private var datesList = mutableListOf<String>()
    private var timeSlotMap = mutableMapOf<String, List<TimeSlotInfo>>()

    private val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.US)
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
    private val fullDateTimeFormat = SimpleDateFormat("MM/dd/yyyy h:mm a", Locale.US)

    // Data class to represent time slot information
    data class TimeSlotInfo(
        val timeSlot: String,
        val isAvailable: Boolean
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_set_appointment)

        // Initialize Cloudinary if not already done
        try {
            MediaManager.get()
        } catch (e: IllegalStateException) {
            CloudinaryManager.initialize(this)
        }

        lawyerId = intent.getStringExtra("LAWYER_ID")

        // Find views using the updated IDs from the new layout
        dateDropdown = findViewById(R.id.date_dropdown)
        timeDropdown = findViewById(R.id.time_dropdown)
        editFullName = findViewById(R.id.edit_full_name)
        editProblem = findViewById(R.id.edit_problem)
        btnSetAppointment = findViewById(R.id.btn_set_appointment)
        backArrow = findViewById(R.id.back_arrow)

        btnAddFile = findViewById(R.id.btn_add_file)
        fileAttachmentRecyclerView = findViewById(R.id.file_attachment_recycler_view)
        noAttachmentsView = findViewById(R.id.no_attachments_view)

        setupFileAttachmentSection()

        clientId = FirebaseAuth.getInstance().currentUser?.uid

        backArrow.setOnClickListener {
            finish()
        }

        if (clientId != null) {
            fetchClientName(clientId!!)
        } else {
            Log.e("SetAppointmentActivity", "Client ID is null, user not logged in")
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
        }

        if (lawyerId != null) {
            fetchAvailabilityAndBookings(lawyerId!!)
        } else {
            Log.e("SetAppointmentActivity", "Lawyer ID is null")
            Toast.makeText(this, "Lawyer not found", Toast.LENGTH_SHORT).show()
        }

        setupDateDropdown()
        setupTimeDropdown()

        btnSetAppointment.setOnClickListener {
            saveAppointment()
        }
    }
    private fun setupFileAttachmentSection() {
        // Set up the RecyclerView
        fileAdapter = FileAttachmentAdapter(attachedFiles) { position ->
            // Handle file removal
            attachedFiles.removeAt(position)
            fileAdapter.notifyItemRemoved(position)
            updateAttachmentVisibility()
        }

        fileAttachmentRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@SetAppointmentActivity)
            adapter = fileAdapter
            setHasFixedSize(true)
        }

        btnAddFile.apply {
            text = "Add File"
            setOnClickListener { openFilePicker() }
        }

        updateAttachmentVisibility()
    }
    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(Intent.createChooser(intent, "Select File"), PICK_FILE_REQUEST_CODE)
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FILE_REQUEST_CODE && resultCode == RESULT_OK) {
            data?.let { handleFileSelection(it) }
        }
    }
    private fun handleFileSelection(data: Intent) {
        // Handle multiple file selection
        if (data.clipData != null) {
            val clipData = data.clipData!!
            for (i in 0 until clipData.itemCount) {
                val uri = clipData.getItemAt(i).uri
                addFileToList(uri)
            }
        }
        // Handle single file selection
        else if (data.data != null) {
            val uri = data.data!!
            addFileToList(uri)
        }
    }
    private fun addFileToList(uri: Uri) {
        // Get file details
        val fileName = getFileName(uri)
        val fileSize = getFileSize(uri)

        // Add to list and update UI
        attachedFiles.add(FileAttachment(uri, fileName, fileSize))
        fileAdapter.notifyItemInserted(attachedFiles.size - 1)
        updateAttachmentVisibility()
    }

    private fun updateAttachmentVisibility() {
        if (attachedFiles.isEmpty()) {
            fileAttachmentRecyclerView.visibility = View.GONE
            noAttachmentsView.visibility = View.VISIBLE
        } else {
            fileAttachmentRecyclerView.visibility = View.VISIBLE
            noAttachmentsView.visibility = View.GONE
        }
    }
    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = it.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.substring(cut!! + 1)
            }
        }
        return result ?: "Unknown file"
    }
    private fun getFileSize(uri: Uri): String {
        var fileSize: Long = 0
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        fileSize = it.getLong(sizeIndex)
                    }
                }
            }
        }
        return formatFileSize(fileSize)
    }
    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }




    private fun fetchClientName(clientId: String) {
        val userRef = FirebaseDatabase.getInstance().getReference("Users").child(clientId)

        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val firstName = snapshot.child("firstName").getValue(String::class.java)
                    val lastName = snapshot.child("lastName").getValue(String::class.java)

                    if (!firstName.isNullOrEmpty() && !lastName.isNullOrEmpty()) {
                        val fullName = "$firstName $lastName"
                        editFullName.setText(fullName)
                    } else {
                        Toast.makeText(applicationContext, "Client name not found", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(applicationContext, "Failed to load client name", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun fetchAvailabilityAndBookings(lawyerId: String) {
        val availabilityRef = FirebaseDatabase.getInstance()
            .getReference("lawyers")
            .child(lawyerId)
            .child("availability")

        // First, fetch all available time slots
        availabilityRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    // Temporary map to store all available time slots
                    val tempTimeSlotMap = mutableMapOf<String, MutableList<TimeSlotInfo>>()
                    datesList.clear()
                    datesList.add("Select Date")

                    // Get current date and time for filtering
                    val currentDateTime = Calendar.getInstance().time

                    for (availabilitySnapshot in snapshot.children) {
                        val date = availabilitySnapshot.child("date").getValue(String::class.java)
                        val startTime = availabilitySnapshot.child("startTime").getValue(String::class.java)
                        val endTime = availabilitySnapshot.child("endTime").getValue(String::class.java)

                        if (date != null && startTime != null && endTime != null) {
                            // Check if this slot is in the future
                            if (isTimeSlotInFuture(date, startTime)) {
                                val timeSlot = "$startTime - $endTime"

                                if (!datesList.contains(date)) {
                                    datesList.add(date)
                                }

                                val timeSlots = tempTimeSlotMap[date] ?: mutableListOf()
                                timeSlots.add(TimeSlotInfo(timeSlot, true)) // All slots are initially available
                                tempTimeSlotMap[date] = timeSlots
                            }
                        }
                    }

                    // Now fetch all existing appointments to find booked slots
                    fetchExistingAppointments(lawyerId, tempTimeSlotMap)
                } else {
                    Toast.makeText(applicationContext, "No availability found", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(applicationContext, "Failed to load availability", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // Add this helper method to check if a time slot is in the future
    private fun isTimeSlotInFuture(dateStr: String, startTimeStr: String): Boolean {
        try {
            // Parse the appointment date and time
            val slotDateTime = parseDateTime(dateStr, startTimeStr)

            // Get current date and time
            val currentDateTime = Calendar.getInstance().time

            // Compare with current time
            return slotDateTime.after(currentDateTime)
        } catch (e: Exception) {
            Log.e("SetAppointmentActivity", "Error parsing date/time: ${e.message}")
            return true // Default to showing the slot if there's a parsing error
        }
    }
    // Helper method to parse date and time strings into a Date object
    private fun parseDateTime(dateStr: String, timeStr: String): Date {
        try {
            // Combine date and start time
            val dateTimeStr = "$dateStr $timeStr"
            return fullDateTimeFormat.parse(dateTimeStr) ?: Date()
        } catch (e: Exception) {
            Log.e("SetAppointmentActivity", "Error parsing date/time: ${e.message}")
            throw e
        }
    }

    private fun fetchExistingAppointments(lawyerId: String, availableTimeSlots: MutableMap<String, MutableList<TimeSlotInfo>>) {
        // First, fetch existing appointments (booked time slots)
        val appointmentsRef = FirebaseDatabase.getInstance().getReference("appointments")

        appointmentsRef.orderByChild("lawyerId").equalTo(lawyerId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (appointmentSnapshot in snapshot.children) {
                            val date = appointmentSnapshot.child("date").getValue(String::class.java)
                            val time = appointmentSnapshot.child("time").getValue(String::class.java)

                            if (date != null && time != null) {
                                availableTimeSlots[date]?.let { timeSlots ->
                                    for (i in timeSlots.indices) {
                                        if (timeSlots[i].timeSlot == time) {
                                            timeSlots[i] = TimeSlotInfo(timeSlots[i].timeSlot, false)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Now check for blocked time slots (from deleted appointments)
                    val blockedSlotsRef = FirebaseDatabase.getInstance()
                        .getReference("lawyers")
                        .child(lawyerId)
                        .child("blockedTimeSlots")

                    blockedSlotsRef.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(blockedSnapshot: DataSnapshot) {
                            if (blockedSnapshot.exists()) {
                                for (slotSnapshot in blockedSnapshot.children) {
                                    val date = slotSnapshot.child("date").getValue(String::class.java)
                                    val time = slotSnapshot.child("time").getValue(String::class.java)

                                    if (date != null && time != null) {
                                        availableTimeSlots[date]?.let { timeSlots ->
                                            for (i in timeSlots.indices) {
                                                if (timeSlots[i].timeSlot == time) {
                                                    timeSlots[i] = TimeSlotInfo(timeSlots[i].timeSlot, false)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Filter dates with no available slots or past dates
                            val filteredDates = mutableListOf<String>("Select Date")
                            val filteredTimeSlotMap = mutableMapOf<String, List<TimeSlotInfo>>()

                            // Get current date for same-day time filtering
                            val currentDate = dateFormat.format(Calendar.getInstance().time)

                            for ((date, timeSlots) in availableTimeSlots) {
                                // If it's today, filter out past time slots
                                if (date == currentDate) {
                                    val currentTimeSlots = timeSlots.filter {
                                        it.isAvailable && isTimeSlotInFuture(date, it.timeSlot.split(" - ")[0].trim())
                                    }
                                    if (currentTimeSlots.isNotEmpty()) {
                                        filteredDates.add(date)
                                        filteredTimeSlotMap[date] = currentTimeSlots
                                    }
                                }
                                // For future dates, just check if there are available slots
                                else {
                                    val availableSlots = timeSlots.filter { it.isAvailable }
                                    if (availableSlots.isNotEmpty() && isDateInFuture(date)) {
                                        filteredDates.add(date)
                                        filteredTimeSlotMap[date] = availableSlots
                                    }
                                }
                            }

                            // Update the class variables with filtered data
                            datesList.clear()
                            datesList.addAll(filteredDates)
                            timeSlotMap.clear()
                            timeSlotMap.putAll(filteredTimeSlotMap)

                            // Now setup the UI with complete data
                            setupDateDropdown()
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.e("SetAppointmentActivity", "Failed to fetch blocked slots: ${error.message}")

                            // Fallback - process without blocked slots information
                            // Same filtering logic as above
                            val filteredDates = mutableListOf<String>("Select Date")
                            val filteredTimeSlotMap = mutableMapOf<String, List<TimeSlotInfo>>()

                            val currentDate = dateFormat.format(Calendar.getInstance().time)

                            for ((date, timeSlots) in availableTimeSlots) {
                                if (date == currentDate) {
                                    val currentTimeSlots = timeSlots.filter {
                                        it.isAvailable && isTimeSlotInFuture(date, it.timeSlot.split(" - ")[0].trim())
                                    }
                                    if (currentTimeSlots.isNotEmpty()) {
                                        filteredDates.add(date)
                                        filteredTimeSlotMap[date] = currentTimeSlots
                                    }
                                } else {
                                    val availableSlots = timeSlots.filter { it.isAvailable }
                                    if (availableSlots.isNotEmpty() && isDateInFuture(date)) {
                                        filteredDates.add(date)
                                        filteredTimeSlotMap[date] = availableSlots
                                    }
                                }
                            }

                            datesList.clear()
                            datesList.addAll(filteredDates)
                            timeSlotMap.clear()
                            timeSlotMap.putAll(filteredTimeSlotMap)
                            setupDateDropdown()
                        }
                    })
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("SetAppointmentActivity", "Failed to fetch appointments: ${error.message}")

                    // Fallback - filter available slots without booking information
                    val filteredDates = mutableListOf<String>("Select Date")
                    val filteredTimeSlotMap = mutableMapOf<String, List<TimeSlotInfo>>()

                    // Filter out past dates
                    for ((date, timeSlots) in availableTimeSlots) {
                        if (isDateInFuture(date)) {
                            val availableSlots = timeSlots.filter { it.isAvailable }
                            if (availableSlots.isNotEmpty()) {
                                filteredDates.add(date)
                                filteredTimeSlotMap[date] = availableSlots
                            }
                        }
                    }

                    datesList.clear()
                    datesList.addAll(filteredDates)
                    timeSlotMap.clear()
                    timeSlotMap.putAll(filteredTimeSlotMap)
                    setupDateDropdown()
                }
            })
    }

    // Helper method to check if a date is in the future
    private fun isDateInFuture(dateStr: String): Boolean {
        try {
            val appointmentDate = dateFormat.parse(dateStr) ?: return false

            // Create a calendar for the appointment date (midnight)
            val appointmentCal = Calendar.getInstance()
            appointmentCal.time = appointmentDate

            // Create a calendar for today (midnight)
            val todayCal = Calendar.getInstance()
            todayCal.set(Calendar.HOUR_OF_DAY, 0)
            todayCal.set(Calendar.MINUTE, 0)
            todayCal.set(Calendar.SECOND, 0)
            todayCal.set(Calendar.MILLISECOND, 0)

            // Compare dates
            return !appointmentCal.before(todayCal)
        } catch (e: Exception) {
            Log.e("SetAppointmentActivity", "Error parsing date: ${e.message}")
            return true // Default to showing the slot if there's a parsing error
        }
    }

    private fun setupDateDropdown() {
        val dateAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, datesList)
        dateDropdown.setAdapter(dateAdapter)

        dateDropdown.setOnItemClickListener { _, _, position, _ ->
            if (position > 0) {
                selectedDate = datesList[position]
                updateTimeDropdown(timeSlotMap[selectedDate] ?: emptyList())
            } else {
                selectedDate = null
                updateTimeDropdown(emptyList())
            }
        }

        // Clear any previous selection
        dateDropdown.setText("", false)
        dateDropdown.hint = "Choose a date"
    }

    private fun setupTimeDropdown() {
        timeDropdown.setOnItemClickListener { _, _, position, _ ->
            val times = timeSlotMap[selectedDate] ?: emptyList()
            if (position >= 0 && position < times.size) {
                selectedTime = times[position].timeSlot
                btnSetAppointment.isEnabled = true
            } else {
                selectedTime = null
                btnSetAppointment.isEnabled = false
            }
        }

        // Clear any previous selection
        timeDropdown.setText("", false)
        timeDropdown.hint = "Choose a time slot"
    }

    private fun updateTimeDropdown(timeSlots: List<TimeSlotInfo>) {
        // Filter out unavailable time slots and create display list
        val availableTimeSlots = timeSlots.filter { it.isAvailable }
        val displayTimeSlots = availableTimeSlots.map { it.timeSlot }

        val timeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, displayTimeSlots)
        timeDropdown.setAdapter(timeAdapter)

        // Clear any previous selection
        timeDropdown.setText("", false)

        if (availableTimeSlots.isEmpty()) {
            timeDropdown.hint = "No times available"
            btnSetAppointment.isEnabled = false
        } else {
            timeDropdown.hint = "Choose a time slot"
        }
    }

    private fun saveAppointment() {
        val fullName = editFullName.text.toString().trim()
        val problem = editProblem.text.toString().trim()

        if (lawyerId == null) {
            Toast.makeText(this, "Lawyer information not available", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedDate == null || selectedDate == "Select Date") {
            Toast.makeText(this, "Please select a date", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedTime == null || selectedTime?.isEmpty() == true) {
            Toast.makeText(this, "Please select a time", Toast.LENGTH_SHORT).show()
            return
        }

        // Additional check to ensure the selected time is available
        val selectedTimeSlotInfo = timeSlotMap[selectedDate]?.find { it.timeSlot == selectedTime }
        if (selectedTimeSlotInfo == null || !selectedTimeSlotInfo.isAvailable) {
            Toast.makeText(this, "This time slot is not available. Please select another time.", Toast.LENGTH_SHORT).show()
            return
        }

        if (fullName.isEmpty()) {
            Toast.makeText(this, "Name is required", Toast.LENGTH_SHORT).show()
            return
        }

        if (problem.isEmpty()) {
            Toast.makeText(this, "Please describe your problem", Toast.LENGTH_SHORT).show()
            return
        }

        if (clientId == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        btnSetAppointment.isEnabled = false

        if (attachedFiles.isNotEmpty()) {
            uploadFilesThenCreateAppointment(fullName, problem)
        } else {
            createAppointment(fullName, problem, emptyList())
        }
    }

    private fun uploadFilesThenCreateAppointment(fullName: String, problem: String) {
        uploadedFileUrls.clear()
        filesToUploadCount.set(attachedFiles.size)
        successfullyUploadedFiles.set(0)

        attachedFiles.forEach { fileAttachment ->
            val requestId = MediaManager.get().upload(fileAttachment.uri)
                .option("resource_type", "auto") // Handles both images and other file types
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String) {
                        Log.d("Cloudinary", "Upload started for ${fileAttachment.fileName}")
                    }

                    override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {
                        // You could show progress if needed
                    }

                    override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                        val secureUrl = resultData["secure_url"] as? String
                        if (secureUrl != null) {
                            uploadedFileUrls.add(secureUrl)
                            successfullyUploadedFiles.incrementAndGet()
                        }

                        // Check if all files are processed
                        if (successfullyUploadedFiles.get() + (filesToUploadCount.get() - successfullyUploadedFiles.get()) == filesToUploadCount.get()) {
                            if (successfullyUploadedFiles.get() > 0) {
                                createAppointment(fullName, problem, uploadedFileUrls)
                            } else {
                                runOnUiThread {
                                    btnSetAppointment.isEnabled = true
                                    Toast.makeText(this@SetAppointmentActivity, "Failed to upload files. Please try again.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }

                    override fun onError(requestId: String, error: ErrorInfo) {
                        Log.e("Cloudinary", "Upload error for ${fileAttachment.fileName}: ${error.description}")

                        // Check if all files are processed
                        if (successfullyUploadedFiles.get() + (filesToUploadCount.get() - successfullyUploadedFiles.get()) == filesToUploadCount.get()) {
                            if (successfullyUploadedFiles.get() > 0) {
                                createAppointment(fullName, problem, uploadedFileUrls)
                            } else {
                                runOnUiThread {
                                    btnSetAppointment.isEnabled = true
                                    Toast.makeText(this@SetAppointmentActivity, "Failed to upload files. Please try again.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }

                    override fun onReschedule(requestId: String, error: ErrorInfo) {
                        Log.d("Cloudinary", "Upload rescheduled for ${fileAttachment.fileName}")
                    }
                })
                .dispatch()
        }
    }

    private fun createAppointment(fullName: String, problem: String, fileUrls: List<String>) {
        val appointmentRef = FirebaseDatabase.getInstance().getReference("appointments")
        val appointmentId = appointmentRef.push().key

        if (appointmentId != null) {
            val appointmentData = hashMapOf(
                "appointmentId" to appointmentId,
                "clientId" to clientId,
                "lawyerId" to lawyerId,
                "date" to selectedDate,
                "time" to selectedTime,
                "fullName" to fullName,
                "problem" to problem,
                "status" to "Pending",
                "attachments" to fileUrls
            )

            appointmentRef.child(appointmentId).setValue(appointmentData)
                .addOnSuccessListener {
                    // Notify the client about the appointment
                    sendNotificationToClient(lawyerId!!, selectedDate!!, selectedTime!!)

                    // Notify the lawyer about the new appointment
                    sendNotificationToLawyer(lawyerId!!, selectedDate!!, selectedTime!!)

                    // Send notification to all secretaries associated with this lawyer
                    sendNotificationToSecretaries(lawyerId!!, selectedDate!!, selectedTime!!, appointmentId)

                    // Create a conversation with the problem description as first message
                    createConversationWithProblem(lawyerId!!, problem, appointmentId)

                    Toast.makeText(this, "Appointment set successfully!", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener {
                    btnSetAppointment.isEnabled = true
                    Toast.makeText(this, "Failed to set appointment", Toast.LENGTH_SHORT).show()
                }
        } else {
            btnSetAppointment.isEnabled = true
            Toast.makeText(this, "Failed to generate appointment ID", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createConversationWithProblem(lawyerId: String, problem: String, appointmentId: String) {
        if (clientId != null) {
            // Get the secretary ID for this lawyer
            val lawyerRef = FirebaseDatabase.getInstance().getReference("lawyers").child(lawyerId)
            lawyerRef.child("secretaryId").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val secretaryId = snapshot.getValue(String::class.java)
                        if (secretaryId != null && secretaryId.isNotEmpty()) {
                            // Generate conversation ID
                            val conversationId = generateConversationId(clientId!!, secretaryId)

                            // Reset forwarding state for any previous conversation
                            resetForwardingState(conversationId)

                            // Create the message
                            val message = mapOf(
                                "senderId" to clientId,
                                "receiverId" to secretaryId,
                                "message" to problem,
                                "timestamp" to System.currentTimeMillis()
                            )

                            // Save the message to the conversation
                            val conversationRef = FirebaseDatabase.getInstance()
                                .getReference("conversations")
                                .child(conversationId)

                            conversationRef.child("messages").push().setValue(message)

                            // Add participants to the conversation
                            val participantsMap = mapOf(
                                clientId!! to true,
                                secretaryId to true,
                                lawyerId to false // Initially set to false for the lawyer
                            )
                            conversationRef.child("participantIds").updateChildren(participantsMap)

                            // Store metadata
                            conversationRef.child("appointedLawyerId").setValue(lawyerId)
                            conversationRef.child("appointmentId").setValue(appointmentId)
                            conversationRef.child("problem").setValue(problem)

                            // Increment unread counter for secretary
                            incrementUnreadCounter(conversationId, secretaryId)

                            // Update secretary's notification
                            updateNotificationWithConversationId(secretaryId, appointmentId, conversationId)

                            Log.d("SetAppointmentActivity", "Created conversation and reset forwarding state")
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("SetAppointmentActivity", "Failed to fetch secretary ID: ${error.message}")
                }
            })
        }
    }

    private fun resetForwardingState(conversationId: String) {
        val conversationRef = FirebaseDatabase.getInstance().getReference("conversations").child(conversationId)
        conversationRef.child("forwarded").setValue(false)
        conversationRef.child("handledByLawyer").setValue(false)
        conversationRef.child("appointedLawyerId").removeValue()
        conversationRef.child("appointmentId").removeValue() // Ensure previous appointment ID is cleared
        conversationRef.child("problem").removeValue() // Ensure previous problem description is cleared
        conversationRef.child("forwardedFromSecretary").setValue(false)
        conversationRef.child("secretaryActive").setValue(true)
        Log.d("SetAppointmentActivity", "Forwarding state reset for conversation: $conversationId")
    }

    private fun generateConversationId(user1: String, user2: String): String {
        return if (user1 < user2) {
            "conversation_${user1}_${user2}"
        } else {
            "conversation_${user2}_${user1}"
        }
    }

    private fun incrementUnreadCounter(conversationId: String, userId: String) {
        val unreadRef = FirebaseDatabase.getInstance().getReference("conversations")
            .child(conversationId)
            .child("unreadMessages").child(userId)

        unreadRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val currentCount = snapshot.getValue(Int::class.java) ?: 0
                unreadRef.setValue(currentCount + 1)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SetAppointmentActivity", "Error updating unread count: ${error.message}")
            }
        })
    }

    private fun updateNotificationWithConversationId(secretaryId: String, appointmentId: String, conversationId: String) {
        val notificationsRef = FirebaseDatabase.getInstance().getReference("notifications")
            .child(secretaryId)

        // Find the notification we just created for this appointment
        notificationsRef.orderByChild("appointmentId").equalTo(appointmentId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (notificationSnapshot in snapshot.children) {
                            // Update the notification with the conversation ID
                            notificationSnapshot.ref.child("conversationId").setValue(conversationId)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("SetAppointmentActivity", "Failed to update notification: ${error.message}")
                }
            })
    }

    // The notification and utility functions remain the same
    private fun sendNotificationToClient(lawyerId: String, date: String, time: String) {
        // Get the lawyer reference
        val lawyerRef = FirebaseDatabase.getInstance().getReference("lawyers").child(lawyerId)

        lawyerRef.get().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val snapshot = task.result

                // Get lawyer name for the message content only
                val lawyerName = if (snapshot != null && snapshot.exists() && snapshot.child("name").exists()) {
                    snapshot.child("name").getValue(String::class.java) ?: "your lawyer"
                } else {
                    "your lawyer"
                }

                // Create notification with correct fields
                if (clientId != null) {
                    val notificationRef = FirebaseDatabase.getInstance().getReference("notifications").child(clientId!!)
                    val notificationId = notificationRef.push().key

                    if (notificationId != null) {
                        val message = "You have set an appointment with Attorney. $lawyerName on $date at $time."

                        val notificationData = HashMap<String, Any>()
                        notificationData["id"] = notificationId
                        notificationData["senderId"] = lawyerId
                        notificationData["senderName"] = ""  // Explicitly use "Appointment Confirmation" as the header
                        notificationData["message"] = message
                        notificationData["timestamp"] = System.currentTimeMillis()
                        notificationData["type"] = "appointment"
                        notificationData["isRead"] = false

                        notificationRef.child(notificationId).setValue(notificationData)
                            .addOnSuccessListener {
                                Log.d("SetAppointmentActivity", "Notification sent successfully")
                            }
                            .addOnFailureListener { e ->
                                Log.e("SetAppointmentActivity", "Failed to send notification: ${e.message}")
                            }
                    }
                }
            } else {
                Log.e("SetAppointmentActivity", "Failed to get lawyer data", task.exception)
            }
        }
    }

    private fun sendNotificationToLawyer(lawyerId: String, date: String, time: String) {
        val notificationRef = FirebaseDatabase.getInstance().getReference("notifications").child(lawyerId)
        val notificationId = notificationRef.push().key

        if (notificationId != null) {
            val currentTimestamp = System.currentTimeMillis().toString()

            val notificationData = mapOf(
                "notificationId" to notificationId,
                "message" to "Your appointment on $date at $time has been accepted.",
                "timestamp" to currentTimestamp
            )

            notificationRef.child("recent").setValue(notificationData)
                .addOnSuccessListener {
                    Log.d("SetAppointmentActivity", "Recent appointment updated for lawyer: $lawyerId")

                    // Move previous "recent" to "earlier" before replacing it
                    notificationRef.child("recent").addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.exists()) {
                                val previousData = snapshot.value
                                notificationRef.child("earlier").setValue(previousData)
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.e("SetAppointmentActivity", "Failed to update earlier notification: ${error.message}")
                        }
                    })
                }
                .addOnFailureListener {
                    Log.e("SetAppointmentActivity", "Failed to update notification")
                }
        }
    }

    private fun sendNotificationToSecretaries(lawyerId: String, date: String, time: String, appointmentId: String) {
        // Get the secretaryID for this lawyer
        val lawyerRef = FirebaseDatabase.getInstance().getReference("lawyers").child(lawyerId)

        lawyerRef.child("secretaryId").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val secretaryId = snapshot.getValue(String::class.java)
                    if (secretaryId != null && secretaryId.isNotEmpty()) {
                        // Send notification to the secretary
                        val notificationRef = FirebaseDatabase.getInstance().getReference("notifications").child(secretaryId)
                        val notificationId = notificationRef.push().key

                        if (notificationId != null) {
                            // Get client name for the notification message
                            val clientName = editFullName.text.toString().trim()

                            val notificationData = mapOf(
                                "id" to notificationId,  // Changed from "notificationId" to match your NotificationItem class
                                "senderId" to clientId,
                                "message" to "New appointment: $clientName on $date at $time",
                                "timestamp" to System.currentTimeMillis(),  // Using Long directly instead of String
                                "type" to "appointment",
                                "isRead" to false,
                                "appointmentId" to appointmentId // For navigation to appointment details
                            )

                            notificationRef.child(notificationId).setValue(notificationData)
                                .addOnSuccessListener {
                                    Log.d("SetAppointmentActivity", "Notification sent to secretary: $secretaryId")
                                }
                                .addOnFailureListener {
                                    Log.e("SetAppointmentActivity", "Failed to send notification to secretary: ${it.message}")
                                }
                        }
                    } else {
                        Log.d("SetAppointmentActivity", "No secretary associated with this lawyer")
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SetAppointmentActivity", "Failed to fetch secretary ID: ${error.message}")
            }
        })
    }
}