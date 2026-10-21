// Tasks tab: create, edit, complete and delete tasks with priority,
// due date, reminder, recurrence and subtasks.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import app.aa.aano.R
import app.aa.aano.data.NoteRepository
import app.aa.aano.data.Prefs
import app.aa.aano.data.Recurrence
import app.aa.aano.data.SubTask
import app.aa.aano.data.Task
import app.aa.aano.data.TaskPriority
import app.aa.aano.data.TaskRepository
import app.aa.aano.databinding.DialogTaskBinding
import app.aa.aano.databinding.FragmentTasksBinding
import app.aa.aano.net.TaskReminderReceiver
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class TasksFragment : Fragment() {

    private var _binding: FragmentTasksBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: TaskRepository
    private lateinit var adapter: TasksAdapter
    private lateinit var prefs: Prefs
    private lateinit var cloudSyncManager: app.aa.aano.net.CloudSyncManager

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

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
            // For recurring tasks on completion, schedule the next reminder.
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
        val dlgBinding = DialogTaskBinding.inflate(layoutInflater)
        val editing = task != null
        dlgBinding.etTitle.setText(task?.title.orEmpty())
        dlgBinding.etNotes.setText(task?.notes.orEmpty())

        when (task?.priority ?: TaskPriority.MEDIUM) {
            TaskPriority.LOW -> dlgBinding.prioLow.isChecked = true
            TaskPriority.HIGH -> dlgBinding.prioHigh.isChecked = true
            else -> dlgBinding.prioMedium.isChecked = true
        }

        var dueAt = task?.dueAt
        var reminderAt = task?.reminderAt
        updateDueButton(dlgBinding, dueAt)
        updateReminderButton(dlgBinding, reminderAt)

        when (task?.recurrence ?: Recurrence.NONE) {
            Recurrence.DAILY -> dlgBinding.chipDaily.isChecked = true
            Recurrence.WEEKLY -> dlgBinding.chipWeekly.isChecked = true
            Recurrence.MONTHLY -> dlgBinding.chipMonthly.isChecked = true
            else -> dlgBinding.chipNone.isChecked = true
        }

        val subtasks = (task?.subtasks ?: emptyList()).toMutableList()
        subtasks.forEach { addSubtaskRow(dlgBinding, it) }
        dlgBinding.chkFocusHidden.isChecked = task?.focusHidden ?: false

        dlgBinding.btnDue.setOnClickListener {
            pickDueDate(dlgBinding) { ms -> dueAt = ms; updateDueButton(dlgBinding, ms) }
        }
        dlgBinding.btnReminder.setOnClickListener {
            pickReminder(dlgBinding) { ms -> reminderAt = ms; updateReminderButton(dlgBinding, ms) }
        }
        dlgBinding.btnAddSubtask.setOnClickListener {
            val text = dlgBinding.etSubtask.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            addSubtaskRow(dlgBinding, SubTask(UUID.randomUUID().toString(), text))
            dlgBinding.etSubtask.text?.clear()
        }

        AlertDialog.Builder(requireContext())
            .setTitle(if (editing) R.string.edit_task else R.string.add_task)
            .setView(dlgBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val title = dlgBinding.etTitle.text.toString().trim()
                val notes = dlgBinding.etNotes.text.toString().trim()
                val priority = when (dlgBinding.prioGroup.checkedRadioButtonId) {
                    dlgBinding.prioLow.id -> TaskPriority.LOW
                    dlgBinding.prioHigh.id -> TaskPriority.HIGH
                    else -> TaskPriority.MEDIUM
                }
                val recurrence = when {
                    dlgBinding.chipDaily.isChecked -> Recurrence.DAILY
                    dlgBinding.chipWeekly.isChecked -> Recurrence.WEEKLY
                    dlgBinding.chipMonthly.isChecked -> Recurrence.MONTHLY
                    else -> Recurrence.NONE
                }
                if (title.isEmpty()) {
                    Toast.makeText(requireContext(), "Title is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val newSubs = currentSubtasks(dlgBinding)
                val focusHidden = dlgBinding.chkFocusHidden.isChecked
                lifecycleScope.launch {
                    if (editing && task != null) {
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
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun pickDueDate(
        dlgBinding: DialogTaskBinding,
        onPicked: (Long) -> Unit
    ) {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, y, m, d ->
                cal.set(y, m, d, 0, 0, 0)
                onPicked(cal.timeInMillis)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun pickReminder(
        dlgBinding: DialogTaskBinding,
        onPicked: (Long) -> Unit
    ) {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, y, m, d ->
                val timeCal = Calendar.getInstance()
                TimePickerDialog(
                    requireContext(),
                    { _, h, min ->
                        val target = Calendar.getInstance()
                        target.set(y, m, d, h, min, 0)
                        onPicked(target.timeInMillis)
                    },
                    timeCal.get(Calendar.HOUR_OF_DAY),
                    timeCal.get(Calendar.MINUTE),
                    true
                ).show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateDueButton(dlgBinding: DialogTaskBinding, dueAt: Long?) {
        dlgBinding.btnDue.text = if (dueAt != null) {
            requireContext().getString(R.string.due_date) + ": " + dateFormat.format(Date(dueAt))
        } else {
            requireContext().getString(R.string.set_due_date)
        }
    }

    private fun updateReminderButton(dlgBinding: DialogTaskBinding, reminderAt: Long?) {
        dlgBinding.btnReminder.text = if (reminderAt != null) {
            val fmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            requireContext().getString(R.string.reminder_at, fmt.format(Date(reminderAt)))
        } else {
            requireContext().getString(R.string.set_reminder)
        }
    }

    private fun addSubtaskRow(dlgBinding: DialogTaskBinding, sub: SubTask) {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val check = CheckBox(requireContext()).apply {
            isChecked = sub.completed
        }
        val text = EditText(requireContext()).apply {
            setText(sub.title)
            setSingleLine(true)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val remove = ImageButton(requireContext()).apply {
            setImageResource(android.R.drawable.ic_menu_delete)
            setBackgroundColor(0)
            setPadding(16, 16, 16, 16)
            setOnClickListener { dlgBinding.subtaskContainer.removeView(row) }
        }
        row.addView(check)
        row.addView(text)
        row.addView(remove)
        text.tag = sub.id
        check.tag = sub.id
        dlgBinding.subtaskContainer.addView(row)
    }

    private fun currentSubtasks(dlgBinding: DialogTaskBinding): List<SubTask> {
        val out = mutableListOf<SubTask>()
        for (i in 0 until dlgBinding.subtaskContainer.childCount) {
            val row = dlgBinding.subtaskContainer.getChildAt(i) as LinearLayout
            val text = row.getChildAt(1) as? EditText ?: continue
            val check = row.getChildAt(0) as? CheckBox ?: continue
            val title = text.text.toString().trim()
            if (title.isEmpty()) continue
            val id = text.tag as? String ?: UUID.randomUUID().toString()
            out.add(SubTask(id, title, check.isChecked))
        }
        return out
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