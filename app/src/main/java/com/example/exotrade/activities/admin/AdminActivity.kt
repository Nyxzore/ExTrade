package com.example.exotrade.activities.admin

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.exotrade.Adapters.FriendAdapter
import com.example.exotrade.Adapters.ReportAdapter
import com.example.exotrade.ExoTradeApplication
import com.example.exotrade.models.Report
import com.example.exotrade.models.User
import com.example.exotrade.R
import com.example.exotrade.activities.BaseActivity
import com.example.exotrade.activities.breeding.BreedingListingDetails
import com.example.exotrade.activities.listings.ListingDetails
import com.example.exotrade.activities.profile.Profile
import com.example.exotrade.databinding.AdminActivityUsersBinding
import com.example.exotrade.utils.Helpers
import com.example.exotrade.utils.NavigationHelper
import com.example.exotrade.data.SessionRepository
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int

/**
 * Main dashboard for administrative tasks.
 */
class AdminActivity : BaseActivity() {

    private lateinit var binding: AdminActivityUsersBinding
    private lateinit var searchAdapter: FriendAdapter
    private lateinit var reportAdapter: ReportAdapter
    private lateinit var session: SessionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = AdminActivityUsersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = ExoTradeApplication.container.sessionRepository
        if (!session.isAdmin()) {
            Toast.makeText(this, "Unauthorized access", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupSearch()
        setupMainList()
        setupToggles()
        setupNavigation()
    }

    private fun setupSearch() {
        binding.rvSearchUsers.layoutManager = LinearLayoutManager(this)
        searchAdapter = FriendAdapter(ArrayList(), FriendAdapter.Mode.ADMIN, object : FriendAdapter.OnFriendActionListener {
            override fun onUserClick(user: User) = openUserProfile(user.id ?: "")
            override fun onActionClick(user: User) = showBanConfirmDialog(user)
            override fun onDeclineClick(user: User) {}
        })
        binding.rvSearchUsers.adapter = searchAdapter

        binding.searchView.editText.setOnEditorActionListener { _, _, _ ->
            searchUsers(binding.searchView.text.toString())
            false
        }
    }

    private fun setupMainList() {
        binding.rvMain.layoutManager = LinearLayoutManager(this)
        reportAdapter = ReportAdapter(ArrayList(), object : ReportAdapter.OnReportActionListener {
            override fun onDismiss(report: Report) = resolveReport(report, "dismiss")
            override fun onDeleteItem(report: Report) = resolveReport(report, "delete")
            override fun onBanUser(report: Report) = resolveReport(report, "ban")
            override fun onTargetClick(report: Report) {
                when (report.targetType) {
                    "listing" -> startActivity(Intent(this@AdminActivity, ListingDetails::class.java).apply { putExtra("listing_id", report.targetId) })
                    "breeding" -> startActivity(Intent(this@AdminActivity, BreedingListingDetails::class.java).apply { putExtra("listing_id", report.targetId) })
                    else -> openUserProfile(report.targetId ?: "")
                }
            }
        })
    }

    private fun setupToggles() {
        binding.toggleAdminMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                if (checkedId == R.id.btnModeReports) {
                    binding.rvMain.adapter = reportAdapter
                    fetchReports()
                } else {
                    binding.rvMain.adapter = null
                    binding.lblEmpty.visibility = View.VISIBLE
                }
            }
        }
        binding.toggleAdminMode.check(R.id.btnModeReports)
    }

    private fun setupNavigation() {
        NavigationHelper.setup(this, binding.bottomNavigation, R.id.nav_admin)
    }

    private fun openUserProfile(userId: String) {
        val intent = Intent(this, Profile::class.java)
        intent.putExtra("user_id", userId)
        startActivity(intent)
    }

    private fun searchUsers(query: String) {
        if (query.isEmpty()) return
        binding.progressBar.visibility = View.VISIBLE
        val params = session.authParams().toMutableMap()
        params["query"] = query

        lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm("friends/search_users", params)
                binding.progressBar.visibility = View.GONE
                val json = Json.parseToJsonElement(response).jsonObject
                if (json["status"]?.jsonPrimitive?.content == "success") {
                    val arr = json["users"]?.jsonArray
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
                    searchAdapter.setUsers(list)
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun fetchReports() {
        binding.progressBar.visibility = View.VISIBLE
        binding.lblEmpty.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm("admin/get_flagged_items", session.authParams())
                binding.progressBar.visibility = View.GONE
                val json = Json.parseToJsonElement(response).jsonObject
                if (json["status"]?.jsonPrimitive?.content == "success") {
                    val arr = json["reports"]?.jsonArray
                    val list = ArrayList<Report>()
                    arr?.forEach { element ->
                        val r = element.jsonObject
                        list.add(
                            Report(
                                id = r["id"]?.jsonPrimitive?.content,
                                reporterId = r["reporter_id"]?.jsonPrimitive?.content,
                                reporterName = r["reporter_name"]?.jsonPrimitive?.content,
                                targetType = r["target_type"]?.jsonPrimitive?.content,
                                targetId = r["target_id"]?.jsonPrimitive?.content,
                                reason = r["reason"]?.jsonPrimitive?.content,
                                details = r["details"]?.jsonPrimitive?.content,
                                status = r["status"]?.jsonPrimitive?.content ?: "pending",
                                createdAt = r["created_at"]?.jsonPrimitive?.content
                            )
                        )
                    }
                    reportAdapter.setReports(list)
                    binding.lblEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.lblEmpty.visibility = View.VISIBLE
            }
        }
    }

    private fun resolveReport(report: Report, action: String) {
        val params = session.authParams().toMutableMap()
        params["report_id"] = report.id ?: ""
        params["action"] = action

        lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm("admin/resolve_report", params)
                val json = Json.parseToJsonElement(response).jsonObject
                Toast.makeText(this@AdminActivity, json["message"]?.jsonPrimitive?.content, Toast.LENGTH_SHORT).show()
                if (json["status"]?.jsonPrimitive?.content == "success") fetchReports()
            } catch (e: Exception) {}
        }
    }

    private fun showBanConfirmDialog(user: User) {
        AlertDialog.Builder(this)
            .setTitle("Ban User")
            .setMessage("Are you sure you want to ban ${user.username}?")
            .setPositiveButton("Ban") { _, _ -> banUser(user.id ?: "") }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun banUser(userId: String) {
        val params = session.authParams().toMutableMap()
        params["target_user_id"] = userId
        params["reason"] = "Violation of terms"
        lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm("admin/ban_user", params)
                val json = Json.parseToJsonElement(response).jsonObject
                if (json["status"]?.jsonPrimitive?.content == "success") {
                    Toast.makeText(this@AdminActivity, "User banned", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {}
        }
    }
}
