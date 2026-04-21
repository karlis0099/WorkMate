package com.workmate.model;

import java.util.*;

public class SurveyManager {
    private ArrayList<SurveyResponse> responses;
    private Stack<String> activityLog;     // O(1) push/pop
    private Queue<Employee> reminderQueue; // O(1) enqueue/dequeue

    public SurveyManager() {
        responses = new ArrayList<>();
        activityLog = new Stack<>();
        reminderQueue = new LinkedList<>();
    }

    public void addResponse(SurveyResponse r) {
        responses.add(r);
        activityLog.push("Check-in by employee #" + r.getEmployeeId() + " (week " + r.getWeekNumber() + ")");
    }

    public String getLastActivity() {
        return activityLog.isEmpty() ? "No activity yet" : activityLog.pop();
    }

    public void addReminder(Employee e) { reminderQueue.add(e); }
    public Employee getNextReminder() { return reminderQueue.poll(); }

    // --- averages across all responses ---

    public double getAverageMood() { return avg(responses, SurveyResponse::getMood); }
    public double getAverageMotivation() { return avg(responses, SurveyResponse::getMotivation); }
    public double getAverageStress() { return avg(responses, SurveyResponse::getStress); }
    public double getAverageEnergy() { return avg(responses, SurveyResponse::getEnergy); }
    public double getAverageWorkLifeBalance() { return avg(responses, SurveyResponse::getWorkLifeBalance); }
    public double getAverageTeamConnection() { return avg(responses, SurveyResponse::getTeamConnection); }
    public double getAverageAccomplishment() { return avg(responses, SurveyResponse::getAccomplishment); }

    // --- averages per week ---

    public ArrayList<SurveyResponse> getResponsesForWeek(int week) {
        ArrayList<SurveyResponse> result = new ArrayList<>();
        for (SurveyResponse r : responses) if (r.getWeekNumber() == week) result.add(r);
        return result;
    }

    public double getAverageMoodForWeek(int w) { return avg(getResponsesForWeek(w), SurveyResponse::getMood); }
    public double getAverageMotivationForWeek(int w) { return avg(getResponsesForWeek(w), SurveyResponse::getMotivation); }
    public double getAverageStressForWeek(int w) { return avg(getResponsesForWeek(w), SurveyResponse::getStress); }
    public double getAverageEnergyForWeek(int w) { return avg(getResponsesForWeek(w), SurveyResponse::getEnergy); }
    public double getAverageWorkLifeBalanceForWeek(int w) { return avg(getResponsesForWeek(w), SurveyResponse::getWorkLifeBalance); }
    public double getAverageTeamConnectionForWeek(int w) { return avg(getResponsesForWeek(w), SurveyResponse::getTeamConnection); }
    public double getAverageAccomplishmentForWeek(int w) { return avg(getResponsesForWeek(w), SurveyResponse::getAccomplishment); }

    public double getResponseRate(int totalEmployees, int weekNumber) {
        long count = responses.stream().filter(r -> r.getWeekNumber() == weekNumber).count();
        return totalEmployees > 0 ? (count * 100.0 / totalEmployees) : 0;
    }

    public ArrayList<Integer> getHighRiskEmployeeIds() {
        ArrayList<Integer> result = new ArrayList<>();
        Map<Integer, SurveyResponse> latest = new HashMap<>();
        for (SurveyResponse r : responses) {
            if (!latest.containsKey(r.getEmployeeId()) ||
                r.getWeekNumber() > latest.get(r.getEmployeeId()).getWeekNumber()) {
                latest.put(r.getEmployeeId(), r);
            }
        }
        for (Map.Entry<Integer, SurveyResponse> e : latest.entrySet()) {
            if ("HIGH".equals(e.getValue().getRiskLevel())) result.add(e.getKey());
        }
        return result;
    }

    public int getLatestWeek() {
        return responses.stream().mapToInt(SurveyResponse::getWeekNumber).max().orElse(1);
    }

    public ArrayList<SurveyResponse> getAllResponses() { return responses; }

    private double avg(ArrayList<SurveyResponse> list, java.util.function.ToIntFunction<SurveyResponse> fn) {
        if (list.isEmpty()) return 0;
        return list.stream().mapToInt(fn).average().orElse(0);
    }
}
