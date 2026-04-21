package com.workmate.ui;

import com.workmate.model.*;
import com.workmate.service.NotificationService;
import com.workmate.util.LogoDrawer;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.time.LocalDate;
import java.util.ArrayList;

public class ManagerView extends JPanel {

    private static final Color BG = new Color(0xF8F8F6);
    private static final Color PRIMARY = new Color(0x2D6A4F);
    private static final Color LIGHT = new Color(0x52B788);
    private static final Color TEXT = new Color(0x1A1A1A);
    private static final Color TEXT_SEC = new Color(0x6B6B6B);
    private static final Color BORDER = new Color(0xE8E8E4);
    private static final Color CARD_BG = Color.WHITE;
    private static final Color RED = new Color(0xE63946);
    private static final Color YELLOW = new Color(0xF4A261);

    private EmployeeDirectory directory;
    private SurveyManager surveyManager;
    private TrayIcon trayIcon;
    private Runnable onLogout;

    public ManagerView(EmployeeDirectory dir, SurveyManager sm, TrayIcon trayIcon, Runnable onLogout) {
        this.directory = dir;
        this.surveyManager = sm;
        this.trayIcon = trayIcon;
        this.onLogout = onLogout;

        setBackground(BG);
        setLayout(new BorderLayout());
        buildHeader();
        buildTabs();
    }

    private void buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(PRIMARY);
        header.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        header.setPreferredSize(new Dimension(0, 54));

