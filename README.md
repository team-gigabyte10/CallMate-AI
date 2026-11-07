CallMate AI — Native Android Technical Architecture & Implementation Blueprint
Selected architecture confirmed
Your decisions establish the following product direction:
- Communication model: Hybrid Assistant — reminders, assisted calls, and automatic messaging where officially supported and authorized.
- Development workflow: Google AI Studio Native Android.
- Primary language: Kotlin.
- UI framework: Jetpack Compose.
- AI engine: Gemini.
- Local database: Room.
- Scheduling: AlarmManager + WorkManager.
- Architecture: Clean Architecture + MVVM.
I recommend building CallMate AI as a native Android application with an offline-capable scheduling engine and an AI layer that can be unavailable without breaking existing reminders.

The most important architectural decision is to separate three responsibilities:
1. AI decides what the user means.
2. The application validates and schedules the requested task.
3. The Android communication layer performs only authorized actions.
This prevents Gemini from directly making phone calls or sending messages without application-level validation.
1. Final system architecture
CallMate AI — Android App
Kotlin · Jetpack Compose · Material 3


Presentation Layer
Home UI

AI Chat UI

Schedule UI

Contact UI


ViewModels · UI State · Navigation


Domain Layer
AI Task Parser

Task Validator

Task Scheduler

Permission Manager


Use cases · Business rules · Domain models


AI Service

Gemini API


Persistence

Room Database


Scheduler

AlarmManager


Communication

Android Intents




Communication execution flow
For a scheduled SMS:

User Voice → Gemini → Task JSON → Validation → Confirmation → Room → Scheduler → SMS Gateway/Provider → Result

For a scheduled call:

User Voice → Gemini → Task JSON → Validation → Confirmation → Room → Notification → User taps Call → Android Dialer

For automatic SMS, the app must use an eligible, authorized sending route. A standard third-party Android application cannot assume it can silently send SMS or WhatsApp messages. Where SMS permission or default-handler restrictions apply, use the assisted SMS flow instead.
2. Important Google AI Studio limitation
There is one architectural issue to account for before implementation.

Google AI Studio's native Android builder generates a single-activity, single-module Kotlin/Compose project. Its native build environment is not equivalent to a full backend development environment. 
Google AI for Developers+1


For CallMate AI:
- Generate the Android client in AI Studio.
- Keep the initial task engine and database local.
- Integrate Gemini through Firebase AI Logic when the Android integration is configured and supported.
- Use a separate backend later for cloud synchronization, secure business logic and integrations that need server credentials.
- Export to Android Studio when you need deeper native debugging and production integration.
Firebase AI Logic provides Kotlin Android SDKs, App Check protection and Gemini structured output/function calling. 
Firebase+2


This is the architecture I would actually implement rather than forcing all functionality into one AI-generated screen.
3. Android project structure
Ask AI Studio to maintain these logical packages, even if the initial project uses one Gradle module.
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


4. Core database architecture
Use Room as the source of truth for scheduled tasks.

I recommend these six entities.
Entity	Responsibility
ContactEntity	Contact and nickname mapping
TaskEntity	Main scheduled task
MessageEntity	Message content and delivery mode
RecurrenceEntity	Repeating schedules
ExecutionEntity	Execution attempts and results
ConversationEntity	AI conversation history

TaskEntity — recommended model
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey
    val id: String,

    val contactId: String?,

    val title: String,

    val actionType: String,

    val messageBody: String?,

    val scheduledAt: Long,

    val timezone: String,

    val executionMode: String,

    val status: String,

    val recurrenceRule: String?,

    val requiresConfirmation: Boolean,

    val createdAt: Long,

    val updatedAt: Long
)


Communication types
enum class ActionType {
    CALL,
    SMS,
    WHATSAPP,
    REMINDER
}

