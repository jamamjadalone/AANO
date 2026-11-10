// Tasks tab: create, edit, complete and delete tasks with priority,
// due date, reminder, recurrence and subtasks.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.pages

import app.aa.aano.components.adapters.*
import app.aa.aano.components.views.*
import app.aa.aano.components.dialogs.*

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import app.aa.aano.R
import app.aa.aano.data.Prefs
import app.aa.aano.data.Recurrence
import app.aa.aano.data.Task
import app.aa.aano.data.TaskRepository
import app.aa.aano.databinding.FragmentTasksBinding
import app.aa.aano.net.TaskReminderReceiver
import kotlinx.coroutines.launch

class TasksFragment : Fragment() {

    private var _binding: FragmentTasksBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: TaskRepository
    private lateinit var adapter: TasksAdapter
    private lateinit var prefs: Prefs
    private lateinit var cloudSyncManager: app.aa.aano.net.CloudSyncManager

    private val notificationPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // permission granted or denied; reminder was already scheduled
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = TaskRepository(requireContext())
        prefs = Prefs(requireContext())
        cloudSyncManager = app.aa.aano.net.CloudSyncManager(
            requireContext(),
            app.aa.aano.data.NoteRepository(requireContext()),
            repository
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTasksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.tasksList.layoutManager = LinearLayoutManager(requireContext())
        adapter = TasksAdapter(
            onToggle = { toggleComplete(it) },
            onClick = { showTaskDialog(it) },
            onLongClick = { confirmDelete(it) }
        )
        binding.tasksList.adapter = adapter
        binding.fabAdd.setOnClickListener { showTaskDialog(null) }
        loadTasks()
    }

    override fun onResume() {
        super.onResume()
        loadTasks()
        if (prefs.autoSync && cloudSyncManager.isLoggedIn) {
            syncSilently()
        }
    }

    private fun syncSilently() {
        lifecycleScope.launch {
            val res = cloudSyncManager.syncNow()
            res.onSuccess {
                prefs.syncPending = false
                loadTasks()
            }.onFailure {
                prefs.syncPending = true
            }
        }
    }

    private fun loadTasks() {
        lifecycleScope.launch {
            val tasks = repository.getActive()
            adapter.submitList(tasks)
            binding.emptyView.visibility = if (tasks.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun toggleComplete(task: Task) {
        lifecycleScope.launch {
            repository.toggleComplete(task.id)
            if (task.recurrence != Recurrence.NONE && !task.completed) {
                scheduleReminderIfNeeded(task.id)
            }
            loadTasks()
        }
    }

    private fun confirmDelete(task: Task) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_task)
            .setMessage(R.string.delete_task_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch {
                    TaskReminderReceiver.cancel(requireContext(), task.id)
                    repository.delete(task.id)
                    loadTasks()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showTaskDialog(task: Task?) {
        TaskDialogHelper.showTaskDialog(
            requireContext(),
            layoutInflater,
            task
        ) { title, notes, priority, dueAt, reminderAt, recurrence, newSubs, focusHidden ->
            lifecycleScope.launch {
                if (task != null) {
                    repository.update(
                        task.id, title, notes, priority, dueAt, reminderAt, recurrence, newSubs, focusHidden
                    )
                    rescheduleReminder(task.id)
                } else {
                    val created = repository.create(
                        title, notes, priority, dueAt, reminderAt, recurrence, newSubs, focusHidden
                    )
                    scheduleReminderIfNeeded(created.id)
                }
                loadTasks()
            }
        }
    }

    private fun scheduleReminderIfNeeded(taskId: String) {
        lifecycleScope.launch {
            val task = repository.getById(taskId) ?: return@launch
            val trigger = task.reminderAt ?: return@launch
            requestNotificationPermissionIfNeeded()
            TaskReminderReceiver.schedule(
                requireContext(), task.id, task.title, trigger
            )
        }
    }

    private fun rescheduleReminder(taskId: String) {
        lifecycleScope.launch {
            val task = repository.getById(taskId) ?: return@launch
            TaskReminderReceiver.cancel(requireContext(), taskId)
            val trigger = task.reminderAt
            if (trigger != null) {
                requestNotificationPermissionIfNeeded()
                TaskReminderReceiver.schedule(requireContext(), task.id, task.title, trigger)
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
