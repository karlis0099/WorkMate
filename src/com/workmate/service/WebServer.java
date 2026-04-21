package com.workmate.service;

import com.sun.net.httpserver.*;
import com.workmate.model.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.Executors;

public class WebServer {

    private final int port;
    private final EmployeeDirectory directory;
    private final SurveyManager surveyManager;

    public WebServer(int port, EmployeeDirectory dir, SurveyManager sm) {
        this.port = port;
        this.directory = dir;
        this.surveyManager = sm;
    }

    public void start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", this::handleRoot);
            server.createContext("/api/employees", this::handleEmployees);
            server.createContext("/api/employee", this::handleEmployee);
            server.createContext("/api/stats", this::handleStats);
            server.createContext("/api/survey", this::handleSurvey);
            server.createContext("/api/checkin", this::handleCheckin);
            server.createContext("/api/remind", this::handleRemind);
            server.createContext("/api/export", this::handleExport);
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            System.out.println("WorkMate running at http://localhost:" + port);
        } catch (IOException e) {
            throw new RuntimeException("Failed to start web server: " + e.getMessage(), e);
        }
    }

    // ── handlers ──────────────────────────────────────────────────────────────

    private void handleRoot(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(405, -1); return; }
        byte[] html = HTML.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, html.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(html); }
    }

    private void handleEmployees(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(405, -1); return; }
        StringBuilder sb = new StringBuilder("[");
        ArrayList<Employee> all = directory.getAllEmployees();
        for (int i = 0; i < all.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(empJson(all.get(i)));
        }
        sendJson(ex, sb.append("]").toString());
    }

    private void handleEmployee(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(405, -1); return; }
        String body = readBody(ex);
        Employee e = new Employee(directory.getNextId(),
            jsonGet(body,"name"), jsonGet(body,"position"),
            jsonGet(body,"department"), jsonGet(body,"email"), jsonGet(body,"phone"));
        directory.addEmployee(e);
        sendJson(ex, empJson(e));
    }

    private void handleStats(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(405, -1); return; }
        int latest = surveyManager.getLatestWeek();
        double rr = surveyManager.getResponseRate(directory.size(), latest);
        StringBuilder sb = new StringBuilder("{")
            .append("\"avgMood\":").append(f(surveyManager.getAverageMood())).append(",")
            .append("\"avgMotivation\":").append(f(surveyManager.getAverageMotivation())).append(",")
            .append("\"avgStress\":").append(f(surveyManager.getAverageStress())).append(",")
            .append("\"avgEnergy\":").append(f(surveyManager.getAverageEnergy())).append(",")
            .append("\"avgWorkLifeBalance\":").append(f(surveyManager.getAverageWorkLifeBalance())).append(",")
            .append("\"avgTeamConnection\":").append(f(surveyManager.getAverageTeamConnection())).append(",")
            .append("\"avgAccomplishment\":").append(f(surveyManager.getAverageAccomplishment())).append(",")
            .append("\"responseRate\":").append(f(rr)).append(",")
            .append("\"weeklyStats\":[");
        for (int w = 1; w <= 4; w++) {
            if (w > 1) sb.append(",");
            sb.append("{\"week\":").append(w)
              .append(",\"mood\":").append(f(surveyManager.getAverageMoodForWeek(w)))
              .append(",\"motivation\":").append(f(surveyManager.getAverageMotivationForWeek(w)))
              .append(",\"stress\":").append(f(surveyManager.getAverageStressForWeek(w)))
              .append(",\"energy\":").append(f(surveyManager.getAverageEnergyForWeek(w)))
              .append(",\"wlb\":").append(f(surveyManager.getAverageWorkLifeBalanceForWeek(w)))
              .append(",\"team\":").append(f(surveyManager.getAverageTeamConnectionForWeek(w)))
              .append(",\"accomplish\":").append(f(surveyManager.getAverageAccomplishmentForWeek(w)))
              .append(",\"dateRange\":\"").append(esc(weekRange(w))).append("\"}");
        }
        sb.append("],\"highRisk\":[");
        ArrayList<Integer> risks = surveyManager.getHighRiskEmployeeIds();
        for (int i = 0; i < risks.size(); i++) {
            if (i > 0) sb.append(",");
            Employee e = directory.findById(risks.get(i));
            if (e != null) sb.append("{\"name\":\"").append(esc(e.getName()))
                .append("\",\"department\":\"").append(esc(e.getDepartment())).append("\"}");
        }
        sb.append("]}");
        sendJson(ex, sb.toString());
    }

    private void handleSurvey(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(405, -1); return; }
        String q = ex.getRequestURI().getQuery();
        int wf = 0;
        if (q != null && q.startsWith("week=")) {
            try { wf = Integer.parseInt(q.substring(5)); } catch (NumberFormatException ignored) {}
        }
        final int wfinal = wf;
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (SurveyResponse r : surveyManager.getAllResponses()) {
            if (wfinal > 0 && r.getWeekNumber() != wfinal) continue;
            if (!first) sb.append(",");
            first = false;
            Employee emp = directory.findById(r.getEmployeeId());
            String name = emp != null ? emp.getName() : "Unknown";
            String dept = emp != null ? emp.getDepartment() : "";
            sb.append("{")
              .append("\"name\":\"").append(esc(name)).append("\",")
              .append("\"dept\":\"").append(esc(dept)).append("\",")
              .append("\"week\":").append(r.getWeekNumber()).append(",")
              .append("\"dateRange\":\"").append(esc(weekRange(r.getWeekNumber()))).append("\",")
              .append("\"mood\":").append(r.getMood()).append(",")
              .append("\"energy\":").append(r.getEnergy()).append(",")
              .append("\"motivation\":").append(r.getMotivation()).append(",")
              .append("\"stress\":").append(r.getStress()).append(",")
              .append("\"wlb\":").append(r.getWorkLifeBalance()).append(",")
              .append("\"team\":").append(r.getTeamConnection()).append(",")
              .append("\"accomplish\":").append(r.getAccomplishment()).append(",")
              .append("\"workload\":\"").append(esc(r.getWorkloadFeel())).append("\",")
              .append("\"risk\":\"").append(r.getRiskLevel()).append("\",")
              .append("\"issues\":\"").append(esc(r.getIssues())).append("\",")
              .append("\"wentWell\":\"").append(esc(r.getWentWell())).append("\",")
              .append("\"challenges\":\"").append(esc(r.getChallenges())).append("\",")
              .append("\"support\":\"").append(esc(r.getSupport())).append("\",")
              .append("\"date\":\"").append(r.getFormattedDate()).append("\"}");
        }
        sendJson(ex, sb.append("]").toString());
    }

    private void handleCheckin(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(405, -1); return; }
        String body = readBody(ex);
        int empId  = parseInt(jsonGet(body, "employeeId"));
        int mood   = parseInt(jsonGet(body, "mood"));
        int motiv  = parseInt(jsonGet(body, "motivation"));
        int stress = parseInt(jsonGet(body, "stress"));
        int energy = parseInt(jsonGet(body, "energy"));
        int wlb    = parseInt(jsonGet(body, "workLifeBalance"));
        int team   = parseInt(jsonGet(body, "teamConnection"));
        int acc    = parseInt(jsonGet(body, "accomplishment"));
        int week   = LocalDate.now().get(WeekFields.of(Locale.getDefault()).weekOfYear());
        surveyManager.addResponse(new SurveyResponse(empId, week, mood, motiv, stress,
            energy, wlb, team, acc,
            jsonGet(body,"issues"), jsonGet(body,"comment"),
            jsonGet(body,"wentWell"), jsonGet(body,"challenges"),
            jsonGet(body,"support"), jsonGet(body,"workloadFeel")));
        sendJson(ex, "{\"ok\":true}");
    }

    private void handleRemind(HttpExchange ex) throws IOException {
        sendJson(ex, "{\"ok\":true}");
    }

    private void handleExport(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(405, -1); return; }
        StringBuilder sb = new StringBuilder();
        sb.append("WorkMate Wellbeing Survey Export - ").append(LocalDate.now()).append("\\n");
        sb.append("=".repeat(80)).append("\\n");
        sb.append(String.format("%-20s %-8s %-5s %-6s %-5s %-6s %-5s %-5s %-5s %-8s %-8s%n",
            "Employee","Week","Mood","Energy","Motiv","Stress","WLB","Team","Acc","Workload","Risk"));
        sb.append("-".repeat(80)).append("\\n");
        for (SurveyResponse r : surveyManager.getAllResponses()) {
            Employee emp = directory.findById(r.getEmployeeId());
            sb.append(String.format("%-20s %-8s %-5d %-6d %-5d %-6d %-5d %-5d %-5d %-8s %-8s%n",
                emp != null ? emp.getName() : "Unknown",
                "Wk " + r.getWeekNumber(), r.getMood(), r.getEnergy(), r.getMotivation(),
                r.getStress(), r.getWorkLifeBalance(), r.getTeamConnection(),
                r.getAccomplishment(), r.getWorkloadFeel(), r.getRiskLevel()));
            if (!r.getWentWell().isEmpty())  sb.append("  + Went well: ").append(r.getWentWell()).append("\\n");
            if (!r.getChallenges().isEmpty()) sb.append("  - Challenge: ").append(r.getChallenges()).append("\\n");
            if (!r.getSupport().isEmpty())   sb.append("  ? Support needed: ").append(r.getSupport()).append("\\n");
        }
        byte[] bytes = sb.toString().replace("\\n", "\n").getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"workmate_export.txt\"");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void sendJson(HttpExchange ex, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private String empJson(Employee e) {
        return "{\"id\":" + e.getId() +
            ",\"name\":\"" + esc(e.getName()) + "\"" +
            ",\"position\":\"" + esc(e.getPosition()) + "\"" +
            ",\"department\":\"" + esc(e.getDepartment()) + "\"" +
            ",\"email\":\"" + esc(e.getEmail()) + "\"" +
            ",\"phone\":\"" + esc(e.getPhone()) + "\"}";
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String f(double v) { return String.format("%.2f", v); }
    private int parseInt(String s) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 5; } }

    private String jsonGet(String json, String key) {
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

    private String weekRange(int week) {
        int year = LocalDate.now().getYear();
        LocalDate jan4 = LocalDate.of(year, 1, 4);
        LocalDate weekMon = jan4.with(DayOfWeek.MONDAY).plusWeeks(week - 1);
        LocalDate weekFri = weekMon.plusDays(4);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d");
        return weekMon.format(fmt) + " – " + weekFri.format(fmt) + ", " + year;
    }

    // ── embedded HTML ─────────────────────────────────────────────────────────

    private static final String HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>WorkMate — Employee Wellbeing</title>
<style>
:root{
  --p:#2D6A4F;--p2:#40916C;--l:#52B788;--bg:#F4F6F4;
  --white:#fff;--text:#1A1A1A;--sec:#6B7280;--border:#E2E8E4;
  --red:#E63946;--yel:#F59E0B;--grn:#22C55E;--card:#fff;
}
*{box-sizing:border-box;margin:0;padding:0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;}
body{background:var(--bg);min-height:100vh;}

/* ── LOGIN ─────────────────────────────────────────────────────── */
.login-wrap{min-height:100vh;display:flex;align-items:center;justify-content:center;
  background:linear-gradient(135deg,#1B4332 0%,#2D6A4F 50%,#40916C 100%);}
.login-card{background:#fff;border-radius:24px;padding:48px 52px;width:440px;
  box-shadow:0 20px 60px rgba(0,0,0,.25);}
.logo-wrap{display:flex;flex-direction:column;align-items:center;margin-bottom:32px;}
.logo-svg-big{width:80px;height:80px;background:var(--p);border-radius:50%;
  display:flex;align-items:center;justify-content:center;
  box-shadow:0 8px 24px rgba(45,106,79,.4);margin-bottom:14px;}
.brand-name{font-size:28px;font-weight:800;color:var(--p);letter-spacing:-.5px;}
.brand-tag{font-size:13px;color:var(--sec);margin-top:3px;}
.field-lbl{font-size:12px;font-weight:600;color:var(--sec);text-transform:uppercase;
  letter-spacing:.5px;margin-bottom:6px;display:block;}
.field-select{width:100%;padding:11px 14px;border:2px solid var(--border);border-radius:10px;
  font-size:14px;background:#fff;color:var(--text);outline:none;
  appearance:none;background-image:url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%236B7280' d='M6 8L1 3h10z'/%3E%3C/svg%3E");
  background-repeat:no-repeat;background-position:right 12px center;cursor:pointer;}
.field-select:focus{border-color:var(--p);}
.login-divider{display:flex;align-items:center;gap:12px;margin:22px 0;color:var(--sec);font-size:12px;}
.login-divider::before,.login-divider::after{content:'';flex:1;height:1px;background:var(--border);}
.btn-full{display:block;width:100%;padding:14px;border-radius:12px;font-size:14px;
  font-weight:700;cursor:pointer;border:none;transition:all .2s;letter-spacing:.2px;}
.btn-full:hover{transform:translateY(-1px);box-shadow:0 4px 12px rgba(0,0,0,.15);}
.btn-primary{background:var(--p);color:#fff;}
.btn-outline{background:transparent;color:var(--p);border:2px solid var(--border);}
.btn-outline:hover{border-color:var(--p);background:#F0FAF5;}
.mt12{margin-top:12px;}
.mt20{margin-top:20px;}

/* ── APP SHELL ─────────────────────────────────────────────────── */
.app{display:none;flex-direction:column;height:100vh;}
.app.on{display:flex;}
header{background:var(--p);padding:0 28px;height:58px;display:flex;align-items:center;
  justify-content:space-between;flex-shrink:0;box-shadow:0 2px 8px rgba(0,0,0,.15);}
.h-left{display:flex;align-items:center;gap:10px;}
.h-logo{width:36px;height:36px;background:rgba(255,255,255,.15);border-radius:50%;
  display:flex;align-items:center;justify-content:center;}
.h-title{color:#fff;font-size:18px;font-weight:700;}
.h-sub{font-size:10px;color:rgba(200,240,220,.9);font-weight:400;margin-top:1px;}
.h-right{display:flex;align-items:center;gap:14px;}
.h-greet{color:rgba(255,255,255,.85);font-size:13px;}
.btn-hdr{padding:7px 18px;border-radius:8px;font-size:13px;font-weight:600;cursor:pointer;
  border:none;background:rgba(255,255,255,.15);color:#fff;transition:background .15s;}
.btn-hdr:hover{background:rgba(255,255,255,.25);}

/* ── TAB BAR ───────────────────────────────────────────────────── */
.tab-bar{background:#fff;border-bottom:1px solid var(--border);display:flex;
  flex-shrink:0;padding:0 28px;}
.tab{padding:0 20px;height:50px;display:flex;align-items:center;font-size:13px;
  cursor:pointer;color:var(--sec);border-bottom:3px solid transparent;
  margin-bottom:-1px;transition:all .15s;font-weight:500;gap:6px;}
.tab:hover{color:var(--p);}
.tab.active{color:var(--p);font-weight:700;border-bottom-color:var(--p);}
.tab-content{flex:1;overflow-y:auto;padding:28px;}

/* ── DIRECTORY ─────────────────────────────────────────────────── */
.dir-top{display:flex;justify-content:space-between;align-items:center;margin-bottom:16px;gap:14px;flex-wrap:wrap;}
.search-inp{padding:9px 14px 9px 36px;border:1px solid var(--border);border-radius:10px;
  font-size:13px;outline:none;width:260px;background:#fff;
  background-image:url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='14' height='14' viewBox='0 0 24 24' fill='none' stroke='%236B7280' stroke-width='2'%3E%3Ccircle cx='11' cy='11' r='8'/%3E%3Cpath d='m21 21-4.35-4.35'/%3E%3C/svg%3E");
  background-repeat:no-repeat;background-position:10px center;}
.search-inp:focus{border-color:var(--p);}
.filters{display:flex;gap:6px;flex-wrap:wrap;}
.fb{padding:5px 13px;border-radius:20px;font-size:12px;cursor:pointer;
  border:1px solid var(--border);background:#fff;color:var(--sec);
  font-weight:500;transition:all .15s;}
.fb:hover{border-color:var(--p);color:var(--p);}
.fb.act{background:var(--p);color:#fff;border-color:var(--p);}
.emp-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:12px;}
.ec{background:#fff;border-radius:14px;padding:16px;display:flex;align-items:center;
  gap:14px;box-shadow:0 1px 4px rgba(0,0,0,.07);cursor:pointer;
  border:1px solid var(--border);transition:all .15s;}
.ec:hover{border-color:var(--l);box-shadow:0 4px 12px rgba(45,106,79,.12);transform:translateY(-1px);}
.av{border-radius:50%;display:flex;align-items:center;justify-content:center;
  font-weight:700;color:#fff;flex-shrink:0;}
.av-md{width:48px;height:48px;font-size:16px;}
.av-lg{width:80px;height:80px;font-size:28px;}
.en{font-size:13px;font-weight:700;color:var(--text);}
.ep{font-size:12px;color:var(--sec);margin-top:2px;}
.ed{font-size:11px;color:#9CA3AF;margin-top:2px;}
.dept-badge{display:inline-block;padding:2px 8px;border-radius:20px;font-size:11px;
  font-weight:600;background:#EEF7F2;color:var(--p);margin-top:4px;}

/* ── CHECK-IN ──────────────────────────────────────────────────── */
.ci-wrap{display:flex;justify-content:center;padding:4px 0 32px;}
.ci-card{background:#fff;border-radius:16px;padding:32px 44px;width:580px;
  box-shadow:0 2px 16px rgba(0,0,0,.08);border:1px solid var(--border);}
.ci-title{font-size:24px;font-weight:800;color:var(--p);}
.ci-date{font-size:13px;color:var(--sec);margin:4px 0 28px;}
.ci-section-ttl{font-size:11px;font-weight:700;text-transform:uppercase;letter-spacing:.8px;
  color:var(--p);margin:24px 0 14px;padding-bottom:6px;border-bottom:2px solid var(--border);}
.sl-row{display:grid;grid-template-columns:1fr 1fr;gap:16px;margin-bottom:4px;}
.sl-item{background:#F8FBF9;border-radius:10px;padding:12px 14px;}
.sl-lbl{font-size:12px;font-weight:700;color:var(--text);margin-bottom:8px;
  display:flex;justify-content:space-between;}
.sl-lbl span{font-size:11px;color:var(--sec);font-weight:400;}
.sl-emrow{display:flex;align-items:center;gap:8px;}
.slem{font-size:16px;}
input[type=range]{flex:1;accent-color:var(--p);height:4px;}
.sl-val{font-size:14px;font-weight:800;color:var(--p);min-width:18px;text-align:right;}
.field-row{margin-bottom:14px;}
.field-row label{font-size:12px;font-weight:700;color:var(--sec);
  text-transform:uppercase;letter-spacing:.5px;display:block;margin-bottom:5px;}
.field-row select,.field-row textarea,.field-row input{
  width:100%;padding:10px 12px;border:1px solid var(--border);border-radius:9px;
  font-size:13px;outline:none;background:#fff;color:var(--text);}
.field-row select:focus,.field-row textarea:focus{border-color:var(--p);}
.field-row textarea{resize:vertical;min-height:64px;line-height:1.5;}
.issues-grid{display:grid;grid-template-columns:1fr 1fr;gap:6px;}
.cb-item{display:flex;align-items:center;gap:8px;padding:8px 10px;
  border-radius:8px;border:1px solid var(--border);cursor:pointer;font-size:13px;
  background:#fff;transition:border-color .15s;}
.cb-item:hover{border-color:var(--l);}
.cb-item input{accent-color:var(--p);width:15px;height:15px;cursor:pointer;flex-shrink:0;}
.ci-locked{text-align:center;padding:60px 20px;}
.ci-locked-icon{font-size:56px;margin-bottom:20px;}
.ci-locked h2{font-size:22px;color:var(--p);font-weight:700;margin-bottom:8px;}
.ci-locked p{font-size:14px;color:var(--sec);line-height:1.6;}
.ci-locked .badge{display:inline-block;margin-top:16px;padding:8px 20px;
  background:#EEF7F2;color:var(--p);border-radius:20px;font-size:13px;font-weight:600;}
.ci-locked .demo-note{margin-top:20px;font-size:12px;color:#9CA3AF;}
.btn-demo{margin-top:10px;padding:8px 18px;background:#fff;border:1px solid var(--border);
  border-radius:8px;font-size:12px;color:var(--sec);cursor:pointer;}
.btn-demo:hover{border-color:var(--p);color:var(--p);}
.submit-row{margin-top:24px;}
.btn-submit{width:100%;padding:14px;background:var(--p);color:#fff;border:none;
  border-radius:12px;font-size:14px;font-weight:700;cursor:pointer;transition:all .2s;}
.btn-submit:hover{background:var(--p2);transform:translateY(-1px);}

/* ── THANK YOU ─────────────────────────────────────────────────── */
.ty{text-align:center;padding:60px 20px;}
.ty-ring{width:80px;height:80px;background:var(--l);border-radius:50%;
  display:flex;align-items:center;justify-content:center;margin:0 auto 20px;
  box-shadow:0 8px 24px rgba(82,183,136,.35);}
.ty h2{font-size:26px;font-weight:800;color:var(--p);margin-bottom:8px;}
.ty p{font-size:14px;color:var(--sec);line-height:1.6;}

/* ── PROFILE ───────────────────────────────────────────────────── */
.pf-wrap{display:flex;justify-content:center;}
.pf-card{background:#fff;border-radius:16px;padding:40px 52px;width:460px;
  box-shadow:0 2px 16px rgba(0,0,0,.08);text-align:center;}
.pf-name{font-size:22px;font-weight:800;color:var(--text);margin-top:14px;}
.pf-pos{font-size:14px;color:var(--sec);margin-top:4px;}
.pf-divider{height:1px;background:var(--border);margin:24px 0;}
.pf-row{display:flex;align-items:flex-start;gap:12px;padding:8px 0;font-size:13px;text-align:left;}
.pf-ico{font-size:16px;width:22px;flex-shrink:0;}
.pf-lbl{font-weight:700;color:var(--sec);font-size:11px;text-transform:uppercase;
  letter-spacing:.4px;min-width:100px;}
.pf-val{color:var(--text);}
.btn-hr{margin-top:28px;padding:12px 24px;background:#EEF7F2;color:var(--p);
  border:none;border-radius:10px;font-size:13px;font-weight:700;cursor:pointer;
  width:100%;transition:background .15s;}
.btn-hr:hover{background:#D1EAD9;}

/* ── DASHBOARD ─────────────────────────────────────────────────── */
.metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin-bottom:18px;}
.mc{background:#fff;border-radius:14px;padding:18px 20px;
  box-shadow:0 1px 4px rgba(0,0,0,.07);border:1px solid var(--border);
  border-top:3px solid var(--l);}
.mc-val{font-size:24px;font-weight:800;margin-bottom:4px;}
.mc-lbl{font-size:11px;color:var(--sec);font-weight:500;text-transform:uppercase;letter-spacing:.4px;}
.card{background:#fff;border-radius:14px;padding:20px 24px;
  box-shadow:0 1px 4px rgba(0,0,0,.07);border:1px solid var(--border);}
.card-ttl{font-size:14px;font-weight:700;color:var(--text);margin-bottom:16px;}
.bars-wrap{display:flex;align-items:flex-end;gap:18px;height:130px;}
.wg{display:flex;align-items:flex-end;gap:3px;flex:1;}
.bar{border-radius:4px 4px 0 0;flex:1;transition:height .3s ease;}
.bar:hover{opacity:.8;cursor:default;}
.w-lbl{flex:1;text-align:center;font-size:11px;color:var(--sec);margin-top:6px;}
.legend{display:flex;gap:16px;margin-top:12px;flex-wrap:wrap;}
.ldot{width:10px;height:10px;border-radius:50%;display:inline-block;margin-right:5px;flex-shrink:0;}
.legend span{font-size:11px;color:var(--sec);}
.dash-bottom{display:flex;gap:14px;margin-top:18px;align-items:flex-start;}
.alert-card{background:#fff;border-radius:14px;padding:18px 20px;flex:1;
  box-shadow:0 1px 4px rgba(0,0,0,.07);border:1px solid var(--border);}
.alert-ttl{color:var(--red);font-weight:700;font-size:13px;margin-bottom:10px;
  display:flex;align-items:center;gap:6px;}
.risk-row{color:var(--red);font-size:13px;margin:5px 0;display:flex;align-items:center;gap:6px;}
.ok-txt{color:var(--grn);font-size:13px;}
.sim-btn{padding:11px 18px;background:#FFF7ED;color:#C2410C;border:none;border-radius:10px;
  font-size:13px;font-weight:600;cursor:pointer;white-space:nowrap;
  border:1px solid #FED7AA;transition:all .15s;margin-top:4px;}
.sim-btn:hover{background:#FED7AA;}

/* ── MANAGER DIRECTORY ─────────────────────────────────────────── */
.mgr-top{display:flex;justify-content:space-between;align-items:center;margin-bottom:14px;}
.btn-add{padding:9px 18px;background:var(--p);color:#fff;border:none;border-radius:10px;
  font-size:13px;font-weight:600;cursor:pointer;transition:opacity .15s;}
.btn-add:hover{opacity:.88;}

/* ── SURVEY DATA ───────────────────────────────────────────────── */
.sv-top{display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;}
.btn-export{padding:9px 18px;background:#EEF7F2;color:var(--p);border:1px solid var(--border);
  border-radius:10px;font-size:13px;font-weight:600;cursor:pointer;transition:all .15s;}
.btn-export:hover{background:#D1EAD9;border-color:var(--l);}
.wk-tabs{display:flex;gap:8px;margin-bottom:16px;flex-wrap:wrap;}
.wk-tab{padding:6px 16px;border-radius:20px;font-size:12px;font-weight:600;cursor:pointer;
  border:1px solid var(--border);background:#fff;color:var(--sec);transition:all .15s;}
.wk-tab:hover{border-color:var(--p);color:var(--p);}
.wk-tab.act{background:var(--p);color:#fff;border-color:var(--p);}
.week-block{margin-bottom:28px;}
.week-hdr{background:var(--p);color:#fff;padding:10px 16px;border-radius:10px 10px 0 0;
  display:flex;justify-content:space-between;align-items:center;}
.week-hdr-title{font-size:14px;font-weight:700;}
.week-hdr-range{font-size:12px;opacity:.8;}
.week-hdr-avg{font-size:12px;background:rgba(255,255,255,.15);padding:3px 10px;border-radius:20px;}
.sv-table{width:100%;border-collapse:collapse;background:#fff;}
.sv-table th{padding:9px 10px;text-align:left;font-size:11px;font-weight:700;
  color:var(--sec);background:#F8FBF9;text-transform:uppercase;letter-spacing:.4px;border-bottom:2px solid var(--border);}
.sv-table td{padding:9px 10px;font-size:12px;border-bottom:1px solid #F0F4F1;}
.sv-table tr:last-child td{border-bottom:none;}
.sv-table tr:hover td{background:#FAFDF9;}
.sv-table .avg-row td{background:#F0F9F4;font-weight:700;font-size:11px;color:var(--p);}
.cell-val{display:inline-block;padding:2px 7px;border-radius:20px;font-weight:700;font-size:11px;}
.cv-g{background:#DCFCE7;color:#166534;}
.cv-y{background:#FEF9C3;color:#854D0E;}
.cv-r{background:#FEE2E2;color:#991B1B;}
.risk-H{color:var(--red);font-weight:800;font-size:11px;}
.risk-M{color:var(--yel);font-weight:700;font-size:11px;}
.risk-L{color:var(--grn);font-weight:700;font-size:11px;}
.workload-tag{padding:2px 7px;border-radius:20px;font-size:10px;font-weight:600;}
.wl-light{background:#DCFCE7;color:#166534;}
.wl-manageable{background:#DBEAFE;color:#1D4ED8;}
.wl-heavy{background:#FEF9C3;color:#854D0E;}
.wl-overwhelming{background:#FEE2E2;color:#991B1B;}

/* ── MODAL ─────────────────────────────────────────────────────── */
.ov{position:fixed;inset:0;background:rgba(0,0,0,.45);display:flex;
  align-items:center;justify-content:center;z-index:1000;backdrop-filter:blur(2px);}
.modal{background:#fff;border-radius:16px;padding:28px 32px;width:420px;
  box-shadow:0 20px 60px rgba(0,0,0,.2);max-height:90vh;overflow-y:auto;}
.modal h2{font-size:18px;font-weight:700;color:var(--text);margin-bottom:18px;}
.modal-row{margin-bottom:12px;}
.modal-row label{display:block;font-size:11px;font-weight:700;color:var(--sec);
  text-transform:uppercase;letter-spacing:.4px;margin-bottom:4px;}
.modal-row input{width:100%;padding:9px 11px;border:1px solid var(--border);
  border-radius:8px;font-size:13px;outline:none;}
.modal-row input:focus{border-color:var(--p);}
.modal-row .detail-val{font-size:13px;color:var(--text);padding:6px 0;}
.mbtns{display:flex;gap:10px;justify-content:flex-end;margin-top:20px;}
.mbtn{padding:9px 22px;border-radius:8px;font-size:13px;font-weight:600;cursor:pointer;border:none;}
.mb-cancel{background:var(--bg);color:var(--sec);}
.mb-save{background:var(--p);color:#fff;}
</style>
</head>
<body>

<!-- ── LOGIN ──────────────────────────────────────────────────────────── -->
<div class="login-wrap" id="loginPage">
  <div class="login-card">
    <div class="logo-wrap">
      <div class="logo-svg-big">
        <svg width="46" height="40" viewBox="0 0 46 40" fill="none">
          <circle cx="16" cy="12" r="7" fill="white"/>
          <path d="M2 36c0-7.732 6.268-12 14-12s14 4.268 14 12" stroke="white" stroke-width="2.5" stroke-linecap="round" fill="none"/>
          <circle cx="34" cy="10" r="5.5" fill="rgba(255,255,255,0.65)"/>
          <path d="M24.5 36c0-6.075 4.253-9.5 9.5-9.5s9.5 3.425 9.5 9.5" stroke="rgba(255,255,255,0.65)" stroke-width="2" stroke-linecap="round" fill="none"/>
        </svg>
      </div>
      <div class="brand-name">WorkMate</div>
      <div class="brand-tag">Employee Wellbeing Platform</div>
    </div>
    <label class="field-lbl">Select your name</label>
    <select class="field-select" id="empSel"></select>
    <button class="btn-full btn-primary mt20" onclick="loginEmp()">Continue as Employee →</button>
    <div class="login-divider">or</div>
    <button class="btn-full btn-outline" onclick="loginMgr()">Manager / HR Login</button>
  </div>
</div>

<!-- ── EMPLOYEE APP ────────────────────────────────────────────────────── -->
<div class="app" id="empApp">
  <header>
    <div class="h-left">
      <div class="h-logo">
        <svg width="22" height="20" viewBox="0 0 46 40" fill="none">
          <circle cx="16" cy="12" r="7" fill="white"/>
          <path d="M2 36c0-7.732 6.268-12 14-12s14 4.268 14 12" stroke="white" stroke-width="3" fill="none"/>
          <circle cx="34" cy="10" r="5.5" fill="rgba(255,255,255,0.6)"/>
          <path d="M24.5 36c0-6.075 4.253-9.5 9.5-9.5s9.5 3.425 9.5 9.5" stroke="rgba(255,255,255,0.6)" stroke-width="2.5" fill="none"/>
        </svg>
      </div>
      <div class="h-title">WorkMate</div>
    </div>
    <div class="h-right">
      <span class="h-greet" id="empGreet"></span>
      <button class="btn-hdr" onclick="logout()">Logout</button>
    </div>
  </header>
  <div class="tab-bar">
    <div class="tab active" onclick="eTab(0,this)"><span>🗒</span> Directory</div>
    <div class="tab" onclick="eTab(1,this)"><span>✓</span> Weekly Check-in</div>
    <div class="tab" onclick="eTab(2,this)"><span>👤</span> My Profile</div>
  </div>
  <div class="tab-content" id="eContent"></div>
</div>

<!-- ── MANAGER APP ────────────────────────────────────────────────────── -->
<div class="app" id="mgrApp">
  <header>
    <div class="h-left">
      <div class="h-logo">
        <svg width="22" height="20" viewBox="0 0 46 40" fill="none">
          <circle cx="16" cy="12" r="7" fill="white"/>
          <path d="M2 36c0-7.732 6.268-12 14-12s14 4.268 14 12" stroke="white" stroke-width="3" fill="none"/>
          <circle cx="34" cy="10" r="5.5" fill="rgba(255,255,255,0.6)"/>
          <path d="M24.5 36c0-6.075 4.253-9.5 9.5-9.5s9.5 3.425 9.5 9.5" stroke="rgba(255,255,255,0.6)" stroke-width="2.5" fill="none"/>
        </svg>
      </div>
      <div><div class="h-title">WorkMate</div><div class="h-sub">Manager Portal</div></div>
    </div>
    <div class="h-right">
      <button class="btn-hdr" onclick="logout()">Logout</button>
    </div>
  </header>
  <div class="tab-bar">
    <div class="tab active" onclick="mTab(0,this)"><span>📊</span> Dashboard</div>
    <div class="tab" onclick="mTab(1,this)"><span>👥</span> Team Directory</div>
    <div class="tab" onclick="mTab(2,this)"><span>📋</span> Survey Data</div>
  </div>
  <div class="tab-content" id="mContent"></div>
</div>

<!-- ── MODAL ──────────────────────────────────────────────────────────── -->
<div class="ov" id="ov" style="display:none" onclick="if(event.target===this)closeModal()">
  <div class="modal" id="modalBody"></div>
</div>

<script>
// ── state ────────────────────────────────────────────────────────────────
let cur=null,emps=[],eDirF='All',mDirF='All',svWk=0,demoMode=false;
const AC=['#2D6A4F','#40916C','#1B4332','#52B788','#095D40','#74C69D'];

// ── utils ────────────────────────────────────────────────────────────────
function avc(name){let h=0;for(let i=0;i<name.length;i++)h=Math.imul(31,h)+name.charCodeAt(i)|0;return AC[Math.abs(h)%AC.length];}
function ini(n){return n.split(' ').map(p=>p[0]).join('').substring(0,2).toUpperCase();}
function today(){return new Date().toLocaleDateString('en-US',{weekday:'long',month:'long',day:'numeric',year:'numeric'});}
function fv(v){return parseFloat(v).toFixed(1);}
function cellCls(v,inv){const n=parseFloat(v);if(inv){return n<=4?'cv-g':n<=6?'cv-y':'cv-r';}return n>=7?'cv-g':n>=5?'cv-y':'cv-r';}

// ── init ─────────────────────────────────────────────────────────────────
async function init(){
  emps=await fetch('/api/employees').then(r=>r.json());
  const s=document.getElementById('empSel');
  emps.forEach(e=>{const o=document.createElement('option');o.value=e.id;o.textContent=e.name;s.appendChild(o);});
}

// ── auth ─────────────────────────────────────────────────────────────────
function loginEmp(){
  const id=parseInt(document.getElementById('empSel').value);
  cur=emps.find(e=>e.id===id);if(!cur)return;
  document.getElementById('loginPage').style.display='none';
  document.getElementById('empGreet').textContent='Hi, '+cur.name.split(' ')[0]+' \uD83D\uDC4B';
  document.getElementById('empApp').classList.add('on');
  eTab(0,document.querySelector('#empApp .tab'));
}
function loginMgr(){
  const pwd=prompt('Enter manager password:');
  if(pwd!=='admin'){if(pwd!==null)alert('Wrong password!');return;}
  document.getElementById('loginPage').style.display='none';
  document.getElementById('mgrApp').classList.add('on');
  mTab(0,document.querySelector('#mgrApp .tab'));
}
function logout(){
  cur=null;demoMode=false;
  document.getElementById('empApp').classList.remove('on');
  document.getElementById('mgrApp').classList.remove('on');
  document.getElementById('loginPage').style.display='flex';
}

// ── tab routing ──────────────────────────────────────────────────────────
function eTab(i,el){document.querySelectorAll('#empApp .tab').forEach(t=>t.classList.remove('active'));el.classList.add('active');const c=document.getElementById('eContent');if(i===0)renderEDir(c);else if(i===1)renderCI(c);else renderPF(c);}
function mTab(i,el){document.querySelectorAll('#mgrApp .tab').forEach(t=>t.classList.remove('active'));el.classList.add('active');const c=document.getElementById('mContent');if(i===0)renderDash(c);else if(i===1)renderMDir(c);else renderSV(c);}

// ── EMPLOYEE DIRECTORY ───────────────────────────────────────────────────
function depts(){return['All','IT','HR','Finance','Marketing','Management','Sales'];}
function renderEDir(c){
  c.innerHTML=`<div class="dir-top">
    <input class="search-inp" id="eSearch" placeholder="Search name or position..." oninput="renderECards()">
    <div class="filters">${depts().map(d=>`<button class="fb${d===eDirF?' act':''}" onclick="setEF('${d}',this)">${d}</button>`).join('')}</div>
  </div><div class="emp-grid" id="eGrid"></div>`;
  renderECards();
}
function setEF(f,el){eDirF=f;document.querySelectorAll('#eContent .fb').forEach(b=>b.classList.remove('act'));el.classList.add('act');renderECards();}
function renderECards(){
  const q=(document.getElementById('eSearch')?.value||'').toLowerCase();
  document.getElementById('eGrid').innerHTML=emps.filter(e=>(eDirF==='All'||e.department===eDirF)&&(q===''||e.name.toLowerCase().includes(q)||e.position.toLowerCase().includes(q)))
    .map(e=>`<div class="ec" onclick="showED(${e.id})">
      <div class="av av-md" style="background:${avc(e.name)}">${ini(e.name)}</div>
      <div><div class="en">${e.name}</div><div class="ep">${e.position}</div>
      <div class="dept-badge">${e.department}</div></div></div>`).join('');
}
function showED(id){
  const e=emps.find(x=>x.id===id);
  showModal(`<h2>${e.name}</h2>
    ${[['Position',e.position],['Department',e.department],['Email',e.email],['Phone',e.phone]]
      .map(([l,v])=>`<div class="modal-row"><label>${l}</label><div class="detail-val">${v}</div></div>`).join('')}
    <div class="mbtns"><button class="mbtn mb-cancel" onclick="closeModal()">Close</button></div>`);
}

// ── WEEKLY CHECK-IN ──────────────────────────────────────────────────────
function renderCI(c){
  const day=new Date().getDay();
  const isOpen=(day===4||day===5||demoMode);
  if(!isOpen){
    const names=['Sunday','Monday','Tuesday','Wednesday','Thursday','Friday','Saturday'];
    const daysLeft=day<4?4-day:4-day+7;
    c.innerHTML=`<div class="ci-wrap"><div class="ci-card"><div class="ci-locked">
      <div class="ci-locked-icon">🗓</div>
      <h2>Check-in opens on Thursday</h2>
      <p>The weekly wellbeing check-in is available <strong>Thursday and Friday</strong> only.<br>
      Today is ${names[day]}.</p>
      <div class="badge">Opens in ${daysLeft} day${daysLeft!==1?'s':''}</div>
      <div class="demo-note">Running a demo?</div>
      <button class="btn-demo" onclick="demoMode=true;renderCI(document.getElementById('eContent'))">Open in demo mode</button>
    </div></div></div>`;
    return;
  }
  c.innerHTML=`<div class="ci-wrap"><div class="ci-card" id="ciCard">
    <div class="ci-title">Weekly Wellbeing Check-in</div>
    <div class="ci-date">${today()}</div>
    <div class="ci-section-ttl">How are you feeling?</div>
    <div class="sl-row">
      ${[['Mood','Overall mood & emotional state','\uD83D\uDE14','\uD83D\uDE0A','sl0'],
         ['Energy','Physical & mental energy','\uD83E\uDEB4','\u26A1','sl1'],
         ['Motivation','Drive to engage with work','\uD83D\uDE34','\uD83D\uDE80','sl2'],
         ['Stress','Current stress & pressure','\uD83D\uDE0C','\uD83D\uDE30','sl3']].map(([l,hint,a,b,id])=>`
      <div class="sl-item"><div class="sl-lbl">${l} <span>${hint}</span></div>
        <div class="sl-emrow"><span class="slem">${a}</span>
          <input type="range" min="1" max="10" value="5" id="${id}" oninput="document.getElementById('v${id}').textContent=this.value">
          <span class="slem">${b}</span><span class="sl-val" id="v${id}">5</span></div></div>`).join('')}
    </div>
    <div class="sl-row" style="margin-top:12px">
      ${[['Work-Life Balance','Balance between work & personal life','\u2696\uFE0F','\uD83C\uDFE1','sl4'],
         ['Team Connection','Feeling connected & supported','\uD83E\uDDE3','\uD83E\uDD1D','sl5'],
         ['Accomplishment','Progress on meaningful goals','\uD83C\uDFAF','sl6']].filter(x=>x.length>=5).map(([l,hint,a,b,id])=>`
      <div class="sl-item"><div class="sl-lbl">${l} <span>${hint}</span></div>
        <div class="sl-emrow"><span class="slem">${a}</span>
          <input type="range" min="1" max="10" value="5" id="${id}" oninput="document.getElementById('v${id}').textContent=this.value">
          <span class="slem">${b}</span><span class="sl-val" id="v${id}">5</span></div></div>`).join('')}
      <div class="sl-item"><div class="sl-lbl">Accomplishment <span>Progress on meaningful goals</span></div>
        <div class="sl-emrow"><span class="slem">\uD83D\uDE10</span>
          <input type="range" min="1" max="10" value="5" id="sl6" oninput="document.getElementById('vsl6').textContent=this.value">
          <span class="slem">\uD83C\uDFAF</span><span class="sl-val" id="vsl6">5</span></div></div>
    </div>
    <div class="ci-section-ttl">Work context</div>
    <div style="display:grid;grid-template-columns:1fr 1fr;gap:14px">
      <div class="field-row"><label>How did your workload feel?</label>
        <select id="wlFeel">
          <option value="light">Light — had capacity to spare</option>
          <option value="manageable" selected>Manageable — just right</option>
          <option value="heavy">Heavy — struggled to keep up</option>
          <option value="overwhelming">Overwhelming — could not cope</option>
        </select></div>
      <div class="field-row"><label>Team communication this week</label>
        <select id="teamComm">
          <option value="excellent">Excellent — clear & supportive</option>
          <option value="good" selected>Good — mostly worked well</option>
          <option value="average">Average — some gaps</option>
          <option value="poor">Poor — frequent misalignment</option>
        </select></div>
    </div>
    <div class="ci-section-ttl">Open reflection (anonymous)</div>
    <div class="field-row"><label>What went well for you this week?</label>
      <textarea id="wentWell" placeholder="e.g. Finished the project milestone, good team meeting..."></textarea></div>
    <div class="field-row"><label>What was your biggest challenge?</label>
      <textarea id="challenges" placeholder="e.g. Too many meetings, unclear priorities..."></textarea></div>
    <div class="field-row"><label>What support or resources would help you most?</label>
      <textarea id="support" placeholder="e.g. Better async tools, clearer expectations, more 1:1 time..."></textarea></div>
    <div class="ci-section-ttl">Anything specific to flag?</div>
    <div class="issues-grid">
      ${['Workload too heavy','Unclear expectations','Team conflicts','Lack of recognition',
         'Technical blockers','Personal challenges','Poor work-life balance','Feeling isolated'].map((o,i)=>
        `<label class="cb-item"><input type="checkbox" id="cb${i}"> ${o}</label>`).join('')}
    </div>
    <div class="submit-row">
      <button class="btn-submit" onclick="submitCI()">Submit Wellbeing Check-in \u2192</button>
    </div>
  </div></div>`;
}
async function submitCI(){
  const issues=['Workload too heavy','Unclear expectations','Team conflicts','Lack of recognition',
    'Technical blockers','Personal challenges','Poor work-life balance','Feeling isolated']
    .filter((_,i)=>document.getElementById('cb'+i)?.checked).join(', ');
  await fetch('/api/checkin',{method:'POST',headers:{'Content-Type':'application/json'},
    body:JSON.stringify({
      employeeId:cur.id,
      mood:+document.getElementById('sl0').value,
      energy:+document.getElementById('sl1').value,
      motivation:+document.getElementById('sl2').value,
      stress:+document.getElementById('sl3').value,
      workLifeBalance:+document.getElementById('sl4').value,
      teamConnection:+document.getElementById('sl5').value,
      accomplishment:+document.getElementById('sl6').value,
      workloadFeel:document.getElementById('wlFeel').value,
      issues,comment:'',
      wentWell:document.getElementById('wentWell').value,
      challenges:document.getElementById('challenges').value,
      support:document.getElementById('support').value
    })});
  document.getElementById('ciCard').innerHTML=`<div class="ty">
    <div class="ty-ring"><svg width="38" height="38" viewBox="0 0 38 38" fill="none">
      <polyline points="7,19 15,27 31,11" stroke="white" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/></svg></div>
    <h2>Thank you for checking in!</h2>
    <p>Your response has been recorded anonymously.<br>Your wellbeing matters — see you next week.</p>
  </div>`;
}

// ── PROFILE ──────────────────────────────────────────────────────────────
function renderPF(c){
  const e=cur;
  c.innerHTML=`<div class="pf-wrap"><div class="pf-card">
    <div class="av av-lg" style="background:${avc(e.name)};margin:0 auto">${ini(e.name)}</div>
    <div class="pf-name">${e.name}</div><div class="pf-pos">${e.position}</div>
    <div class="dept-badge" style="margin-top:8px">${e.department}</div>
    <div class="pf-divider"></div>
    <div>${[['\uD83D\uDCE7','Email',e.email],['\uD83D\uDCF1','Phone',e.phone],
            ['\uD83C\uDFE2','Department',e.department],['\uD83C\uDD94','Employee ID','#'+e.id]]
      .map(([ic,l,v])=>`<div class="pf-row"><span class="pf-ico">${ic}</span>
        <span class="pf-lbl">${l}</span><span class="pf-val">${v}</span></div>`).join('')}</div>
    <button class="btn-hr" onclick="alert('HR Department\\nAnna B\\u0113rzi\\u0146a\\nEmail: anna.berzina@company.lv\\nPhone: +371 20000001')">\uD83D\uDCDE Contact HR</button>
  </div></div>`;
}

// ── DASHBOARD ────────────────────────────────────────────────────────────
async function renderDash(c){
  const s=await fetch('/api/stats').then(r=>r.json());
  function mc(v){return parseFloat(v)>=7?'#22C55E':parseFloat(v)>=5?'#F59E0B':'#E63946';}
  function sc(v){return parseFloat(v)<=4?'#22C55E':parseFloat(v)<=6?'#F59E0B':'#E63946';}
  const mh=120;function bh(v){return Math.max(4,Math.round(parseFloat(v)/10*mh));}
  c.innerHTML=`
  <div class="metrics">
    ${[['Avg Mood',s.avgMood,mc(s.avgMood)],['Avg Energy',s.avgEnergy,mc(s.avgEnergy)],
       ['Avg Stress',s.avgStress,sc(s.avgStress)],['Response Rate',s.responseRate,parseFloat(s.responseRate)>70?'#22C55E':'#F59E0B']]
      .map(([l,v,col])=>`<div class="mc" style="border-top-color:${col}">
        <div class="mc-val" style="color:${col}">${l.includes('Rate')?parseFloat(v).toFixed(0)+'%':parseFloat(v).toFixed(1)+' / 10'}</div>
        <div class="mc-lbl">${l}</div></div>`).join('')}
  </div>
  <div class="card" style="margin-bottom:16px">
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
      <div class="card-ttl">4-Week Team Overview</div>
      <div class="legend">
        <span><span class="ldot" style="background:#52B788"></span>Mood</span>
        <span><span class="ldot" style="background:#2D6A4F"></span>Motivation</span>
        <span><span class="ldot" style="background:#F59E0B"></span>Stress</span>
        <span><span class="ldot" style="background:#60A5FA"></span>Energy</span>
        <span><span class="ldot" style="background:#A78BFA"></span>WLB</span>
      </div>
    </div>
    <div class="bars-wrap">${s.weeklyStats.map(w=>`<div class="wg">
      <div class="bar" style="height:${bh(w.mood)}px;background:#52B788" title="Mood: ${fv(w.mood)}"></div>
      <div class="bar" style="height:${bh(w.motivation)}px;background:#2D6A4F" title="Motivation: ${fv(w.motivation)}"></div>
      <div class="bar" style="height:${bh(w.stress)}px;background:#F59E0B" title="Stress: ${fv(w.stress)}"></div>
      <div class="bar" style="height:${bh(w.energy)}px;background:#60A5FA" title="Energy: ${fv(w.energy)}"></div>
      <div class="bar" style="height:${bh(w.wlb)}px;background:#A78BFA" title="Work-Life Balance: ${fv(w.wlb)}"></div>
    </div>`).join('')}</div>
    <div style="display:flex;gap:18px;margin-top:8px">${s.weeklyStats.map((w,i)=>`<div class="w-lbl">Week ${i+1}<br><span style="font-size:10px;color:#9CA3AF">${w.dateRange}</span></div>`).join('')}</div>
  </div>
  <div class="dash-bottom">
    <div class="alert-card">
      <div class="alert-ttl"><span>⚠</span> Burnout Risk Alerts</div>
      ${s.highRisk.length===0?'<div class="ok-txt">✔ No high-risk employees detected this week</div>':
        s.highRisk.map(e=>`<div class="risk-row"><span style="color:#E63946">●</span> ${e.name} <span style="color:#9CA3AF;font-size:11px">– ${e.department}</span></div>`).join('')}
    </div>
    <button class="sim-btn" onclick="simRemind()">🔔 Simulate Monday Reminder</button>
  </div>`;
}
async function simRemind(){await fetch('/api/remind',{method:'POST'});alert('Monday reminder sent to all employees!');}

// ── MANAGER DIRECTORY ────────────────────────────────────────────────────
function renderMDir(c){
  c.innerHTML=`<div class="mgr-top">
    <h2 style="font-size:16px;font-weight:700">${emps.length} Team Members</h2>
    <button class="btn-add" onclick="showAddEmp()">+ Add Employee</button>
  </div>
  <div class="filters" style="margin-bottom:16px">${['All','IT','HR','Finance','Marketing','Management','Sales']
    .map(d=>`<button class="fb${d===mDirF?' act':''}" onclick="setMF('${d}',this)">${d}</button>`).join('')}</div>
  <div class="emp-grid" id="mGrid"></div>`;
  renderMCards();
}
function setMF(f,el){mDirF=f;document.querySelectorAll('#mContent .fb').forEach(b=>b.classList.remove('act'));el.classList.add('act');renderMCards();}
function renderMCards(){
  document.getElementById('mGrid').innerHTML=emps.filter(e=>mDirF==='All'||e.department===mDirF)
    .map(e=>`<div class="ec" onclick="showED(${e.id})">
      <div class="av av-md" style="background:${avc(e.name)}">${ini(e.name)}</div>
      <div><div class="en">${e.name}</div><div class="ep">${e.position}</div>
      <div class="dept-badge">${e.department}</div></div></div>`).join('');
}
function showAddEmp(){
  showModal(`<h2>Add Employee</h2>
    ${['Full Name','Position','Department','Email','Phone'].map((l,i)=>`
    <div class="modal-row"><label>${l}</label><input type="text" id="ef${i}" placeholder="${l}"></div>`).join('')}
    <div class="mbtns"><button class="mbtn mb-cancel" onclick="closeModal()">Cancel</button>
    <button class="mbtn mb-save" onclick="addEmp()">Add Employee</button></div>`);
}
async function addEmp(){
  const fs=[0,1,2,3,4].map(i=>document.getElementById('ef'+i).value.trim());
  if(!fs[0]){alert('Name is required!');return;}
  const e=await fetch('/api/employee',{method:'POST',headers:{'Content-Type':'application/json'},
    body:JSON.stringify({name:fs[0],position:fs[1],department:fs[2],email:fs[3],phone:fs[4]})}).then(r=>r.json());
  emps.push(e);closeModal();renderMDir(document.getElementById('mContent'));
}

// ── SURVEY DATA ──────────────────────────────────────────────────────────
async function renderSV(c){
  const url=svWk>0?'/api/survey?week='+svWk:'/api/survey';
  const rows=await fetch(url).then(r=>r.json());
  const wkTabs=[['All Weeks',0],['Week 1',1],['Week 2',2],['Week 3',3],['Week 4',4]]
    .map(([l,w])=>`<button class="wk-tab${svWk===w?' act':''}" onclick="setSvWk(${w})">${l}</button>`).join('');

  // group by week
  const byWeek={};
  rows.forEach(r=>{const k=r.week;if(!byWeek[k])byWeek[k]=[];byWeek[k].push(r);});
  const weeks=Object.keys(byWeek).map(Number).sort((a,b)=>a-b);

  function avgArr(arr,key){if(!arr.length)return 0;return(arr.reduce((s,r)=>s+r[key],0)/arr.length).toFixed(1);}
  function cv(v,inv){const cls=cellCls(v,inv);return `<span class="cell-val ${cls}">${v}</span>`;}
  function wlCls(w){return'workload-tag wl-'+w;}

  const weeksHtml=weeks.map(wk=>{
    const rs=byWeek[wk];
    const dr=rs[0].dateRange;
    const avgs={mood:avgArr(rs,'mood'),energy:avgArr(rs,'energy'),motivation:avgArr(rs,'motivation'),
      stress:avgArr(rs,'stress'),wlb:avgArr(rs,'wlb'),team:avgArr(rs,'team'),accomplish:avgArr(rs,'accomplish')};
    const riskCounts={HIGH:rs.filter(r=>r.risk==='HIGH').length,MEDIUM:rs.filter(r=>r.risk==='MEDIUM').length,LOW:rs.filter(r=>r.risk==='LOW').length};
    return `<div class="week-block">
      <div class="week-hdr">
        <div><div class="week-hdr-title">Week ${wk}</div><div class="week-hdr-range">${dr}</div></div>
        <div style="display:flex;gap:8px;align-items:center">
          ${riskCounts.HIGH>0?`<span style="background:rgba(230,57,70,.25);color:#FCA5A5;padding:3px 9px;border-radius:20px;font-size:11px;font-weight:700">${riskCounts.HIGH} HIGH</span>`:''}
          ${riskCounts.MEDIUM>0?`<span style="background:rgba(245,158,11,.2);color:#FCD34D;padding:3px 9px;border-radius:20px;font-size:11px;font-weight:700">${riskCounts.MEDIUM} MEDIUM</span>`:''}
          <span class="week-hdr-avg">Avg mood ${avgs.mood} · Avg stress ${avgs.stress}</span>
        </div>
      </div>
      <table class="sv-table">
        <thead><tr>
          <th>Employee</th><th>Dept</th>
          <th title="Mood">😊</th><th title="Energy">⚡</th><th title="Motivation">🚀</th>
          <th title="Stress">😰</th><th title="Work-Life Balance">⚖</th>
          <th title="Team Connection">🤝</th><th title="Accomplishment">🎯</th>
          <th>Workload</th><th>Risk</th><th>Highlights</th>
        </tr></thead>
        <tbody>
          ${rs.map(r=>`<tr>
            <td style="font-weight:600">${r.name}</td>
            <td><span class="dept-badge" style="font-size:10px">${r.dept}</span></td>
            <td>${cv(r.mood,false)}</td><td>${cv(r.energy,false)}</td><td>${cv(r.motivation,false)}</td>
            <td>${cv(r.stress,true)}</td><td>${cv(r.wlb,false)}</td>
            <td>${cv(r.team,false)}</td><td>${cv(r.accomplish,false)}</td>
            <td><span class="${wlCls(r.workload)}">${r.workload}</span></td>
            <td class="risk-${r.risk[0]}">${r.risk}</td>
            <td style="max-width:160px;font-size:11px;color:#6B7280">
              ${r.wentWell?'✓ '+r.wentWell.substring(0,40)+(r.wentWell.length>40?'...':''):'—'}
            </td></tr>`).join('')}
          <tr class="avg-row">
            <td colspan="2">TEAM AVERAGES</td>
            <td>${avgs.mood}</td><td>${avgs.energy}</td><td>${avgs.motivation}</td>
            <td>${avgs.stress}</td><td>${avgs.wlb}</td><td>${avgs.team}</td><td>${avgs.accomplish}</td>
            <td colspan="3">${rs.length} responses</td>
          </tr>
        </tbody>
      </table></div>`;
  }).join('');

  c.innerHTML=`<div class="sv-top">
    <h2 style="font-size:16px;font-weight:700">Survey Responses</h2>
    <button class="btn-export" onclick="exportSV()">\u2B07 Export Report</button>
  </div>
  <div class="wk-tabs">${wkTabs}</div>
  ${weeksHtml||'<div style="color:var(--sec);text-align:center;padding:40px">No responses found.</div>'}`;
}
function setSvWk(w){svWk=w;renderSV(document.getElementById('mContent'));}
async function exportSV(){
  const text=await fetch('/api/export').then(r=>r.text());
  const a=document.createElement('a');
  a.href=URL.createObjectURL(new Blob([text],{type:'text/plain'}));
  a.download='workmate_wellbeing_report.txt';a.click();
}

// ── MODAL ────────────────────────────────────────────────────────────────
function showModal(html){document.getElementById('modalBody').innerHTML=html;document.getElementById('ov').style.display='flex';}
function closeModal(){document.getElementById('ov').style.display='none';}

init();
</script>
</body>
</html>
""";
}