enum class ExecutionMode {
    REMINDER,
    ASSISTED,
    AUTOMATIC
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


Use enums and typed database converters in the actual implementation, rather than storing unchecked arbitrary strings.
Important design decision
Keep executionMode separate from actionType.

For example:
- CALL + REMINDER = Notify me to call Mom.
- CALL + ASSISTED = Open the dialer when I approve.
- SMS + ASSISTED = Prepare an SMS for my approval.
- SMS + AUTOMATIC = Send through an authorized SMS integration.
This distinction will make future development much easier.
5. AI task parsing architecture
Gemini should return a structured task proposal, not execute a task.

I recommend this Kotlin model:
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


AI instruction hierarchy
The system prompt should establish these rules:
1. Understand Bangla, English and Banglish.
2. Extract communication intent.
3. Resolve relative dates using an explicit current-date/time context.
4. Never invent a contact.
5. Never guess an ambiguous time.
6. Distinguish a reminder from an actual communication action.
7. Ask clarification questions when necessary.
8. Never execute communication actions.
9. Return structured output.
10. Require user confirmation before committing an external action.
Example interaction
"আগামীকাল দুপুর ২টায় আম্মুকে ফোন করে ওষুধ খেতে মনে করিয়ে দিও।"

CallMate AI

I found Mom in your contacts.

Scheduled action: Call Mom

Purpose: Medicine reminder

Date: Tomorrow

Time: 2:00 PM

Mode: Reminder


Confirm
Change time
Cancel

Illustrative confirmation flow.

6. Scheduler and background execution
This module is responsible for making CallMate useful even when the application is closed.
Recommended scheduling strategy
Requirement	Android component
Exact user-requested reminder	AlarmManager
Flexible background processing	WorkManager
Alarm event handling	BroadcastReceiver
Device reboot recovery	BootReceiver
Notification display	NotificationManager
Task persistence	Room

Android documents that AlarmManager can trigger scheduled operations even when the app is not running. Exact alarms require special permission on applicable Android versions. 
Android Developers+1

Scheduler execution logic
#chatgpt-mermaid-_r_it_{font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";font-size:16px;fill:rgb(237, 237, 237);}@keyframes edge-animation-frame{from{stroke-dashoffset:0;}}@keyframes dash{to{stroke-dashoffset:0;}}#chatgpt-mermaid-_r_it_ .edge-animation-slow{stroke-dasharray:9,5!important;stroke-dashoffset:900;animation:dash 50s linear infinite;stroke-linecap:round;}#chatgpt-mermaid-_r_it_ .edge-animation-fast{stroke-dasharray:9,5!important;stroke-dashoffset:900;animation:dash 20s linear infinite;stroke-linecap:round;}#chatgpt-mermaid-_r_it_ .error-icon{fill:rgb(48, 48, 48);}#chatgpt-mermaid-_r_it_ .error-text{fill:rgb(237, 237, 237);stroke:rgb(237, 237, 237);}#chatgpt-mermaid-_r_it_ .edge-thickness-normal{stroke-width:1px;}#chatgpt-mermaid-_r_it_ .edge-thickness-thick{stroke-width:3.5px;}#chatgpt-mermaid-_r_it_ .edge-pattern-solid{stroke-dasharray:0;}#chatgpt-mermaid-_r_it_ .edge-thickness-invisible{stroke-width:0;fill:none;}#chatgpt-mermaid-_r_it_ .edge-pattern-dashed{stroke-dasharray:3;}#chatgpt-mermaid-_r_it_ .edge-pattern-dotted{stroke-dasharray:2;}#chatgpt-mermaid-_r_it_ .marker{fill:rgb(175, 175, 175);stroke:rgb(175, 175, 175);}#chatgpt-mermaid-_r_it_ .marker.cross{stroke:rgb(175, 175, 175);}#chatgpt-mermaid-_r_it_ svg{font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";font-size:16px;}#chatgpt-mermaid-_r_it_ p{margin:0;}#chatgpt-mermaid-_r_it_ .label{font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";color:rgb(237, 237, 237);}#chatgpt-mermaid-_r_it_ .cluster-label text{fill:rgb(237, 237, 237);}#chatgpt-mermaid-_r_it_ .cluster-label span{color:rgb(237, 237, 237);}#chatgpt-mermaid-_r_it_ .cluster-label span p{background-color:transparent;}#chatgpt-mermaid-_r_it_ .label text,#chatgpt-mermaid-_r_it_ span{fill:rgb(237, 237, 237);color:rgb(237, 237, 237);}#chatgpt-mermaid-_r_it_ .node rect,#chatgpt-mermaid-_r_it_ .node circle,#chatgpt-mermaid-_r_it_ .node ellipse,#chatgpt-mermaid-_r_it_ .node polygon,#chatgpt-mermaid-_r_it_ .node path{fill:rgb(9, 23, 44);stroke:rgb(31, 78, 148);stroke-width:1px;}#chatgpt-mermaid-_r_it_ .rough-node .label text,#chatgpt-mermaid-_r_it_ .node .label text,#chatgpt-mermaid-_r_it_ .image-shape .label,#chatgpt-mermaid-_r_it_ .icon-shape .label{text-anchor:middle;}#chatgpt-mermaid-_r_it_ .node .katex path{fill:#000;stroke:#000;stroke-width:1px;}#chatgpt-mermaid-_r_it_ .rough-node .label,#chatgpt-mermaid-_r_it_ .node .label,#chatgpt-mermaid-_r_it_ .image-shape .label,#chatgpt-mermaid-_r_it_ .icon-shape .label{text-align:center;}#chatgpt-mermaid-_r_it_ .node.clickable{cursor:pointer;}#chatgpt-mermaid-_r_it_ .root .anchor path{fill:rgb(175, 175, 175)!important;stroke-width:0;stroke:rgb(175, 175, 175);}#chatgpt-mermaid-_r_it_ .arrowheadPath{fill:rgb(175, 175, 175);}#chatgpt-mermaid-_r_it_ .edgePath .path{stroke:rgb(175, 175, 175);stroke-width:1px;}#chatgpt-mermaid-_r_it_ .flowchart-link{stroke:rgb(175, 175, 175);fill:none;}#chatgpt-mermaid-_r_it_ .edgeLabel{background-color:rgb(0, 0, 0);text-align:center;}#chatgpt-mermaid-_r_it_ .edgeLabel p{background-color:rgb(0, 0, 0);}#chatgpt-mermaid-_r_it_ .edgeLabel rect{opacity:0.5;background-color:rgb(0, 0, 0);fill:rgb(0, 0, 0);}#chatgpt-mermaid-_r_it_ .labelBkg{background-color:rgba(0, 0, 0, 0.5);}#chatgpt-mermaid-_r_it_ .cluster rect{fill:rgb(48, 48, 48);stroke:rgba(255, 255, 255, 0.15);stroke-width:1px;}#chatgpt-mermaid-_r_it_ .cluster text{fill:rgb(237, 237, 237);}#chatgpt-mermaid-_r_it_ .cluster span{color:rgb(237, 237, 237);}#chatgpt-mermaid-_r_it_ div.mermaidTooltip{position:absolute;text-align:center;max-width:200px;padding:2px;font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";font-size:12px;background:rgb(48, 48, 48);border:1px solid rgba(255, 255, 255, 0.15);border-radius:2px;pointer-events:none;z-index:100;}#chatgpt-mermaid-_r_it_ .flowchartTitleText{text-anchor:middle;font-size:18px;fill:rgb(237, 237, 237);}#chatgpt-mermaid-_r_it_ rect.text{fill:none;stroke-width:0;}#chatgpt-mermaid-_r_it_ .icon-shape,#chatgpt-mermaid-_r_it_ .image-shape{background-color:rgb(0, 0, 0);text-align:center;}#chatgpt-mermaid-_r_it_ .icon-shape p,#chatgpt-mermaid-_r_it_ .image-shape p{background-color:rgb(0, 0, 0);padding:2px;}#chatgpt-mermaid-_r_it_ .icon-shape .label rect,#chatgpt-mermaid-_r_it_ .image-shape .label rect{opacity:0.5;background-color:rgb(0, 0, 0);fill:rgb(0, 0, 0);}#chatgpt-mermaid-_r_it_ .label-icon{display:inline-block;height:1em;overflow:visible;vertical-align:-0.125em;}#chatgpt-mermaid-_r_it_ .node .label-icon path{fill:currentColor;stroke:revert;stroke-width:revert;}#chatgpt-mermaid-_r_it_ .node .neo-node{stroke:rgb(31, 78, 148);}#chatgpt-mermaid-_r_it_ [data-look="neo"].node rect,#chatgpt-mermaid-_r_it_ [data-look="neo"].cluster rect,#chatgpt-mermaid-_r_it_ [data-look="neo"].node polygon{stroke:url(#chatgpt-mermaid-_r_it_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-_r_it_ [data-look="neo"].swimlane.cluster rect{filter:none;}#chatgpt-mermaid-_r_it_ [data-look="neo"].node path{stroke:url(#chatgpt-mermaid-_r_it_-gradient);stroke-width:1px;}#chatgpt-mermaid-_r_it_ [data-look="neo"].node .outer-path{filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-_r_it_ [data-look="neo"].node .neo-line path{stroke:rgb(31, 78, 148);filter:none;}#chatgpt-mermaid-_r_it_ [data-look="neo"].node circle{stroke:url(#chatgpt-mermaid-_r_it_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-_r_it_ [data-look="neo"].node circle .state-start{fill:#000000;}#chatgpt-mermaid-_r_it_ [data-look="neo"].icon-shape .icon{fill:url(#chatgpt-mermaid-_r_it_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-_r_it_ [data-look="neo"].icon-shape .icon-neo path{stroke:url(#chatgpt-mermaid-_r_it_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-_r_it_ .node text{font-size:14px;font-weight:600;letter-spacing:normal;fill:rgb(153, 206, 255);}#chatgpt-mermaid-_r_it_ .edgeLabels text{font-size:13px;font-weight:600;letter-spacing:-0.08px;fill:rgb(153, 206, 255);}#chatgpt-mermaid-_r_it_ .node tspan[font-weight="normal"],#chatgpt-mermaid-_r_it_ .edgeLabels tspan[font-weight="normal"]{font-weight:600;}#chatgpt-mermaid-_r_it_ .edgeLabel .label rect{opacity:1;rx:13px;ry:13px;fill:rgb(0, 14, 26);stroke:rgb(26, 62, 95);stroke-width:1px;}#chatgpt-mermaid-_r_it_ .node rect,#chatgpt-mermaid-_r_it_ .node circle,#chatgpt-mermaid-_r_it_ .node ellipse,#chatgpt-mermaid-_r_it_ .node polygon,#chatgpt-mermaid-_r_it_ .node path{fill:rgb(0, 40, 77);stroke:rgba(255, 255, 255, 0.1);stroke-width:1px;}#chatgpt-mermaid-_r_it_ .node rect{rx:16px;ry:16px;}#chatgpt-mermaid-_r_it_ .mermaid-decision .label-container{fill:rgb(0, 14, 26);stroke:rgb(26, 62, 95);stroke-dasharray:2,2;}#chatgpt-mermaid-_r_it_ .edgePaths .flowchart-link{stroke:rgb(175, 175, 175);stroke-width:1px;stroke-linecap:round;stroke-linejoin:round;}#chatgpt-mermaid-_r_it_ .marker{fill:rgb(175, 175, 175);stroke:rgb(175, 175, 175);}#chatgpt-mermaid-_r_it_ :root{--mermaid-font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";}User confirms taskSave task in RoomValidate date and timeSchedule Android alarmAlarmReceiverCheck task statusAction type?Show call reminderShow message notificationAuthorized sending route?Execute SMSUser taps CallUser reviews messageRecord resultUpdate Room DatabaseCALLSMS AssistedSMS AutomaticYesNo





Scheduling safeguards
Implement these cases:
- App closed or process killed.
- Device rebooted.
- Notification permission denied.
- Exact alarm permission denied.
- User changes device timezone.
- User changes system clock.
- User edits or cancels a task.
- Duplicate alarm prevention.
- Battery optimization restrictions.
Do not assume that WorkManager provides exact-to-the-minute delivery. Use it for deferrable work, not as a substitute for exact user-requested reminders.
7. Hybrid communication architecture
This is where CallMate's product identity comes from.
A. Scheduled phone calls
The user receives:
Call Reminder

Due now

Call Mom

Remind her to take her medicine.

2:00 PM · Today
Snooze

Call Mom



Implementation:
- Resolve contact ID.
- Retrieve phone number.
- Show notification.
- User taps Call.
- Launch ACTION_DIAL.
- Record that the dialer was opened.
Do not report that a call was completed merely because the dialer opened.
B. Automatic and assisted SMS
I recommend three SMS routes:
Route	Behavior
Assisted SMS	Open SMS app with recipient and body
Authorized SMS	Send using an approved Android SMS role and permission
Cloud messaging	Use an officially supported provider/API

For V1, implement assisted SMS first. Automatic SMS should be an optional capability with a permission and policy eligibility check. Google Play restricts SMS permissions to qualifying default handlers and approved exceptions. 
Play Console Help

C. WhatsApp
For the first release:
- Open the relevant WhatsApp conversation.
- Prepopulate the message where supported.
- Require the user to press Send.
- Track only what your app can actually verify.
Do not label a WhatsApp message as delivered or sent when your application has only opened the conversation.
D. Execution policy
Create a dedicated ExecutionPolicy class.

It should evaluate:
CanExecute(task):
    Is task valid?
    Is contact resolved?
    Is scheduled time reached?
    Is the task still active?
    Is the communication route supported?
    Is required permission granted?
    Is user authorization valid?
    Is this execution duplicated?

