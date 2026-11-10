package app.aa.aano.components.dialogs

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import app.aa.aano.R
import app.aa.aano.data.Recurrence
import app.aa.aano.data.SubTask
import app.aa.aano.data.Task
import app.aa.aano.data.TaskPriority
import app.aa.aano.databinding.DialogTaskBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

object TaskDialogHelper {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    fun showTaskDialog(
        context: Context,
        layoutInflater: LayoutInflater,
        task: Task?,
        onSave: (title: String, notes: String, priority: TaskPriority, dueAt: Long?, reminderAt: Long?, recurrence: Recurrence, subtasks: List<SubTask>, focusHidden: Boolean) -> Unit
    ) {
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
        updateDueButton(context, dlgBinding, dueAt)
        updateReminderButton(context, dlgBinding, reminderAt)

        when (task?.recurrence ?: Recurrence.NONE) {
            Recurrence.DAILY -> dlgBinding.chipDaily.isChecked = true
            Recurrence.WEEKLY -> dlgBinding.chipWeekly.isChecked = true
            Recurrence.MONTHLY -> dlgBinding.chipMonthly.isChecked = true
            else -> dlgBinding.chipNone.isChecked = true
        }

        val subtasks = (task?.subtasks ?: emptyList()).toMutableList()
        subtasks.forEach { addSubtaskRow(context, dlgBinding, it) }
        dlgBinding.chkFocusHidden.isChecked = task?.focusHidden ?: false

        dlgBinding.btnDue.setOnClickListener {
            pickDueDate(context) { ms -> dueAt = ms; updateDueButton(context, dlgBinding, ms) }
        }
        dlgBinding.btnReminder.setOnClickListener {
            pickReminder(context) { ms -> reminderAt = ms; updateReminderButton(context, dlgBinding, ms) }
        }
        dlgBinding.btnAddSubtask.setOnClickListener {
            val text = dlgBinding.etSubtask.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            addSubtaskRow(context, dlgBinding, SubTask(UUID.randomUUID().toString(), text))
            dlgBinding.etSubtask.text?.clear()
        }

        AlertDialog.Builder(context)
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
                    Toast.makeText(context, "Title is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val newSubs = currentSubtasks(dlgBinding)
                val focusHidden = dlgBinding.chkFocusHidden.isChecked
                onSave(title, notes, priority, dueAt, reminderAt, recurrence, newSubs, focusHidden)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun pickDueDate(context: Context, onPicked: (Long) -> Unit) {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, y, m, d ->
                cal.set(y, m, d, 0, 0, 0)
                onPicked(cal.timeInMillis)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun pickReminder(context: Context, onPicked: (Long) -> Unit) {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, y, m, d ->
                val timeCal = Calendar.getInstance()
                TimePickerDialog(
                    context,
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

    private fun updateDueButton(context: Context, dlgBinding: DialogTaskBinding, dueAt: Long?) {
        dlgBinding.btnDue.text = if (dueAt != null) {
            context.getString(R.string.due_date) + ": " + dateFormat.format(Date(dueAt))
        } else {
            context.getString(R.string.set_due_date)
        }
    }

    private fun updateReminderButton(context: Context, dlgBinding: DialogTaskBinding, reminderAt: Long?) {
        dlgBinding.btnReminder.text = if (reminderAt != null) {
            val fmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            context.getString(R.string.reminder_at, fmt.format(Date(reminderAt)))
        } else {
            context.getString(R.string.set_reminder)
        }
    }

    private fun addSubtaskRow(context: Context, dlgBinding: DialogTaskBinding, sub: SubTask) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val check = CheckBox(context).apply {
            isChecked = sub.completed
        }
        val text = EditText(context).apply {
            setText(sub.title)
            setSingleLine(true)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val remove = ImageButton(context).apply {
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
}
