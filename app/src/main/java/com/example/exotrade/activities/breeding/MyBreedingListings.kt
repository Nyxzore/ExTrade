package com.example.exotrade.activities.breeding

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.exotrade.ExoTradeApplication
import com.example.exotrade.R
import com.example.exotrade.databinding.BreedingActivityMyListingsBinding
import com.example.exotrade.data.SessionRepository
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int

/**
 * Activity for users to manage their active breeding listings.
 */
class MyBreedingListings : AppCompatActivity() {
    private lateinit var binding: BreedingActivityMyListingsBinding
    private lateinit var session: SessionRepository
    private lateinit var adapter: MyBreedingAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = BreedingActivityMyListingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = ExoTradeApplication.container.sessionRepository
        binding.rvMyBreeding.layoutManager = LinearLayoutManager(this)
        adapter = MyBreedingAdapter(mutableListOf())
        binding.rvMyBreeding.adapter = adapter

        binding.toolbar.setOnClickListener { finish() }

        fetchMyListings()
    }

    private fun fetchMyListings() {
        val params = session.authParams().toMutableMap()
        lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm("breeding/get_my_breeding_status.php", params)
                val json = Json.parseToJsonElement(response).jsonObject
                if (json["status"]?.jsonPrimitive?.content == "success") {
                    val arr = json["listings"]?.jsonArray
                    val list = mutableListOf<MyBreedingItem>()
                    arr?.forEach { element ->
                        val o = element.jsonObject
                        list.add(
                            MyBreedingItem(
                                o["id"]?.jsonPrimitive?.content ?: "",
                                o["common_name"]?.jsonPrimitive?.content ?: "",
                                o["scientific_name"]?.jsonPrimitive?.content ?: "",
                                o["match_count"]?.jsonPrimitive?.int ?: 0,
                                o["image_url"]?.jsonPrimitive?.content ?: ""
                            )
                        )
                    }
                    adapter.updateList(list)
                }
            } catch (e: Exception) {}
        }
    }

    private data class MyBreedingItem(
        val id: String,
        val common: String,
        val scientific: String,
        val matchCount: Int,
        val imageUrl: String
    )

    private inner class MyBreedingAdapter(private var items: MutableList<MyBreedingItem>) :
        RecyclerView.Adapter<MyBreedingAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.text1.text = item.common.ifEmpty { item.scientific }
            val matchText = if (item.matchCount > 0) {
                getString(R.string.matches_found_plural, item.matchCount)
            } else {
                getString(R.string.no_matches_yet)
            }
            holder.text2.text = matchText
            holder.itemView.setOnClickListener {
                val intent = Intent(this@MyBreedingListings, BreedingListingDetails::class.java)
                intent.putExtra("listing_id", item.id)
                startActivity(intent)
            }
        }

        override fun getItemCount(): Int = items.size

        fun updateList(newList: List<MyBreedingItem>) {
            this.items = newList.toMutableList()
            notifyDataSetChanged()
        }

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val text1: TextView = v.findViewById(android.R.id.text1)
            val text2: TextView = v.findViewById(android.R.id.text2)
        }
    }
}
