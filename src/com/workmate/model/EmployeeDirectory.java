package com.workmate.model;

import java.util.ArrayList;

/**
 * Manages the list of all employees with search and sort operations.
 */
public class EmployeeDirectory {
    private ArrayList<Employee> employees;

    public EmployeeDirectory() {
        employees = new ArrayList<>();
    }

    public void addEmployee(Employee e) {
        employees.add(e);
    }

    public void removeEmployee(int id) {
        employees.removeIf(e -> e.getId() == id);
    }

    // linear search by name - O(n)
    public Employee findByName(String name) {
        for (Employee e : employees) {
            if (e.getName().equalsIgnoreCase(name)) return e;
        }
        return null;
    }

    // find by id - O(n)
    public Employee findById(int id) {
        for (Employee e : employees) {
            if (e.getId() == id) return e;
        }
        return null;
    }

    // filter by department - O(n)
    public ArrayList<Employee> findByDepartment(String dept) {
        ArrayList<Employee> result = new ArrayList<>();
        for (Employee e : employees) {
            if (e.getDepartment().equalsIgnoreCase(dept)) result.add(e);
        }
        return result;
    }

    // bubble sort by name - O(n^2), fine for small teams
    public void sortByName() {
        int n = employees.size();
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - i - 1; j++) {
                if (employees.get(j).getName().compareTo(employees.get(j + 1).getName()) > 0) {
                    Employee temp = employees.get(j);
                    employees.set(j, employees.get(j + 1));
                    employees.set(j + 1, temp);
                }
            }
        }
    }

    // binary search on sorted list - O(log n)
    // call sortByName() before using this
    public Employee binarySearch(String name) {
        int left = 0, right = employees.size() - 1;
        while (left <= right) {
            int mid = (left + right) / 2;
            int cmp = employees.get(mid).getName().compareToIgnoreCase(name);
            if (cmp == 0) return employees.get(mid);
            else if (cmp < 0) left = mid + 1;
            else right = mid - 1;
        }
        return null;
    }

    public ArrayList<Employee> getAllEmployees() { return employees; }

    public int size() { return employees.size(); }

    // get next available id
    public int getNextId() {
        return employees.stream().mapToInt(Employee::getId).max().orElse(0) + 1;
    }
}
