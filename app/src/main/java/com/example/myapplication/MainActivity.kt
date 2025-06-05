package com.example.myapplication

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Rect
import android.graphics.Typeface
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.telephony.SmsManager
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var inputPrompt: TextInputEditText
    private lateinit var voiceBtn: MaterialButton
    private lateinit var sendBtn: MaterialButton
    private lateinit var historyBtn: MaterialButton
    private lateinit var aboutCard: View
    private lateinit var aboutToggleBtn: FloatingActionButton
    private val chatMessages = mutableListOf<String>()
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var dbHelper: NotesDatabase
    private lateinit var userDbHelper: UserDatabase
    private var userId: Long = -1L
    private lateinit var username: String
    private val DEBUG = true
    private val TAG = "LlamaDebug"

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.all { it.value }) {
            appendMessage("Bot: Permissions granted. Try your request again.")
        } else {
            appendMessage("Bot: Some permissions denied.")
        }
    }

    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            if (!spokenText.isNullOrEmpty()) {
                inputPrompt.setText(spokenText)
                appendMessage("You (via voice): $spokenText")
                userDbHelper.saveConversation(userId, "You (via voice): $spokenText", true)
                processPrompt(spokenText)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        inputPrompt = findViewById(R.id.inputPrompt)
        voiceBtn = findViewById(R.id.voiceBtn)
        sendBtn = findViewById(R.id.sendBtn)
        historyBtn = findViewById(R.id.historyBtn)
        aboutCard = findViewById(R.id.aboutCard)
        aboutToggleBtn = findViewById(R.id.aboutToggleBtn)
        dbHelper = NotesDatabase(this)
        userDbHelper = UserDatabase(this)

        // Get userId and username from SharedPreferences or Intent
        val sharedPrefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        userId = sharedPrefs.getLong("USER_ID", -1L)
        username = sharedPrefs.getString("USERNAME", "") ?: ""

        if (userId == -1L || username.isBlank()) {
            // Fallback to Intent extras
            userId = intent.getLongExtra("USER_ID", -1L)
            username = intent.getStringExtra("USERNAME") ?: ""
            if (userId == -1L || username.isBlank()) {
                Log.e(TAG, "Invalid userId or username")
                Toast.makeText(this, "Error: Please log in", Toast.LENGTH_LONG).show()
                startActivity(Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
                return
            }
            // Save to SharedPreferences
            sharedPrefs.edit()
                .putLong("USER_ID", userId)
                .putString("USERNAME", username)
                .apply()
            Log.d(TAG, "Saved userId=$userId, username=$username to SharedPreferences")
        }

        Log.d(TAG, "User: userId=$userId, username=$username")

        // Navigation Drawer setup
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
        val navView = findViewById<NavigationView>(R.id.nav_view)
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        val toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.open, R.string.close)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_history -> {
                    startActivity(Intent(this, HistoryActivity::class.java).putExtra("USER_ID", userId))
                    true
                }
                R.id.nav_sign_out -> {
                    getSharedPreferences("UserPrefs", MODE_PRIVATE).edit().clear().apply()
                    startActivity(Intent(this, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    finish()
                    true
                }
                else -> false
            }
        }

        // Update nav header username
        try {
            val navHeader = navView.getHeaderView(0)
            val navUsername = navHeader.findViewById<TextView>(R.id.nav_username)
            navUsername.text = username
            Log.d(TAG, "Set nav_username to: $username")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set nav_username: ${e.message}")
            Toast.makeText(this, "Error setting username in nav header", Toast.LENGTH_SHORT).show()
        }

        // Initialize sign-out button in aboutCard
        try {
            val signOutButton: MaterialButton = findViewById(R.id.signOutBtn)
            signOutButton.setOnClickListener {
                Log.d(TAG, "SignOut button clicked")
                try {
                    // Clear user session
                    val sharedPref = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                    sharedPref.edit().clear().apply()
                    Log.d(TAG, "SharedPreferences cleared")

                    // Navigate to LoginActivity and clear back stack
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                    Log.d(TAG, "Navigated to LoginActivity")
                } catch (e: Exception) {
                    Log.e(TAG, "Sign-out failed: ${e.message}")
                    Toast.makeText(this, "Error signing out: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            Log.d(TAG, "SignOut button initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize signOutBtn: ${e.message}")
            Toast.makeText(this, "Error initializing sign-out: ${e.message}", Toast.LENGTH_LONG).show()
        }

        createNotificationChannels()
        requestPermissions()

        // Setup RecyclerView
        chatAdapter = ChatAdapter(chatMessages)
        chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = chatAdapter
        }

        voiceBtn.setOnClickListener {
            startVoiceInput()
        }

        sendBtn.setOnClickListener {
            val prompt = inputPrompt.text.toString().trim()
            if (prompt.isNotEmpty()) {
                appendMessage("You: $prompt")
                userDbHelper.saveConversation(userId, "You: $prompt", true)
                inputPrompt.text?.clear()
                processPrompt(prompt)
            } else {
                Toast.makeText(this, "Please enter a prompt", Toast.LENGTH_SHORT).show()
            }
        }

        historyBtn.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            intent.putExtra("USER_ID", userId)
            startActivity(intent)
        }

        aboutToggleBtn.setOnClickListener {
            aboutCard.visibility = if (aboutCard.visibility == View.GONE) View.VISIBLE else View.GONE
        }

        // Setup keyboard visibility listener
        setupKeyboardListener()
    }

    private fun setupKeyboardListener() {
        val rootView = findViewById<View>(R.id.rootContainer)
        rootView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val rect = Rect()
                rootView.getWindowVisibleDisplayFrame(rect)
                val screenHeight = rootView.height
                val keypadHeight = screenHeight - rect.bottom

                if (keypadHeight > screenHeight * 0.15) { // Keyboard is visible
                    Log.d(TAG, "Keyboard visible, height: $keypadHeight")
                    val inputBar = findViewById<View>(R.id.inputBar)
                    inputBar.translationY = -keypadHeight.toFloat()
                    chatRecyclerView.scrollToPosition(chatMessages.size - 1)
                } else {
                    Log.d(TAG, "Keyboard hidden")
                    val inputBar = findViewById<View>(R.id.inputBar)
                    inputBar.translationY = 0f
                }
            }
        })

        // Handle window insets for edge-to-edge display
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun appendMessage(message: String) {
        runOnUiThread {
            chatMessages.add(message)
            chatAdapter.notifyItemInserted(chatMessages.size - 1)
            chatRecyclerView.scrollToPosition(chatMessages.size - 1)
            Log.d(TAG, "Appended message: $message")
        }
    }

    private fun processPrompt(prompt: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val modelPath = copyModelToInternalStorage()
                logDebug("Calling runLlama() with prompt: $prompt")

                val result = runLlama(modelPath, prompt)

                withContext(Dispatchers.Main) {
                    logDebug("Model raw output: $result")
                    handleModelResponse(result, prompt)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    appendMessage("Bot: Something went wrong.")
                    logError("Exception in processPrompt: ${e.message}")
                    if (DEBUG) Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
                e.printStackTrace()
            }
        }
    }

    private fun makeCall(input: String) {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                appendMessage("Bot: Call permission denied. Please grant permission.")
                userDbHelper.saveConversation(userId, "Bot: Call permission denied. Please grant permission.", false)
                requestPermissions()
                return
            }

            // Check if input is a phone number
            val phoneNumberPattern = Regex("^[0-9]{10}$") // Adjust for your region
            if (phoneNumberPattern.matches(input)) {
                val intent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$input")
                }
                startActivity(intent)
                appendMessage("Bot: Initiating call to $input.")
                userDbHelper.saveConversation(userId, "Bot: Initiating call to $input.", false)
                return
            }

            // Search contacts by name
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                appendMessage("Bot: Contacts permission denied. Please grant permission.")
                userDbHelper.saveConversation(userId, "Bot: Contacts permission denied. Please grant permission.", false)
                requestPermissions()
                return
            }

            val phoneNumber = getPhoneNumberFromContact(input)
            if (phoneNumber != null) {
                val intent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$phoneNumber")
                }
                startActivity(intent)
                appendMessage("Bot: Initiating call to $input ($phoneNumber).")
                userDbHelper.saveConversation(userId, "Bot: Initiating call to $input ($phoneNumber).", false)
            } else {
                appendMessage("Bot: No contact found for '$input' or invalid phone number.")
                userDbHelper.saveConversation(userId, "Bot: No contact found for '$input' or invalid phone number.", false)
            }
        } catch (e: Exception) {
            appendMessage("Bot: Could not initiate call: ${e.message}")
            userDbHelper.saveConversation(userId, "Bot: Could not initiate call: ${e.message}", false)
            logError("Call failed: ${e.message}")
        }
    }

    private fun getPhoneNumberFromContact(name: String): String? {
        try {
            Log.d(TAG, "Searching for contact: $name")
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$name%")

            contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val foundNumber = cursor.getString(numberIndex).replace("[^0-9]".toRegex(), "")
                    Log.d(TAG, "Found phone number for $name: $foundNumber")
                    return foundNumber
                } else {
                    Log.d(TAG, "No contact found for: $name")
                }
            }
        } catch (e: Exception) {
            logError("Contact search failed: ${e.message}")
        }
        return null
    }

    private fun startVoiceInput() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_SHORT).show()
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            appendMessage("Bot: Microphone permission denied. Please grant permission.")
            requestPermissions()
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
        }

        speechLauncher.launch(intent)
    }

    private fun handleModelResponse(response: String, prompt: String) {
        try {
            val json = JSONObject(response)
            if (json.has("error")) {
                val errorMessage = json.getString("error")
                appendMessage("Bot: ❌ Error - $errorMessage")
                userDbHelper.saveConversation(userId, "Bot: ❌ Error - $errorMessage", false)
                logError("Native error returned: $errorMessage")
                return
            }

            when (val intent = json.optString("intent", "")) {
                "schedule" -> {
                    val details = json.getJSONObject("details")
                    val event = details.optString("event", "Unnamed Event")
                    val time = details.optString("time", "")
                    val date = details.optString("date", "")
                    appendMessage("Bot: Scheduled \"$event\" at $time on $date.")
                    userDbHelper.saveConversation(userId, "Bot: Scheduled \"$event\" at $time on $date.", false)
                    addCalendarEvent(event, time, date)
                }
                "delete_event" -> {
                    val details = json.getJSONObject("details")
                    val event = details.optString("event", "Unnamed Event")
                    val date = details.optString("date", "")
                    deleteCalendarEvent(event, date)
                }
                "reminder" -> {
                    val details = json.getJSONObject("details")
                    val title = details.optString("title", "Unnamed Reminder")
                    val time = details.optString("time", "")
                    val date = details.optString("date", "")
                    appendMessage("Bot: Reminder \"$title\" noted for $time on $date.")
                    userDbHelper.saveConversation(userId, "Bot: Reminder \"$title\" noted for $time on $date.", false)
                }
                "alarm" -> {
                    val details = json.getJSONObject("details")
                    val time = details.optString("time", "")
                    val date = details.optString("date", "")
                    val label = details.optString("label", "Alarm")
                    setAlarm(time, date, label, prompt)
                }
                "note" -> {
                    val details = json.getJSONObject("details")
                    val content = details.optString("content", "")
                    var title = try {
                        details.getString("title")
                    } catch (e: Exception) {
                        parseNoteTitleFromPrompt(prompt)
                    }
                    val date = details.optString("date", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE))

                    if (title.isBlank()) {
                        appendMessage("Bot: Note title cannot be empty. Please specify a title (e.g., 'create a note vegetables \"content\"').")
                        userDbHelper.saveConversation(userId, "Bot: Note title cannot be empty. Please specify a title (e.g., 'create a note vegetables \"content\"').", false)
                        return
                    }

                    val fileSaved = NoteUtil.createNote(this, title, content, date)
                    val dbSaved = saveNoteToDatabase(title, content, date)
                    if (fileSaved && dbSaved) {
                        appendMessage("Bot: Note '$title.txt' saved in Downloads and database.")
                        userDbHelper.saveConversation(userId, "Bot: Note '$title.txt' saved in Downloads and database.", false)
                        showNoteNotification(title)
                    } else {
                        appendMessage("Bot: Failed to save note '$title.txt'.")
                        userDbHelper.saveConversation(userId, "Bot: Failed to save note '$title.txt'.", false)
                    }
                }
                "read_notes" -> {
                    val notes = NoteUtil.readNotes(this)
                    if (notes.isEmpty()) {
                        appendMessage("Bot: No notes found.")
                        userDbHelper.saveConversation(userId, "Bot: No notes found.", false)
                    } else {
                        for (note in notes) {
                            appendMessage("Bot: Note ${note.title}: ${note.content} (Title: ${note.title}, Date: ${note.date})")
                            userDbHelper.saveConversation(userId, "Bot: Note ${note.title}: ${note.content} (Title: ${note.title}, Date: ${note.date})", false)
                        }
                    }
                }
                "update_note" -> {
                    val details = json.getJSONObject("details")
                    val noteId = details.optString("id", "")
                    val newContent = details.optString("content", "")
                    val newDate = details.optString("date", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE))
                    val updated = NoteUtil.updateNote(this, noteId, newContent, newDate)
                    appendMessage("Bot: ${if (updated) "Note updated." else "Could not update note."}")
                    userDbHelper.saveConversation(userId, "Bot: ${if (updated) "Note updated." else "Could not update note."}", false)
                }
                "delete_note" -> {
                    val details = json.getJSONObject("details")
                    val noteId = details.optString("id", "")
                    val deleted = NoteUtil.deleteNote(this, noteId)
                    appendMessage("Bot: ${if (deleted) "Note deleted." else "Could not delete note."}")
                    userDbHelper.saveConversation(userId, "Bot: ${if (deleted) "Note deleted." else "Could not delete note."}", false)
                }
                "call" -> {
                    val details = json.getJSONObject("details")
                    val input = details.optString("number", "").ifEmpty { details.optString("name", "") }
                    if (input.isNotEmpty()) {
                        makeCall(input)
                    } else {
                        appendMessage("Bot: Invalid phone number or contact name for call.")
                        userDbHelper.saveConversation(userId, "Bot: Invalid phone number or contact name for call.", false)
                    }
                }
                "sms" -> {
                    val details = json.getJSONObject("details")
                    val number = details.optString("number", "")
                    val message = details.optString("message", "")
                    if (number.isNotEmpty() && message.isNotEmpty()) {
                        appendMessage("Bot: Sending SMS to $number: $message")
                        userDbHelper.saveConversation(userId, "Bot: Sending SMS to $number: $message", false)
                        sendSMS(number, message)
                    } else {
                        appendMessage("Bot: Invalid phone number or message for SMS.")
                        userDbHelper.saveConversation(userId, "Bot: Invalid phone number or message for SMS.", false)
                    }
                }
                "create_excel" -> {
                    val details = json.getJSONObject("details")
                    val filename = details.optString("filename", "output.xlsx")
                    val data = details.optJSONArray("data") ?: JSONArray()
                    lifecycleScope.launch(Dispatchers.IO) {
                        val result = ExcelUtil.createExcel(this@MainActivity, filename, data)
                        withContext(Dispatchers.Main) {
                            appendMessage("Bot: ${if (result) "Excel file '$filename' created in Downloads." else "Failed to create Excel file '$filename'."}")
                            userDbHelper.saveConversation(userId, "Bot: ${if (result) "Excel file '$filename' created in Downloads." else "Failed to create Excel file '$filename'."}", false)
                        }
                    }
                }
                "delete_excel" -> {
                    val details = json.getJSONObject("details")
                    val filename = details.optString("filename", "")
                    if (filename.isNotEmpty()) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            val result = ExcelUtil.deleteExcel(this@MainActivity, filename)
                            withContext(Dispatchers.Main) {
                                appendMessage("Bot: ${if (result) "Excel file '$filename' deleted." else "Failed to delete Excel file '$filename'."}")
                                userDbHelper.saveConversation(userId, "Bot: ${if (result) "Excel file '$filename' deleted." else "Failed to delete Excel file '$filename'."}", false)
                            }
                        }
                    } else {
                        appendMessage("Bot: Invalid filename for deletion.")
                        userDbHelper.saveConversation(userId, "Bot: Invalid filename for deletion.", false)
                    }
                }
                "read_excel" -> {
                    val details = json.getJSONObject("details")
                    val filename = details.optString("filename", "")
                    if (filename.isNotEmpty()) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            val result = ExcelUtil.readExcel(this@MainActivity, filename)
                            withContext(Dispatchers.Main) {
                                appendMessage("Bot: Excel content: $result")
                                userDbHelper.saveConversation(userId, "Bot: Excel content: $result", false)
                            }
                        }
                    } else {
                        appendMessage("Bot: Invalid filename for reading.")
                        userDbHelper.saveConversation(userId, "Bot: Invalid filename for reading.", false)
                    }
                }
                else -> {
                    val text = json.optString("response", "Sorry, I couldn't understand that. Try again?")
                    appendMessage("Bot: $text")
                    userDbHelper.saveConversation(userId, "Bot: $text", false)
                }
            }
        } catch (e: Exception) {
            appendMessage("Bot: Sorry, I couldn't process that. Try again?")
            userDbHelper.saveConversation(userId, "Bot: Sorry, I couldn't process that. Try again?", false)
            logError("JSON parsing failed: ${e.message}")
        }
    }

    private fun deleteCalendarEvent(event: String, date: String): Boolean {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                appendMessage("Bot: Calendar permissions denied. Please grant read/write calendar permissions.")
                userDbHelper.saveConversation(userId, "Bot: Calendar permissions denied. Please grant read/write calendar permissions.", false)
                requestPermissions()
                return false
            }

            val normalizedEvent = event.trim().lowercase(Locale.getDefault())
            val normalizedDate = date.trim().replace("\\s+|/".toRegex(), "-").let {
                when {
                    it.matches(Regex("\\d{2}-\\d{2}-\\d{4}")) -> it.split("-").let { parts ->
                        "${parts[2]}-${parts[1]}-${parts[0]}"
                    }
                    it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) -> it
                    else -> it
                }
            }

            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
                isLenient = true
            }
            val parsedDate = try {
                formatter.parse(normalizedDate)
            } catch (e: Exception) {
                appendMessage("Bot: Invalid date format. Use yyyy-MM-dd (e.g., 2026-08-28), dd-MM-yyyy (e.g., 28-08-2026), or dd MM yyyy (e.g., 28 08 2026).")
                userDbHelper.saveConversation(userId, "Bot: Invalid date format. Use yyyy-MM-dd (e.g., 2026-08-28), dd-MM-yyyy (e.g., 28-08-2026), or dd MM yyyy (e.g., 28 08 2026).", false)
                logError("Date parsing failed for '$date': ${e.message}")
                return false
            } ?: run {
                appendMessage("Bot: Could not parse date '$date'.")
                userDbHelper.saveConversation(userId, "Bot: Could not parse date '$date'.", false)
                return false
            }

            val calendar = Calendar.getInstance().apply { time = parsedDate }
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val beginTime = calendar.timeInMillis

            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            val endTime = calendar.timeInMillis

            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.CALENDAR_ID
            )
            val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
            val selectionArgs = arrayOf(beginTime.toString(), endTime.toString())

            val cursor: Cursor? = contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )

            val eventIds = mutableListOf<Long>()
            val allEvents = mutableListOf<String>()
            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(CalendarContract.Events._ID))
                    val title = it.getString(it.getColumnIndexOrThrow(CalendarContract.Events.TITLE))?.lowercase(Locale.getDefault()) ?: ""
                    val startTime = it.getLong(it.getColumnIndexOrThrow(CalendarContract.Events.DTSTART))
                    val calendarId = it.getLong(it.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_ID))

                    allEvents.add("Event: ID=$id, Title='$title', Start=$startTime, Calendar=$calendarId")
                    if (title == normalizedEvent) {
                        eventIds.add(id)
                    }
                }
            }

            allEvents.forEach { logError(it) }
            if (allEvents.isEmpty()) {
                logError("No events found on date '$normalizedDate' in range $beginTime to $endTime")
            }

            if (eventIds.isNotEmpty()) {
                var deletedCount = 0
                for (eventId in eventIds) {
                    val deleteUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
                    val rows = contentResolver.delete(deleteUri, null, null)
                    if (rows > 0) deletedCount++
                }
                appendMessage("Bot: Deleted $deletedCount event(s) named '$event' on $normalizedDate.")
                userDbHelper.saveConversation(userId, "Bot: Deleted $deletedCount event(s) named '$event' on $normalizedDate.", false)
                return true
            } else {
                appendMessage("Bot: No events named '$event' found on $normalizedDate. Check the event title or date.")
                userDbHelper.saveConversation(userId, "Bot: No events named '$event' found on $normalizedDate. Check the event title or date.", false)
                logError("No events matched: Title='$normalizedEvent', Date='$normalizedDate', Range=$beginTime to $endTime")
                return false
            }
        } catch (e: Exception) {
            appendMessage("Bot: Could not delete calendar event: ${e.message}")
            userDbHelper.saveConversation(userId, "Bot: Could not delete calendar event: ${e.message}", false)
            logError("Calendar event deletion failed: ${e.message}")
            return false
        }
    }

    private fun sendSMS(number: String, message: String) {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                appendMessage("Bot: SMS permission denied. Please grant permission.")
                userDbHelper.saveConversation(userId, "Bot: SMS permission denied. Please grant permission.", false)
                requestPermissions()
                return
            }
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(number, null, message, null, null)
            appendMessage("Bot: SMS sent successfully.")
            userDbHelper.saveConversation(userId, "Bot: SMS sent successfully.", false)
        } catch (e: Exception) {
            appendMessage("Bot: Could not send SMS.")
            userDbHelper.saveConversation(userId, "Bot: Could not send SMS.", false)
            logError("SMS sending failed: ${e.message}")
        }
    }

    private fun addCalendarEvent(event: String, time: String, date: String) {
        try {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            val startTime = LocalDateTime.parse("$date $time", formatter)
            val beginTime = startTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, event)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginTime)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, beginTime + 3600000)
            }
            startActivity(intent)
        } catch (e: Exception) {
            appendMessage("Bot: Could not create calendar event.")
            userDbHelper.saveConversation(userId, "Bot: Could not create calendar event.", false)
            logError("Calendar event creation failed: ${e.message}")
        }
    }

    private fun setAlarm(time: String, date: String, label: String, prompt: String = "") {
        try {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            val localDateTime = LocalDateTime.parse("$date $time", formatter)
            val hour = localDateTime.hour
            val minute = localDateTime.minute

            val enableVibration = prompt.lowercase().contains("vibrate") || prompt.lowercase().contains("vibration")

            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                putExtra(AlarmClock.EXTRA_RINGTONE, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM).toString())
                putExtra(AlarmClock.EXTRA_VIBRATE, enableVibration)
            }

            val recurrenceDays = parseRecurrenceDays(prompt.lowercase())
            val specificDay = parseSpecificDay(prompt.lowercase(), date)

            var isOneTime = false
            var message = ""
            if (specificDay != null) {
                isOneTime = true
                message = "Bot: Alarm \"$label\" set at $time on ${dayToString(specificDay.day)} (${specificDay.date})${if (enableVibration) " with vibration" else ""} in Clock app."
            } else if (recurrenceDays.isEmpty()) {
                val now = LocalDateTime.now()
                if (localDateTime.isAfter(now)) {
                    isOneTime = true
                    message = "Bot: Alarm \"$label\" set at $time on $date${if (enableVibration) " with vibration" else ""} in Clock app."
                } else {
                    message = "Bot: Cannot set alarm for past date $date."
                    appendMessage(message)
                    userDbHelper.saveConversation(userId, message, false)
                    return
                }
            } else {
                intent.putExtra(AlarmClock.EXTRA_DAYS, recurrenceDays.toIntArray())
                val recurrenceText = when {
                    recurrenceDays.size == 7 -> "daily"
                    else -> "on ${recurrenceDays.map { dayToString(it) }.joinToString(", ")}"
                }
                message = "Bot: Alarm \"$label\" set at $time $recurrenceText${if (enableVibration) " with vibration" else ""} in Clock app."
            }

            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                appendMessage(message)
                userDbHelper.saveConversation(userId, message, false)
                showAlarmCreationNotification(label, time, date)
            } else {
                appendMessage("Bot: No Clock app found to set the alarm.")
                userDbHelper.saveConversation(userId, "Bot: No Clock app found to set the alarm.", false)
            }
        } catch (e: Exception) {
            appendMessage("Bot: Can not set alarm: ${e.message}")
            userDbHelper.saveConversation(userId, "Bot: Can not set alarm: ${e.message}", false)
            logError("Alarm setup failed: ${e.message}")
        }
    }

    private fun parseSpecificDay(prompt: String, date: String): SpecificDay? {
        val daysMap = mapOf(
            "monday" to Calendar.MONDAY,
            "tuesday" to Calendar.TUESDAY,
            "wednesday" to Calendar.WEDNESDAY,
            "thursday" to Calendar.THURSDAY,
            "friday" to Calendar.FRIDAY,
            "saturday" to Calendar.SATURDAY,
            "sunday" to Calendar.SUNDAY,
            "mon" to Calendar.MONDAY,
            "tue" to Calendar.TUESDAY,
            "wed" to Calendar.WEDNESDAY,
            "thu" to Calendar.THURSDAY,
            "fri" to Calendar.FRIDAY,
            "sat" to Calendar.SATURDAY,
            "sun" to Calendar.SUNDAY
        )

        val dayKey = daysMap.keys.find { prompt.contains(it) }
        if (dayKey != null) {
            val calendarDay = daysMap[dayKey]!!
            val now = LocalDateTime.now()
            val today = Calendar.getInstance()
            val targetCalendar = Calendar.getInstance()

            var daysToAdd = (calendarDay - today.get(Calendar.DAY_OF_WEEK) + 7) % 7
            if (prompt.contains("next")) daysToAdd += 7
            targetCalendar.add(Calendar.DAY_OF_MONTH, daysToAdd)

            val targetDate = LocalDateTime.of(
                targetCalendar.get(Calendar.YEAR),
                targetCalendar.get(Calendar.MONTH) + 1,
                targetCalendar.get(Calendar.DAY_OF_MONTH),
                0, 0
            )

            if (targetDate.isAfter(now)) {
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                return SpecificDay(calendarDay, targetDate.format(formatter))
            }
        }

        return null
    }

    private data class SpecificDay(val day: Int, val date: String)

    private fun parseRecurrenceDays(prompt: String): List<Int> {
        if (prompt.contains("daily")) {
            return listOf(
                Calendar.SUNDAY,
                Calendar.MONDAY,
                Calendar.TUESDAY,
                Calendar.WEDNESDAY,
                Calendar.THURSDAY,
                Calendar.FRIDAY,
                Calendar.SATURDAY
            )
        }

        val daysMap = mapOf(
            "monday" to Calendar.MONDAY,
            "tuesday" to Calendar.TUESDAY,
            "wednesday" to Calendar.WEDNESDAY,
            "thursday" to Calendar.THURSDAY,
            "friday" to Calendar.FRIDAY,
            "saturday" to Calendar.SATURDAY,
            "sunday" to Calendar.SUNDAY,
            "mon" to Calendar.MONDAY,
            "tue" to Calendar.TUESDAY,
            "wed" to Calendar.WEDNESDAY,
            "thu" to Calendar.THURSDAY,
            "fri" to Calendar.FRIDAY,
            "sat" to Calendar.SATURDAY,
            "sun" to Calendar.SUNDAY
        )

        return daysMap.filterKeys { prompt.contains(it) }.values.toList()
    }

    private fun dayToString(day: Int): String {
        return when (day) {
            Calendar.SUNDAY -> "Sunday"
            Calendar.MONDAY -> "Monday"
            Calendar.TUESDAY -> "Tuesday"
            Calendar.WEDNESDAY -> "Wednesday"
            Calendar.THURSDAY -> "Thursday"
            Calendar.FRIDAY -> "Friday"
            Calendar.SATURDAY -> "Saturday"
            else -> ""
        }
    }

    private fun showAlarmCreationNotification(label: String, time: String, date: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            appendMessage("Bot: Notification permission denied.")
            userDbHelper.saveConversation(userId, "Bot: Notification permission denied.", false)
            requestPermissions()
            return
        }

        val notification = NotificationCompat.Builder(this, "alarm_channel")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Alarm Created")
            .setContentText("Bot: $label scheduled for $time, $date")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
            .setVibrate(longArrayOf(0, 200, 200, 200))
            .build()

        NotificationManagerCompat.from(this).notify(label.hashCode(), notification)
    }

    private fun showNoteNotification(title: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            appendMessage("Bot: Notification permission denied.")
            userDbHelper.saveConversation(userId, "Bot: Notification permission denied.", false)
            requestPermissions()
            return
        }

        val notification = NotificationCompat.Builder(this, "note_channel")
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentTitle("Note Created")
            .setContentText("Bot: Saved '$title.txt' in Downloads.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun parseNoteTitleFromPrompt(prompt: String): String {
        val promptLower = prompt.lowercase().trim()
        if (promptLower.startsWith("create a note ")) {
            val parts = prompt.substringAfter("create a note ").split("\"", limit = 2)
            return parts.getOrNull(0)?.trim() ?: ""
        }
        return ""
    }

    private fun saveNoteToDatabase(title: String, content: String, date: String): Boolean {
        return try {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put("title", title)
                put("content", content)
                put("date", date)
            }
            val result = db.insert("notes", null, values) != -1L
            db.close()
            result
        } catch (e: Exception) {
            logError("Note saving to database failed: ${e.message}")
            false
        }
    }

    private fun copyModelToInternalStorage(): String {
        val modelName = "Llama-3.2-3B-Instruct-Q4_K_S.gguf"
        val modelFile = File(filesDir, modelName)
        if (!modelFile.exists()) {
            assets.open(modelName).use { input ->
                FileOutputStream(modelFile).use { output ->
                    input.copyTo(output)
                }
            }
            logDebug("Model copied to: ${modelFile.absolutePath}")
        }
        return modelFile.absolutePath
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val noteChannel = NotificationChannel(
                "note_channel",
                "Note Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Channel for notifying note creation"
            }
            val alarmChannel = NotificationChannel(
                "alarm_channel",
                "Alarm Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for alarm-related notifications"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(noteChannel)
            notificationManager.createNotificationChannel(alarmChannel)
        }
    }

    external fun runLlama(modelPath: String, prompt: String): String

    companion object {
        init {
            try {
                System.loadLibrary("ggml-base")
                System.loadLibrary("ggml-cpu")
                System.loadLibrary("ggml")
                System.loadLibrary("llama")
                System.loadLibrary("native-lib")
            } catch (e: UnsatisfiedLinkError) {
                Log.e("LlamaDebug", "Failed to load native libraries: ${e.message}")
            }
        }
    }

    private fun logDebug(message: String) {
        if (DEBUG) Log.d(TAG, message)
    }

    private fun logError(message: String) {
        if (DEBUG) Log.e(TAG, message)
    }

    class ChatAdapter(private val messages: List<String>) :
        RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {
        class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val messageText: TextView = itemView.findViewById(R.id.messageText)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.chat_message_item, parent, false)
            return MessageViewHolder(view)
        }

        override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
            val message = messages[position]
            holder.messageText.text = message
            holder.messageText.gravity = Gravity.START
            holder.messageText.background = null
            holder.messageText.setTypeface(null, Typeface.BOLD)
            holder.messageText.setTextColor(0xFFFFFFFF.toInt())
        }

        override fun getItemCount(): Int = messages.size
    }

    class NotesDatabase(context: Context) : SQLiteOpenHelper(context, "notes.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE notes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    content TEXT NOT NULL,
                    date TEXT NOT NULL
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS notes")
            onCreate(db)
        }
    }
}



