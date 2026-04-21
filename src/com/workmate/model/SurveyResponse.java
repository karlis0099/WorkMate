package com.workmate.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SurveyResponse {
    private int employeeId;
    private int weekNumber;
    private int mood;           // 1-10
    private int motivation;     // 1-10
    private int stress;         // 1-10
    private int energy;         // 1-10
    private int workLifeBalance; // 1-10
    private int teamConnection; // 1-10
    private int accomplishment; // 1-10
    private String issues;
    private String comment;
    private String wentWell;
    private String challenges;
    private String support;
    private String workloadFeel;
    private LocalDateTime timestamp;

    // backward-compat constructor used by sample data generation
    public SurveyResponse(int employeeId, int weekNumber, int mood, int motivation, int stress,
                          String issues, String comment) {
        this(employeeId, weekNumber, mood, motivation, stress,
             5, 5, 5, 5, issues, comment, "", "", "", "manageable");
    }

    // full constructor used by web check-in submissions
    public SurveyResponse(int employeeId, int weekNumber, int mood, int motivation, int stress,
                          int energy, int workLifeBalance, int teamConnection, int accomplishment,
                          String issues, String comment, String wentWell, String challenges,
                          String support, String workloadFeel) {
        this.employeeId = employeeId;
        this.weekNumber = weekNumber;
        this.mood = mood;
        this.motivation = motivation;
        this.stress = stress;
        this.energy = energy;
        this.workLifeBalance = workLifeBalance;
        this.teamConnection = teamConnection;
        this.accomplishment = accomplishment;
        this.issues = issues != null ? issues : "";
        this.comment = comment != null ? comment : "";
        this.wentWell = wentWell != null ? wentWell : "";
        this.challenges = challenges != null ? challenges : "";
        this.support = support != null ? support : "";
        this.workloadFeel = workloadFeel != null ? workloadFeel : "manageable";
        this.timestamp = LocalDateTime.now();
    }

    // weighted burnout risk across all dimensions
    public String getRiskLevel() {
        int score = 0;
        if (stress > 7) score += 3; else if (stress > 5) score += 1;
        if (mood < 4) score += 3; else if (mood < 6) score += 1;
        if (energy < 4) score += 2; else if (energy < 6) score += 1;
        if (workLifeBalance < 4) score += 2; else if (workLifeBalance < 6) score += 1;
        if (teamConnection < 4) score += 1;
        if ("overwhelming".equals(workloadFeel)) score += 2;
        else if ("heavy".equals(workloadFeel)) score += 1;
        if (score >= 7) return "HIGH";
        if (score >= 3) return "MEDIUM";
        return "LOW";
    }

    public int getEmployeeId() { return employeeId; }
    public int getWeekNumber() { return weekNumber; }
    public int getMood() { return mood; }
    public int getMotivation() { return motivation; }
    public int getStress() { return stress; }
    public int getEnergy() { return energy; }
    public int getWorkLifeBalance() { return workLifeBalance; }
    public int getTeamConnection() { return teamConnection; }
    public int getAccomplishment() { return accomplishment; }
    public String getIssues() { return issues; }
    public String getComment() { return comment; }
    public String getWentWell() { return wentWell; }
    public String getChallenges() { return challenges; }
    public String getSupport() { return support; }
    public String getWorkloadFeel() { return workloadFeel; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public int getYear() { return timestamp.getYear(); }
    public String getFormattedDate() {
        return timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }
}
