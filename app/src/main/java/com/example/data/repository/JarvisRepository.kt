package com.example.data.repository

import com.example.data.database.NoteDao
import com.example.data.database.ReminderDao
import com.example.data.database.CommandLogDao
import com.example.data.models.Note
import com.example.data.models.Reminder
import com.example.data.models.CommandLog
import kotlinx.coroutines.flow.Flow

class JarvisRepository(
    private val noteDao: NoteDao,
    private val reminderDao: ReminderDao,
    private val commandLogDao: CommandLogDao
) {
    val allNotes: Flow<List<Note>> = noteDao.getAllNotes()
    val allReminders: Flow<List<Reminder>> = reminderDao.getAllReminders()
    val allLogs: Flow<List<CommandLog>> = commandLogDao.getAllLogs()

    suspend fun insertNote(note: Note) = noteDao.insertNote(note)
    suspend fun deleteNoteById(id: Int) = noteDao.deleteNoteById(id)

    suspend fun insertReminder(reminder: Reminder) = reminderDao.insertReminder(reminder)
    suspend fun updateReminderCompletion(id: Int, isCompleted: Boolean) =
        reminderDao.updateCompletionStatus(id, isCompleted)
    suspend fun deleteReminderById(id: Int) = reminderDao.deleteReminderById(id)

    suspend fun insertLog(log: CommandLog) = commandLogDao.insertLog(log)
    suspend fun clearLogs() = commandLogDao.clearLogs()
}
