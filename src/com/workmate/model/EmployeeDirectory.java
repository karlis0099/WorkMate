package com.workmate.model;

import java.util.*;
import java.util.logging.Logger;

/**
 * In-memory employee registry with search and sort operations.
 *
 * <p>Used during sample-data seeding and kept for algorithm-demonstration purposes.
 * In production the web layer reads directly from {@code DataStore}.</p>
 */
public class EmployeeDirectory {

    private static final Logger log = Logger.getLogger(EmployeeDirectory.class.getName());

    private final List<Employee> employees;

    /** Creates an empty directory. */
    public EmployeeDirectory() {
        employees = new ArrayList<>();
    }

    // ── mutation ──────────────────────────────────────────────────────────────

    /**
     * Appends an employee to the directory.
     *
     * @param e the employee to add
     */
    public void addEmployee(Employee e) { employees.add(e); }

    /**
     * Removes the employee with the given id, if present.
     *
     * @param id the employee id to remove
     */
    public void removeEmployee(int id) { employees.removeIf(e -> e.getId() == id); }

    // ── search ────────────────────────────────────────────────────────────────

    /**
     * Linear search by name — O(n).
     *
     * @param name the name to match (case-insensitive)
     * @return the matching Employee, or null
     */
    public Employee findByName(String name) {
        for (Employee e : employees)
            if (e.getName().equalsIgnoreCase(name)) return e;
        return null;
    }

    /**
     * Linear search by id — O(n).
     *
     * @param id the employee id
     * @return the matching Employee, or null
     */
    public Employee findById(int id) {
        for (Employee e : employees)
            if (e.getId() == id) return e;
        return null;
    }

    /**
     * Filters employees by department — O(n).
     *
     * @param dept department name (case-insensitive)
     * @return a new list containing only matching employees
     */
    public List<Employee> findByDepartment(String dept) {
        List<Employee> result = new ArrayList<>();
        for (Employee e : employees)
            if (e.getDepartment().equalsIgnoreCase(dept)) result.add(e);
        return result;
    }

    // ── sort ──────────────────────────────────────────────────────────────────

    /**
     * Sorts the directory alphabetically by employee name.
     *
     * <p>Bubble sort replaced with O(n log n) merge sort via Collections.sort().</p>
     */
    public void sortByName() {
        // Bubble sort replaced with O(n log n) merge sort via Collections.sort()
        Collections.sort(employees, Comparator.comparing(Employee::getName));
    }

    /**
     * Binary search on a name-sorted list — O(log n).
     * Call {@link #sortByName()} before using this method.
     *
     * @param name the name to search for (case-insensitive comparison)
     * @return the matching Employee, or null
     */
    public Employee binarySearch(String name) {
        int left = 0, right = employees.size() - 1;
        while (left <= right) {
            int mid = (left + right) / 2;
            int cmp = employees.get(mid).getName().compareToIgnoreCase(name);
            if (cmp == 0)      return employees.get(mid);
            else if (cmp < 0) left = mid + 1;
            else              right = mid - 1;
        }
        return null;
    }

    // ── accessors ─────────────────────────────────────────────────────────────

    /** Returns all employees in insertion order. */
    public ArrayList<Employee> getAllEmployees() { return new ArrayList<>(employees); }

    /** Returns the number of employees in the directory. */
    public int size() { return employees.size(); }

    /**
     * Computes the next available employee id (max existing id + 1).
     *
     * @return the next id
     */
    public int getNextId() {
        return employees.stream().mapToInt(Employee::getId).max().orElse(0) + 1;
    }
}
