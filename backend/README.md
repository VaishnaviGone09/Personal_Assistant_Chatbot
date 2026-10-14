# Personal Assistant Chatbot – Backend

A Spring Boot 3.5 REST API for a personal assistant chatbot. It manages tasks, detects schedule conflicts,
suggests free time slots, answers simple chat messages and builds a daily briefing.
Data is stored in a file-based H2 database, so your tasks are kept after a restart.

No external AI key, Google account or email setup is needed. The chat uses a built-in rule-based Java parser.

## Tech stack

- Java 17 or newer (tested with Java 25)
- Spring Boot 3.5.16 (Spring Web, Spring Data JPA, Bean Validation)
- H2 database (file-based)
- Maven
- JUnit 5 + Spring MockMvc tests

## Project structure

```
backend
├── pom.xml
├── README.md
└── src
    ├── main
    │   ├── java/com/personalassistant/backend
    │   │   ├── PersonalAssistantApplication.java   <- starts the app
    │   │   ├── config      (CORS settings, Clock)
    │   │   ├── task        (Task entity, repository, service, REST controller)
    │   │   ├── schedule    (conflict detection, free slot suggestions)
    │   │   ├── chat        (/api/chat and the rule-based message parser)
    │   │   ├── briefing    (/api/briefing)
    │   │   ├── health      (/api/health)
    │   │   └── exception   (JSON error responses)
    │   └── resources/application.properties
    └── test
        ├── java/...        (JUnit tests)
        └── resources/application-test.properties   (tests use an in-memory database)
```

## Start the backend on Windows 11

Open **PowerShell** or **Command Prompt** and run these commands.

1. Check that Java and Maven are installed:

   ```powershell
   java -version
   mvn -v
   ```

   `mvn -v` should show a Java version of 17 or newer.

2. Go to the backend folder (replace the path with your project folder):

   ```powershell
   cd C:\path\to\your-project\backend
   ```

3. Run the tests:

   ```powershell
   mvn clean test
   ```

4. Start the server:

   ```powershell
   mvn spring-boot:run
   ```

   Wait until you see `Started PersonalAssistantApplication`. The API is now at **http://localhost:8080**.
   Press `Ctrl + C` to stop it. If Windows asks `Terminate batch job (Y/N)?`, type `Y` and press Enter.

5. Check that it works by opening http://localhost:8080/api/health in your browser.

### Build and run a JAR file instead

```powershell
mvn clean package
java -jar target\personal-assistant-backend-0.0.1-SNAPSHOT.jar
```

`mvn clean package` also runs all tests.

### Where is my data?

The database file is created in a `data` folder **inside the folder you start the app from**
(for example `backend\data\personal-assistant-db.mv.db`). Always start the app from the `backend` folder
so it uses the same file. To start with an empty database, stop the app and delete the `data` folder.

You can look at the data in the H2 console: http://localhost:8080/h2-console
(JDBC URL: `jdbc:h2:file:./data/personal-assistant-db`, user: `sa`, empty password).
Stop the app first if you want to open the file with another tool.

### Configuration

Settings are in `src/main/resources/application.properties`. You can override some of them with environment
variables before starting the app:

| Environment variable       | Default                                          | Meaning                                      |
|----------------------------|--------------------------------------------------|----------------------------------------------|
| `PORT`                     | `8080`                                           | HTTP port                                    |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173,...` | Browser origins allowed to call the API      |
| `DB_URL`                   | `jdbc:h2:file:./data/personal-assistant-db`      | Database location                            |

Example (PowerShell) – allow a frontend running on port 8000:

```powershell
$env:APP_CORS_ALLOWED_ORIGINS = "http://localhost:8000"
mvn spring-boot:run
```

Example (Command Prompt):

```bat
set APP_CORS_ALLOWED_ORIGINS=http://localhost:8000
mvn spring-boot:run
```

Patterns such as `http://localhost:*` are also accepted. Working hours used for free-slot suggestions are
`app.schedule.day-start` (08:00) and `app.schedule.day-end` (20:00).

