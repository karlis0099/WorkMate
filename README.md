# WorkMate — Employee Wellbeing Platform

A Java desktop application that runs silently in the system tray and serves a modern web UI at `http://localhost:8765`. Employees and managers access it through any browser on the same network.

---

## Screenshots

### Employee Portal
| Directory | Weekly Check-in | My Profile |
|-----------|----------------|------------|
| ![Directory](docs/directory.png) | ![Check-in](docs/checkin.png) | ![Profile](docs/profile.png) |

### Manager Portal
| Dashboard | Team Directory | Survey Data |
|-----------|---------------|-------------|
| ![Dashboard](docs/dashboard.png) | ![Team Directory](docs/team-directory.png) | ![Survey Data](docs/survey-data.png) |

---

## Features

### Employee Portal
- **Contact Directory** — searchable, filterable card grid of all colleagues: name, position, department, email, phone
- **Weekly Check-in** — 7 psychological wellbeing sliders + open-text reflection questions (configurable days)
- **Real-time wellbeing score** — composite score updates live as sliders are moved
- **My Profile** — personal contact card with HR contact button and PIN change
- **Secure login** — select name + 4-digit PIN (default `1234`, changeable by employee or manager)

### Manager Portal
- **Dashboard** — 4 key metrics, 5-dimension weekly bar chart (vanilla Canvas), burnout risk alerts
- **Team Directory** — full employee list with Add Employee and PIN Reset per employee
- **Survey Data** — grouped by week, heat-map coloured cells, team averages, CSV export

### Platform
- **SQLite persistence** — all data survives restarts (no data loss)
- **Auth** — SHA-256+salt for both manager password and employee PINs; UUID session tokens (8h expiry)
- **Burnout webhook** — Slack/Teams-compatible POST on HIGH-risk submission
- **System tray** — app lives only in the menu/taskbar; tray click opens the browser
- **Configurable** — `workmate.properties` controls port, check-in window, webhook URL, and more

---

## Wellbeing Dimensions Tracked

| Dimension | Scale | Why it matters |
|-----------|-------|----------------|
| Mood | 1–10 | Core emotional state indicator |
| Energy | 1–10 | Physical/mental fatigue signal |
| Motivation | 1–10 | Engagement & discretionary effort |
| Stress | 1–10 (inverted) | Primary burnout predictor |
| Work-Life Balance | 1–10 | Sustainable performance |
| Team Connection | 1–10 | Social belonging & psychological safety |
| Accomplishment | 1–10 | Sense of purpose & progress |

Plus: workload feel, team communication quality, open reflection (what went well / challenges / support needed).

**Composite score** = (mood + energy + motivation + wlb + teamConnection + accomplishment + (11−stress)) / 7

---

## Algorithms

| Algorithm | Location | Complexity |
|-----------|----------|------------|
| Merge sort via Collections.sort() | `EmployeeDirectory.sortByName()` | O(n log n) |
| Linear search | `EmployeeDirectory.findByName()` | O(n) |
| Binary search | `EmployeeDirectory.binarySearch()` | O(log n) |
| Stack push/pop | `SurveyManager` activity log | O(1) |
| Queue enqueue/dequeue | `SurveyManager` reminder queue | O(1) |

---

## Project Structure

```
WorkMate/
├── src/com/workmate/
│   ├── WorkMateApp.java              — entry point, tray, DB init, graceful shutdown
│   ├── config/
│   │   └── AppConfig.java            — reads workmate.properties, typed accessors
│   ├── model/
│   │   ├── Employee.java             — base entity (id, name, position, dept, email, phone)
│   │   ├── Manager.java              — extends Employee with teamSize
│   │   ├── SurveyResponse.java       — 7-dim check-in, ISO week key, risk + composite score
│   │   ├── EmployeeDirectory.java    — in-memory demo registry (sort/search)
│   │   └── SurveyManager.java        — in-memory aggregation (Stack log, Queue reminders)
│   ├── service/
│   │   ├── WebServer.java            — routing only, no HTML — serves static from resources/
│   │   ├── DataStore.java            — SQLite CRUD for employees + survey_responses
│   │   ├── AuthService.java          — SHA-256+salt hash, UUID sessions with 8h expiry
│   │   └── NotificationService.java  — tray bubbles + async Slack/Teams webhook
│   └── util/
│       └── LogoDrawer.java           — Java2D programmatic tray icon
├── resources/
│   └── static/
│       ├── css/
│       │   └── app.css               — Clinical Warmth dark design system
│       ├── js/
│       │   └── app.js                — vanilla JS SPA (no frameworks)
│       └── index.html                — app shell (login + employee + manager views)
├── lib/
│   └── sqlite-jdbc.jar               — SQLite JDBC driver (download — see Setup)
├── workmate.properties               — runtime configuration
├── build.sh                          — compile + run script
└── README.md
```

