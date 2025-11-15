package org.fossify.voicerecorder.dialogs

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.voicerecorder.R
import org.fossify.voicerecorder.models.Transcription
import org.fossify.voicerecorder.models.TranscriptionSegment
import org.fossify.voicerecorder.models.Events
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Dialog for displaying transcription with timestamps
 * Supports both completed transcriptions and live transcription updates
 */
class TranscriptionDialog(
    private val activity: Activity,
    private val recordingId: Int,
    private var transcription: Transcription?,
    private val isLive: Boolean = false,
    private val onSegmentClick: ((Long) -> Unit)? = null
) {
    private var dialog: AlertDialog? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingView: View
    private lateinit var emptyView: TextView
    private lateinit var statusView: TextView

    private val adapter = TranscriptionAdapter()
    private val segments = mutableListOf<TranscriptionSegment>()

    init {
        EventBus.getDefault().register(this)
    }

    fun show() {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_transcription, null)

        recyclerView = view.findViewById(R.id.transcription_list)
        loadingView = view.findViewById(R.id.transcription_loading)
        emptyView = view.findViewById(R.id.transcription_empty)
        statusView = view.findViewById(R.id.transcription_status)

        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.adapter = adapter

        // Initialize with existing data
        if (transcription != null) {
            segments.clear()
            segments.addAll(transcription!!.segments)
            adapter.notifyDataSetChanged()
            updateUI()
        } else if (isLive) {
            loadingView.beVisible()
            statusView.text = "Starting live transcription..."
            statusView.beVisible()
        }

        AlertDialog.Builder(activity).apply {
            setPositiveButton(org.fossify.commons.R.string.ok) { _, _ ->
                dismiss()
            }
            if (isLive) {
                setNeutralButton("Stop Live") { _, _ ->
                    EventBus.getDefault().post(Events.LiveTranscriptionStateChanged(false))
                    dismiss()
                }
            }
            activity.setupDialogStuff(view, this) { alertDialog ->
                dialog = alertDialog
            }
        }
    }

    fun dismiss() {
        EventBus.getDefault().unregister(this)
        dialog?.dismiss()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onTranscriptionSegmentReady(event: Events.TranscriptionSegmentReady) {
        if (event.recordingId != recordingId) return

        segments.add(event.segment)
        adapter.notifyItemInserted(segments.size - 1)
        recyclerView.scrollToPosition(segments.size - 1)
        updateUI()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onTranscriptionProgress(event: Events.TranscriptionProgress) {
        if (event.recordingId != recordingId) return

        val percentage = (event.progress * 100).toInt()
        statusView.text = "Transcribing: $percentage% (${event.currentSegment}/${event.totalSegments})"
        statusView.beVisible()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onTranscriptionCompleted(event: Events.TranscriptionCompleted) {
        if (event.recordingId != recordingId) return

        transcription = event.transcription
        segments.clear()
        segments.addAll(event.transcription.segments)
        adapter.notifyDataSetChanged()
        updateUI()

        statusView.text = "Transcription complete"
        statusView.beVisible()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onTranscriptionFailed(event: Events.TranscriptionFailed) {
        if (event.recordingId != recordingId) return

        statusView.text = "Error: ${event.error}"
        statusView.beVisible()
        loadingView.beGone()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onLiveTranscriptionStateChanged(event: Events.LiveTranscriptionStateChanged) {
        if (!event.isActive && isLive) {
            statusView.text = "Live transcription stopped"
            loadingView.beGone()
        }
    }

    private fun updateUI() {
        if (segments.isEmpty()) {
            recyclerView.beGone()
            emptyView.beVisible()
            loadingView.beGone()
        } else {
            recyclerView.beVisible()
            emptyView.beGone()
            loadingView.beGone()
        }
    }

    /**
     * Adapter for displaying transcription segments
     */
    private inner class TranscriptionAdapter : RecyclerView.Adapter<TranscriptionAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_transcription_segment, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val segment = segments[position]
            holder.bind(segment)
        }

        override fun getItemCount() = segments.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val timestampText: TextView = itemView.findViewById(R.id.segment_timestamp)
            private val contentText: TextView = itemView.findViewById(R.id.segment_text)

            fun bind(segment: TranscriptionSegment) {
                timestampText.text = segment.formatTimeRange()
                contentText.text = segment.text

                timestampText.setTextColor(activity.getProperTextColor())
                contentText.setTextColor(activity.getProperTextColor())

                itemView.setOnClickListener {
                    onSegmentClick?.invoke(segment.startTime)
                }
            }
        }
    }
}
