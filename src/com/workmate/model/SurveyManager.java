package com.workmate.model;

import java.util.*;
import java.util.function.ToIntFunction;

/**
 * Thin in-memory aggregation helper used during sample-data seeding.
 * In normal operation the WebServer reads directly from DataStore;
 * this class is retained for algorithmic demonstration purposes.
 */
public class SurveyManager {

    private final List<SurveyResponse> responses;
    private final Stack<String>        activityLog;   // O(1) push / pop
    private final Queue<Employee>      reminderQueue; // O(1) enqueue / dequeue

    /** Creates an empty SurveyManager with empty log and reminder queue. */
    public SurveyManager() {
        responses     = new ArrayList<>();
        activityLog   = new Stack<>();
        reminderQueue = new LinkedList<>();
    }

    // ── mutation ──────────────────────────────────────────────────────────────

    /**
     * Adds a survey response and records an activity-log entry.
     *
     * @param r the response to add
     */
    public void addResponse(SurveyResponse r) {
        responses.add(r);
        activityLog.push("Check-in by employee #" + r.getEmployeeId()
                         + " (week " + r.getWeekStart() + ")");
    }

    /**
     * Pops and returns the most recent activity-log entry.
     *
     * @return activity string, or "No activity yet" if the log is empty
     */
    public String getLastActivity() {
        return activityLog.isEmpty() ? "No activity yet" : activityLog.pop();
    }

    /**
     * Enqueues an employee to receive a reminder notification.
     *
     * @param e employee to notify
     */
    public void addReminder(Employee e) { reminderQueue.add(e); }

    /**
     * Dequeues and returns the next employee awaiting a reminder.
     *
     * @return the next Employee, or null if the queue is empty
     */
    public Employee getNextReminder() { return reminderQueue.poll(); }

    // ── aggregates across all responses ───────────────────────────────────────

    /** Returns the average mood score across all responses. */
    public double getAverageMood()           { return avg(responses, SurveyResponse::getMood); }

    /** Returns the average motivation score across all responses. */
    public double getAverageMotivation()     { return avg(responses, SurveyResponse::getMotivation); }

    /** Returns the average stress score across all responses. */
    public double getAverageStress()         { return avg(responses, SurveyResponse::getStress); }

    /** Returns the average energy score across all responses. */
    public double getAverageEnergy()         { return avg(responses, SurveyResponse::getEnergy); }

    /** Returns the average work-life-balance score across all responses. */
    public double getAverageWorkLifeBalance(){ return avg(responses, SurveyResponse::getWorkLifeBalance); }

    /** Returns the average team-connection score across all responses. */
    public double getAverageTeamConnection() { return avg(responses, SurveyResponse::getTeamConnection); }

    /** Returns the average accomplishment score across all responses. */
    public double getAverageAccomplishment() { return avg(responses, SurveyResponse::getAccomplishment); }

    // ── aggregates per week ───────────────────────────────────────────────────

    /**
     * Returns all responses whose weekStart matches the given ISO string.
     *
     * @param weekStart ISO week string, e.g. "2026-W18"
     * @return matching responses
     */
    public List<SurveyResponse> getResponsesForWeek(String weekStart) {
        List<SurveyResponse> result = new ArrayList<>();
        for (SurveyResponse r : responses)
            if (weekStart.equals(r.getWeekStart())) result.add(r);
        return result;
    }

    /**
     * Returns all distinct week strings present in the responses, sorted.
     *
     * @return sorted list of ISO week strings
     */
    public List<String> getDistinctWeeks() {
        Set<String> set = new TreeSet<>(responses.stream()
            .map(SurveyResponse::getWeekStart)
            .collect(java.util.stream.Collectors.toSet()));
        return new ArrayList<>(set);
    }

    /**
     * Returns the ids of employees whose most-recent response is rated HIGH risk.
     *
     * @return list of employee ids
     */
    public List<Integer> getHighRiskEmployeeIds() {
        Map<Integer, SurveyResponse> latest = new HashMap<>();
        for (SurveyResponse r : responses) {
            SurveyResponse prev = latest.get(r.getEmployeeId());
            if (prev == null || r.getWeekStart().compareTo(prev.getWeekStart()) > 0)
                latest.put(r.getEmployeeId(), r);
        }
        List<Integer> result = new ArrayList<>();
        for (Map.Entry<Integer, SurveyResponse> e : latest.entrySet())
            if ("HIGH".equals(e.getValue().getRiskLevel())) result.add(e.getKey());
        return result;
    }

    /**
     * Computes the response rate as a percentage for a given week.
     *
     * @param totalEmployees denominator
     * @param weekStart      ISO week string
     * @return percentage 0–100
     */
    public double getResponseRate(int totalEmployees, String weekStart) {
        long count = responses.stream().filter(r -> weekStart.equals(r.getWeekStart())).count();
        return totalEmployees > 0 ? (count * 100.0 / totalEmployees) : 0;
    }

    /** Returns all responses in insertion order. */
    public List<SurveyResponse> getAllResponses() { return responses; }

    // ── internal ──────────────────────────────────────────────────────────────

    // Bubble sort replaced with O(n log n) merge sort via Collections.sort()
    private double avg(List<SurveyResponse> list, ToIntFunction<SurveyResponse> fn) {
        if (list.isEmpty()) return 0;
        return list.stream().mapToInt(fn).average().orElse(0);
    }
}
