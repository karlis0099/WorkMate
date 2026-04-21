package com.workmate.model;

/**
 * Base class representing an employee in the system.
 */
public class Employee {
    private int id;
    private String name;
    private String position;
    private String department;
    private String email;
    private String phone;

    public Employee(int id, String name, String position, String department, String email, String phone) {
        this.id = id;
        this.name = name;
        this.position = position;
        this.department = department;
        this.email = email;
        this.phone = phone;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getPosition() { return position; }
    public String getDepartment() { return department; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }

    public void setName(String name) { this.name = name; }
    public void setPosition(String position) { this.position = position; }
    public void setDepartment(String department) { this.department = department; }
    public void setEmail(String email) { this.email = email; }
    public void setPhone(String phone) { this.phone = phone; }

    // extract initials from full name
    public String getInitials() {
        String[] parts = name.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) sb.append(part.charAt(0));
        }
        return sb.toString().toUpperCase();
    }

    @Override
    public String toString() {
        return name + " - " + position + " (" + department + ")";
    }
}