    If all conditions pass:
        Allow execution
    Otherwise:
        Request approval or show reminder


An additional important safeguard: a task that was originally approved as a reminder must never silently become an automatic SMS task after an AI update.
8. Detailed Google AI Studio prompts
Use these prompts in order. Each prompt assumes the previous implementation has been generated and tested.

Open Google AI Studio → Build → Android.
Prompt 1 — Build the foundation
Phase 1
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


Prompt 2 — Implement the AI assistant
Phase 2
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




Prompt 3 — Voice assistant
Phase 3
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




Prompt 4 — Task scheduler
Phase 4
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




Prompt 5 — Hybrid communication module
Phase 5
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




Prompt 6 — Integration and production testing
Phase 6
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




9. Firebase and Gemini integration
For the AI layer, use Firebase AI Logic instead of embedding a Gemini Developer API key in the APK.

Recommended configuration:
Service	Purpose
Firebase AI Logic	Gemini communication
Firebase App Check	Protect AI requests
Firebase Authentication	Optional user identity
Cloud Firestore	Optional cloud sync
Firebase Crashlytics	Crash reporting
Firebase Remote Config	Update AI configuration

Official resources:
- Firebase AI Logic — Android setup
- Gemini function calling
- Firebase App Check
AI Studio implementation note: Native Android generation and Firebase service provisioning are separate concerns. Verify that your current AI Studio project supports the required Firebase integration. If it does not, export the project ZIP and complete the Firebase configuration in Android Studio. 
Google AI for Developers+1

10. Android permissions architecture
Do not request every permission when the user first opens the application.

Request permissions contextually.
Permission / access	When required
RECORD_AUDIO	Voice commands
READ_CONTACTS	Contact selection
POST_NOTIFICATIONS	Reminders
RECEIVE_BOOT_COMPLETED	Restore schedules
SCHEDULE_EXACT_ALARM	Precise alarms, when applicable
SEND_SMS	Only for an eligible automatic SMS implementation
Internet	Gemini and cloud services

For the MVP, use ACTION_DIAL and ACTION_SENDTO so you don't need broad phone or SMS permissions.

The exact-alarm access is especially important: check canScheduleExactAlarms() and provide an appropriate fallback if the user declines. 
Android Developers+2

11. Development milestones and acceptance tests
I recommend this implementation order:
1. Milestone 1 — UI foundation
   First build
   Deliverable: Complete navigation, Home screen, schedule interface and mock data.
   
