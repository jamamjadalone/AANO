package app.aa.aano

import app.aa.aano.data.Recurrence
import app.aa.aano.data.SubTask
import app.aa.aano.data.Task
import app.aa.aano.data.TaskPriority
import org.junit.Assert.*
import org.junit.Test

class TaskModelTest {

    @Test
    fun testTaskCreationAndDefaults() {
        val task = Task(
            id = "task_001",
            title = "Complete Android Audit"
        )

        assertEquals("task_001", task.id)
        assertEquals("Complete Android Audit", task.title)
        assertEquals("", task.notes)
        assertFalse(task.completed)
        assertEquals(TaskPriority.MEDIUM, task.priority)
        assertEquals(Recurrence.NONE, task.recurrence)
        assertNull(task.dueAt)
        assertTrue(task.subtasks.isEmpty())
        assertFalse(task.focusHidden)
    }

    @Test
    fun testSubTaskCompletionState() {
        val sub1 = SubTask(id = "sub_1", title = "Check security", completed = true)
        val sub2 = SubTask(id = "sub_2", title = "Run tests", completed = false)

        val task = Task(
            id = "task_002",
            title = "QA Verification",
            subtasks = listOf(sub1, sub2),
            priority = TaskPriority.HIGH,
            recurrence = Recurrence.DAILY
        )

        assertEquals(2, task.subtasks.size)
        assertTrue(task.subtasks[0].completed)
        assertFalse(task.subtasks[1].completed)
        assertEquals(TaskPriority.HIGH, task.priority)
        assertEquals(Recurrence.DAILY, task.recurrence)
    }

    @Test
    fun testTaskCompletionCopy() {
        val task = Task(id = "task_003", title = "Write reports", completed = false)
        val completedTask = task.copy(completed = true, updatedAt = 9999L)

        assertTrue(completedTask.completed)
        assertEquals(9999L, completedTask.updatedAt)
        assertEquals(task.id, completedTask.id)
    }
}
