package com.workmate.model;

/**
 * Manager extends Employee with an additional teamSize field.
 */
public class Manager extends Employee {
    private int teamSize;

    public Manager(int id, String name, String position, String department, String email, String phone, int teamSize) {
        super(id, name, position, department, email, phone);
        this.teamSize = teamSize;
    }

    public int getTeamSize() { return teamSize; }
    public void setTeamSize(int teamSize) { this.teamSize = teamSize; }

    @Override
    public String toString() {
        return super.toString() + " [Team size: " + teamSize + "]";
    }
}
