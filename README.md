# CallMate AI — Native Android Architecture & Implementation Blueprint

**CallMate AI** is a personal AI communication assistant for Android designed to manage scheduled phone calls, SMS, WhatsApp message preparation, and personal reminders through natural language voice and text commands.

---

## 1. Product Overview & Key Capabilities

- **Hybrid Communication Assistant**: Handles reminders, assisted calls, and automated messaging through authorized and policy-compliant Android interfaces.
- **Multilingual Understanding**: Natural language processing with support for English, Bangla, and Banglish.
- **Strict Separation of Concerns**:
  1. **AI Layer**: Interprets user intent and extracts structured parameters.
  2. **Application Layer**: Validates, requests confirmation, and schedules tasks.
  3. **Android Communication Layer**: Executes actions only with explicit user authorization and appropriate permissions.
- **Offline Reliability**: Local task management and exact alarm triggering via Room database and Android `AlarmManager`.

---

## 2. Technical Stack

| Layer | Technology |
|---|---|
| **Platform** | Native Android (Kotlin) |
| **UI Framework** | Jetpack Compose · Material 3 |
| **Architecture** | Clean Architecture + MVVM |
| **Database** | Room (SQLite) |
| **Scheduling** | AlarmManager (exact) + WorkManager (deferred tasks) |
| **AI Engine** | Gemini API / Firebase AI Logic |
| **Voice & Speech** | Android `SpeechRecognizer` + `TextToSpeech` |
| **Intents & Actions** | Android `ACTION_DIAL`, `ACTION_SENDTO`, WhatsApp Intents |

---

## 3. System Architecture & Execution Flow

```
CallMate AI — Clean Architecture Layers
├── Presentation Layer: ViewModels, UI State, Navigation, Jetpack Compose Screens
├── Domain Layer: AI Task Parser, Task Validator, Use Cases, Business Policies
├── AI Layer: Gemini Integration, Voice Input / TTS Controllers
├── Data Layer: Room Database, Repositories, Preferences
├── Scheduler: AlarmScheduler, AlarmReceiver, BootReceiver, NotificationManager
└── Communication Layer: CallManager, SmsManager, WhatsAppManager, ExecutionPolicy
```

### Communication Execution Flow

```mermaid
flowchart TD
    User["User Voice / Text Input"] --> AI["Gemini Task Parser"]
    AI --> Proposal["Structured Task Proposal (JSON)"]
    Proposal --> Validator["Task Validator & Policy Check"]
    Validator --> Confirm["User Confirmation UI"]
    Confirm --> Room[("Room Database")]
    Room --> Scheduler["AlarmScheduler (AlarmManager)"]
    Scheduler --> Receiver["AlarmReceiver (Due Time)"]
    Receiver --> ActionCheck{"Action Type?"}
    
    ActionCheck -->|CALL| CallFlow["Notification → User Taps 'Call' → ACTION_DIAL"]
    ActionCheck -->|SMS (Assisted)| SmsAssisted["Notification → Pre-fill ACTION_SENDTO → User Sends"]
    ActionCheck -->|SMS (Automatic)| SmsAutoCheck{"Authorized Route Available?"}
    ActionCheck -->|WHATSAPP| WaFlow["Notification → Open Pre-filled Chat → User Sends"]

    SmsAutoCheck -->|Yes| AutoSend["Send via Authorized SMS Gateway"]
    SmsAutoCheck -->|No / Restricted| SmsAssisted

    CallFlow --> Result["Log Execution & Update Status"]
    SmsAssisted --> Result
    AutoSend --> Result
    WaFlow --> Result
```

---

## 4. Android Project Structure

The project follows standard Clean Architecture modular packaging:

