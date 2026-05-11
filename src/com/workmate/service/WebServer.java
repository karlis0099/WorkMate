package com.workmate.service;

import com.sun.net.httpserver.*;
import com.workmate.config.AppConfig;
import com.workmate.model.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Embedded HTTP server.
 *
 * <p>All API endpoints return JSON; static assets (HTML/CSS/JS) are served
 * from the {@code resources/static/} directory on the classpath.
 * The embedded HTML that previously lived in this file has been removed.</p>
 */
public class WebServer {

    private static final Logger log = Logger.getLogger(WebServer.class.getName());

    private final int         port;
    private final DataStore   dataStore;
    private final AuthService authService;
    private final NotificationService notifService;

    // ── constructor ───────────────────────────────────────────────────────────

    /**
     * Creates a new WebServer.
     *
     * @param port         TCP port to listen on
     * @param dataStore    persistence layer
     * @param authService  authentication service
     * @param notifService notification service
     */
    public WebServer(int port, DataStore dataStore,
                     AuthService authService, NotificationService notifService) {
        this.port         = port;
        this.dataStore    = dataStore;
        this.authService  = authService;
        this.notifService = notifService;
    }

    // ── lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Binds the server socket and registers all request contexts.
     *
     * @throws RuntimeException if the socket cannot be bound
     */
    public void start() {
        try {
            HttpServer server = HttpServer.create(
                new InetSocketAddress(AppConfig.get().getBindAddress(), port), 0);

            // static asset serving
            server.createContext("/",                     this::handleStatic);

            // auth endpoints (no session required)
            server.createContext("/api/auth/login",       this::handleAuthLogin);
            server.createContext("/api/auth/logout",      this::handleAuthLogout);
            server.createContext("/api/auth/me",          this::handleAuthMe);
            server.createContext("/api/auth/setup",       this::handleAuthSetup);
            server.createContext("/api/auth/pin",         this::handleAuthPin);

            // public config
            server.createContext("/api/config",           this::handleConfig);

            // employee endpoints
            server.createContext("/api/employees",        this::handleEmployees);

            // survey endpoints
            server.createContext("/api/survey/responses", this::handleSurveyResponses);
            server.createContext("/api/survey/summary",   this::handleSurveySummary);
            server.createContext("/api/survey/export/csv",this::handleSurveyExportCsv);

            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            log.info("WorkMate running at http://localhost:" + port);
        } catch (IOException e) {
            throw new RuntimeException("Failed to start web server: " + e.getMessage(), e);
        }
    }

    // ── static file handler ───────────────────────────────────────────────────

    /**
     * Serves static files from {@code resources/static/}.
     * Requests to {@code /} and unknown paths redirect to {@code /index.html}.
     *
     * @param ex the HTTP exchange
     */
    private void handleStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/") || path.isEmpty()) path = "/index.html";

        // Prevent path-traversal attacks
        if (path.contains("..")) { ex.sendResponseHeaders(400, -1); return; }

        InputStream is = getClass().getResourceAsStream("/static" + path);
        if (is == null) {
            // For SPA navigation fall back to index.html for non-asset paths
            if (!path.contains(".")) {
                is = getClass().getResourceAsStream("/static/index.html");
            }
            if (is == null) { ex.sendResponseHeaders(404, -1); return; }
        }

        byte[] bytes = is.readAllBytes();
        is.close();
        ex.getResponseHeaders().set("Content-Type", mimeType(path));
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    // ── auth handlers ─────────────────────────────────────────────────────────

    /**
     * {@code POST /api/auth/login}
     * Body: {@code {"type":"employee","employeeId":1}} or {@code {"type":"manager","password":"..."}}
     * Response: {@code {"token":"...","role":"...","name":"..."}}
     */
    private void handleAuthLogin(HttpExchange ex) throws IOException {
        if (!requireMethod(ex, "POST")) return;
        String body = readBody(ex);
        String type = jsonGet(body, "type");

        if ("manager".equalsIgnoreCase(type)) {
            String pwd   = jsonGet(body, "password");
            String token = authService.loginManager(pwd);
            if (token == null) { sendError(ex, 401, "UNAUTHORIZED", "Invalid password"); return; }
            sendJson(ex, "{\"token\":\"" + token + "\",\"role\":\"MANAGER\",\"name\":\"Manager\"}");

        } else {
            int id = parseInt(jsonGet(body, "employeeId"));
            Employee emp = dataStore.findById(id);
            if (emp == null) { sendError(ex, 404, "NOT_FOUND", "Employee not found"); return; }
            String pin = jsonGet(body, "pin");
            if (!authService.verifyEmployeePin(id, pin)) {
                sendError(ex, 401, "WRONG_PIN", "Wrong PIN"); return;
            }
            String token = authService.loginEmployee(emp);
            sendJson(ex, "{\"token\":\"" + token + "\",\"role\":\"EMPLOYEE\","
                         + "\"name\":\"" + esc(emp.getName()) + "\","
                         + "\"employeeId\":" + emp.getId() + "}");
        }
    }

