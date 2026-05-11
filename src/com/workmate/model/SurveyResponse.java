package com.workmate.model;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;

/**
 * Represents one weekly wellbeing check-in submitted by an employee.
 * Risk scoring uses a weighted heuristic across all seven dimensions.
 */
public class SurveyResponse {

    private int    employeeId;
    private String weekStart;        // ISO week string, e.g. "2026-W18"
    private int    mood;             // 1-10
    private int    motivation;       // 1-10
    private int    stress;           // 1-10
    private int    energy;           // 1-10
    private int    workLifeBalance;  // 1-10
    private int    teamConnection;   // 1-10
    private int    accomplishment;   // 1-10
    private String workloadFeel;
    private String teamCommunication;
    private String wentWell;
    private String challenges;
    private String support;
    private String issues;
    private String submittedAt;      // datetime string from DB, or null for in-memory

    // ── constructors ──────────────────────────────────────────────────────────

    /**
     * Full constructor used by web check-in submissions and sample data.
     *
     * @param employeeId        the employee's database id
     * @param weekStart         ISO week string, e.g. "2026-W18"
     * @param mood              mood score 1–10
     * @param motivation        motivation score 1–10
     * @param stress            stress score 1–10
     * @param energy            energy score 1–10
     * @param workLifeBalance   work-life balance score 1–10
     * @param teamConnection    team connection score 1–10
     * @param accomplishment    accomplishment score 1–10
     * @param issues            comma-separated flagged issues
     * @param wentWell          open-text reflection – what went well
     * @param challenges        open-text reflection – challenges
     * @param support           open-text reflection – support needed
     * @param workloadFeel      one of: light / manageable / heavy / overwhelming
     * @param teamCommunication one of: excellent / good / average / poor
     * @param submittedAt       datetime string (from DB row), or null to use now
     */
    public SurveyResponse(int employeeId, String weekStart,
                          int mood, int motivation, int stress,
                          int energy, int workLifeBalance, int teamConnection, int accomplishment,
                          String issues, String wentWell, String challenges, String support,
                          String workloadFeel, String teamCommunication, String submittedAt) {
        this.employeeId       = employeeId;
        this.weekStart        = weekStart != null ? weekStart : currentIsoWeek();
        this.mood             = mood;
        this.motivation       = motivation;
        this.stress           = stress;
        this.energy           = energy;
        this.workLifeBalance  = workLifeBalance;
        this.teamConnection   = teamConnection;
        this.accomplishment   = accomplishment;
        this.issues           = issues           != null ? issues           : "";
        this.wentWell         = wentWell         != null ? wentWell         : "";
        this.challenges       = challenges       != null ? challenges       : "";
        this.support          = support          != null ? support          : "";
        this.workloadFeel     = workloadFeel     != null ? workloadFeel     : "manageable";
        this.teamCommunication= teamCommunication!= null ? teamCommunication: "good";
        this.submittedAt      = submittedAt      != null ? submittedAt
                                                         : LocalDateTime.now()
                                                             .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    // ── risk scoring ──────────────────────────────────────────────────────────

    /**
     * Computes a weighted burnout-risk label from the seven dimensions.
     *
     * @return "HIGH", "MEDIUM", or "LOW"
     */
    public String getRiskLevel() {
        int score = 0;
        if (stress > 7) score += 3; else if (stress > 5) score += 1;
        if (mood < 4)   score += 3; else if (mood < 6)   score += 1;
        if (energy < 4) score += 2; else if (energy < 6) score += 1;
        if (workLifeBalance < 4) score += 2; else if (workLifeBalance < 6) score += 1;
        if (teamConnection < 4) score += 1;
        if ("overwhelming".equals(workloadFeel)) score += 2;
        else if ("heavy".equals(workloadFeel)) score += 1;
        if (score >= 7) return "HIGH";
        if (score >= 3) return "MEDIUM";
        return "LOW";
    }

    /**
     * Returns a composite wellbeing score (0–10, higher is better).
     * Stress is inverted so that high stress reduces the score.
     */
    public double getCompositeScore() {
        double positive = mood + motivation + energy + workLifeBalance + teamConnection + accomplishment;
        double invertedStress = 11 - stress;
        return (positive + invertedStress) / 7.0;
    }

    // ── getters ───────────────────────────────────────────────────────────────

    /** Returns the employee's database id. */
    public int getEmployeeId() { return employeeId; }

    /** Returns the ISO week string, e.g. "2026-W18". */
    public String getWeekStart() { return weekStart; }

    /** Returns the mood score (1–10). */
    public int getMood() { return mood; }

    /** Returns the motivation score (1–10). */
    public int getMotivation() { return motivation; }

    /** Returns the stress score (1–10). */
    public int getStress() { return stress; }

    /** Returns the energy score (1–10). */
    public int getEnergy() { return energy; }

    /** Returns the work-life balance score (1–10). */
    public int getWorkLifeBalance() { return workLifeBalance; }

    /** Returns the team connection score (1–10). */
    public int getTeamConnection() { return teamConnection; }

    /** Returns the accomplishment score (1–10). */
    public int getAccomplishment() { return accomplishment; }

    /** Returns comma-separated flagged issues. */
    public String getIssues() { return issues; }

    /** Returns the open-text "what went well" reflection. */
    public String getWentWell() { return wentWell; }

    /** Returns the open-text "challenges" reflection. */
    public String getChallenges() { return challenges; }

    /** Returns the open-text "support needed" reflection. */
    public String getSupport() { return support; }

    /** Returns the workload-feel category (light/manageable/heavy/overwhelming). */
    public String getWorkloadFeel() { return workloadFeel; }

    /** Returns the team-communication rating (excellent/good/average/poor). */
    public String getTeamCommunication() { return teamCommunication; }

    /** Returns the submission datetime as a string (yyyy-MM-dd HH:mm:ss). */
    public String getSubmittedAt() { return submittedAt; }

    /** Returns just the date portion of submittedAt (yyyy-MM-dd). */
    public String getFormattedDate() {
        return submittedAt != null && submittedAt.length() >= 10
               ? submittedAt.substring(0, 10)
               : LocalDate.now().toString();
    }

    // ── helper ────────────────────────────────────────────────────────────────

    /** Returns the ISO week string for the current date, e.g. "2026-W18". */
    public static String currentIsoWeek() {
        LocalDate now = LocalDate.now();
        int week = now.get(WeekFields.ISO.weekOfWeekBasedYear());
        int year = now.get(WeekFields.ISO.weekBasedYear());
        return String.format("%d-W%02d", year, week);
    }
}
