// EditorAudioHelper: Voice memo recording, playback, timer and lifecycle cleanup
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.components.editor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import app.aa.aano.R
import app.aa.aano.databinding.ActivityEditorBinding
import java.io.File

class EditorAudioHelper(
    private val context: Context,
    private val binding: ActivityEditorBinding,
    private val onAudioUpdated: (uri: String?, duration: Long) -> Unit
) {

    var attachedAudioUri: String? = null
        private set
    var attachedAudioDuration: Long = 0L
        private set

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    var isRecording: Boolean = false
        private set
    var isPlayingVoice: Boolean = false
        private set
    private var recordingStartTime: Long = 0L
    private val recordingTimerHandler = Handler(Looper.getMainLooper())
    private var currentRecordingFile: File? = null

    fun setInitialAudio(uri: String?, duration: Long) {
        attachedAudioUri = uri
        attachedAudioDuration = duration
        renderVoiceMemoBar()
    }

    fun handleRecordVoiceClick(permissionLauncher: ActivityResultLauncher<String>) {
        if (isRecording) {
            stopVoiceRecording()
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startVoiceRecording()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    fun startVoiceRecording() {
        try {
            val audioDir = File(context.filesDir, "media/audio").apply { mkdirs() }
            val file = File(audioDir, "voice_${System.currentTimeMillis()}.m4a")
            currentRecordingFile = file

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(96000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            recordingStartTime = SystemClock.elapsedRealtime()
            binding.recordingLiveBar.visibility = android.view.View.VISIBLE
            startRecordingTimer()
        } catch (e: Exception) {
            isRecording = false
            Toast.makeText(context, "Could not start recording: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopVoiceRecording() {
        if (!isRecording) return
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: Exception) { Log.w("AANO", "MediaRecorder release error: ${e.message}") }
        mediaRecorder = null
        isRecording = false
        stopRecordingTimer()
        binding.recordingLiveBar.visibility = android.view.View.GONE

        val duration = SystemClock.elapsedRealtime() - recordingStartTime
        currentRecordingFile?.let { file ->
            if (file.exists() && file.length() > 0) {
                attachedAudioUri = file.absolutePath
                attachedAudioDuration = duration
                renderVoiceMemoBar()
                onAudioUpdated(attachedAudioUri, attachedAudioDuration)
                Toast.makeText(context, "Voice memo saved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startRecordingTimer() {
        recordingTimerHandler.post(object : Runnable {
            override fun run() {
                if (isRecording) {
                    val elapsed = SystemClock.elapsedRealtime() - recordingStartTime
                    binding.tvRecordingDuration.text = "Recording Voice... ${formatDuration(elapsed)}"
                    recordingTimerHandler.postDelayed(this, 500)
                }
            }
        })
    }

    private fun stopRecordingTimer() {
        recordingTimerHandler.removeCallbacksAndMessages(null)
    }

    fun playVoiceMemo() {
        val uri = attachedAudioUri ?: return
        val file = File(uri)
        if (!file.exists()) return

        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            isPlayingVoice = false
            binding.btnVoicePlayPause.setImageResource(R.drawable.ic_play)
            return
        }

        try {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    prepare()
                    setOnCompletionListener {
                        isPlayingVoice = false
                        binding.btnVoicePlayPause.setImageResource(R.drawable.ic_play)
                        binding.tvVoiceTimer.text = "Voice Memo (${formatDuration(attachedAudioDuration)})"
                    }
                }
            }
            mediaPlayer?.start()
            isPlayingVoice = true
            binding.btnVoicePlayPause.setImageResource(R.drawable.ic_pause)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot play audio: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteVoiceMemo() {
        AlertDialog.Builder(context)
            .setTitle("Delete Voice Memo")
            .setMessage("Delete this recorded voice memo?")
            .setPositiveButton("Delete") { _, _ ->
                try {
                    mediaPlayer?.stop()
                    mediaPlayer?.release()
                } catch (e: Exception) { Log.w("AANO", "MediaPlayer release error: ${e.message}") }
                mediaPlayer = null
                isPlayingVoice = false

                attachedAudioUri?.let { File(it).delete() }
                attachedAudioUri = null
                attachedAudioDuration = 0L
                renderVoiceMemoBar()
                onAudioUpdated(null, 0L)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun renderVoiceMemoBar() {
        if (attachedAudioUri.isNullOrBlank()) {
            binding.voiceMemoBar.visibility = android.view.View.GONE
            return
        }
        binding.voiceMemoBar.visibility = android.view.View.VISIBLE
        binding.tvVoiceTimer.text = "Voice Memo (${formatDuration(attachedAudioDuration)})"
        binding.btnVoicePlayPause.setImageResource(
            if (isPlayingVoice) R.drawable.ic_pause else R.drawable.ic_play
        )
    }

    fun formatDuration(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    fun setExistingAudio(uri: String?, duration: Long) = setInitialAudio(uri, duration)

    fun onPauseCleanup() {
        if (isRecording) stopVoiceRecording()
        if (isPlayingVoice || mediaPlayer?.isPlaying == true) {
            try {
                mediaPlayer?.pause()
            } catch (e: Exception) {
                Log.d("EditorAudioHelper", "Pause playback error: ${e.message}")
            }
            isPlayingVoice = false
            binding.btnVoicePlayPause.setImageResource(R.drawable.ic_play)
        }
    }

    fun onDestroyCleanup() {
        release()
    }

    fun release() {
        if (isRecording) {
            stopVoiceRecording()
        }
        try {
            mediaRecorder?.release()
        } catch (e: Exception) { Log.w("AANO", "MediaRecorder release error: ${e.message}") }
        mediaRecorder = null
        try {
            mediaPlayer?.release()
        } catch (e: Exception) { Log.w("AANO", "MediaPlayer release error: ${e.message}") }
        mediaPlayer = null
        recordingTimerHandler.removeCallbacksAndMessages(null)
    }
}
