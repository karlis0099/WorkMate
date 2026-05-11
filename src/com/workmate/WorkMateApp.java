package com.workmate;

import com.workmate.config.AppConfig;
import com.workmate.model.*;
import com.workmate.service.*;
import com.workmate.util.LogoDrawer;

import java.awt.*;
import java.net.URI;
import java.util.Random;
import java.util.logging.*;

/**
 * Application entry point.
 *
 * <p>Startup sequence:
 * <ol>
 *   <li>Load configuration from workmate.properties</li>
 *   <li>Open (or create) the SQLite database</li>
 *   <li>Seed sample data on first run</li>
 *   <li>Start the embedded HTTP server</li>
 *   <li>Register OS system-tray icon</li>
 *   <li>Open the default browser</li>
 * </ol>
 * Graceful shutdown is handled via a JVM shutdown hook that closes the DB.</p>
 */
public class WorkMateApp {

    private static final Logger log = Logger.getLogger(WorkMateApp.class.getName());

    private DataStore   dataStore;
    private TrayIcon    trayIcon;

    /**
     * Application entry point.
     *
     * @param args command-line arguments (unused)
     */
    public static void main(String[] args) {
        // suppress macOS dock icon so only the tray icon is visible
        System.setProperty("apple.awt.UIElement", "true");
        configureLogging();
        new WorkMateApp().start();
    }

    /** Configures java.util.logging for a clean console output format. */
    private static void configureLogging() {
        Logger root = Logger.getLogger("");
        for (Handler h : root.getHandlers()) root.removeHandler(h);
        ConsoleHandler ch = new ConsoleHandler();
        ch.setFormatter(new SimpleFormatter() {
            @Override
            public synchronized String format(LogRecord lr) {
                return String.format("[WorkMate] %s %s: %s%n",
                    lr.getLevel(), lr.getLoggerName().replaceFirst(".*\\.", ""), lr.getMessage());
            }
        });
        ch.setLevel(Level.ALL);
        root.addHandler(ch);
        root.setLevel(Level.INFO);
    }

    /** Runs the full startup sequence. */
    private void start() {
        AppConfig cfg = AppConfig.get();
        log.info("Starting WorkMate on port " + cfg.getPort());

        // open database — creates workmate.db if absent
        dataStore = new DataStore();
        registerShutdownHook();

        // seed sample employees on very first run
        if (dataStore.countEmployees() == 0) {
            seedSampleData();
        }

        AuthService        authService  = new AuthService(dataStore);
        NotificationService notifService = new NotificationService();
        setupSystemTray(notifService);
        notifService.setTrayIcon(trayIcon);

        new WebServer(cfg.getPort(), dataStore, authService, notifService).start();

        setupWeeklyReminderTimer(notifService);
        openBrowser(cfg.getPort());
    }

    // ── sample data ───────────────────────────────────────────────────────────