        JPanel left = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                LogoDrawer.drawLogo(g2, 0, 2, 30);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 18));
                g2.drawString("WorkMate", 38, 22);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
                g2.setColor(new Color(180, 230, 200));
                g2.drawString("Manager Portal", 38, 34);
                g2.dispose();
            }
        };
        left.setOpaque(false);
        left.setPreferredSize(new Dimension(180, 40));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);

        RoundedButton logoutBtn = new RoundedButton("Logout", new Color(0x1E4D38), Color.WHITE);
        logoutBtn.addActionListener(e -> onLogout.run());
        right.add(logoutBtn);

        header.add(left, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
    }

    private void buildTabs() {
        String[] tabNames = {"\uD83D\uDCCA  Dashboard", "\uD83D\uDC65  Team Directory", "\uD83D\uDCCB  Survey Data"};
        JPanel[] panels = {buildDashboard(), buildDirectoryTab(), buildSurveyDataTab()};
        add(buildCustomTabs(tabNames, panels), BorderLayout.CENTER);
    }

    private JPanel buildCustomTabs(String[] names, JPanel[] panels) {
        JPanel container = new JPanel(new BorderLayout());
        container.setBackground(BG);

        CardLayout cl = new CardLayout();
        JPanel contentArea = new JPanel(cl);
        contentArea.setBackground(BG);
        for (int i = 0; i < panels.length; i++) contentArea.add(panels[i], String.valueOf(i));

        JPanel tabBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tabBar.setBackground(CARD_BG);
        tabBar.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, BORDER));

        JToggleButton[] tabs = new JToggleButton[names.length];
        ButtonGroup group = new ButtonGroup();

        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            JToggleButton tab = makeTabBtn(names[i]);
            group.add(tab);
            tabs[i] = tab;
            tab.addActionListener(e -> {
                cl.show(contentArea, String.valueOf(idx));
                for (JToggleButton t : tabs) t.repaint();
            });
            tabBar.add(tab);
        }
        tabs[0].setSelected(true);

        container.add(tabBar, BorderLayout.NORTH);
        container.add(contentArea, BorderLayout.CENTER);
        return container;
    }

    private JToggleButton makeTabBtn(String text) {
        JToggleButton btn = new JToggleButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD_BG);
                g2.fillRect(0, 0, getWidth(), getHeight());
                if (isSelected()) {
                    g2.setColor(PRIMARY);
                    g2.setStroke(new BasicStroke(2.5f));
                    g2.drawLine(0, getHeight() - 2, getWidth(), getHeight() - 2);
                    g2.setFont(new Font("SansSerif", Font.BOLD, 13));
                    g2.setColor(PRIMARY);
                } else {
                    g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
                    g2.setColor(TEXT_SEC);
                }
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        btn.setPreferredSize(new Dimension(200, 46));
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // --- DASHBOARD TAB ---
    private JPanel buildDashboard() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // top: 4 metric cards
        JPanel metricsRow = new JPanel(new GridLayout(1, 4, 12, 0));
        metricsRow.setBackground(BG);
        metricsRow.setPreferredSize(new Dimension(0, 100));

        double mood = surveyManager.getAverageMood();
        double motiv = surveyManager.getAverageMotivation();
        double stress = surveyManager.getAverageStress();
        int latest = surveyManager.getLatestWeek();
        double rate = surveyManager.getResponseRate(directory.size(), latest);

        metricsRow.add(makeMetricCard("Avg Mood", String.format("%.1f / 10", mood), moodColor(mood)));
        metricsRow.add(makeMetricCard("Avg Motivation", String.format("%.1f / 10", motiv), moodColor(motiv)));
        metricsRow.add(makeMetricCard("Avg Stress", String.format("%.1f / 10", stress), stressColor(stress)));
        metricsRow.add(makeMetricCard("Response Rate", String.format("%.0f%%", rate), rate > 70 ? LIGHT : YELLOW));

        // middle: bar chart
        JPanel chartCard = makeCard();
        chartCard.setLayout(new BorderLayout());
        chartCard.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        JLabel chartTitle = new JLabel("Last 4 Weeks Overview");
        chartTitle.setFont(new Font("SansSerif", Font.BOLD, 14));
        chartTitle.setForeground(TEXT);
        chartCard.add(chartTitle, BorderLayout.NORTH);
        chartCard.add(new BarChartPanel(), BorderLayout.CENTER);

        // bottom: risk alerts + simulate button
        JPanel bottomRow = new JPanel(new BorderLayout(16, 0));
        bottomRow.setBackground(BG);

        JPanel alertCard = makeCard();
        alertCard.setLayout(new BorderLayout());
        alertCard.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        JLabel alertTitle = new JLabel("\u26A0  Burnout Risk Alerts");
        alertTitle.setFont(new Font("SansSerif", Font.BOLD, 13));
        alertTitle.setForeground(RED);
        alertCard.add(alertTitle, BorderLayout.NORTH);

        JPanel alertList = new JPanel();
        alertList.setOpaque(false);
        alertList.setLayout(new BoxLayout(alertList, BoxLayout.Y_AXIS));

        ArrayList<Integer> risks = surveyManager.getHighRiskEmployeeIds();
        if (risks.isEmpty()) {
            JLabel ok = new JLabel("No high risk employees this week \u2714");
            ok.setFont(new Font("SansSerif", Font.PLAIN, 13));
            ok.setForeground(LIGHT);
            alertList.add(ok);
        } else {
            for (int id : risks) {
                Employee e = directory.findById(id);
                if (e != null) {
                    JLabel riskLbl = new JLabel("\u25CF  " + e.getName() + " - " + e.getDepartment());
                    riskLbl.setFont(new Font("SansSerif", Font.PLAIN, 13));
                    riskLbl.setForeground(RED);
                    alertList.add(riskLbl);
                    alertList.add(Box.createVerticalStrut(4));
                }
            }
        }
        alertCard.add(new JScrollPane(alertList) {{ setBorder(null); setOpaque(false); getViewport().setOpaque(false); }}, BorderLayout.CENTER);

        // simulate monday reminder button
        RoundedButton simBtn = new RoundedButton("\uD83D\uDD14  Simulate Monday Reminder", new Color(0xFFF3E0), new Color(0xE65100));
        simBtn.addActionListener(e -> {
            NotificationService.showTrayNotification(trayIcon, "Weekly Check-in Reminder",
                "Time for your weekly check-in! It takes 2 minutes.");
            JOptionPane.showMessageDialog(this, "Monday reminder notification sent to all employees!", "Reminder Sent", JOptionPane.INFORMATION_MESSAGE);
        });

        JPanel simPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        simPanel.setBackground(BG);
        simPanel.add(simBtn);

        bottomRow.add(alertCard, BorderLayout.CENTER);
        bottomRow.add(simPanel, BorderLayout.EAST);

        JPanel center = new JPanel(new BorderLayout(0, 14));
        center.setBackground(BG);
        center.add(chartCard, BorderLayout.CENTER);
        center.add(bottomRow, BorderLayout.SOUTH);

        panel.add(metricsRow, BorderLayout.NORTH);
        panel.add(new JPanel(new BorderLayout()) {{ setBackground(BG); setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0)); add(center); }}, BorderLayout.CENTER);

        return panel;
    }

    private JPanel makeMetricCard(String label, String value, Color accent) {
        JPanel card = makeCard();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        // colored top bar
        JPanel topBar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(accent);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 4, 4));
                g2.dispose();
            }
        };
        topBar.setPreferredSize(new Dimension(0, 4));
        topBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 4));
        topBar.setOpaque(false);
        card.add(topBar);
        card.add(Box.createVerticalStrut(10));

        JLabel valL = new JLabel(value);
        valL.setFont(new Font("SansSerif", Font.BOLD, 22));
        valL.setForeground(accent);
        valL.setAlignmentX(LEFT_ALIGNMENT);

        JLabel lblL = new JLabel(label);
        lblL.setFont(new Font("SansSerif", Font.PLAIN, 12));
        lblL.setForeground(TEXT_SEC);
        lblL.setAlignmentX(LEFT_ALIGNMENT);

        card.add(valL);
        card.add(Box.createVerticalStrut(4));
        card.add(lblL);
        return card;
    }

    // bar chart panel drawn with Java2D
    private class BarChartPanel extends JPanel {
        public BarChartPanel() {
            setOpaque(false);
            setPreferredSize(new Dimension(0, 180));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int pad = 36;
            int chartH = h - pad - 20;
            int weeks = 4;
            int groupW = (w - pad * 2) / weeks;
            int barW = groupW / 4;

            // y axis
            g2.setColor(BORDER);
            g2.setStroke(new BasicStroke(1));
            g2.drawLine(pad, 10, pad, h - pad);
            g2.drawLine(pad, h - pad, w - 10, h - pad);

            // draw bars for each week
            for (int week = 1; week <= weeks; week++) {
                double mood = surveyManager.getAverageMoodForWeek(week);
                double motiv = surveyManager.getAverageMotivationForWeek(week);
                double stress = surveyManager.getAverageStressForWeek(week);

                int x = pad + (week - 1) * groupW + groupW / 8;

                drawBar(g2, x, h - pad, barW - 2, (int)(mood / 10.0 * chartH), new Color(0x52B788), "M");
                drawBar(g2, x + barW, h - pad, barW - 2, (int)(motiv / 10.0 * chartH), new Color(0x2D6A4F), "Mo");
                drawBar(g2, x + barW * 2, h - pad, barW - 2, (int)(stress / 10.0 * chartH), new Color(0xF4A261), "S");

                // week label
                g2.setColor(TEXT_SEC);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
                g2.drawString("Wk " + week, x, h - pad + 14);
            }

            // legend
            int lx = w - 130;
            int ly = 14;
            drawLegendDot(g2, lx, ly, new Color(0x52B788), "Mood");
            drawLegendDot(g2, lx + 50, ly, new Color(0x2D6A4F), "Motiv.");
            drawLegendDot(g2, lx + 110, ly, new Color(0xF4A261), "Stress");

            g2.dispose();
        }

        private void drawBar(Graphics2D g2, int x, int baseY, int w, int h, Color color, String label) {
            if (h <= 0) return;
            g2.setColor(color);
            g2.fill(new RoundRectangle2D.Float(x, baseY - h, w, h, 4, 4));
        }

        private void drawLegendDot(Graphics2D g2, int x, int y, Color color, String label) {
            g2.setColor(color);
            g2.fillOval(x, y, 10, 10);
            g2.setColor(TEXT_SEC);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g2.drawString(label, x + 13, y + 9);
        }
    }

    // --- TEAM DIRECTORY TAB ---
    private JPanel buildDirectoryTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(BG);
        topBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));

        JLabel title = new JLabel("Team Members (" + directory.size() + ")");
        title.setFont(new Font("SansSerif", Font.BOLD, 16));
        title.setForeground(TEXT);

        RoundedButton addBtn = new RoundedButton("+ Add Employee", PRIMARY, Color.WHITE);
        addBtn.addActionListener(e -> showAddEmployeeDialog(panel));

        topBar.add(title, BorderLayout.WEST);
        topBar.add(addBtn, BorderLayout.EAST);

        JPanel grid = new JPanel(new GridLayout(0, 2, 12, 12));
        grid.setBackground(BG);

        for (Employee emp : directory.getAllEmployees()) {
            grid.add(makeManagerCard(emp, grid));
        }

        if (grid.getComponentCount() % 2 != 0) grid.add(new JPanel() {{ setOpaque(false); }});

        JScrollPane scroll = new JScrollPane(grid);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        panel.add(topBar, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel makeManagerCard(Employee emp, JPanel grid) {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0, 0, 0, 14));
                g2.fill(new RoundRectangle2D.Float(2, 3, getWidth() - 2, getHeight() - 2, 14, 14));
                g2.setColor(CARD_BG);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth() - 2, getHeight() - 2, 14, 14));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setLayout(new BorderLayout(12, 0));
        card.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        card.setPreferredSize(new Dimension(0, 88));

        Color ac = avatarColor(emp.getName());
        JPanel avatar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ac);
                g2.fillOval(0, 0, 48, 48);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 16));
                FontMetrics fm = g2.getFontMetrics();
                String init = emp.getInitials();
                g2.drawString(init, (48 - fm.stringWidth(init)) / 2, (48 + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        avatar.setPreferredSize(new Dimension(48, 48));
        avatar.setOpaque(false);

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        JLabel n = new JLabel(emp.getName()); n.setFont(new Font("SansSerif", Font.BOLD, 13)); n.setForeground(TEXT);
        JLabel p = new JLabel(emp.getPosition()); p.setFont(new Font("SansSerif", Font.PLAIN, 12)); p.setForeground(TEXT_SEC);
        JLabel d = new JLabel(emp.getDepartment()); d.setFont(new Font("SansSerif", Font.PLAIN, 11)); d.setForeground(new Color(0x9B9B9B));
        info.add(n); info.add(Box.createVerticalStrut(2)); info.add(p); info.add(Box.createVerticalStrut(2)); info.add(d);

        card.add(avatar, BorderLayout.WEST);
        card.add(info, BorderLayout.CENTER);
        return card;
    }

    private void showAddEmployeeDialog(JPanel directoryPanel) {
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this), "Add Employee", Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setSize(400, 380);
        dlg.setLocationRelativeTo(this);

        JPanel p = new JPanel();
        p.setBackground(BG);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        JTextField[] fields = new JTextField[5];
        String[] labels = {"Full Name", "Position", "Department", "Email", "Phone"};
        for (int i = 0; i < labels.length; i++) {
            JLabel lbl = new JLabel(labels[i]);
            lbl.setFont(new Font("SansSerif", Font.BOLD, 12));
            lbl.setForeground(TEXT_SEC);
            lbl.setAlignmentX(LEFT_ALIGNMENT);
            p.add(lbl);
            p.add(Box.createVerticalStrut(4));
            fields[i] = new JTextField();
            fields[i].setFont(new Font("SansSerif", Font.PLAIN, 13));
            fields[i].setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), BorderFactory.createEmptyBorder(6, 8, 6, 8)));
            fields[i].setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            fields[i].setAlignmentX(LEFT_ALIGNMENT);
            p.add(fields[i]);
            p.add(Box.createVerticalStrut(10));
        }

        RoundedButton saveBtn = new RoundedButton("Add Employee", PRIMARY, Color.WHITE);
        saveBtn.setAlignmentX(LEFT_ALIGNMENT);
        saveBtn.addActionListener(e -> {
            if (fields[0].getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(dlg, "Name is required!", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            Employee newEmp = new Employee(
                directory.getNextId(),
                fields[0].getText().trim(),
                fields[1].getText().trim(),
                fields[2].getText().trim(),
                fields[3].getText().trim(),
                fields[4].getText().trim());
            directory.addEmployee(newEmp);
            dlg.dispose();
            JOptionPane.showMessageDialog(this, "Employee added successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
        });
        p.add(saveBtn);

        dlg.add(p);
        dlg.setVisible(true);
    }

    // --- SURVEY DATA TAB ---
    private JPanel buildSurveyDataTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel topBar = new JPanel(new BorderLayout(0, 8));
        topBar.setBackground(BG);
        topBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setBackground(BG);
        JLabel title = new JLabel("Survey Responses");
        title.setFont(new Font("SansSerif", Font.BOLD, 16));
        title.setForeground(TEXT);
        RoundedButton exportBtn = new RoundedButton("\u2B07 Export to .txt", new Color(0xEEF7F2), PRIMARY);
        exportBtn.addActionListener(e -> exportData());
        titleRow.add(title, BorderLayout.WEST);
        titleRow.add(exportBtn, BorderLayout.EAST);

        // week filter buttons
        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        filterRow.setBackground(BG);
        String[] weekLabels = {"All", "Week 1", "Week 2", "Week 3", "Week 4"};

        JPanel tableHolder = new JPanel(new BorderLayout());
        tableHolder.setBackground(BG);

        Runnable[] refreshRef = new Runnable[1];
        int[] activeWeek = {0}; // 0 = all

        for (int i = 0; i < weekLabels.length; i++) {
            final int weekIdx = i; // 0=all, 1-4=week number
            JButton fb = new JButton(weekLabels[i]) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    boolean active = activeWeek[0] == weekIdx;
                    g2.setColor(active ? PRIMARY : CARD_BG);
                    g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 14, 14));
                    if (!active) { g2.setColor(BORDER); g2.draw(new RoundRectangle2D.Float(0, 0, getWidth()-1, getHeight()-1, 14, 14)); }
                    g2.setColor(active ? Color.WHITE : TEXT_SEC);
                    g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(getText(), (getWidth()-fm.stringWidth(getText()))/2, (getHeight()+fm.getAscent()-fm.getDescent())/2);
                    g2.dispose();
                }
            };
            fb.setPreferredSize(new Dimension(weekLabels[i].length() * 8 + 18, 28));
            fb.setContentAreaFilled(false); fb.setBorderPainted(false); fb.setFocusPainted(false);
            fb.setCursor(new Cursor(Cursor.HAND_CURSOR));
            fb.addActionListener(e -> {
                activeWeek[0] = weekIdx;
                filterRow.repaint();
                if (refreshRef[0] != null) refreshRef[0].run();
            });
            filterRow.add(fb);
        }

        topBar.add(titleRow, BorderLayout.NORTH);
        topBar.add(filterRow, BorderLayout.SOUTH);

        DefaultTableCellRenderer colorRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                if (sel) { c.setBackground(new Color(0xD8EFE3)); }
                else { c.setBackground(row % 2 == 0 ? CARD_BG : new Color(0xF5FAF7)); }
                setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
                if (col == 5 && val != null) {
                    String risk = val.toString();
                    if ("HIGH".equals(risk)) c.setForeground(RED);
                    else if ("MEDIUM".equals(risk)) c.setForeground(YELLOW);
                    else c.setForeground(LIGHT);
                    ((JLabel) c).setFont(new Font("SansSerif", Font.BOLD, 12));
                } else {
                    c.setForeground(TEXT);
                    ((JLabel) c).setFont(new Font("SansSerif", Font.PLAIN, 13));
                }
                return c;
            }
        };

        refreshRef[0] = () -> {
            tableHolder.removeAll();
            String[] cols = {"Employee", "Week", "Mood", "Motivation", "Stress", "Risk", "Issues"};
            ArrayList<SurveyResponse> filtered = new ArrayList<>();
            for (SurveyResponse r : surveyManager.getAllResponses()) {
                if (activeWeek[0] == 0 || r.getWeekNumber() == activeWeek[0]) filtered.add(r);
            }
            Object[][] data = new Object[filtered.size()][7];
            for (int i = 0; i < filtered.size(); i++) {
                SurveyResponse r = filtered.get(i);
                Employee emp = directory.findById(r.getEmployeeId());
                data[i][0] = emp != null ? emp.getName() : "Unknown";
                data[i][1] = "Week " + r.getWeekNumber();
                data[i][2] = r.getMood();
                data[i][3] = r.getMotivation();
                data[i][4] = r.getStress();
                data[i][5] = r.getRiskLevel();
                data[i][6] = r.getIssues().isEmpty() ? "-" : r.getIssues();
            }
            JTable table = new JTable(data, cols) {
                @Override public boolean isCellEditable(int row, int col) { return false; }
            };
            table.setFont(new Font("SansSerif", Font.PLAIN, 13));
            table.setRowHeight(32);
            table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
            table.getTableHeader().setBackground(new Color(0xF0F5F2));
            table.getTableHeader().setForeground(TEXT);
            table.setShowGrid(false);
            table.setIntercellSpacing(new Dimension(0, 0));
            table.setDefaultRenderer(Object.class, colorRenderer);
            JScrollPane scroll = new JScrollPane(table);
            scroll.setBorder(BorderFactory.createLineBorder(BORDER));
            tableHolder.add(scroll, BorderLayout.CENTER);
            tableHolder.revalidate();
            tableHolder.repaint();
        };
        refreshRef[0].run();

        panel.add(topBar, BorderLayout.NORTH);
        panel.add(tableHolder, BorderLayout.CENTER);
        return panel;
    }

    private void exportData() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("workmate_survey_" + LocalDate.now() + ".txt"));
        int result = fc.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            try (PrintWriter pw = new PrintWriter(fc.getSelectedFile())) {
                pw.println("WorkMate Survey Export - " + LocalDate.now());
                pw.println("=".repeat(60));
                pw.printf("%-20s %-8s %-6s %-12s %-7s %-8s%n",
                    "Employee", "Week", "Mood", "Motivation", "Stress", "Risk");
                pw.println("-".repeat(60));

                for (SurveyResponse r : surveyManager.getAllResponses()) {
                    Employee emp = directory.findById(r.getEmployeeId());
                    String name = emp != null ? emp.getName() : "Unknown";
                    pw.printf("%-20s %-8s %-6d %-12d %-7d %-8s%n",
                        name, "Week " + r.getWeekNumber(), r.getMood(),
                        r.getMotivation(), r.getStress(), r.getRiskLevel());
                    if (!r.getIssues().isEmpty()) pw.println("  Issues: " + r.getIssues());
                }

                JOptionPane.showMessageDialog(this, "Exported successfully!", "Export Done", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // helper to create a card panel with rounded corners
    private JPanel makeCard() {
        return new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0, 0, 0, 12));
                g2.fill(new RoundRectangle2D.Float(2, 3, getWidth() - 2, getHeight() - 2, 14, 14));
                g2.setColor(CARD_BG);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth() - 2, getHeight() - 2, 14, 14));
                g2.dispose();
                super.paintComponent(g);
            }
            { setOpaque(false); }
        };
    }

    private Color avatarColor(String name) {
        Color[] colors = {
            new Color(0x2D6A4F), new Color(0x40916C), new Color(0x1B4332),
            new Color(0x52B788), new Color(0x095D40), new Color(0x74C69D)
        };
        return colors[Math.abs(name.hashCode()) % colors.length];
    }

    private Color moodColor(double val) {
        if (val >= 7) return LIGHT;
        if (val >= 5) return YELLOW;
        return RED;
    }

    private Color stressColor(double val) {
        if (val <= 4) return LIGHT;
        if (val <= 6) return YELLOW;
        return RED;
    }
}
