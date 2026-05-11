package com.workmate.service;

import com.workmate.config.AppConfig;

import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * Delivers notifications through two channels:
 * <ul>
 *   <li>OS system-tray bubbles (via {@link TrayIcon})</li>
 *   <li>Slack/Teams-compatible webhook POST (fired asynchronously)</li>
 * </ul>
 */
public class NotificationService {

    private static final Logger log = Logger.getLogger(NotificationService.class.getName());

    private TrayIcon trayIcon;

    /**
     * Injects the system-tray icon used for bubble notifications.
     * Call this after the tray icon has been registered.
     *
     * @param icon the registered {@link TrayIcon}, or null if the tray is unavailable
     */
    public void setTrayIcon(TrayIcon icon) {
        this.trayIcon = icon;
    }

    // ── tray notifications ────────────────────────────────────────────────────

    /**
     * Shows a tray-bubble reminder targeted at employees.
     *
     * @param title   bubble title
     * @param message bubble body text
     */
    public void showReminder(String title, String message) {
        showTrayBubble(title, message, TrayIcon.MessageType.INFO);
    }

    /**
     * Fires a HIGH-burnout-risk alert: shows a tray bubble and, if configured,
     * posts an asynchronous webhook message.
     *
     * @param employeeName display name of the at-risk employee
     * @param weekStart    ISO week string, e.g. "2026-W18"
     * @param score        composite wellbeing score (lower = worse)
     */
    public void alertHighRisk(String employeeName, String weekStart, double score) {
        String title   = "⚠ WorkMate — High Burnout Risk";
        String message = employeeName + " flagged HIGH risk (score " + String.format("%.1f", score) + ") · " + weekStart;
        showTrayBubble(title, message, TrayIcon.MessageType.WARNING);
        sendWebhookAsync(employeeName, weekStart, score);
    }

    // ── legacy static helper (kept for backward compat) ───────────────────────

    /**
     * Static helper used by callers that hold a direct {@link TrayIcon} reference.
     *
     * @param trayIcon target icon
     * @param title    bubble title
     * @param message  bubble body text
     */
    public static void showTrayNotification(TrayIcon trayIcon, String title, String message) {
        if (trayIcon != null) trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO);
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private void showTrayBubble(String title, String message, TrayIcon.MessageType type) {
        if (trayIcon != null) trayIcon.displayMessage(title, message, type);
        else log.info("NOTIFY [" + type + "] " + title + ": " + message);
    }

    /**
     * Posts a Slack/Teams-compatible payload to the configured webhook URL.
     * Runs in a daemon thread so it never blocks the HTTP response.
     *
     * @param employeeName employee display name
     * @param weekStart    ISO week string
     * @param score        composite wellbeing score
     */
    private void sendWebhookAsync(String employeeName, String weekStart, double score) {
        AppConfig cfg = AppConfig.get();
        if (!cfg.isWebhookEnabled()) return;

        String url     = cfg.getWebhookUrl();
        String payload = buildSlackPayload(employeeName, weekStart, score);

        Thread t = new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(5_000);
                conn.setReadTimeout(5_000);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.getBytes(StandardCharsets.UTF_8));
                }
                int status = conn.getResponseCode();
                if (status >= 200 && status < 300) {
                    log.info("Webhook delivered for " + employeeName + " (HTTP " + status + ")");
                } else {
                    log.warning("Webhook returned HTTP " + status + " for " + employeeName);
                }
                conn.disconnect();
            } catch (IOException e) {
                log.warning("Webhook delivery failed: " + e.getMessage());
            }
        }, "workmate-webhook");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Builds a Slack Block Kit JSON payload for a HIGH-risk alert.
     *
     * @param employeeName display name
     * @param weekStart    ISO week string
     * @param score        composite score
     * @return JSON string ready to POST
     */
    private String buildSlackPayload(String employeeName, String weekStart, double score) {
        String safeEmpName  = employeeName.replace("\"", "\\\"");
        String safeWeek     = weekStart.replace("\"", "\\\"");
        String scoreStr     = String.format("%.1f", score);
        return "{"
            + "\"text\":\"⚠️ WorkMate burnout alert\","
            + "\"blocks\":[{"
            + "\"type\":\"section\","
            + "\"text\":{"
            + "\"type\":\"mrkdwn\","
            + "\"text\":\"*⚠️ High Burnout Risk Detected*\\n"
            + "Employee: " + safeEmpName + "\\n"
            + "Score: " + scoreStr + " / 10\\n"
            + "Week: " + safeWeek + "\""
            + "}}]}";
    }
}