    /**
     * Inserts demo employees and four weeks of random survey responses.
     * Called only when the database is empty (first run).
     */
    private void seedSampleData() {
        log.info("Seeding sample data…");

        Employee[] emps = {
            new Employee(0, "Anna Bērziņa",    "HR Manager",           "HR",         "anna.berzina@company.lv",    "+371 20000001"),
            new Employee(0, "Jānis Kalniņš",   "Software Developer",   "IT",         "janis.kalnins@company.lv",   "+371 20000002"),
            new Employee(0, "Marta Ozola",     "Marketing Specialist", "Marketing",  "marta.ozola@company.lv",     "+371 20000003"),
            new Employee(0, "Pēteris Liepiņš", "CFO",                  "Finance",    "peteris.liepins@company.lv", "+371 20000004"),
            new Employee(0, "Laura Zariņa",    "IT Administrator",     "IT",         "laura.zarina@company.lv",    "+371 20000005"),
            new Employee(0, "Andris Krūmiņš",  "Sales Manager",        "Sales",      "andris.krumins@company.lv",  "+371 20000006"),
            new Employee(0, "Ilze Āboliņa",    "Accountant",           "Finance",    "ilze.abolina@company.lv",    "+371 20000007"),
            new Employee(0, "Kārlis Dūmiņš",   "Junior Developer",     "IT",         "karlis.dumins@company.lv",   "+371 20000008"),
            new Employee(0, "Signe Vītoliņa",  "HR Specialist",        "HR",         "signe.vitolina@company.lv",  "+371 20000009"),
            new Manager(0,  "Toms Ādamsons",   "CEO",                  "Management", "toms.adamsons@company.lv",   "+371 20000010", 9)
        };

        for (Employee e : emps) dataStore.insertEmployee(e);

        java.util.List<Employee> all = dataStore.getAllEmployees();
        String[] workloads = { "light", "manageable", "manageable", "heavy", "overwhelming" };
        String[] comms     = { "excellent", "good", "good", "average", "poor" };
        Random rand = new Random(2024L);

        // generate 4 weeks of data using ISO week strings relative to now
        java.time.LocalDate today = java.time.LocalDate.now();
        for (int wk = 4; wk >= 1; wk--) {
            java.time.LocalDate weekDate = today.minusWeeks(wk);
            int isoWeek = weekDate.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
            int isoYear = weekDate.get(java.time.temporal.WeekFields.ISO.weekBasedYear());
            String weekStart = String.format("%d-W%02d", isoYear, isoWeek);

            for (Employee emp : all) {
                dataStore.insertResponse(new SurveyResponse(
                    emp.getId(), weekStart,
                    3 + rand.nextInt(7),  // mood
                    3 + rand.nextInt(7),  // motivation
                    2 + rand.nextInt(7),  // stress
                    3 + rand.nextInt(7),  // energy
                    3 + rand.nextInt(6),  // wlb
                    4 + rand.nextInt(6),  // teamConnection
                    3 + rand.nextInt(7),  // accomplishment
                    "",                   // issues
                    "",                   // wentWell
                    "",                   // challenges
                    "",                   // support
                    workloads[rand.nextInt(workloads.length)],
                    comms[rand.nextInt(comms.length)],
                    null
                ));
            }
        }
        // set default PIN "1234" for all seeded employees
        AuthService tempAuth = new AuthService(dataStore);
        for (Employee e : all) tempAuth.setEmployeePin(e.getId(), "1234");

        log.info("Sample data seeded: " + all.size() + " employees, 4 weeks of responses (default PIN: 1234)");
    }

    // ── tray ──────────────────────────────────────────────────────────────────

    /**
     * Registers the system-tray icon with Open and Exit menu items.
     *
     * @param notifService the notification service that receives a tray reference
     */
    private void setupSystemTray(NotificationService notifService) {
        if (!SystemTray.isSupported()) {
            log.warning("System tray not supported on this platform");
            return;
        }
        Image icon = LogoDrawer.createTrayImage();
        PopupMenu popup = new PopupMenu();

        int port = AppConfig.get().getPort();
        MenuItem openItem = new MenuItem("Open WorkMate");
        openItem.addActionListener(e -> openBrowser(port));

        MenuItem exitItem = new MenuItem("Exit WorkMate");
        exitItem.addActionListener(e -> System.exit(0));

        popup.add(openItem);
        popup.addSeparator();
        popup.add(exitItem);

        trayIcon = new TrayIcon(icon, "WorkMate", popup);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> openBrowser(port));

        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            log.warning("Could not add tray icon: " + e.getMessage());
        }
    }

    // ── browser ───────────────────────────────────────────────────────────────

    /**
     * Opens the default system browser at the WorkMate URL.
     *
     * @param port the HTTP port to connect to
     */
    private void openBrowser(int port) {
        try {
            Desktop.getDesktop().browse(new URI("http://localhost:" + port));
        } catch (Exception e) {
            log.info("Open your browser at http://localhost:" + port);
        }
    }

    // ── timers ────────────────────────────────────────────────────────────────

    /**
     * Schedules a repeating tray notification that reminds employees to check in.
     * Fires every 30 seconds in demo mode (simulating a Monday morning reminder).
     *
     * @param notifService the service used to show the reminder
     */
    private void setupWeeklyReminderTimer(NotificationService notifService) {
        java.util.Timer timer = new java.util.Timer(true);
        timer.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                notifService.showReminder(
                    "Time for your weekly check-in!",
                    "It only takes 3 minutes. Open WorkMate to get started.");
            }
        }, 30_000, 30_000);
    }

    // ── shutdown ──────────────────────────────────────────────────────────────

    /** Registers a JVM shutdown hook to close the database connection cleanly. */
    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down — closing database connection");
            if (dataStore != null) dataStore.close();
        }, "workmate-shutdown"));
    }
}