```
com.callmate.ai/
│
├── MainActivity.kt
│
├── core/
│   ├── common/
│   ├── permissions/
│   ├── notifications/
│   ├── time/
│   └── navigation/
│
├── data/
│   ├── local/
│   │   ├── CallMateDatabase.kt
│   │   ├── dao/
│   │   └── entities/
│   ├── repository/
│   └── preferences/
│
├── domain/
│   ├── model/
│   ├── repository/
│   └── usecase/
│
├── ai/
│   ├── GeminiClient.kt
│   ├── VoiceInputManager.kt
│   ├── TaskParser.kt
│   ├── TaskValidator.kt
│   └── AiPrompts.kt
│
├── scheduler/
│   ├── AlarmScheduler.kt
│   ├── AlarmReceiver.kt
│   ├── BootReceiver.kt
│   └── ReminderNotification.kt
│
├── communication/
│   ├── CallManager.kt
│   ├── SmsManager.kt
│   ├── WhatsAppManager.kt
│   └── ExecutionPolicy.kt
│
└── presentation/
    ├── home/
    ├── assistant/
    ├── schedule/
    ├── contacts/
    ├── calendar/
    └── settings/
```

---

## 5. Database Architecture

Room serves as the offline single source of truth for all scheduled tasks and history.

### Entities

| Entity | Purpose |
|---|---|
| `TaskEntity` | Main scheduled task details, schedule time, and status |
| `ContactEntity` | Contact details, phone numbers, and custom nickname mappings |
| `MessageEntity` | Pre-composed message bodies and communication payloads |
| `RecurrenceEntity` | Recurrence rules for recurring reminders (daily, weekly, monthly) |
| `ExecutionEntity` | Audit logs of execution attempts, timestamps, and outcomes |
| `ConversationEntity` | Local AI chat history and prompt context |

### Core Task Entity Model

```kotlin
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey
    val id: String,
    val contactId: String?,
    val title: String,
    val actionType: ActionType,
    val messageBody: String?,
    val scheduledAt: Long,
    val timezone: String,
    val executionMode: ExecutionMode,
    val status: TaskStatus,
    val recurrenceRule: String?,
    val requiresConfirmation: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

enum class ActionType {
    CALL,
    SMS,
    WHATSAPP,
    REMINDER
}

enum class ExecutionMode {
    REMINDER,   // Notify user to take action
    ASSISTED,   // Launch native dialer or messaging app pre-filled for user review
    AUTOMATIC   // Direct send (only when eligible and authorized)
}

enum class TaskStatus {
    DRAFT,
    SCHEDULED,
    DUE,
    COMPLETED,
    SNOOZED,
    CANCELLED,
    MISSED,
    FAILED
}
```

> **Design Note:** Keeping `executionMode` separate from `actionType` allows full flexibility (e.g., `CALL` + `REMINDER` prompts the user, while `CALL` + `ASSISTED` triggers the dialer intent upon user confirmation).

---

## 6. AI Task Parsing & Validation

Gemini parses raw user speech/text into structured proposals. The AI **never** triggers native Android communication APIs directly.

### Structured Task Proposal Model

```kotlin
data class AiTaskProposal(
    val intent: String,
    val contactName: String?,
    val actionType: ActionType?,
    val messageBody: String?,
    val scheduledDateTime: String?,
    val timezone: String,
    val recurrence: String?,
    val executionMode: ExecutionMode,
    val missingFields: List<String>,
    val clarificationQuestion: String?,
    val requiresConfirmation: Boolean
)
```

### Core AI Governance Rules

1. **Multilingual Recognition**: Parse Bangla, English, and Banglish seamlessly.
2. **Contact Protection**: Never invent or guess contacts; match strictly against user contacts or ask for clarification.
3. **Time Disambiguation**: Resolve relative times (e.g., "tomorrow at 2 PM") against the device's explicit clock and timezone.
4. **Mandatory Confirmation**: Every generated task must be presented to the user for confirmation or editing before saving to Room.
5. **Mode Locking**: An action approved as a `REMINDER` must never silently change into an `AUTOMATIC` action.

---

## 7. Background Execution & Scheduling Safeguards

| Component | Responsibility |
|---|---|
| `AlarmManager` | Exact time delivery using `setExactAndAllowWhileIdle()` |
| `WorkManager` | Deferred tasks, routine cleanup, and non-time-critical synchronization |
| `BroadcastReceiver` | Handles system alarm triggers and surfaces notifications |
| `BootReceiver` | Reschedules active tasks upon device reboot (`RECEIVE_BOOT_COMPLETED`) |
| `NotificationManager` | Posts interactive notifications with direct actions (Call, Snooze, Open App) |