   Acceptance: All screens work and navigation is consistent.
2. Milestone 2 — AI integration
   
   Deliverable: Bangla/English voice and text commands converted into structured task proposals.
   
   Acceptance: Correctly identify action, contact, time and execution mode.
3. Milestone 3 — Local scheduling
   
   Deliverable: Room database, alarms and notifications.
   
   Acceptance: A reminder remains scheduled after the app is closed and device restarted.
4. Milestone 4 — Communication
   
   Deliverable: Call reminders, assisted SMS and WhatsApp integration.
   
   Acceptance: Correct recipient and message are prepared without unintended sending.
5. Milestone 5 — Authorized automation
   
   Deliverable: Eligible automatic SMS integration.
   
   Acceptance: Explicit authorization, duplicate prevention and execution logging.
6. Milestone 6 — Production hardening
   
   Deliverable: Testing, permissions, privacy, error handling and APK.
   
   Acceptance: All core workflows pass on real Android devices.
12. My final product recommendations
I would add three features that could make CallMate AI considerably more useful.

1. Promise Tracker

Remember commitments such as:
- "I promised to send the document to Rahim."
- "I need to call the client about the payment."
- "I told Mom I would visit her on Friday."
The AI turns these into pending follow-up tasks.

2. Smart Follow-up

If you don't complete a reminder, the application can ask:

"Did you manage to call Mom?"

If not, offer to reschedule it.

3. Daily Communication Briefing

Every morning, display:
- Important calls for today.
- Pending messages.
- Overdue follow-ups.
- Recurring reminders.
- Meeting-related tasks.
