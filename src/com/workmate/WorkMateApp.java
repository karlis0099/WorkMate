package com.workmate;

import com.workmate.model.*;
import com.workmate.service.NotificationService;
import com.workmate.service.WebServer;
import com.workmate.util.LogoDrawer;

import java.awt.*;
import java.awt.event.*;
import java.net.URI;
import java.util.Random;

public class WorkMateApp {

    private EmployeeDirectory directory;
    private SurveyManager surveyManager;
    private TrayIcon trayIcon;
    static final int PORT = 8765;

    public static void main(String[] args) {
        System.setProperty("apple.awt.UIElement", "true");
        new WorkMateApp().start();
    }

    public void start() {
        initSampleData();
        new WebServer(PORT, directory, surveyManager).start();
        setupSystemTray();
        setupMondayReminderTimer();
        openBrowser();
    }

    private void openBrowser() {
        try {
            Desktop.getDesktop().browse(new URI("http://localhost:" + PORT));
        } catch (Exception e) {
            System.out.println("Open browser: http://localhost:" + PORT);
        }
    }

    private void initSampleData() {
        directory = new EmployeeDirectory();
        surveyManager = new SurveyManager();

        directory.addEmployee(new Employee(1,  "Anna Bērziņa",    "HR Manager",           "HR",         "anna.berzina@company.lv",    "+371 20000001"));
        directory.addEmployee(new Employee(2,  "Jānis Kalniņš",   "Software Developer",   "IT",         "janis.kalnins@company.lv",   "+371 20000002"));
        directory.addEmployee(new Employee(3,  "Marta Ozola",     "Marketing Specialist", "Marketing",  "marta.ozola@company.lv",     "+371 20000003"));
        directory.addEmployee(new Employee(4,  "Pēteris Liepiņš", "CFO",                  "Finance",    "peteris.liepins@company.lv", "+371 20000004"));
        directory.addEmployee(new Employee(5,  "Laura Zariņa",    "IT Administrator",     "IT",         "laura.zarina@company.lv",    "+371 20000005"));
        directory.addEmployee(new Employee(6,  "Andris Krūmiņš",  "Sales Manager",        "Sales",      "andris.krumins@company.lv",  "+371 20000006"));
        directory.addEmployee(new Employee(7,  "Ilze Āboliņa",    "Accountant",           "Finance",    "ilze.abolina@company.lv",    "+371 20000007"));
        directory.addEmployee(new Employee(8,  "Kārlis Dūmiņš",   "Junior Developer",     "IT",         "karlis.dumins@company.lv",   "+371 20000008"));
        directory.addEmployee(new Employee(9,  "Signe Vītoliņa",  "HR Specialist",        "HR",         "signe.vitolina@company.lv",  "+371 20000009"));
        directory.addEmployee(new Manager(10,  "Toms Ādamsons",   "CEO",                  "Management", "toms.adamsons@company.lv",   "+371 20000010", 9));

        String[] workloads = {"light", "manageable", "manageable", "heavy", "overwhelming"};
        Random rand = new Random(2024);

        for (int week = 1; week <= 4; week++) {
            for (Employee emp : directory.getAllEmployees()) {
                int mood           = 3 + rand.nextInt(7);
                int motivation     = 3 + rand.nextInt(7);
                int stress         = 2 + rand.nextInt(7);
                int energy         = 3 + rand.nextInt(7);
                int workLifeBal    = 3 + rand.nextInt(6);
                int teamConn       = 4 + rand.nextInt(6);
                int accomplish     = 3 + rand.nextInt(7);
                String workload    = workloads[rand.nextInt(workloads.length)];
                surveyManager.addResponse(new SurveyResponse(
                    emp.getId(), week, mood, motivation, stress,
                    energy, workLifeBal, teamConn, accomplish,
                    "", "", "", "", "", workload));
            }
        }

        for (Employee emp : directory.getAllEmployees()) surveyManager.addReminder(emp);
    }

    private void setupSystemTray() {
        if (!SystemTray.isSupported()) return;
        Image icon = LogoDrawer.createTrayImage();
        PopupMenu popup = new PopupMenu();

        MenuItem openItem = new MenuItem("Open WorkMate");
        openItem.addActionListener(e -> openBrowser());
        MenuItem exitItem = new MenuItem("Exit WorkMate");
        exitItem.addActionListener(e -> System.exit(0));

        popup.add(openItem);
        popup.addSeparator();
        popup.add(exitItem);

        trayIcon = new TrayIcon(icon, "WorkMate", popup);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> openBrowser());

        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            System.out.println("Could not add tray icon: " + e.getMessage());
        }
    }

    private void setupMondayReminderTimer() {
        java.util.Timer timer = new java.util.Timer(true);
        timer.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override public void run() {
                if (trayIcon != null) {
                    NotificationService.showTrayNotification(trayIcon,
                        "Time for your weekly check-in!",
                        "It only takes 3 minutes. Open WorkMate to get started.");
                }
            }
        }, 30000, 30000);
    }
}
