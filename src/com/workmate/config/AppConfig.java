package com.workmate.config;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Loads and exposes application configuration from workmate.properties.
 * Falls back to sensible defaults when the file is absent or a key is missing.
 */
public class AppConfig {

    private static final Logger log = Logger.getLogger(AppConfig.class.getName());
    private static final String PROPS_FILE = "workmate.properties";

    private final Properties props;

    /** Singleton instance, initialised on first access. */
    private static AppConfig instance;

    private AppConfig() {
        props = new Properties();
        File f = new File(PROPS_FILE);
        if (f.exists()) {
            try (InputStream in = new FileInputStream(f)) {
                props.load(in);
                log.info("Loaded configuration from " + f.getAbsolutePath());
            } catch (IOException e) {
                log.warning("Could not read " + PROPS_FILE + ": " + e.getMessage());
            }
        } else {
            log.info("workmate.properties not found – using defaults");
        }
    }

    /** Returns the shared AppConfig instance. */
    public static synchronized AppConfig get() {
        if (instance == null) instance = new AppConfig();
        return instance;
    }

    // ── accessors ─────────────────────────────────────────────────────────────

    /** HTTP port the embedded server listens on. */
    public int getPort() {
        return getInt("server.port", 8765);
    }

    /** Bind address for the HTTP server. */
    public String getBindAddress() {
        return getString("server.bind", "0.0.0.0");
    }

    /**
     * Days of the week (1 = Monday … 7 = Sunday, ISO-8601) on which
     * the check-in form is open.
     */
    public Set<Integer> getCheckinDays() {
        String raw = getString("checkin.days", "4,5");
        Set<Integer> days = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            try { days.add(Integer.parseInt(part.trim())); } catch (NumberFormatException ignored) {}
        }
        return days;
    }

    /** Hour (0-23) from which the check-in window opens. */
    public int getCheckinHourStart() {
        return getInt("checkin.hour.start", 0);
    }

    /** Hour (0-23, inclusive) at which the check-in window closes. */
    public int getCheckinHourEnd() {
        return getInt("checkin.hour.end", 23);
    }

    /** Duration in hours for which a manager session token remains valid. */
    public int getAdminSessionHours() {
        return getInt("admin.session.hours", 8);
    }

    /** Webhook URL for burnout-risk notifications (empty = disabled). */
    public String getWebhookUrl() {
        return getString("notification.webhook.url", "");
    }

    /** Whether webhook notifications are enabled. */
    public boolean isWebhookEnabled() {
        return getBool("notification.webhook.enabled", false)
               && !getWebhookUrl().isBlank();
    }

    /** Composite score below which an employee is flagged as high-risk. */
    public double getBurnoutAlertThreshold() {
        return getDouble("burnout.alert.threshold", 4.5);
    }

    // ── internal helpers ──────────────────────────────────────────────────────

    private String getString(String key, String def) {
        String v = props.getProperty(key);
        return (v != null && !v.isBlank()) ? v.trim() : def;
    }

    private int getInt(String key, int def) {
        try { return Integer.parseInt(props.getProperty(key, "").trim()); }
        catch (NumberFormatException e) { return def; }
    }

    private double getDouble(String key, double def) {
        try { return Double.parseDouble(props.getProperty(key, "").trim()); }
        catch (NumberFormatException e) { return def; }
    }

    private boolean getBool(String key, boolean def) {
        String v = props.getProperty(key, "").trim();
        if (v.isEmpty()) return def;
        return "true".equalsIgnoreCase(v) || "1".equals(v) || "yes".equalsIgnoreCase(v);
    }
}
