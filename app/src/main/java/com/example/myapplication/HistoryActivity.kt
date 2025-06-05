package com.example.myapplication

import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class HistoryActivity : AppCompatActivity() {
    private val TAG = "HistoryActivity"
    private lateinit var historyRecyclerView: RecyclerView
    private lateinit var dbHelper: UserDatabase
    private lateinit var historyAdapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_history)
            Log.d(TAG, "Successfully set activity_history layout")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set activity_history layout: ${e.message}", e)
            Toast.makeText(this, "Error loading history: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        try {
            historyRecyclerView = findViewById(R.id.historyRecyclerView)
            dbHelper = UserDatabase(this)
            Log.d(TAG, "Initialized UI components and database")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize UI or database: ${e.message}", e)
            Toast.makeText(this, "Error initializing history: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Get userId from intent
        val userId = intent.getLongExtra("USER_ID", -1L)
        if (userId == -1L) {
            Log.e(TAG, "Invalid USER_ID")
            Toast.makeText(this, "Error: Invalid user", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Load conversations
        try {
            val conversations = dbHelper.getConversations(userId).toMutableList()
            historyAdapter = HistoryAdapter(conversations)
            Log.d(TAG, "Loaded ${conversations.size} conversations")

            historyRecyclerView.apply {
                layoutManager = LinearLayoutManager(this@HistoryActivity)
                adapter = historyAdapter
                addItemDecoration(object : RecyclerView.ItemDecoration() {
                    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                        outRect.bottom = 6 // Total ~6dp with 3dp margin
                    }
                })
                layoutAnimation = AnimationUtils.loadLayoutAnimation(this@HistoryActivity, R.anim.history_layout_animation)
                scheduleLayoutAnimation()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load conversations: ${e.message}", e)
            Toast.makeText(this, "Error loading history: ${e.message}", Toast.LENGTH_LONG).show()
        }

        // Setup Clear History button
        try {
            val clearHistoryButton: Button = findViewById(R.id.clear_history_button)
            clearHistoryButton.setOnClickListener {
                try {
                    dbHelper.deleteConversations(userId)
                    historyAdapter.updateConversations(emptyList())
                    Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "Cleared conversations for userId: $userId")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to clear history: ${e.message}", e)
                    Toast.makeText(this, "Error clearing history: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup clear history button: ${e.message}", e)
            Toast.makeText(this, "Error setting up clear history: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

class HistoryAdapter(private val conversations: MutableList<ConversationItem>) :
    RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val conversationText: TextView = itemView.findViewById(R.id.conversationText)
        val cardView: MaterialCardView = itemView as MaterialCardView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.history_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = conversations[position]
        holder.conversationText.text = item.text
        // Apply gravity to TextView
        holder.conversationText.gravity = if (item.isUserPrompt) Gravity.END else Gravity.START
        holder.cardView.setCardBackgroundColor(
            if (item.isUserPrompt) 0xFF444444.toInt() else 0xFF333333.toInt()
        )
    }

    override fun getItemCount(): Int = conversations.size

    fun updateConversations(newConversations: List<ConversationItem>) {
        conversations.clear()
        conversations.addAll(newConversations)
        notifyDataSetChanged()
    }
}