    /**
     * {@code POST /api/auth/logout}
     * Header: Authorization: Bearer &lt;token&gt;
     */
    private void handleAuthLogout(HttpExchange ex) throws IOException {
        String token = AuthService.extractBearer(ex.getRequestHeaders().getFirst("Authorization"));
        authService.logout(token);
        sendJson(ex, "{\"ok\":true}");
    }

    /**
     * {@code GET /api/auth/me}
     * Returns the current session's identity.
     */
    private void handleAuthMe(HttpExchange ex) throws IOException {
        AuthService.SessionInfo s = resolveSession(ex);
        if (s == null) return;
        sendJson(ex, "{\"role\":\"" + s.role + "\",\"name\":\"" + esc(s.name) + "\","
                     + "\"employeeId\":" + s.employeeId + "}");
    }

    /**
     * {@code POST /api/auth/setup}
     * First-run password setup. Rejected if a password is already set.
     * Body: {@code {"password":"..."}}
     */
    private void handleAuthSetup(HttpExchange ex) throws IOException {
        if (!requireMethod(ex, "POST")) return;
        if (authService.isPasswordSet()) {
            sendError(ex, 409, "CONFLICT", "Password already configured");
            return;
        }
        String body = readBody(ex);
        String pwd  = jsonGet(body, "password");
        if (pwd.isBlank()) { sendError(ex, 400, "BAD_REQUEST", "Password required"); return; }
        authService.setManagerPassword(pwd);
        sendJson(ex, "{\"ok\":true}");
    }

    /**
     * {@code POST /api/auth/pin}
     * Change the calling employee's PIN. Requires valid session.
     * Manager can reset any employee's PIN by supplying {@code employeeId}.
     * Body: {@code {"newPin":"1234"}} or {@code {"employeeId":2,"newPin":"5678"}}
     */
    private void handleAuthPin(HttpExchange ex) throws IOException {
        if (!requireMethod(ex, "POST")) return;
        AuthService.SessionInfo s = resolveSession(ex);
        if (s == null) return;
        String body   = readBody(ex);
        String newPin = jsonGet(body, "newPin");
        if (newPin.isBlank()) { sendError(ex, 400, "BAD_REQUEST", "newPin required"); return; }

        int targetId;
        if (authService.isManager(s)) {
            // manager can reset any employee's PIN
            int reqId = parseInt(jsonGet(body, "employeeId"));
            targetId = reqId > 0 ? reqId : -1;
            if (targetId < 0) { sendError(ex, 400, "BAD_REQUEST", "employeeId required"); return; }
        } else {
            targetId = s.employeeId;
        }
        authService.setEmployeePin(targetId, newPin);
        sendJson(ex, "{\"ok\":true}");
    }

    // ── config handler ────────────────────────────────────────────────────────

    /**
     * {@code GET /api/config}
     * Returns public configuration values consumed by the frontend.
     */
    private void handleConfig(HttpExchange ex) throws IOException {
        AppConfig cfg = AppConfig.get();
        String days = cfg.getCheckinDays().toString()
                         .replace("[", "").replace("]", "").replace(" ", "");
        sendJson(ex, "{\"port\":" + cfg.getPort()
                     + ",\"checkinDays\":\"" + days + "\""
                     + ",\"checkinHourStart\":" + cfg.getCheckinHourStart()
                     + ",\"checkinHourEnd\":" + cfg.getCheckinHourEnd()
                     + ",\"passwordSet\":" + authService.isPasswordSet()
                     + ",\"currentWeek\":\"" + SurveyResponse.currentIsoWeek() + "\"}");
    }

    // ── employee handlers ─────────────────────────────────────────────────────

