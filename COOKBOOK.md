# AANO Developer & Agent Architecture Cookbook
> **Audience**: AI Agents & Human Contributors  
> **Mission**: Exact, deterministic recipes for the 5 most common code additions.  
> **Rule 21 Compliance**: All files must remain <= 300 lines. Always consult and update ARCHITECTURE_INDEX.md.

---

## Recipe 1: Add a New Preference / Setting Toggle

When adding a new toggle in **SettingsActivity**:

1. **Add Key in `data/Prefs.kt`**:
   ```kotlin
   const val KEY_VIBRATION_FEEDBACK = "vibration_feedback"
   var isVibrationEnabled: Boolean
       get() = prefs.getBoolean(KEY_VIBRATION_FEEDBACK, true)
       set(value) = prefs.edit().putBoolean(KEY_VIBRATION_FEEDBACK, value).apply()
   ```
2. **Add Switch / Item in `res/layout/activity_settings.xml`**:
   Use `MaterialSwitch` with ID `switchVibration`.
3. **Bind in `pages/SettingsActivity.kt`**:
   ```kotlin
   binding.switchVibration.isChecked = prefs.isVibrationEnabled
   binding.switchVibration.setOnCheckedChangeListener { _, isChecked ->
       prefs.isVibrationEnabled = isChecked
   }
   ```
4. **Include in Backup (`data/SettingsBackupDelegate.kt`)**:
   Include key in `exportSettingsJson()` and `importSettingsJson()` so settings sync cleanly.
5. **Verify**:
   `python aano_dev.py check`

---

## Recipe 2: Add a New Field to the Note Model

When extending the `Note` entity (e.g. adding a reminder type or color label):

1. **Modify `data/Note.kt`**:
   Add the field with a sensible default value for backward compatibility:
   ```kotlin
   val reminderType: Int = 0,
   val reminderDate: Long = 0L
   ```
2. **Update JSON Serialization in `data/Note.kt`**:
   - In `toJson()`: `put("reminderType", reminderType)`
   - In `fromJson()`: `reminderType = optInt("reminderType", 0)`
3. **Expose Mutations in `data/NoteCrudDelegate.kt`**:
   Add atomic update method:
   ```kotlin
   fun updateReminder(noteId: String, type: Int, date: Long): Note?
   ```
4. **Forward through `data/NoteRepository.kt` Facade**:
   Expose matching method in `NoteRepository`:
   ```kotlin
   fun updateReminder(noteId: String, type: Int, date: Long) = crudDelegate.updateReminder(noteId, type, date)
   ```
5. **Verify**:
   `python aano_dev.py check` & `python aano_dev.py doctor`

---

## Recipe 3: Create a Custom Bottom Sheet / Dialog

When building a modal sheet (e.g. a color picker, folder selector, or action sheet):

1. **Scaffold the Dialog**:
   ```bash
   python aano_dev.py scaffold dialog CustomPicker
   ```
   This generates `components/dialogs/CustomPickerDialogHelper.kt`.
2. **Structure the Helper**:
   - Subclass or use `BottomSheetDialog(context)`.
   - Keep line count <= 250 lines.
   - Use lambda callbacks for action handling:
     ```kotlin
     class CustomPickerDialogHelper(
         private val context: Context,
         private val onItemSelected: (String) -> Unit
     ) {
         fun show() { /* inflate & bind */ }
     }
     ```
3. **Register in `ARCHITECTURE_INDEX.md`**:
   Run `python aano_dev.py map` to sync index.

---

## Recipe 4: Add a P2P Drop Command or Payload Route

When adding a new Wi-Fi Direct transfer action (e.g. syncing reminders or pinging peers):

1. **Add Route in `drop/DropServer.kt`**:
   In `serve(session: IHTTPSession)`:
   ```kotlin
   "/aano/custom_action" -> handleCustomAction(session)
   ```
2. **Process Payload in `drop/DropPayloadProcessor.kt`**:
   Decrypt with AES-GCM, validate schema, apply changes to repository:
   ```kotlin
   fun processCustomPayload(jsonPayload: String): Boolean
   ```
3. **Add Outbound Trigger in `drop/DropClient.kt`**:
   ```kotlin
   fun sendCustomPayload(peerIp: String, data: String): Boolean
   ```
4. **Expose in `drop/AanoDropManager.kt` Facade**.

---

## Recipe 5: Add a Background Worker or Scheduled Alarm

When adding a recurring task or scheduled reminder:

1. **Create Receiver in `net/`**:
   - If exact alarm: Subclass `BroadcastReceiver` in `net/NoteReminderReceiver.kt`.
   - Register in `AndroidManifest.xml` with `android:exported="false"`.
2. **Hook System Boot in `net/BootReceiver.kt`**:
   Ensure alarms re-schedule on device restart (`ACTION_BOOT_COMPLETED`).
3. **If Periodic Background Task**:
   - Subclass `CoroutineWorker(context, params)`.
   - Enforce network/battery constraints via `WorkManager`.
4. **Verify Manifest**:
   `python aano_dev.py doctor`
