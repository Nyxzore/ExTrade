package com.example.exotrade.utils

import android.content.Context
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.exotrade.ExoTradeApplication
import com.example.exotrade.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Utility class to display a standardized reporting dialog.
 * Handles the collection of reporting reasons and details, and submits them to the backend.
 *
 * @see com.example.exotrade.models.Report
 */
object ReportDialog {

    /** Callback interface for report submission events. */
    fun interface ReportCallback {
        /** Invoked when the report has been successfully submitted to the server. */
        fun onReportSubmitted()
    }

    /**
     * Shows the report dialog.
     *
     * @param context    The UI context.
     * @param targetType The type of entity being reported ("listing" or "user").
     * @param targetId   The unique ID of the target entity.
     * @param callback   Optional callback for success.
     */
    @JvmStatic
    fun show(context: Context, targetType: String, targetId: String, callback: ReportCallback?) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_report, null)
        
        val menuLayout = view.findViewById<TextInputLayout>(R.id.menuReason)
        val etReason = menuLayout.editText as AutoCompleteTextView
        val etDetails = view.findViewById<EditText>(R.id.etDetails)

        val reasons = arrayOf(
            "Scam or Fraud",
            "Illegal Species",
            "Animal Welfare Concern",
            "Misleading Information",
            "Spam",
            "Other"
        )

        val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, reasons)
        etReason.setAdapter(adapter)

        val title = when (targetType) {
            "user" -> "Report User"
            "listing" -> "Report Listing"
            "breeding" -> "Report Breeding Listing"
            else -> "Report"
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(view)
            .setPositiveButton("Submit Report", null) // Handle manually to prevent close on error
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val reason = etReason.text.toString()
            val details = etDetails.text.toString().trim()

            if (reason.isEmpty()) {
                menuLayout.error = "Please select a reason"
                return@setOnClickListener
            }

            submitReport(context, targetType, targetId, reason, details) {
                dialog.dismiss()
                callback?.onReportSubmitted()
            }
        }
    }

    /**
     * Internal method to submit the report data to the server.
     */
    private fun submitReport(
        context: Context,
        type: String,
        id: String,
        reason: String,
        details: String,
        onSuccess: () -> Unit
    ) {
        val session = ExoTradeApplication.container.sessionRepository
        val params = session.authParams().toMutableMap()
        params["target_type"] = type
        params["target_id"] = id
        params["reason"] = reason
        params["details"] = details

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm("core/report_item", params)
                val json = Json.parseToJsonElement(response).jsonObject
                Toast.makeText(context, json["message"]?.jsonPrimitive?.content, Toast.LENGTH_LONG).show()
                if ("success" == json["status"]?.jsonPrimitive?.content) {
                    onSuccess()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to submit report", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