    /**
     * Dispatcher for {@code /api/employees} and {@code /api/employees/{id}}.
     * Routes: GET /api/employees, POST /api/employees,
     *         GET /api/employees/{id}, PUT /api/employees/{id}
     */
    private void handleEmployees(HttpExchange ex) throws IOException {
        String path   = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        // extract optional {id} segment
        String[] parts = path.split("/");
        boolean hasId  = parts.length >= 4 && !parts[3].isEmpty();
        int id = hasId ? parseInt(parts[3]) : -1;

        if ("GET".equals(method) && !hasId) {
            // GET /api/employees — public so the login page can populate the name list
            List<Employee> all = dataStore.getAllEmployees();
            // Bubble sort replaced with O(n log n) merge sort via Collections.sort()
            all.sort(Comparator.comparing(Employee::getName));
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < all.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(empJson(all.get(i)));
            }
            sendJson(ex, sb.append("]").toString());

        } else if ("POST".equals(method) && !hasId) {
            // POST /api/employees  (manager only)
            AuthService.SessionInfo s = resolveSession(ex);
            if (s == null) return;
            if (!authService.isManager(s)) { sendError(ex, 403, "FORBIDDEN", "Managers only"); return; }
            String body = readBody(ex);
            Employee e = new Employee(0,
                jsonGet(body, "name"), jsonGet(body, "position"),
                jsonGet(body, "department"), jsonGet(body, "email"), jsonGet(body, "phone"));
            int newId = dataStore.insertEmployee(e);
            Employee saved = dataStore.findById(newId);
            sendJson(ex, empJson(saved));

        } else if ("GET".equals(method) && hasId) {
            // GET /api/employees/{id}
            AuthService.SessionInfo s = resolveSession(ex);
            if (s == null) return;
            Employee e = dataStore.findById(id);
            if (e == null) { sendError(ex, 404, "NOT_FOUND", "Employee not found"); return; }
            sendJson(ex, empJson(e));

        } else if ("PUT".equals(method) && hasId) {
            // PUT /api/employees/{id}  (manager only)
            AuthService.SessionInfo s = resolveSession(ex);
            if (s == null) return;
            if (!authService.isManager(s)) { sendError(ex, 403, "FORBIDDEN", "Managers only"); return; }
            Employee existing = dataStore.findById(id);
            if (existing == null) { sendError(ex, 404, "NOT_FOUND", "Employee not found"); return; }
            String body = readBody(ex);
            String name  = jsonGet(body, "name");
            String pos   = jsonGet(body, "position");
            String dept  = jsonGet(body, "department");
            String email = jsonGet(body, "email");
            String phone = jsonGet(body, "phone");
            if (!name.isBlank())  existing.setName(name);
            if (!pos.isBlank())   existing.setPosition(pos);
            if (!dept.isBlank())  existing.setDepartment(dept);
            if (!email.isBlank()) existing.setEmail(email);
            if (!phone.isBlank()) existing.setPhone(phone);
            dataStore.updateEmployee(existing);
            sendJson(ex, empJson(existing));

        } else {
            ex.sendResponseHeaders(405, -1);
        }
    }

    // ── survey handlers ───────────────────────────────────────────────────────

    /**
     * Dispatcher for {@code GET /api/survey/responses} and {@code POST /api/survey/responses}.
     * Query params for GET: {@code week} (ISO string), {@code employee_id}.
     */
    private void handleSurveyResponses(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();

        if ("GET".equals(method)) {
            AuthService.SessionInfo s = resolveSession(ex);
            if (s == null) return;
            if (!authService.isManager(s)) { sendError(ex, 403, "FORBIDDEN", "Managers only"); return; }

            Map<String, String> params = parseQuery(ex.getRequestURI().getQuery());
            String weekStart  = params.getOrDefault("week", "");
            int    empId      = parseInt(params.getOrDefault("employee_id", "0"));
            List<SurveyResponse> responses = dataStore.getResponses(
                weekStart.isBlank() ? null : weekStart, empId > 0 ? empId : -1);

            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (SurveyResponse r : responses) {
                if (!first) sb.append(",");
                first = false;
                Employee emp = dataStore.findById(r.getEmployeeId());
                String name  = emp != null ? emp.getName()     : "Unknown";
                String dept  = emp != null ? emp.getDepartment(): "";
                sb.append(responseJson(r, name, dept));
            }
            sendJson(ex, sb.append("]").toString());

        } else if ("POST".equals(method)) {
            AuthService.SessionInfo s = resolveSession(ex);
            if (s == null) return;

            String body = readBody(ex);
            int empId = s.employeeId > 0 ? s.employeeId : parseInt(jsonGet(body, "employeeId"));
            SurveyResponse r = new SurveyResponse(
                empId,
                jsonGet(body, "weekStart").isBlank()
                    ? SurveyResponse.currentIsoWeek()
                    : jsonGet(body, "weekStart"),
                parseInt(jsonGet(body, "mood")),
                parseInt(jsonGet(body, "motivation")),
                parseInt(jsonGet(body, "stress")),
                parseInt(jsonGet(body, "energy")),
                parseInt(jsonGet(body, "workLifeBalance")),
                parseInt(jsonGet(body, "teamConnection")),
                parseInt(jsonGet(body, "accomplishment")),
                jsonGet(body, "issues"),
                jsonGet(body, "wentWell"),
                jsonGet(body, "challenges"),
                jsonGet(body, "support"),
                jsonGet(body, "workloadFeel"),
                jsonGet(body, "teamCommunication"),
                null
            );
            dataStore.insertResponse(r);

            // fire async burnout alert if HIGH risk
            if ("HIGH".equals(r.getRiskLevel())) {
                Employee emp = dataStore.findById(empId);
                String empName = emp != null ? emp.getName() : "Employee #" + empId;
                notifService.alertHighRisk(empName, r.getWeekStart(), r.getCompositeScore());
            }
            sendJson(ex, "{\"ok\":true,\"risk\":\"" + r.getRiskLevel() + "\"}");

        } else {
            ex.sendResponseHeaders(405, -1);
        }
    }

    /**
     * {@code GET /api/survey/summary}
     * Returns aggregated metrics for the manager dashboard.
     * Always reads all responses from the DB for the most recent week.
     */
    private void handleSurveySummary(HttpExchange ex) throws IOException {
        if (!requireMethod(ex, "GET")) return;
        AuthService.SessionInfo s = resolveSession(ex);
        if (s == null) return;
        if (!authService.isManager(s)) { sendError(ex, 403, "FORBIDDEN", "Managers only"); return; }

        List<SurveyResponse> all = dataStore.getAllResponses();
        int totalEmployees = dataStore.countEmployees();

        // Find distinct weeks, sorted
        List<String> weeks = all.stream()
            .map(SurveyResponse::getWeekStart)
            .distinct()
            .sorted()
            .collect(java.util.stream.Collectors.toList());

        String latestWeek = weeks.isEmpty() ? SurveyResponse.currentIsoWeek()
                                             : weeks.get(weeks.size() - 1);
        List<SurveyResponse> latest = filterByWeek(all, latestWeek);
        long responseCount = latest.size();
        double responseRate = totalEmployees > 0 ? (responseCount * 100.0 / totalEmployees) : 0;

        StringBuilder sb = new StringBuilder("{");
        sb.append("\"avgMood\":")            .append(f(avg(latest, SurveyResponse::getMood)))           .append(",");
        sb.append("\"avgMotivation\":")      .append(f(avg(latest, SurveyResponse::getMotivation)))     .append(",");
        sb.append("\"avgStress\":")          .append(f(avg(latest, SurveyResponse::getStress)))         .append(",");
        sb.append("\"avgEnergy\":")          .append(f(avg(latest, SurveyResponse::getEnergy)))         .append(",");
        sb.append("\"avgWorkLifeBalance\":") .append(f(avg(latest, SurveyResponse::getWorkLifeBalance))).append(",");
        sb.append("\"avgTeamConnection\":")  .append(f(avg(latest, SurveyResponse::getTeamConnection))) .append(",");
        sb.append("\"avgAccomplishment\":")  .append(f(avg(latest, SurveyResponse::getAccomplishment))) .append(",");
        sb.append("\"responseRate\":")       .append(f(responseRate))                                   .append(",");
        sb.append("\"latestWeek\":\"")       .append(esc(latestWeek))                                   .append("\",");

        // weekly stats for chart
        sb.append("\"weeklyStats\":[");
        for (int i = 0; i < weeks.size(); i++) {
            if (i > 0) sb.append(",");
            String wk = weeks.get(i);
            List<SurveyResponse> wr = filterByWeek(all, wk);
            sb.append("{\"week\":\"").append(esc(wk)).append("\"")
              .append(",\"mood\":").append(f(avg(wr, SurveyResponse::getMood)))
              .append(",\"motivation\":").append(f(avg(wr, SurveyResponse::getMotivation)))
              .append(",\"stress\":").append(f(avg(wr, SurveyResponse::getStress)))
              .append(",\"energy\":").append(f(avg(wr, SurveyResponse::getEnergy)))
              .append(",\"wlb\":").append(f(avg(wr, SurveyResponse::getWorkLifeBalance)))
              .append(",\"team\":").append(f(avg(wr, SurveyResponse::getTeamConnection)))
              .append(",\"accomplish\":").append(f(avg(wr, SurveyResponse::getAccomplishment)))
              .append("}");
        }
        sb.append("],");

        // high-risk employees (latest response per employee)
        Map<Integer, SurveyResponse> latestPerEmp = new HashMap<>();
        for (SurveyResponse r : all) {
            SurveyResponse prev = latestPerEmp.get(r.getEmployeeId());
            if (prev == null || r.getWeekStart().compareTo(prev.getWeekStart()) > 0)
                latestPerEmp.put(r.getEmployeeId(), r);
        }
        sb.append("\"highRisk\":[");
        boolean firstHR = true;
        for (Map.Entry<Integer, SurveyResponse> entry : latestPerEmp.entrySet()) {
            if ("HIGH".equals(entry.getValue().getRiskLevel())) {
                if (!firstHR) sb.append(",");
                firstHR = false;
                Employee emp = dataStore.findById(entry.getKey());
                String name = emp != null ? emp.getName()     : "Unknown";
                String dept = emp != null ? emp.getDepartment(): "";
                sb.append("{\"id\":").append(entry.getKey())
                  .append(",\"name\":\"").append(esc(name)).append("\"")
                  .append(",\"department\":\"").append(esc(dept)).append("\"")
                  .append(",\"score\":").append(f(entry.getValue().getCompositeScore()))
                  .append("}");
            }
        }
        sb.append("]}");
        sendJson(ex, sb.toString());
    }

    /**
     * {@code GET /api/survey/export/csv}
     * Returns all survey responses as a downloadable CSV file.
     */
    private void handleSurveyExportCsv(HttpExchange ex) throws IOException {
        if (!requireMethod(ex, "GET")) return;
        AuthService.SessionInfo s = resolveSession(ex);
        if (s == null) return;
        if (!authService.isManager(s)) { sendError(ex, 403, "FORBIDDEN", "Managers only"); return; }

        List<SurveyResponse> all = dataStore.getAllResponses();
        StringBuilder csv = new StringBuilder();
        csv.append("Employee,Department,Week,Mood,Energy,Motivation,Stress,WLB,Team,Accomplishment,Workload,Risk,Went Well,Challenges,Support\n");
        for (SurveyResponse r : all) {
            Employee emp = dataStore.findById(r.getEmployeeId());
            String name = emp != null ? emp.getName()     : "Unknown";
            String dept = emp != null ? emp.getDepartment(): "";
            csv.append(csvEsc(name)).append(",")
               .append(csvEsc(dept)).append(",")
               .append(csvEsc(r.getWeekStart())).append(",")
               .append(r.getMood()).append(",")
               .append(r.getEnergy()).append(",")
               .append(r.getMotivation()).append(",")
               .append(r.getStress()).append(",")
               .append(r.getWorkLifeBalance()).append(",")
               .append(r.getTeamConnection()).append(",")
               .append(r.getAccomplishment()).append(",")
               .append(csvEsc(r.getWorkloadFeel())).append(",")
               .append(csvEsc(r.getRiskLevel())).append(",")
               .append(csvEsc(r.getWentWell())).append(",")
               .append(csvEsc(r.getChallenges())).append(",")
               .append(csvEsc(r.getSupport())).append("\n");
        }
        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        String filename = "workmate_export_" + LocalDate.now() + ".csv";
        ex.getResponseHeaders().set("Content-Type", "text/csv; charset=utf-8");
        ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Resolves the session from the Authorization header and sends 401 if absent/invalid.
     *
     * @param ex the HTTP exchange
     * @return the SessionInfo, or null (response already sent)
     */
    private AuthService.SessionInfo resolveSession(HttpExchange ex) throws IOException {
        String token = AuthService.extractBearer(ex.getRequestHeaders().getFirst("Authorization"));
        AuthService.SessionInfo s = authService.getSession(token);
        if (s == null) { sendError(ex, 401, "UNAUTHORIZED", "Invalid session"); return null; }
        return s;
    }

    /** Sends a 405 if the method does not match. Returns false when mismatch. */
    private boolean requireMethod(HttpExchange ex, String method) throws IOException {
        if (!method.equals(ex.getRequestMethod())) {
            ex.sendResponseHeaders(405, -1);
            return false;
        }
        return true;
    }

    /** Sends a JSON response with status 200. */
    private void sendJson(HttpExchange ex, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    /** Sends a standard error JSON payload. */
    private void sendError(HttpExchange ex, int status, String code, String message) throws IOException {
        String json = "{\"error\":true,\"code\":\"" + code + "\",\"message\":\"" + esc(message) + "\"}";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private String empJson(Employee e) {
        if (e == null) return "null";
        boolean mgr = e instanceof Manager;
        return "{\"id\":" + e.getId()
            + ",\"name\":\"" + esc(e.getName()) + "\""
            + ",\"position\":\"" + esc(e.getPosition()) + "\""
            + ",\"department\":\"" + esc(e.getDepartment()) + "\""
            + ",\"email\":\"" + esc(e.getEmail()) + "\""
            + ",\"phone\":\"" + esc(e.getPhone()) + "\""
            + ",\"isManager\":" + mgr
            + (mgr ? ",\"teamSize\":" + ((Manager) e).getTeamSize() : "")
            + "}";
    }

    private String responseJson(SurveyResponse r, String name, String dept) {
        return "{\"employeeId\":" + r.getEmployeeId()
            + ",\"name\":\"" + esc(name) + "\""
            + ",\"department\":\"" + esc(dept) + "\""
            + ",\"weekStart\":\"" + esc(r.getWeekStart()) + "\""
            + ",\"mood\":" + r.getMood()
            + ",\"energy\":" + r.getEnergy()
            + ",\"motivation\":" + r.getMotivation()
            + ",\"stress\":" + r.getStress()
            + ",\"workLifeBalance\":" + r.getWorkLifeBalance()
            + ",\"teamConnection\":" + r.getTeamConnection()
            + ",\"accomplishment\":" + r.getAccomplishment()
            + ",\"workloadFeel\":\"" + esc(r.getWorkloadFeel()) + "\""
            + ",\"teamCommunication\":\"" + esc(r.getTeamCommunication()) + "\""
            + ",\"risk\":\"" + esc(r.getRiskLevel()) + "\""
            + ",\"compositeScore\":" + f(r.getCompositeScore())
            + ",\"wentWell\":\"" + esc(r.getWentWell()) + "\""
            + ",\"challenges\":\"" + esc(r.getChallenges()) + "\""
            + ",\"support\":\"" + esc(r.getSupport()) + "\""
            + ",\"issues\":\"" + esc(r.getIssues()) + "\""
            + ",\"submittedAt\":\"" + esc(r.getSubmittedAt()) + "\""
            + "}";
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }

    private String csvEsc(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n"))
            return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    private String f(double v) { return String.format("%.2f", v); }

    private int parseInt(String s) {
        if (s == null) return 0;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 0; }
    }

    /** Minimal JSON field extractor (handles string and number values). */
    private String jsonGet(String json, String key) {
        if (json == null) return "";
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return "";
        int start = idx + search.length();
        if (start >= json.length()) return "";
        char first = json.charAt(start);
        if (first == '"') {
            int end = start + 1;
            while (end < json.length() && json.charAt(end) != '"') {
                if (json.charAt(end) == '\\') end++;
                end++;
            }
            return json.substring(start + 1, end);
        } else {
            int end = start;
            while (end < json.length() && ",}]".indexOf(json.charAt(end)) < 0) end++;
            return json.substring(start, end).trim();
        }
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> map = new LinkedHashMap<>();
        if (query == null || query.isBlank()) return map;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) map.put(kv[0], kv[1]);
            else if (kv.length == 1) map.put(kv[0], "");
        }
        return map;
    }

    private String mimeType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".css"))  return "text/css; charset=utf-8";
        if (path.endsWith(".js"))   return "application/javascript; charset=utf-8";
        if (path.endsWith(".svg"))  return "image/svg+xml";
        if (path.endsWith(".png"))  return "image/png";
        if (path.endsWith(".ico"))  return "image/x-icon";
        if (path.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }

    private List<SurveyResponse> filterByWeek(List<SurveyResponse> list, String week) {
        List<SurveyResponse> result = new ArrayList<>();
        for (SurveyResponse r : list) if (week.equals(r.getWeekStart())) result.add(r);
        return result;
    }

    private double avg(List<SurveyResponse> list, java.util.function.ToIntFunction<SurveyResponse> fn) {
        if (list.isEmpty()) return 0;
        return list.stream().mapToInt(fn).average().orElse(0);
    }
}