### Reliability Safeguards

- **Reboot Recovery**: Re-registers all pending alarms from the Room database on boot.
- **Clock & Timezone Changes**: Listens for `TIMEZONE_CHANGED` and `TIME_SET` broadcasts to recompute alarm triggers.
- **Duplicate Prevention**: Unique, deterministic request codes based on task ID hashes.
- **Graceful Permission Degradation**: Validates `canScheduleExactAlarms()` on Android 12+ (API 31+).

---

## 8. Hybrid Communication Strategy

### A. Scheduled Phone Calls
- **Flow**: Task notification fires → User taps **Call** action → Triggers `Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))`.
- **Audit**: Logs that the dialer was launched; does not falsely mark call duration or completion without carrier/call-state verification.

### B. SMS (Assisted vs. Automatic)
- **Assisted SMS (Default)**: Prepares message via `Intent(Intent.ACTION_SENDTO)` with pre-filled recipient and text, requiring the user to tap send.
- **Authorized Automatic SMS**: Reserved for deployments meeting Google Play Store default SMS handler or policy requirements with explicit user consent.

### C. WhatsApp Message Preparation
- Opens WhatsApp chat URI (`https://api.whatsapp.com/send?phone=...&text=...`) with pre-filled content.
- Requires final user tap to dispatch the message.

---

## 9. Android Permissions Architecture

Permissions are requested contextually rather than all at once on initial launch:

| Permission | When Required | Scope |
|---|---|---|
| `RECORD_AUDIO` | User taps microphone for voice commands | Audio input |
| `READ_CONTACTS` | Contact matching and phone number resolution | Read-only |
| `POST_NOTIFICATIONS` | Alarm notifications on Android 13+ (API 33+) | System notification |
| `RECEIVE_BOOT_COMPLETED` | Alarm re-registration upon device reboot | Background receiver |
| `SCHEDULE_EXACT_ALARM` | Precise to-the-minute alarm delivery (API 31+) | System alarm |
| `INTERNET` | Gemini API and Firebase service requests | Network |
| `SEND_SMS` | Only if authorized automatic SMS sending is enabled | Telephony |

---

## 10. Development Milestones & Roadmap

- **Milestone 1 — Foundation & UI**: Compose navigation, Material 3 theming, Home, Schedule, and Contacts screens with mock data.
- **Milestone 2 — AI Task Intelligence**: Gemini integration, Bangla/English task parser, intent detection, and structured output validator.
- **Milestone 3 — Local Persistence & Scheduling**: Room database, `AlarmManager` integration, `BootReceiver`, and notification engine.
- **Milestone 4 — Communication Integrations**: Dialer launch, assisted SMS (`ACTION_SENDTO`), and WhatsApp URI launcher.
- **Milestone 5 — Reliability & Policy Hardening**: Timezone reconciliation, permission fallbacks, battery optimization handling, and execution audit logging.
- **Milestone 6 — Advanced Assistant Features**:
  - **Promise Tracker**: Extracts implicit commitments (e.g., *"I promised to send Rahim the file"*) into trackable follow-ups.
  - **Smart Follow-up**: Prompts user if an action was missed and offers quick rescheduling.
  - **Daily Briefing**: Morning summary of upcoming calls, pending follow-ups, and recurring tasks.


### Prompt 1 — Build the foundation

PROJECT: CallMate AI

Build a complete native Android application using Kotlin, Jetpack Compose and Material 3.

PRODUCT:
CallMate AI is a personal AI communication assistant that manages scheduled phone calls, SMS, WhatsApp message preparation, and personal reminders.

ARCHITECTURE:
Use MVVM and Clean Architecture principles with clear separation between presentation, domain and data layers.

Create the following packages:

core, data, domain, ai, scheduler, communication, presentation.

UI REQUIREMENTS:

1. Home:
- Personalized greeting.
- AI assistant conversation panel.
- Animated microphone button.
- Text command input.
- Today's tasks.
- Upcoming communication reminders.
- Quick action buttons.

2. Schedule:
- Upcoming tasks.
- Completed tasks.
- Missed tasks.
- Recurring tasks.
- Filters and search.

3. Contacts:
- Device contact integration.
- Contact nickname management.
- Favorite contacts.

4. Calendar:
- Daily and weekly schedule views.
- Meeting follow-ups.

5. Settings:
- Language.
- Theme.
- AI configuration.
- Voice settings.
- Notifications.
- Communication permissions.
- Privacy.

DATABASE:
Create Room entities, DAOs and repositories for:
ContactEntity, TaskEntity, MessageEntity, RecurrenceEntity, ExecutionEntity and ConversationEntity.

TASK TYPES:
CALL, SMS, WHATSAPP, REMINDER.

EXECUTION MODES:
REMINDER, ASSISTED, AUTOMATIC.

TASK STATUSES:
DRAFT, SCHEDULED, DUE, COMPLETED, SNOOZED, CANCELLED, MISSED, FAILED.

DESIGN:
Create a premium AI productivity app.
Use a modern, minimal, polished design.
Provide dark and light themes.
Support English and Bangla.
Use reusable Compose components.

CONSTRAINTS:
- Do not build a web application.
- Do not use Flutter or React Native.
- Do not generate a fake backend.
- Do not hardcode contact records.
- Do not claim unimplemented functions work.
- Use a single Activity and a maintainable project structure.

First generate the application foundation, UI, navigation, database and mock repository. Compile and fix errors before finishing.


### Prompt 2 — Implement the AI assistant

Implement the CallMate AI intelligence module.

Use Gemini through Firebase AI Logic with a compatible Kotlin Android SDK if supported by the project environment. If configuration is unavailable, implement a clean GeminiRepository interface and a testable mock implementation. Do not place a permanent API key in source code.

FEATURES:

- Bangla natural language processing.
- English natural language processing.
- Banglish understanding.
- Speech-to-text command processing.
- AI conversational interface.
- Contact name extraction.
- Date and time extraction.
- Communication intent detection.
- Recurrence detection.
- Missing-information detection.

Create:
GeminiRepository
TaskParser
TaskValidator
AiTaskProposal
ConversationRepository

SUPPORTED INTENTS:

CREATE_CALL_REMINDER
CREATE_SMS_REMINDER
CREATE_WHATSAPP_REMINDER
CREATE_GENERAL_REMINDER
CREATE_RECURRING_TASK
UPDATE_TASK
CANCEL_TASK
LIST_TASKS
COMPLETE_TASK

AI TASK OUTPUT:

Return structured JSON with:
intent
contactName
actionType
messageBody
scheduledDateTime
timezone
recurrence
executionMode
missingFields
clarificationQuestion
requiresConfirmation

AI RULES:

1. Never invent contact information.
2. Never guess an ambiguous date or time.
3. Resolve relative dates using the device's current local date and timezone.
4. Distinguish reminder from communication execution.
5. Default calls to REMINDER mode.
6. Default external messages to ASSISTED mode.
7. Never upgrade executionMode without explicit authorization.
8. Require user confirmation before saving a new task.
9. Never let Gemini execute phone or messaging APIs directly.
10. Validate all structured output using Kotlin code.

Add error handling, retry handling, network availability checks and loading indicators.

Provide unit tests for natural-language parsing and validation.



### Prompt 3 — Voice assistant

Implement the native voice assistant for CallMate AI.

Use Android SpeechRecognizer for speech recognition and Android TextToSpeech for spoken responses.

REQUIREMENTS:

1. Microphone permission handling.
2. Voice recording state.
3. Listening animation.
4. Speech-to-text result display.
5. English and Bangla language support where available.
6. Banglish input support through Gemini text understanding.
7. Spoken AI responses.
8. Stop and cancel voice input.
9. Handle speech recognition errors.
10. Allow editing recognized text before submission.

VOICE INTERACTION:

User taps microphone.
Application listens.
Speech is converted to text.
Text is sent to TaskParser.
Gemini generates a structured proposal.
Application displays confirmation.
User confirms or edits.

Do not continuously listen in the background.

Do not upload microphone recordings unless explicitly needed and authorized.

Create a reusable VoiceAssistantController and integrate it with the Home screen.



### Prompt 4 — Task scheduler

Implement the production-oriented task scheduling engine.

Use:
- AlarmManager for user-requested time-based reminders.
- WorkManager for deferrable background work.
- BroadcastReceiver for scheduled alarm delivery.
- BootReceiver for reboot recovery.
- Room Database for persistent task state.
- NotificationManager for notifications.

FUNCTIONAL REQUIREMENTS:

1. Create scheduled task.
2. Edit task.
3. Cancel task.
4. Snooze task.
5. Complete task.
6. Recurring daily tasks.
7. Recurring weekly tasks.
8. Recurring monthly tasks.
9. Handle timezone changes.
10. Recover tasks after device restart.
11. Avoid duplicate alarms.
12. Handle exact alarm permission denial.
13. Handle notification permission denial.

Implement:
AlarmScheduler
AlarmReceiver
BootReceiver
TaskSchedulingRepository
ReminderNotificationManager

Use stable task identifiers for PendingIntent uniqueness.

Never create an alarm for a cancelled task.

Revalidate task status from Room when the receiver executes.

Do not execute an external communication action directly from the alarm receiver.

For a communication task, display a notification with the appropriate user action.

Create tests for scheduling, cancellation, recurrence and reboot recovery.



### Prompt 5 — Hybrid communication module

Implement CallMate AI's hybrid communication system.

Create:
CallManager
SmsManager
WhatsAppManager
ExecutionPolicy
CommunicationRepository

CALL FUNCTIONALITY:

- Resolve contact details.
- Display selected contact and phone number.
- Use ACTION_DIAL.
- Add Call and Snooze notification actions.
- Track when the dialer is opened.
- Never claim a call was completed without verification.

SMS FUNCTIONALITY:

- Create message templates.
- Schedule message reminders.
- Prepare messages using ACTION_SENDTO.
- Prefill recipient and body.
- Support optional automatic SMS only when the application meets Android and Google Play eligibility requirements and the user has explicitly authorized it.
- If permission or eligibility is unavailable, use assisted messaging.

WHATSAPP FUNCTIONALITY:

- Open supported WhatsApp conversation links.
- Prefill text where supported.
- Require user interaction to send.
- Do not claim successful delivery without provider verification.

SECURITY:

- Never execute an unapproved AI-generated action.
- Validate task status and execution time.
- Prevent duplicate execution.
- Validate the destination contact.
- Check communication permissions.
- Preserve the user's original execution mode.
- Log execution attempts and outcomes.

Create clear failure states and user-friendly notifications.


### Prompt 6 — Integration and production testing

Perform complete integration testing and production hardening of CallMate AI.

Verify:

1. AI voice command to task proposal.
2. Bangla and English command parsing.
3. Contact resolution.
4. User confirmation.
5. Room persistence.
6. Alarm scheduling.
7. Notification delivery.
8. Call reminder and dialer launch.
9. Assisted SMS workflow.
10. Optional authorized automatic SMS workflow.
11. WhatsApp assisted workflow.
12. Snooze and reschedule.
13. Recurring reminders.
14. Device reboot recovery.
15. Timezone changes.
16. Permission denial handling.
17. Duplicate execution prevention.
18. Offline scheduling.

TEST DEVICES:
- Android 10.
- Android 12.
- Android 13.
- Android 14.
- Android 15 or newer where available.

Check Android API compatibility and runtime permissions.

Improve app performance, accessibility, error handling, localization and privacy.

Remove all fake production functionality.

Create a README containing:
- Architecture.
- Dependencies.
- Firebase configuration.
- Gemini setup.
- Android permissions.
- Build instructions.
- Testing instructions.
- Known platform limitations.

Run the available build and tests. Fix all compilation errors before declaring the project complete.