package com.example.exotrade.activities.profile

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.exotrade.Adapters.FriendAdapter
import com.example.exotrade.ExoTradeApplication
import com.example.exotrade.models.User
import com.example.exotrade.R
import com.example.exotrade.databinding.ProfileActivityFriendsBinding
import com.example.exotrade.data.SessionRepository
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int

/**
 * Activity for managing user friends, friend requests, and searching for new users.
 */
class FriendsActivity : AppCompatActivity() {

    private lateinit var binding: ProfileActivityFriendsBinding
    private lateinit var adapter: FriendAdapter
    private lateinit var session: SessionRepository
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ProfileActivityFriendsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = ExoTradeApplication.container.sessionRepository
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupRecyclerView()
        setupListeners()

        binding.toggleGroup.check(R.id.btnFriendsTab)
    }

    private fun setupRecyclerView() {
        binding.rvUsers.layoutManager = LinearLayoutManager(this)
        adapter = FriendAdapter(ArrayList(), FriendAdapter.Mode.FRIENDS, object : FriendAdapter.OnFriendActionListener {
            override fun onUserClick(user: User) {
                val intent = Intent(this@FriendsActivity, Profile::class.java)
                intent.putExtra("user_id", user.id)
                startActivity(intent)
            }

            override fun onActionClick(user: User) {
                handleAction(user, true)
            }

            override fun onDeclineClick(user: User) {
                handleAction(user, false)
            }
        })
        binding.rvUsers.adapter = adapter
    }

    private fun setupListeners() {
        binding.toggleGroup.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) updateMode()
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                if (query.isEmpty()) {
                    binding.toggleGroup.visibility = View.VISIBLE
                    updateMode()
                } else {
                    binding.toggleGroup.visibility = View.GONE
                    binding.lblTitle.setText(R.string.search_results)
                    adapter.setMode(FriendAdapter.Mode.SEARCH)
                    searchRunnable = Runnable { searchUsers(query) }
                    searchHandler.postDelayed(searchRunnable!!, 500)
                }
            }
        })
    }

    private fun updateMode() {
        if (binding.toggleGroup.checkedButtonId == R.id.btnFriendsTab) {
            binding.lblTitle.setText(R.string.your_friends)
            adapter.setMode(FriendAdapter.Mode.FRIENDS)
            fetchFriends()
        } else {
            binding.lblTitle.setText(R.string.requests)
            adapter.setMode(FriendAdapter.Mode.REQUESTS)
            fetchRequests()
        }
    }

    private fun fetchFriends() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm("friends/get_friends", session.authParams())
                binding.progressBar.visibility = View.GONE
                val json = Json.parseToJsonElement(response).jsonObject
                if ("success" == json["status"]?.jsonPrimitive?.content) {
                    val arr = json["friends"]?.jsonArray
                    val list = parseUsers(arr)
                    adapter.setUsers(list)
                    showEmptyIf(list.isEmpty(), R.string.no_friends_yet)
                } else {
                    Toast.makeText(this@FriendsActivity, json["message"]?.jsonPrimitive?.content, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@FriendsActivity, "Couldn't load friends. Check your connection.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchRequests() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm("friends/get_friend_requests", session.authParams())
                binding.progressBar.visibility = View.GONE
                val json = Json.parseToJsonElement(response).jsonObject
                if ("success" == json["status"]?.jsonPrimitive?.content) {
                    val arr = json["requests"]?.jsonArray
                    val list = parseUsers(arr)
                    adapter.setUsers(list)
                    showEmptyIf(list.isEmpty(), R.string.no_requests_yet)
                } else {
                    Toast.makeText(this@FriendsActivity, json["message"]?.jsonPrimitive?.content, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@FriendsActivity, "Couldn't load friends. Check your connection.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun searchUsers(query: String) {
        binding.progressBar.visibility = View.VISIBLE
        val params = session.authParams().toMutableMap()
        params["query"] = query

        lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm("friends/search_users", params)
                binding.progressBar.visibility = View.GONE
                val json = Json.parseToJsonElement(response).jsonObject
                if ("success" == json["status"]?.jsonPrimitive?.content) {
                    val arr = json["users"]?.jsonArray
                    val list = parseUsers(arr)
                    adapter.setUsers(list)
                    showEmptyIf(list.isEmpty(), R.string.no_results_found)
                } else {
                    Toast.makeText(this@FriendsActivity, json["message"]?.jsonPrimitive?.content, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@FriendsActivity, "Couldn't load friends. Check your connection.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun parseUsers(arr: JsonArray?): List<User> {
        val list = ArrayList<User>()
        arr?.forEach { element ->
            val u = element.jsonObject
            list.add(
                User(
                    id = u["id"]?.jsonPrimitive?.content,
                    username = u["username"]?.jsonPrimitive?.content,
                    profilePic = u["profile_pic"]?.jsonPrimitive?.content,
                    subscriptionTier = u["subscription_tier"]?.jsonPrimitive?.int ?: 0
                )
            )
        }
        return list
    }

    private fun showEmptyIf(condition: Boolean, stringRes: Int) {
        if (condition) {
            binding.lblEmpty.setText(stringRes)
            binding.lblEmpty.visibility = View.VISIBLE
            binding.rvUsers.visibility = View.GONE
        } else {
            binding.lblEmpty.visibility = View.GONE
            binding.rvUsers.visibility = View.VISIBLE
        }
    }

    private fun handleAction(user: User, accept: Boolean) {
        val params = session.authParams().toMutableMap()
        val endpoint: String
        val message: String

        if (binding.toggleGroup.checkedButtonId == R.id.btnFriendsTab) {
            endpoint = "friends/remove_friend"
            params["friend_id"] = user.id ?: ""
            message = "Friend removed"
        } else {
            if (accept) {
                endpoint = "friends/accept_friend_request"
                message = "Friend request accepted"
            } else {
                endpoint = "friends/decline_friend_request"
                message = "Friend request declined"
            }
            params["requester_id"] = user.id ?: ""
        }

        lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm(endpoint, params)
                val json = Json.parseToJsonElement(response).jsonObject
                if ("success" == json["status"]?.jsonPrimitive?.content) {
                    Toast.makeText(this@FriendsActivity, message, Toast.LENGTH_SHORT).show()
                    updateMode()
                } else {
                    Toast.makeText(this@FriendsActivity, json["message"]?.jsonPrimitive?.content, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@FriendsActivity, "Action failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
