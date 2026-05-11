package com.workmate.service;

import com.workmate.model.*;

import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * SQLite-backed persistence layer.
 * Replaces the in-memory ArrayList / Stack / Queue structures.
 * The database file is created automatically on first run.
 */
public class DataStore {

    private static final Logger log = Logger.getLogger(DataStore.class.getName());
    private static final String DB_FILE = "workmate.db";

    private final Connection conn;

    // ── lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Opens (or creates) the SQLite database and ensures the schema exists.
     *
     * @throws RuntimeException if the JDBC driver cannot be loaded or the DB cannot be opened
     */
    public DataStore() {
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection("jdbc:sqlite:" + DB_FILE);
            conn.setAutoCommit(true);
            initSchema();
            log.info("DataStore connected to " + DB_FILE);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("SQLite JDBC driver not found. Add lib/sqlite-jdbc.jar to classpath.", e);
        } catch (SQLException e) {
            throw new RuntimeException("Cannot open database: " + e.getMessage(), e);
        }
    }

    /** Closes the underlying JDBC connection. Called on graceful shutdown. */
    public void close() {
        try { if (conn != null && !conn.isClosed()) conn.close(); }
        catch (SQLException e) { log.warning("Error closing DB: " + e.getMessage()); }
    }

    // ── schema ────────────────────────────────────────────────────────────────

    private void initSchema() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS employees (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    name       TEXT NOT NULL,
                    position   TEXT,
                    department TEXT,
                    email      TEXT,
                    phone      TEXT,
                    is_manager INTEGER DEFAULT 0,
                    team_size  INTEGER DEFAULT 0,
                    pin_hash   TEXT,
                    pin_salt   TEXT,
                    created_at TEXT DEFAULT (datetime('now'))
                )""");
            // migrate existing DB — add PIN columns if absent
            try { st.executeUpdate("ALTER TABLE employees ADD COLUMN pin_hash TEXT"); } catch (SQLException ignored) {}
            try { st.executeUpdate("ALTER TABLE employees ADD COLUMN pin_salt TEXT"); } catch (SQLException ignored) {}

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS survey_responses (
                    id                INTEGER PRIMARY KEY AUTOINCREMENT,
                    employee_id       INTEGER NOT NULL,
                    week_start        TEXT NOT NULL,
                    mood              INTEGER,
                    energy            INTEGER,
                    motivation        INTEGER,
                    stress            INTEGER,
                    work_life_balance INTEGER,
                    team_connection   INTEGER,
                    accomplishment    INTEGER,
                    workload_feel     TEXT,
                    team_communication TEXT,
                    went_well         TEXT,
                    challenges        TEXT,
                    support_needed    TEXT,
                    issues            TEXT,
                    risk_level        TEXT,
                    composite_score   REAL,
                    submitted_at      TEXT DEFAULT (datetime('now')),
                    FOREIGN KEY (employee_id) REFERENCES employees(id)
                )""");

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS app_config (
                    key   TEXT PRIMARY KEY,
                    value TEXT
                )""");
        }
        log.info("Database schema initialised");
    }

    // ── employee CRUD ─────────────────────────────────────────────────────────

    /**
     * Inserts an employee row and returns the auto-assigned id.
     *
     * @param e employee to persist (id field is ignored; DB assigns it)
     * @return the generated database id
     * @throws RuntimeException on SQL error
     */
    public int insertEmployee(Employee e) {
        String sql = "INSERT INTO employees (name,position,department,email,phone,is_manager,team_size) VALUES (?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, e.getName());
            ps.setString(2, e.getPosition());
            ps.setString(3, e.getDepartment());
            ps.setString(4, e.getEmail());
            ps.setString(5, e.getPhone());
            ps.setInt(6, e instanceof Manager ? 1 : 0);
            ps.setInt(7, e instanceof Manager ? ((Manager) e).getTeamSize() : 0);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("insertEmployee failed: " + ex.getMessage(), ex);
        }
        throw new RuntimeException("insertEmployee: no generated key returned");
    }

    /**
     * Returns all employees ordered by name.
     *
     * @return sorted list of Employee/Manager objects
     */
    public List<Employee> getAllEmployees() {
        List<Employee> list = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM employees ORDER BY name")) {
            while (rs.next()) list.add(rowToEmployee(rs));
        } catch (SQLException e) {
            log.warning("getAllEmployees error: " + e.getMessage());
        }
        return list;
    }

    /**
     * Finds a single employee by id.
     *
     * @param id the employee's database id
     * @return the Employee, or null if not found
     */
    public Employee findById(int id) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM employees WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rowToEmployee(rs);
            }
        } catch (SQLException e) {
            log.warning("findById error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Updates mutable fields of an existing employee record.
     *
     * @param e employee with updated fields; id must match an existing row
     */
    public void updateEmployee(Employee e) {
        String sql = "UPDATE employees SET name=?,position=?,department=?,email=?,phone=? WHERE id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, e.getName());
            ps.setString(2, e.getPosition());
            ps.setString(3, e.getDepartment());
            ps.setString(4, e.getEmail());
            ps.setString(5, e.getPhone());
            ps.setInt(6, e.getId());
            ps.executeUpdate();
        } catch (SQLException ex) {
            log.warning("updateEmployee error: " + ex.getMessage());
        }
    }

    /**
     * Stores a hashed PIN for an employee.
     *
     * @param employeeId the employee's id
     * @param pinHash    SHA-256 hex hash of the PIN
     * @param pinSalt    base-64 encoded random salt
     */
    public void setEmployeePin(int employeeId, String pinHash, String pinSalt) {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE employees SET pin_hash=?, pin_salt=? WHERE id=?")) {
            ps.setString(1, pinHash);
            ps.setString(2, pinSalt);
            ps.setInt(3, employeeId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warning("setEmployeePin error: " + e.getMessage());
        }
    }

    /**
     * Returns the stored PIN hash and salt for an employee.
     *
     * @param employeeId the employee's id
     * @return String array [hash, salt], or null if not set
     */
    public String[] getEmployeePin(int employeeId) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT pin_hash, pin_salt FROM employees WHERE id=?")) {
            ps.setInt(1, employeeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String hash = rs.getString(1);
                    String salt = rs.getString(2);
                    if (hash != null && salt != null) return new String[]{hash, salt};
                }
            }
        } catch (SQLException e) {
            log.warning("getEmployeePin error: " + e.getMessage());
        }
        return null;
    }

    /** Returns the number of employee rows in the database. */
    public int countEmployees() {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM employees")) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            log.warning("countEmployees error: " + e.getMessage());
        }
        return 0;
    }

    // ── survey CRUD ───────────────────────────────────────────────────────────

    /**
     * Persists a survey response and returns its generated id.
     *
     * @param r the response to save
     * @return the auto-assigned database id
     */
    public int insertResponse(SurveyResponse r) {
        String sql = """
            INSERT INTO survey_responses
              (employee_id,week_start,mood,energy,motivation,stress,
               work_life_balance,team_connection,accomplishment,
               workload_feel,team_communication,went_well,challenges,
               support_needed,issues,risk_level,composite_score)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, r.getEmployeeId());
            ps.setString(2, r.getWeekStart());
            ps.setInt(3, r.getMood());
            ps.setInt(4, r.getEnergy());
            ps.setInt(5, r.getMotivation());
            ps.setInt(6, r.getStress());
            ps.setInt(7, r.getWorkLifeBalance());
            ps.setInt(8, r.getTeamConnection());
            ps.setInt(9, r.getAccomplishment());
            ps.setString(10, r.getWorkloadFeel());
            ps.setString(11, r.getTeamCommunication());
            ps.setString(12, r.getWentWell());
            ps.setString(13, r.getChallenges());
            ps.setString(14, r.getSupport());
            ps.setString(15, r.getIssues());
            ps.setString(16, r.getRiskLevel());
            ps.setDouble(17, r.getCompositeScore());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("insertResponse failed: " + e.getMessage(), e);
        }
        return -1;
    }

    /**
     * Returns survey responses, optionally filtered by week ISO string and/or employee id.
     *
     * @param weekStart ISO week string (e.g. "2026-W18"), or null for all weeks
     * @param employeeId positive value to filter by employee, or -1 for all
     * @return matching responses ordered by submitted_at descending
     */
    public List<SurveyResponse> getResponses(String weekStart, int employeeId) {
        StringBuilder sql = new StringBuilder("SELECT * FROM survey_responses WHERE 1=1");
        if (weekStart != null && !weekStart.isBlank()) sql.append(" AND week_start = ?");
        if (employeeId > 0) sql.append(" AND employee_id = ?");
        sql.append(" ORDER BY submitted_at DESC");

        List<SurveyResponse> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            if (weekStart != null && !weekStart.isBlank()) ps.setString(idx++, weekStart);
            if (employeeId > 0) ps.setInt(idx, employeeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(rowToResponse(rs));
            }
        } catch (SQLException e) {
            log.warning("getResponses error: " + e.getMessage());
        }
        return list;
    }

    /** Returns all responses (no filter). */
    public List<SurveyResponse> getAllResponses() {
        return getResponses(null, -1);
    }

    // ── app_config helpers ────────────────────────────────────────────────────

    /**
     * Stores or updates a key/value pair in app_config.
     *
     * @param key   configuration key
     * @param value configuration value
     */
    public void setConfig(String key, String value) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO app_config(key,value) VALUES (?,?)")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warning("setConfig error: " + e.getMessage());
        }
    }

    /**
     * Retrieves a value from app_config.
     *
     * @param key configuration key
     * @param def default value if key is absent
     * @return stored value, or def
     */
    public String getConfig(String key, String def) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT value FROM app_config WHERE key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (SQLException e) {
            log.warning("getConfig error: " + e.getMessage());
        }
        return def;
    }

    // ── row mappers ───────────────────────────────────────────────────────────

    private Employee rowToEmployee(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String name = rs.getString("name");
        String pos  = rs.getString("position");
        String dept = rs.getString("department");
        String mail = rs.getString("email");
        String ph   = rs.getString("phone");
        if (rs.getInt("is_manager") == 1) {
            return new Manager(id, name, pos, dept, mail, ph, rs.getInt("team_size"));
        }
        return new Employee(id, name, pos, dept, mail, ph);
    }

    private SurveyResponse rowToResponse(ResultSet rs) throws SQLException {
        return new SurveyResponse(
            rs.getInt("employee_id"),
            rs.getString("week_start"),
            rs.getInt("mood"),
            rs.getInt("motivation"),
            rs.getInt("stress"),
            rs.getInt("energy"),
            rs.getInt("work_life_balance"),
            rs.getInt("team_connection"),
            rs.getInt("accomplishment"),
            rs.getString("issues"),
            rs.getString("went_well"),
            rs.getString("challenges"),
            rs.getString("support_needed"),
            rs.getString("workload_feel"),
            rs.getString("team_communication"),
            rs.getString("submitted_at")
        );
    }
}
