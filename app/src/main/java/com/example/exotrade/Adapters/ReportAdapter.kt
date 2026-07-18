package com.example.exotrade.Adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.exotrade.models.Report
import com.example.exotrade.databinding.AdminItemReportBinding

/**
 * Adapter for displaying reports in the admin section.
 */
class ReportAdapter(
    private var reports: List<Report>,
    private val listener: OnReportActionListener
) : RecyclerView.Adapter<ReportAdapter.ViewHolder>() {

    /**
     * Interface definition for a callback to be invoked when actions are performed on a report item.
     */
    interface OnReportActionListener {
        fun onDismiss(report: Report)
        fun onDeleteItem(report: Report)
        fun onBanUser(report: Report)
        fun onTargetClick(report: Report)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = AdminItemReportBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val report = reports[position]
        holder.bind(report, listener)
    }

    override fun getItemCount(): Int = reports.size

    fun setReports(reports: List<Report>) {
        this.reports = reports
        notifyDataSetChanged()
    }

    class ViewHolder(private val binding: AdminItemReportBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(report: Report, listener: OnReportActionListener) {
            with(binding) {
                lblTarget.text = "${report.targetType?.uppercase() ?: "UNKNOWN"} ID: ${report.targetId ?: ""}"
                lblDate.text = report.createdAt
                lblReason.text = "Reason: ${report.reason}"
                lblDetails.text = report.details
                lblReporter.text = "Reported by: ${report.reporterName}"

                root.setOnClickListener { listener.onTargetClick(report) }
                btnDismiss.setOnClickListener { listener.onDismiss(report) }
                btnDelete.setOnClickListener { listener.onDeleteItem(report) }
                btnBan.setOnClickListener { listener.onBanUser(report) }
            }
        }
    }
}
