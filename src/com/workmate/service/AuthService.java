package com.workmate.service;

import com.workmate.config.AppConfig;
import com.workmate.model.Employee;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Handles password hashing (SHA-256 + salt) and session token management.
 *
 * <p>Manager accounts authenticate with a hashed password stored in app_config.
 * Employee accounts are password-free — selecting a name is sufficient (demo mode).
 * Sessions expire after the configured number of hours.</p>
 */
public class AuthService {

    private static final Logger log = Logger.getLogger(AuthService.class.getName());
    private static final String CONFIG_KEY_HASH = "admin.password.hash";
    private static final String CONFIG_KEY_SALT = "admin.password.salt";
    private static final String ROLE_MANAGER  = "MANAGER";
    private static final String ROLE_EMPLOYEE = "EMPLOYEE";

    private final DataStore dataStore;
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    /** Creates an AuthService backed by the supplied DataStore. */
    public AuthService(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    // ── password management ───────────────────────────────────────────────────

    /**
     * Returns true if a manager password has been set in the database.
     *
     * @return true when the hash/salt records exist
     */
    public boolean isPasswordSet() {
        return !dataStore.getConfig(CONFIG_KEY_HASH, "").isBlank();
    }

    /**
     * Hashes and stores the manager password using SHA-256 + random salt.
     *
     * @param plainPassword the raw password chosen during first-run setup
     */
    public void setManagerPassword(String plainPassword) {
        String salt = generateSalt();
        String hash = hash(plainPassword, salt);
        dataStore.setConfig(CONFIG_KEY_SALT, salt);
        dataStore.setConfig(CONFIG_KEY_HASH, hash);
        log.info("Manager password updated");
    }

    /**
     * Verifies whether the supplied plain-text password matches the stored hash.
     *
     * @param plainPassword candidate password
     * @return true if the password is correct
     */
    public boolean verifyManagerPassword(String plainPassword) {
        String salt = dataStore.getConfig(CONFIG_KEY_SALT, "");
        String hash = dataStore.getConfig(CONFIG_KEY_HASH, "");
        if (salt.isBlank() || hash.isBlank()) return false;
        return hash(plainPassword, salt).equals(hash);
    }

    // ── session management ────────────────────────────────────────────────────

    /**
     * Creates and returns a manager session token after validating the password.
     *
     * @param plainPassword the supplied password
     * @return session token string, or null if authentication failed
     */
    public String loginManager(String plainPassword) {
        if (!verifyManagerPassword(plainPassword)) return null;
        String token = UUID.randomUUID().toString();
        long expiresAt = Instant.now()
            .plusSeconds(AppConfig.get().getAdminSessionHours() * 3600L)
            .toEpochMilli();
        sessions.put(token, new SessionInfo(-1, ROLE_MANAGER, "Manager", expiresAt));
        log.info("Manager session created");
        return token;
    }

    // ── employee PIN management ───────────────────────────────────────────────

    /**
     * Hashes and stores a PIN for the given employee.
     *
     * @param employeeId the employee's database id
     * @param plainPin   the raw PIN string
     */
    public void setEmployeePin(int employeeId, String plainPin) {
        String salt = generateSalt();
        String hash = hash(plainPin, salt);
        dataStore.setEmployeePin(employeeId, hash, salt);
    }

    /**
     * Verifies an employee's PIN against the stored hash.
     *
     * @param employeeId the employee's id
     * @param plainPin   the candidate PIN
     * @return true if correct, false if wrong or not set
     */
    public boolean verifyEmployeePin(int employeeId, String plainPin) {
        String[] stored = dataStore.getEmployeePin(employeeId);
        if (stored == null) {
            // no PIN set yet — accept "1234" and persist it so subsequent logins work
            if ("1234".equals(plainPin)) {
                setEmployeePin(employeeId, "1234");
                return true;
            }
            return false;
        }
        return hash(plainPin, stored[1]).equals(stored[0]);
    }

    /**
     * Returns true when the employee has a PIN configured in the database.
     *
     * @param employeeId the employee's id
     * @return true if a PIN hash exists
     */
    public boolean employeeHasPin(int employeeId) {
        return dataStore.getEmployeePin(employeeId) != null;
    }

    /**
     * Creates and returns a session token for an employee after PIN verification.
     *
     * @param employee the authenticated employee
     * @return session token string
     */
    public String loginEmployee(Employee employee) {
        String token = UUID.randomUUID().toString();
        long expiresAt = Instant.now()
            .plusSeconds(AppConfig.get().getAdminSessionHours() * 3600L)
            .toEpochMilli();
        sessions.put(token, new SessionInfo(employee.getId(), ROLE_EMPLOYEE,
                                            employee.getName(), expiresAt));
        return token;
    }

    /**
     * Invalidates a session token.
     *
     * @param token the token to revoke
     */
    public void logout(String token) {
        sessions.remove(token);
    }

    /**
     * Resolves a Bearer token to its SessionInfo, purging expired sessions.
     *
     * @param token the Bearer token value (without "Bearer " prefix)
     * @return the SessionInfo, or null if invalid/expired
     */
    public SessionInfo getSession(String token) {
        if (token == null || token.isBlank()) return null;
        SessionInfo info = sessions.get(token);
        if (info == null) return null;
        if (Instant.now().toEpochMilli() > info.expiresAt) {
            sessions.remove(token);
            return null;
        }
        return info;
    }

    /**
     * Extracts the Bearer token from an Authorization header value.
     *
     * @param authHeader value of the Authorization header, e.g. "Bearer abc123"
     * @return the raw token, or null if the header is absent or malformed
     */
    public static String extractBearer(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        return authHeader.substring(7).trim();
    }

    // ── convenience ───────────────────────────────────────────────────────────

    /** Returns true when the session belongs to a manager. */
    public boolean isManager(SessionInfo s) {
        return s != null && ROLE_MANAGER.equals(s.role);
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private String generateSalt() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private String hash(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] digest = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    // ── inner types ───────────────────────────────────────────────────────────

    /** Immutable record representing an authenticated session. */
    public static class SessionInfo {
        /** The employee's database id, or -1 for manager sessions. */
        public final int    employeeId;
        /** "MANAGER" or "EMPLOYEE". */
        public final String role;
        /** Display name of the authenticated user. */
        public final String name;
        /** Unix epoch milliseconds at which this session expires. */
        public final long   expiresAt;

        SessionInfo(int employeeId, String role, String name, long expiresAt) {
            this.employeeId = employeeId;
            this.role       = role;
            this.name       = name;
            this.expiresAt  = expiresAt;
        }
    }
}