## Data model

| Field         | Type   | Required | Notes                                                    |
|---------------|--------|----------|----------------------------------------------------------|
| `title`       | text   | yes      | max 100 characters                                       |
| `description` | text   | no       | max 1000 characters                                      |
| `date`        | date   | yes      | `yyyy-MM-dd`, e.g. `2026-10-20`                          |
| `startTime`   | time   | no       | `HH:mm`, e.g. `14:30`. Give both start and end, or neither |
| `endTime`     | time   | no       | must be after `startTime` (same day)                     |
| `priority`    | enum   | no       | `LOW`, `MEDIUM` (default), `HIGH` (any letter case)      |
| `status`      | enum   | no       | `PENDING` (default), `IN_PROGRESS`, `COMPLETED`, `CANCELLED` |

Schedule rules:

- Only tasks with both a start and end time take up time. Cancelled tasks are ignored.
- `10:00-11:00` and `11:00-12:00` do **not** overlap.
- Creating or updating a task that overlaps another returns **409 Conflict** with the overlapping tasks and up to
  3 free alternative slots of the same length. Add `?allowConflict=true` to save it anyway.
- Free slots are only searched within working hours, never in the past (for today, they start from the
  next quarter hour), and past dates have no free slots.

## API endpoints

| Method | URL                                   | Description                                          |
|--------|---------------------------------------|------------------------------------------------------|
| GET    | `/api/health`                         | Health check (app + database)                        |
| GET    | `/api/tasks`                          | List tasks. Optional `?date=`, `?status=`, `?priority=` |
| GET    | `/api/tasks/today`                    | Today's tasks                                        |
| GET    | `/api/tasks/{id}`                     | One task                                             |
| POST   | `/api/tasks`                          | Create a task (`?allowConflict=true` optional)       |
| PUT    | `/api/tasks/{id}`                     | Update all fields of a task (`?allowConflict=true` optional) |
| PATCH  | `/api/tasks/{id}/status`              | Change only the status                               |
| DELETE | `/api/tasks/{id}`                     | Delete a task                                        |
| GET    | `/api/schedule/check?date=&startTime=&endTime=` | Check a time for conflicts, get suggestions |
| GET    | `/api/schedule/free-slots?date=&durationMinutes=` | Free gaps on a day (default 30 minutes)  |
| GET    | `/api/briefing`                       | Daily summary (optional `?date=`, default today)     |
| POST   | `/api/chat`                           | Chat with the assistant                              |

## Example requests

The examples use **PowerShell** (`Invoke-RestMethod`). Below them are the same requests with `curl` for
**Command Prompt** (curl is included in Windows 11). Change the dates to today or later.

### PowerShell

```powershell
# Health check
Invoke-RestMethod http://localhost:8080/api/health

# Create a task and remember its id
$body = @{
  title       = "Team meeting"
  description = "Weekly sync"
  date        = "2026-10-20"
  startTime   = "10:00"
  endTime     = "11:00"
  priority    = "HIGH"
} | ConvertTo-Json
$task = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/tasks -ContentType "application/json" -Body $body
$id = $task.id
$task

# List all tasks / tasks on one day / today's tasks / one task
Invoke-RestMethod http://localhost:8080/api/tasks
Invoke-RestMethod "http://localhost:8080/api/tasks?date=2026-10-20"
Invoke-RestMethod http://localhost:8080/api/tasks/today
Invoke-RestMethod http://localhost:8080/api/tasks/$id

# Update the task (PUT replaces ALL fields, so send every field you want to keep)
$update = @{ title = "Team meeting (moved)"; description = "Weekly sync"; date = "2026-10-20"; startTime = "14:00"; endTime = "15:00"; priority = "HIGH"; status = "PENDING" } | ConvertTo-Json
Invoke-RestMethod -Method Put -Uri http://localhost:8080/api/tasks/$id -ContentType "application/json" -Body $update

# Mark the task as completed
Invoke-RestMethod -Method Patch -Uri http://localhost:8080/api/tasks/$id/status -ContentType "application/json" -Body '{"status":"COMPLETED"}'

# Check for conflicts and get free slots
Invoke-RestMethod "http://localhost:8080/api/schedule/check?date=2026-10-20&startTime=14:30&endTime=15:30"
Invoke-RestMethod "http://localhost:8080/api/schedule/free-slots?date=2026-10-20&durationMinutes=60"

# Daily briefing
Invoke-RestMethod http://localhost:8080/api/briefing
Invoke-RestMethod "http://localhost:8080/api/briefing?date=2026-10-20"

# Chat
$chat = @{ message = "Add Dentist tomorrow from 3pm to 4pm high priority" } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/chat -ContentType "application/json" -Body $chat
$chat = @{ message = "What are my tasks tomorrow?" } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/chat -ContentType "application/json" -Body $chat

# Delete the task
Invoke-RestMethod -Method Delete -Uri http://localhost:8080/api/tasks/$id
```

