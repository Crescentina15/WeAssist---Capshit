package com.remedio.weassist.Clients

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.Lawyer.Lawyer
import com.remedio.weassist.Lawyer.LawyerAdapter
import com.remedio.weassist.Lawyer.LawyerBackgroundActivity
import com.remedio.weassist.Miscellaneous.ChatbotActivity
import com.remedio.weassist.Lawyer.LawyersListActivity
import com.remedio.weassist.R

class ClientHomeFragment : Fragment() {

    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var welcomeMessageTextView: TextView
    private lateinit var specializationsLayout: GridLayout
    private lateinit var profileImageView: ImageView
    private lateinit var topLawyerRecyclerView: RecyclerView
    private lateinit var topLawyerAdapter: LawyerAdapter
    private lateinit var specializationSpinner: Spinner
    private var topLawyersList = mutableListOf<Lawyer>()
    private var allSpecializations = mutableListOf<String>()
    private var allTopLawyers = mutableListOf<Lawyer>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_client_home, container, false)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("lawyers")

        // Initialize UI elements
        welcomeMessageTextView = view.findViewById(R.id.welcome_message)
        specializationsLayout = view.findViewById(R.id.specializations_layout)
        val searchButton: Button = view.findViewById(R.id.search_button)
        val notificationButton: ImageButton = view.findViewById(R.id.notification_icon)
        profileImageView = view.findViewById(R.id.profile_image)
        topLawyerRecyclerView = view.findViewById(R.id.top_lawyer_list)
        specializationSpinner = view.findViewById(R.id.specialization_spinner)

        setupTopLawyersRecyclerView()
        setupSpecializationSpinner()

        // Fetch user's first name and profile image URL from Firebase
        auth.currentUser?.let { fetchUserData(it.uid) } ?: run {
            Toast.makeText(context, "User not logged in", Toast.LENGTH_SHORT).show()
        }

        // Setup notification badge
        setupNotificationBadge(notificationButton)

        // Click listener for chatbot
        searchButton.setOnClickListener {
            val intent = Intent(requireContext(), ChatbotActivity::class.java)
            startActivity(intent)
        }

        // Click listener for notifications
        notificationButton.setOnClickListener {
            val intent = Intent(requireContext(), ClientNotificationActivity::class.java)
            startActivity(intent)
        }

        // Fetch specializations and dynamically create buttons
        fetchSpecializations()
        fetchTopLawyers()

        return view
    }

    private fun setupSpecializationSpinner() {
        // Add "All Specializations" as the first option
        val spinnerOptions = mutableListOf<String>().apply {
            add("All Specializations")
        }

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            spinnerOptions
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        specializationSpinner.adapter = adapter

        specializationSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedSpecialization = if (position == 0) null else parent?.getItemAtPosition(position).toString()
                filterTopLawyers(selectedSpecialization)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                filterTopLawyers(null)
            }
        }
    }

    private fun filterTopLawyers(specialization: String?) {
        if (specialization == null || specialization == "All Specializations") {
            topLawyersList.clear()
            topLawyersList.addAll(allTopLawyers)
        } else {
            topLawyersList.clear()
            topLawyersList.addAll(allTopLawyers.filter { it.specialization == specialization })
        }
        topLawyerAdapter.notifyDataSetChanged()

        if (topLawyersList.isEmpty()) {
            view?.findViewById<TextView>(R.id.top_lawyer_title)?.text =
                "No Top Rated Lawyers Available"
        } else {
            view?.findViewById<TextView>(R.id.top_lawyer_title)?.text = "Top Lawyer"
        }
    }

    private fun setupTopLawyersRecyclerView() {
        topLawyerRecyclerView.layoutManager = LinearLayoutManager(
            context,
            LinearLayoutManager.HORIZONTAL,
            false
        )

        topLawyerAdapter = LawyerAdapter(
            lawyersList = topLawyersList,
            onLawyerClick = { lawyer ->
                val intent = Intent(requireContext(), LawyerBackgroundActivity::class.java)
                intent.putExtra("LAWYER_ID", lawyer.id)
                startActivity(intent)
            },
            isTopLawyer = true
        )

        topLawyerRecyclerView.adapter = topLawyerAdapter
    }

    private fun fetchTopLawyers() {
        database.orderByChild("rate").equalTo("500")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val tempList = mutableListOf<Lawyer>()
                    for (lawyerSnapshot in snapshot.children) {
                        val lawyer = lawyerSnapshot.getValue(Lawyer::class.java)
                        lawyer?.let {
                            if (it.rate == "500") {
                                tempList.add(it)
                            }
                        }
                    }
                    allTopLawyers.clear()
                    allTopLawyers.addAll(tempList)
                    topLawyersList.clear()
                    topLawyersList.addAll(allTopLawyers)
                    topLawyerAdapter.notifyDataSetChanged()

                    if (topLawyersList.isEmpty()) {
                        view?.findViewById<TextView>(R.id.top_lawyer_title)?.text =
                            "No Top Rated Lawyers Available"
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(context, "Failed to load top lawyers", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun fetchUserData(userId: String) {
        val userRef = FirebaseDatabase.getInstance().getReference("Users").child(userId)

        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded || isDetached || activity == null) {
                    return
                }

                if (snapshot.exists()) {
                    val firstName = snapshot.child("firstName").getValue(String::class.java)
                    if (!firstName.isNullOrEmpty()) {
                        welcomeMessageTextView.text = "Welcome!\n$firstName"
                    } else {
                        Log.e("Firebase", "First name is null or empty")
                        welcomeMessageTextView.text = "Welcome!"
                    }

                    val profileImageUrl = snapshot.child("profileImageUrl").getValue(String::class.java)
                    if (!profileImageUrl.isNullOrEmpty()) {
                        if (isAdded && !isDetached && activity != null) {
                            Glide.with(requireContext()).load(profileImageUrl).into(profileImageView)
                        }
                    } else {
                        Log.e("Firebase", "Profile image URL is null or empty")
                    }
                } else {
                    Log.e("Firebase", "User data not found")
                    welcomeMessageTextView.text = "Welcome!"
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Database error: ${error.message}")
                welcomeMessageTextView.text = "Welcome!"
            }
        })
    }

    private fun setupNotificationBadge(notificationButton: ImageButton) {
        val badge = LayoutInflater.from(context).inflate(R.layout.notification_badge, null)
        val tvBadgeCount = badge.findViewById<TextView>(R.id.tvBadgeCount)

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.gravity = Gravity.END or Gravity.TOP
        params.setMargins(0, 0, 0, 0)

        val buttonParent = notificationButton.parent as ViewGroup
        val buttonIndex = buttonParent.indexOfChild(notificationButton)

        val frameLayout = FrameLayout(requireContext())

        buttonParent.removeView(notificationButton)
        frameLayout.addView(notificationButton)
        frameLayout.addView(badge, params)
        buttonParent.addView(frameLayout, buttonIndex)

        badge.visibility = View.GONE

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val notificationsRef = FirebaseDatabase.getInstance().getReference("notifications").child(currentUserId)
        notificationsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var unreadCount = 0

                for (notificationSnapshot in snapshot.children) {
                    val isRead = notificationSnapshot.child("isRead").getValue(Boolean::class.java) ?: false
                    if (!isRead) {
                        unreadCount++
                    }
                }

                if (unreadCount > 0) {
                    badge.visibility = View.VISIBLE
                    tvBadgeCount.text = if (unreadCount > 99) "99+" else unreadCount.toString()
                } else {
                    badge.visibility = View.GONE
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ClientHomeFragment", "Error loading notification count: ${error.message}")
            }
        })
    }

    private fun fetchSpecializations() {
        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val specializations = mutableSetOf<String>()

                for (lawyerSnapshot in snapshot.children) {
                    val specialization = lawyerSnapshot.child("specialization").getValue(String::class.java)
                    if (!specialization.isNullOrEmpty()) {
                        specializations.add(specialization)
                    }
                }

                allSpecializations.clear()
                allSpecializations.addAll(specializations.sorted())

                // Update spinner with specializations
                val spinnerAdapter = specializationSpinner.adapter as ArrayAdapter<String>
                spinnerAdapter.clear()
                spinnerAdapter.add("All Specializations")
                spinnerAdapter.addAll(allSpecializations)
                spinnerAdapter.notifyDataSetChanged()

                createSpecializationButtons(allSpecializations)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Failed to fetch specializations: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun createSpecializationButtons(specializations: List<String>) {
        val context = context ?: return
        val resources = resources ?: return
        val container = view?.findViewById<GridLayout>(R.id.specializations_layout) ?: return

        container.removeAllViews()

        val buttonPadding = resources.getDimensionPixelSize(R.dimen.button_padding)
        val buttonCornerRadius = resources.getDimensionPixelSize(R.dimen.button_corner_radius)
        val buttonElevation = resources.getDimensionPixelSize(R.dimen.button_elevation)

        for (specialization in specializations) {
            val button = Button(context).apply {
                text = specialization
                layoutParams = GridLayout.LayoutParams().apply {
                    width = resources.getDimensionPixelSize(R.dimen.button_width)
                    height = resources.getDimensionPixelSize(R.dimen.button_height)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(8, 8, 8, 8)
                }

                setBackgroundResource(R.drawable.specialization_button_background)
                setTextColor(context.getColor(R.color.white))
                textSize = 14f
                setPadding(buttonPadding, buttonPadding, buttonPadding, buttonPadding)
                foreground = context.getDrawable(R.drawable.button_ripple_effect)
                elevation = buttonElevation.toFloat()
                transformationMethod = null

                setOnClickListener {
                    openLawyersList(specialization)
                }
            }

            container.addView(button)
        }
    }

    private fun openLawyersList(specialization: String) {
        val intent = Intent(requireContext(), LawyersListActivity::class.java)
        intent.putExtra("SPECIALIZATION", specialization)
        startActivity(intent)
    }
}