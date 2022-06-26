package com.example.chitchat

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.AsyncTask
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import kotlin.math.pow
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    private lateinit var okHTTPClient: OkHttpClient
    private val CHIT_CHAT_URL = ""
    private val API_KEY = ""
    private val CLIENT = ""

    private val REACTED_MESSAGES_KEY = "REACTED_MESSAGES_KEY"
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var reactedMessageIds: MutableSet<String>

    private val MESSAGES_PER_PAGE = 20
    private val chitChatMessages = mutableListOf<ChitChatMessage>()
    private val inputDateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz")
    private val outputDateFormat = SimpleDateFormat("MMMM d, yyyy 'at' h:mm aa")
    private var longitude: Double? = null
    private var latitude: Double? = null

    private lateinit var inputManager: InputMethodManager
    private lateinit var messageInputView: EditText
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var chitChatRecycler: RecyclerView
    private lateinit var chitChatAdapter: ChitChatAdapter
    private lateinit var loadMoreBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        okHTTPClient = OkHttpClient()

        // Reacted Messages from Shared Preferences
        sharedPreferences = getSharedPreferences(CLIENT, MODE_PRIVATE)
        reactedMessageIds = sharedPreferences.getStringSet(REACTED_MESSAGES_KEY, mutableSetOf()) ?: mutableSetOf()

        // Message Input
        inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        messageInputView = findViewById(R.id.message_input)
        messageInputView.setRawInputType(InputType.TYPE_CLASS_TEXT)
        messageInputView.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                PostTask(messageInputView.text.toString()).execute()
                messageInputView.text.clear()
                inputManager.toggleSoftInput(0, 0)
                return@setOnEditorActionListener true
            } else {
                return@setOnEditorActionListener false
            }
        }

        // Current Location
        // Credit: https://stackoverflow.com/a/2227299
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101)
        }
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        longitude = location?.longitude
        latitude = location?.latitude

        // Recycler View
        swipeRefreshLayout = findViewById(R.id.chit_chat_swipe)
        swipeRefreshLayout.setOnRefreshListener { refresh() }
        chitChatRecycler = findViewById(R.id.chit_chat_recycler)
        chitChatRecycler.layoutManager = LinearLayoutManager(this)
        chitChatAdapter = ChitChatAdapter()
        chitChatRecycler.adapter = chitChatAdapter

        // Load More Button
        loadMoreBtn = findViewById(R.id.load_more_btn)
        loadMoreBtn.setOnClickListener {
            loadMore()
            it.visibility = View.GONE
        }

        // Credit: https://stackoverflow.com/a/46342525
        chitChatRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (!recyclerView.canScrollVertically(1) && newState == RecyclerView.SCROLL_STATE_IDLE) {
                    loadMoreBtn.visibility = View.VISIBLE
                } else {
                    loadMoreBtn.visibility = View.GONE
                }
            }
        })

        refresh()
    }

    override fun onPause() {
        super.onPause()

        // Save Reacted Messages to Shared Preferences
        sharedPreferences.edit().apply {
            clear()
            putStringSet(REACTED_MESSAGES_KEY, reactedMessageIds)
            apply()
        }
    }

    private inner class RefreshTask(limit: Int) : AsyncTask<Void?, Void?, String>() {
        private val URL = "$CHIT_CHAT_URL?key=$API_KEY&client=$CLIENT&limit=$limit"

        override fun doInBackground(vararg voids: Void?): String {
            val request = Request.Builder().url(URL).build()
            return performRequest(request)
        }

        override fun onPostExecute(result: String) {
            super.onPostExecute(result)
            parseMessages(result)
            chitChatAdapter.notifyDataSetChanged()
            swipeRefreshLayout.isRefreshing = false
        }
    }

    private inner class ReactTask(private val message: ChitChatMessage, private val isLike: Boolean) : AsyncTask<Void?, Void?, String>() {
        private val PARAMETER = if (isLike) { "like" } else { "dislike" }
        private val URL = "$CHIT_CHAT_URL/$PARAMETER/${message.id}?key=$API_KEY&client=$CLIENT"

        override fun doInBackground(vararg voids: Void?): String {
            val request = Request.Builder().url(URL).build()
            return performRequest(request)
        }

        override fun onPostExecute(result: String) {
            super.onPostExecute(result)
            if (JSONObject(result).getString("message") == "Success") {
                if (isLike) {
                    message.likes = message.likes + 1
                } else {
                    message.dislikes = message.dislikes + 1
                }
                reactedMessageIds.add(message.id)
                chitChatAdapter.notifyItemChanged(chitChatMessages.indexOf(message))
            }
        }
    }

    private inner class PostTask(message: String) : AsyncTask<Void?, Void?, String>() {
        private val URL = "$CHIT_CHAT_URL?key=$API_KEY&client=$CLIENT&message=$message&lat=$latitude&lon=$longitude"

        override fun doInBackground(vararg voids: Void?): String {
            val request = Request.Builder().method("POST", "".toRequestBody()).url(URL).build()
            return performRequest(request)
        }

        override fun onPostExecute(result: String) {
            super.onPostExecute(result)
            if (JSONObject(result).getString("message") == "Success") { refresh() }
        }
    }

    /**
     * Perform a Request using the OkHTTPClient
     *
     * @param request the Request to be executed
     * @return the body of the Response
     */
    private fun performRequest(request: Request): String {
        try {
            okHTTPClient.newCall(request).execute().use { response -> return response.body!!.string() }
        } catch (e: Exception) {
            e.printStackTrace()
            return "Error: Could not complete request."
        }
    }

    private fun parseMessages(jsonString: String) {
        chitChatMessages.clear()

        try {
            val messages = JSONObject(jsonString).getJSONArray("messages")
            for (i in 0 until messages.length()) {
                val messageData = messages.getJSONObject(i)
                val message = ChitChatMessage(
                        id = messageData.getString("_id"),
                        name = getNameFromEmail(messageData.getString("client")),
                        ip = messageData.getString("ip"),
                        location = Array(2) { messageData.getJSONArray("loc").getString(it).toDoubleOrNull() },
                        date = inputDateFormat.parse(messageData.getString("date")),
                        likes = messageData.getInt("likes"),
                        dislikes = messageData.getInt("dislikes"),
                        message = messageData.getString("message")
                )
                chitChatMessages.add(message)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Determine the name from a Champlain student or faculty email address
     *
     * @param email the email - Ex. cameron.labounty@mymail.champlain.edu or dkopec@champlain.edu
     * @return the name - Ex. Cameron Labounty or D Kopec
     */
    private fun getNameFromEmail(email: String): String {
        val emailUsername = email.substringBefore('@')
        if (emailUsername.contains('.')) { // Student
            val nameList = emailUsername.split('.')
            return nameList[0].capitalize() + " " + nameList[1].capitalize()
        } else { // Faculty
            return emailUsername.take(1).capitalize() + " " + emailUsername.substring(1).capitalize()
        }
    }

    private fun refresh() { RefreshTask(MESSAGES_PER_PAGE).execute() }
    private fun loadMore() { RefreshTask(chitChatMessages.size + MESSAGES_PER_PAGE).execute() }

    private inner class ChitChatHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameView = itemView.findViewById<TextView>(R.id.name)
        private val dateView = itemView.findViewById<TextView>(R.id.date)
        private val distanceView = itemView.findViewById<TextView>(R.id.distance)
        private val messageView = itemView.findViewById<TextView>(R.id.message)
        private val likeBtnView = itemView.findViewById<Button>(R.id.like_btn)
        private val dislikeBtnView = itemView.findViewById<Button>(R.id.dislike_btn)

        fun bind(message: ChitChatMessage) {
            nameView.text = message.name
            dateView.text = outputDateFormat.format(message.date)

            // Distance, if available
            if (latitude == null || longitude == null || message.location[0] == null || message.location[1] == null) {
                distanceView.visibility = View.GONE
            } else {
                distanceView.text = getString(
                        R.string.distance_text,
                        getDistanceFromLocation(message.location as Array<Double>)
                )
                distanceView.visibility = View.VISIBLE
            }

            messageView.text = message.message
            likeBtnView.text = getString(R.string.like_btn, message.likes)
            dislikeBtnView.text = getString(R.string.dislike_btn, message.dislikes)

            // Like and Dislike Buttons
            if (!reactedMessageIds.contains(message.id)) {
                listOf(likeBtnView, dislikeBtnView).forEach { button ->
                    button.apply {
                        isEnabled = true
                        isClickable = true
                        setOnClickListener(null)
                        setOnClickListener {
                            ReactTask(message, button == likeBtnView).execute()
                            likeBtnView.isClickable = false
                            dislikeBtnView.isClickable = false
                        }
                    }
                }
            } else {
                likeBtnView.isEnabled = false
                dislikeBtnView.isEnabled = false
            }
        }

        /**
         * Calculate the current distance in miles from a location
         *
         * @param location the location array [long, lat]
         * @return the current distance in miles
         */
        private fun getDistanceFromLocation(location: Array<Double>): Double {
            return 69 * sqrt((location[0] - longitude!!).pow(2) + (location[1] - latitude!!).pow(2))
        }
    }

    private inner class ChitChatAdapter : RecyclerView.Adapter<ChitChatHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChitChatHolder {
            val view = layoutInflater.inflate(R.layout.chit_chat_item, parent, false)
            return ChitChatHolder(view)
        }

        override fun onBindViewHolder(holder: ChitChatHolder, position: Int) {
            holder.bind(chitChatMessages[position])
        }

        override fun getItemCount() = chitChatMessages.size
    }
}