Note: for error responses (400, 404, 409) `Invoke-RestMethod` throws an exception. To see the JSON error body in
PowerShell 7 add `-SkipHttpErrorCheck`, or use the curl examples below.

### Command Prompt (curl)

The create request returns the new task's `id`. The examples below use id `1` (the first task in an empty
database); replace `1` with the id you got back.

```bat
curl http://localhost:8080/api/health

curl -X POST http://localhost:8080/api/tasks -H "Content-Type: application/json" -d "{\"title\":\"Team meeting\",\"date\":\"2026-10-20\",\"startTime\":\"10:00\",\"endTime\":\"11:00\",\"priority\":\"HIGH\"}"

curl http://localhost:8080/api/tasks
curl http://localhost:8080/api/tasks/today
curl http://localhost:8080/api/tasks/1

curl -X PUT http://localhost:8080/api/tasks/1 -H "Content-Type: application/json" -d "{\"title\":\"Team meeting (moved)\",\"description\":\"Weekly sync\",\"date\":\"2026-10-20\",\"startTime\":\"14:00\",\"endTime\":\"15:00\",\"priority\":\"HIGH\"}"

curl -X PATCH http://localhost:8080/api/tasks/1/status -H "Content-Type: application/json" -d "{\"status\":\"COMPLETED\"}"

curl "http://localhost:8080/api/schedule/free-slots?date=2026-10-20&durationMinutes=60"
curl http://localhost:8080/api/briefing

curl -X POST http://localhost:8080/api/chat -H "Content-Type: application/json" -d "{\"message\":\"What are my tasks today?\"}"

curl -X DELETE http://localhost:8080/api/tasks/1
```

In PowerShell, type `curl.exe` instead of `curl` (in PowerShell `curl` is an alias for `Invoke-WebRequest`).

`PUT` replaces the whole task: any field you leave out is cleared (`description`) or reset to its default
(`priority` becomes `MEDIUM`, `status` becomes `PENDING`). Use `PATCH /api/tasks/{id}/status` to change only the status.

### Example responses

Task:

```json
{
  "id": 1,
  "title": "Team meeting",
  "description": "Weekly sync",
  "date": "2026-10-20",
  "startTime": "10:00",
  "endTime": "11:00",
  "priority": "HIGH",
  "status": "PENDING",
  "createdAt": "2026-10-14T13:20:00.123",
  "updatedAt": "2026-10-14T13:20:00.123"
}
```

Validation error (400):

```json
{
  "timestamp": "2026-10-14T13:21:00.456",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/tasks",
  "details": { "fieldErrors": { "title": "Title is required", "date": "Date is required (format: yyyy-MM-dd)" } }
}
```

Schedule conflict (409):

```json
{
  "status": 409,
  "error": "Conflict",
  "message": "This time overlaps 1 existing task(s). Pick a suggested slot or send allowConflict=true to save anyway.",
  "path": "/api/tasks",
  "details": {
    "conflicts": [ { "id": 1, "title": "Team meeting", "startTime": "10:00", "endTime": "11:00", "...": "..." } ],
    "suggestedSlots": [
      { "startTime": "08:30", "endTime": "09:30", "durationMinutes": 60 },
      { "startTime": "09:00", "endTime": "10:00", "durationMinutes": 60 },
      { "startTime": "11:00", "endTime": "12:00", "durationMinutes": 60 }
    ]
  }
}
```