---

## Setup & Quick Start

### 1. Download the SQLite JDBC driver

```bash
# Create the lib directory
mkdir -p lib

# Download the JAR (version 3.45+ recommended)
curl -L -o lib/sqlite-jdbc.jar \
  https://github.com/xerial/sqlite-jdbc/releases/download/3.45.1.0/sqlite-jdbc-3.45.1.0.jar
```

Or download manually from: https://github.com/xerial/sqlite-jdbc/releases

### 2. Build & Run

```bash
./build.sh
```

This compiles all Java sources, copies static resources onto the classpath, and launches the application. A browser tab opens automatically.

**Manual steps (without the script):**

```bash
# Compile
mkdir -p out
cp -r resources/static out/static
find src -name "*.java" > /tmp/sources.txt
javac -cp lib/sqlite-jdbc.jar -d out @/tmp/sources.txt

# Run
java -cp out:lib/sqlite-jdbc.jar com.workmate.WorkMateApp
```

On Windows, replace `:` with `;` in `-cp`.

### 3. First-run setup

On first launch, WorkMate shows a **Setup** screen to create the manager password. After that:

- **Employee login**: select your name → enter 4-digit PIN (default `1234`) → Continue
- **Manager login**: click "Manager Login" → enter the password you set during Setup

Each employee can change their own PIN from **My Profile → Change my PIN**.
A manager can reset any employee's PIN from **Team Directory → employee card → Reset PIN**.

---

## Configuration (`workmate.properties`)

| Key | Default | Description |
|-----|---------|-------------|
| `server.port` | `8765` | HTTP port |
| `server.bind` | `0.0.0.0` | Bind address (all interfaces) |
| `checkin.days` | `4,5` | ISO weekdays check-in is open (4=Thu, 5=Fri) |
| `checkin.hour.start` | `0` | Hour (0-23) check-in window opens |
| `checkin.hour.end` | `23` | Hour (0-23) check-in window closes |
| `admin.session.hours` | `8` | Session token validity |
| `notification.webhook.url` | _(empty)_ | Slack/Teams webhook URL |
| `notification.webhook.enabled` | `false` | Enable webhook notifications |
| `burnout.alert.threshold` | `4.5` | Composite score below which HIGH risk is flagged |

---

## REST API

All endpoints return JSON. Manager-only endpoints require `Authorization: Bearer <token>`.

### Auth

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/auth/login` | — | Login (employee or manager) |
| `POST` | `/api/auth/logout` | any | Invalidate session |
| `GET`  | `/api/auth/me` | any | Current session identity |
| `POST` | `/api/auth/setup` | — | First-run password setup |

**Login body (employee):**
```json
{ "type": "employee", "employeeId": 1, "pin": "1234" }
```
**Login body (manager):**
```json
{ "type": "manager", "password": "yourpassword" }
```
**Response:**
```json
{ "token": "uuid-string", "role": "MANAGER", "name": "Manager" }
```

### Employees

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET`  | `/api/employees` | any | List all employees (sorted by name) |
| `POST` | `/api/employees` | manager | Create employee |
| `GET`  | `/api/employees/{id}` | any | Get one employee |
| `PUT`  | `/api/employees/{id}` | manager | Update employee |

### Survey

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET`  | `/api/survey/responses` | manager | All responses (`?week=2026-W18&employee_id=1`) |
| `POST` | `/api/survey/responses` | any | Submit check-in |
| `GET`  | `/api/survey/summary` | manager | Aggregated dashboard metrics |
| `GET`  | `/api/survey/export/csv` | manager | CSV download |

### Config

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET`  | `/api/config` | — | Public config (port, check-in days, etc.) |

**Error format:**
```json
{ "error": true, "code": "UNAUTHORIZED", "message": "Invalid session" }
```

---

## Deployment on Windows Server

### Quick (foreground process)
```bat
java -cp out;lib\sqlite-jdbc.jar com.workmate.WorkMateApp
```

### As a Windows Service (NSSM)
1. Download NSSM from https://nssm.cc/download
2. Open Command Prompt as Administrator:
```bat
nssm install WorkMate "C:\Program Files\Java\jdk-25\bin\java.exe"
```
3. Set Arguments: `-cp C:\WorkMate\out;C:\WorkMate\lib\sqlite-jdbc.jar com.workmate.WorkMateApp`
4. Set Startup directory: `C:\WorkMate`
5. `nssm start WorkMate`

### Firewall rule
```bat
netsh advfirewall firewall add rule name="WorkMate" dir=in action=allow protocol=TCP localport=8765
```

---

## Requirements

- Java 11 or higher (tested on Java 25 Temurin)
- `lib/sqlite-jdbc.jar` (see Setup above)
- Any modern browser (Chrome, Firefox, Safari, Edge)
