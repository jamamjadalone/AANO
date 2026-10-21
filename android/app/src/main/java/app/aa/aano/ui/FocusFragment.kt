// Focus tab: pick 1/3/5 tasks to work on, a simple focus timer, and
// auto-pull of the next pending task when one is completed or hidden.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import app.aa.aano.R
import app.aa.aano.data.TaskRepository
import app.aa.aano.databinding.FragmentFocusBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FocusFragment : Fragment() {

    private var _binding: FragmentFocusBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: TaskRepository
    private lateinit var adapter: FocusAdapter

    private var focusCount = 3
    private var timerJob: Job? = null
    private var remainingMs = DEFAULT_FOCUS_MS
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = TaskRepository(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFocusBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.focusList.layoutManager = LinearLayoutManager(requireContext())
        adapter = FocusAdapter(
            onToggle = { completeTask(it) },
            onHide = { hideTask(it) }
        )
        binding.focusList.adapter = adapter

        binding.focus1.setOnClickListener { setFocusCount(1) }
        binding.focus3.setOnClickListener { setFocusCount(3) }
        binding.focus5.setOnClickListener { setFocusCount(5) }

        binding.btnTimer.setOnClickListener { toggleTimer() }
        binding.btnReset.setOnClickListener { resetTimer() }
        binding.btnShowHidden.setOnClickListener {
            lifecycleScope.launch {
                repository.showAllFocusTasks()
                loadTasks()
            }
        }

        updateTimerText()
        loadTasks()
    }

    override fun onResume() {
        super.onResume()
        loadTasks()
    }

    override fun onPause() {
        super.onPause()
        pauseTimer()
    }

    override fun onDestroyView() {
        timerJob?.cancel()
        super.onDestroyView()
        _binding = null
    }

    private fun setFocusCount(count: Int) {
        focusCount = count
        loadTasks()
    }

    private fun completeTask(task: app.aa.aano.data.Task) {
        lifecycleScope.launch {
            repository.toggleComplete(task.id)
            // Pull in the next pending task automatically.
            loadTasks()
        }
    }

    private fun hideTask(task: app.aa.aano.data.Task) {
        lifecycleScope.launch {
            repository.setFocusHidden(task.id, true)
            loadTasks()
        }
    }

    private fun loadTasks() {
        lifecycleScope.launch {
            val all = repository.getActive()
            val visible = all.filterNot { it.focusHidden }
            val focused = visible.take(focusCount)
            val hiddenCount = all.count { it.focusHidden }
            adapter.submitList(focused)
            binding.emptyView.visibility =
                if (focused.isEmpty()) View.VISIBLE else View.GONE
            // Show an "unhide" affordance only when something is hidden.
            binding.btnShowHidden.visibility =
                if (hiddenCount > 0 && focused.isNotEmpty()) View.VISIBLE else View.GONE

            binding.focus1.isChecked = focusCount == 1
            binding.focus3.isChecked = focusCount == 3
            binding.focus5.isChecked = focusCount == 5
        }
    }

    // ---- Focus timer (simple countdown, 25 or 50 minutes) ----

    private fun toggleTimer() {
        if (running) pauseTimer()
        else startTimer()
    }

    private fun startTimer() {
        if (remainingMs <= 0) resetTimer()
        running = true
        binding.btnTimer.text = getString(R.string.focus_timer_pause)
        timerJob?.cancel()
        timerJob = lifecycleScope.launch {
            while (running) {
                delay(1000)
                remainingMs -= 1000
                if (remainingMs <= 0) {
                    remainingMs = 0
                    running = false
                    binding.btnTimer.text = getString(R.string.focus_timer_start)
                    break
                }
                updateTimerText()
            }
            updateTimerText()
        }
    }

    private fun pauseTimer() {
        running = false
        timerJob?.cancel()
        binding.btnTimer.text = getString(R.string.focus_timer_start)
    }

    private fun resetTimer() {
        pauseTimer()
        remainingMs = DEFAULT_FOCUS_MS
        updateTimerText()
    }

    private fun updateTimerText() {
        val totalSec = remainingMs / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        binding.timerText.text = String.format(java.util.Locale.ROOT, "%02d:%02d", m, s)
    }

    companion object {
        private const val DEFAULT_FOCUS_MS = 25L * 60 * 1000
    }
}