Chat:

```json
{
  "reply": "Done! I created 'Dentist' tomorrow from 15:00 to 16:00 (HIGH priority).",
  "intent": "CREATE_TASK",
  "source": "rule-based",
  "tasks": [ { "id": 2, "title": "Dentist", "...": "..." } ],
  "createdTask": { "id": 2, "title": "Dentist", "...": "..." },
  "suggestedSlots": [],
  "timestamp": "2026-10-14T13:22:00.789"
}
```

## What the chat understands

The chat is a simple rule-based parser, not an AI. It recognises these kinds of messages:

| You type                                                       | What happens                                  | `intent`                   |
|----------------------------------------------------------------|-----------------------------------------------|----------------------------|
| `What are my tasks today?`, `Show tasks for 2026-10-20`, `List all tasks` | Lists tasks                       | `LIST_TASKS`               |
| `Add Team meeting tomorrow from 10:00 to 11:00 high priority`  | Creates the task                              | `CREATE_TASK`              |
| `Remind me to call mom today at 6pm for 30 minutes`            | Creates the task (18:00-18:30)                | `CREATE_TASK`              |
| `Add Team meeting` (no date/time)                              | Asks for the missing details, creates nothing | `CREATE_TASK_NEEDS_DETAILS`|
| A new task that overlaps another                               | Does not create it, lists conflicts and free alternatives | `SCHEDULE_CONFLICT` |
| `When am I free tomorrow?`, `Am I free on friday for 2 hours?` | Lists free slots                              | `FREE_SLOTS`               |
| `Give me my briefing`, `Summary for tomorrow`                  | Daily briefing                                | `BRIEFING`                 |
| `hello`, `help`                                                | Help text                                     | `HELP`                     |

Rules for creating tasks from chat:

- Start the message with `add`, `create`, `schedule`, `book`, `set up`, `new task` or `remind me to`.
- A **title**, a **date** and a **start time** are required. Otherwise the assistant asks for what is missing.
- Dates: `today`, `tonight`, `tomorrow`, `day after tomorrow`, weekday names (`friday`, `next monday`) or `2026-10-20`.
- Times: `3pm`, `9:15am`, `14:00`, `noon`, ranges like `from 10:00 to 11:00`, `3-4pm`, `10:00-11:30`.
  A number without `am`/`pm` or `:` is read as a 24-hour time only after `at`/`from` (e.g. `at 9` = 09:00).
- If there is no end time, use `for 30 minutes` / `for 2 hours`; otherwise 60 minutes is assumed.
- Priority: `high priority`, `low priority`, `urgent`, `priority: high`. Default is `MEDIUM`.
- Put the title in double quotes if it contains words that look like dates or times: `add "Read chapter 5" tomorrow at 20:00`.

## Running the tests

```powershell
mvn clean test
```

There are three test classes:

- `ApiIntegrationTest` – calls every endpoint through Spring MockMvc (CRUD, validation, errors, conflicts,
  free slots, today, briefing, chat, CORS).
- `ScheduleServiceTest` – conflict detection and free-slot logic against an in-memory database.
- `ChatMessageParserTest` – how chat messages are understood.

The tests use an in-memory database and a fixed date (2026-10-14 09:00), so they never change your real data
and give the same result on any day.

On Java 21+ the test run may print warnings such as `A Java agent has been loaded dynamically` (from Mockito,
which is part of Spring Boot's test starter), `Sharing is only supported for boot loader classes` or, with some
Maven versions, `sun.misc.Unsafe ... has been called`. They are harmless and do not affect the results.

## Not included yet

Google Calendar, Google Meet, email delivery, external AI providers and voice recognition are not part of this
version. There is no authentication, so only run this backend on your own computer.
