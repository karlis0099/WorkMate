package com.workmate.ui;

import com.workmate.model.*;
import com.workmate.util.LogoDrawer;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public class EmployeeView extends JPanel {

    private static final Color BG = new Color(0xF8F8F6);
    private static final Color PRIMARY = new Color(0x2D6A4F);
    private static final Color LIGHT = new Color(0x52B788);
    private static final Color TEXT = new Color(0x1A1A1A);
    private static final Color TEXT_SEC = new Color(0x6B6B6B);
    private static final Color BORDER = new Color(0xE8E8E4);
    private static final Color CARD_BG = Color.WHITE;

    private Employee currentEmployee;
    private EmployeeDirectory directory;
    private SurveyManager surveyManager;
    private Runnable onLogout;

    private JPanel cardsPanel;
    private JTextField searchField;
    private String activeFilter = "All";

    public EmployeeView(Employee emp, EmployeeDirectory dir, SurveyManager sm, Runnable onLogout) {
        this.currentEmployee = emp;
        this.directory = dir;
        this.surveyManager = sm;
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

        // logo + app name
        JPanel left = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                LogoDrawer.drawLogo(g2, 0, 2, 30);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 18));
                g2.drawString("WorkMate", 38, 22);
                g2.dispose();
            }
        };
        left.setOpaque(false);
        left.setPreferredSize(new Dimension(160, 34));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);

        JLabel greeting = new JLabel("Hi, " + currentEmployee.getName().split(" ")[0] + " \uD83D\uDC4B");
        greeting.setForeground(Color.WHITE);
        greeting.setFont(new Font("SansSerif", Font.PLAIN, 13));

        RoundedButton logoutBtn = new RoundedButton("Logout", new Color(0x1E4D38), Color.WHITE);
        logoutBtn.addActionListener(e -> onLogout.run());

        right.add(greeting);
        right.add(logoutBtn);

        header.add(left, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
    }

    private void buildTabs() {
        String[] tabNames = {"\uD83D\uDDD2  Directory", "\u2713  Weekly Check-in", "\uD83D\uDC64  My Profile"};

        JPanel[] content = new JPanel[]{
            buildDirectoryPanel(),
            buildCheckInPanel(),
            buildProfilePanel()
        };

        add(buildCustomTabs(tabNames, content), BorderLayout.CENTER);
    }

    // custom tab bar with CardLayout for content
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
            JToggleButton tab = makeTabButton(names[i]);
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

    private JToggleButton makeTabButton(String text) {
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
        btn.setPreferredSize(new Dimension(190, 46));
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // --- DIRECTORY TAB ---
    private JPanel buildDirectoryPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel topBar = new JPanel(new BorderLayout(10, 0));
        topBar.setBackground(BG);
        topBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));

        searchField = new JTextField();
        searchField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        searchField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1, true),
            BorderFactory.createEmptyBorder(7, 12, 7, 12)));
        searchField.setPreferredSize(new Dimension(240, 36));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refreshCards(); }
            public void removeUpdate(DocumentEvent e) { refreshCards(); }
            public void changedUpdate(DocumentEvent e) { refreshCards(); }
        });

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        filters.setBackground(BG);
        for (String f : new String[]{"All", "IT", "HR", "Finance", "Marketing", "Management", "Sales"}) {
            filters.add(makeFilterBtn(f));
        }

        topBar.add(searchField, BorderLayout.WEST);
        topBar.add(filters, BorderLayout.EAST);

        cardsPanel = new JPanel(new GridLayout(0, 2, 12, 12));
        cardsPanel.setBackground(BG);

        JScrollPane scroll = new JScrollPane(cardsPanel);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        panel.add(topBar, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);

        refreshCards();
        return panel;
    }

    private JButton makeFilterBtn(String label) {
        JButton btn = new JButton(label) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean active = label.equals(activeFilter);
                g2.setColor(active ? PRIMARY : CARD_BG);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 14, 14));
                if (!active) {
                    g2.setColor(BORDER);
                    g2.draw(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 14, 14));
                }
                g2.setColor(active ? Color.WHITE : TEXT_SEC);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        btn.setPreferredSize(new Dimension(label.length() * 8 + 18, 28));
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> { activeFilter = label; refreshCards(); });
        return btn;
    }

    // rebuild cards based on search and filter - rebuilt every time (simple approach)
    private void refreshCards() {
        cardsPanel.removeAll();
        String query = searchField != null ? searchField.getText().toLowerCase() : "";

        for (Employee emp : directory.getAllEmployees()) {
            boolean matchFilter = "All".equals(activeFilter) || emp.getDepartment().equalsIgnoreCase(activeFilter);
            boolean matchSearch = query.isEmpty()
                || emp.getName().toLowerCase().contains(query)
                || emp.getPosition().toLowerCase().contains(query);

            if (matchFilter && matchSearch) {
                cardsPanel.add(makeEmployeeCard(emp));
            }
        }

        // pad to even for GridLayout
        if (cardsPanel.getComponentCount() % 2 != 0) {
            JPanel filler = new JPanel();
            filler.setOpaque(false);
            cardsPanel.add(filler);
        }

        cardsPanel.revalidate();
        cardsPanel.repaint();
    }

    private JPanel makeEmployeeCard(Employee emp) {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // shadow
                g2.setColor(new Color(0, 0, 0, 14));
                g2.fill(new RoundRectangle2D.Float(2, 3, getWidth() - 2, getHeight() - 2, 14, 14));
                // card bg
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
        card.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // colored initials avatar
        Color avatarColor = avatarColor(emp.getName());
        JPanel avatar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(avatarColor);
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

        JLabel nameL = new JLabel(emp.getName());
        nameL.setFont(new Font("SansSerif", Font.BOLD, 13));
        nameL.setForeground(TEXT);

        JLabel posL = new JLabel(emp.getPosition());
        posL.setFont(new Font("SansSerif", Font.PLAIN, 12));
        posL.setForeground(TEXT_SEC);

        JLabel deptL = new JLabel(emp.getDepartment() + " \u2022 " + emp.getEmail());
        deptL.setFont(new Font("SansSerif", Font.PLAIN, 11));
        deptL.setForeground(new Color(0x9B9B9B));

        info.add(nameL);
        info.add(Box.createVerticalStrut(2));
        info.add(posL);
        info.add(Box.createVerticalStrut(2));
        info.add(deptL);

        card.add(avatar, BorderLayout.WEST);
        card.add(info, BorderLayout.CENTER);

        card.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) { showDetails(emp); }
            public void mouseEntered(MouseEvent e) {
                card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(LIGHT, 1, true),
                    BorderFactory.createEmptyBorder(13, 13, 13, 13)));
            }
            public void mouseExited(MouseEvent e) {
                card.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
            }
        });

        return card;
    }

    private void showDetails(Employee emp) {
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this), emp.getName(), Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setSize(360, 280);
        dlg.setLocationRelativeTo(this);

        JPanel content = new JPanel();
        content.setBackground(BG);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        addDetailRow(content, "Name", emp.getName());
        addDetailRow(content, "Position", emp.getPosition());
        addDetailRow(content, "Department", emp.getDepartment());
        addDetailRow(content, "Email", emp.getEmail());
        addDetailRow(content, "Phone", emp.getPhone());

        dlg.add(content);
        dlg.setVisible(true);
    }

    private void addDetailRow(JPanel p, String label, String value) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        JLabel lbl = new JLabel(label + ":");
        lbl.setFont(new Font("SansSerif", Font.BOLD, 12));
        lbl.setForeground(TEXT_SEC);
        lbl.setPreferredSize(new Dimension(100, 22));
        JLabel val = new JLabel(value);
        val.setFont(new Font("SansSerif", Font.PLAIN, 13));
        val.setForeground(TEXT);
        row.add(lbl, BorderLayout.WEST);
        row.add(val, BorderLayout.CENTER);
        p.add(row);
    }

    // generate a consistent color based on name
    private Color avatarColor(String name) {
        Color[] colors = {
            new Color(0x2D6A4F), new Color(0x40916C), new Color(0x1B4332),
            new Color(0x52B788), new Color(0x095D40), new Color(0x74C69D)
        };
        return colors[Math.abs(name.hashCode()) % colors.length];
    }

    // --- WEEKLY CHECK-IN TAB ---
    private JPanel buildCheckInPanel() {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(BG);

        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setBackground(BG);

        JPanel form = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0, 0, 0, 12));
                g2.fill(new RoundRectangle2D.Float(3, 4, getWidth() - 3, getHeight() - 3, 16, 16));
                g2.setColor(CARD_BG);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth() - 2, getHeight() - 2, 16, 16));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(28, 40, 28, 40));
        form.setPreferredSize(new Dimension(560, 560));

        JLabel title = new JLabel("How's your week going?");
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setForeground(PRIMARY);
        title.setAlignmentX(LEFT_ALIGNMENT);

        JLabel dateL = new JLabel(LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy")));
        dateL.setFont(new Font("SansSerif", Font.PLAIN, 13));
        dateL.setForeground(TEXT_SEC);
        dateL.setAlignmentX(LEFT_ALIGNMENT);

        form.add(title);
        form.add(Box.createVerticalStrut(4));
        form.add(dateL);
        form.add(Box.createVerticalStrut(22));

        JSlider moodSlider = addSlider(form, "Mood", "\uD83D\uDE14", "\uD83D\uDE0A");
        form.add(Box.createVerticalStrut(14));
        JSlider motivSlider = addSlider(form, "Motivation", "\uD83D\uDE34", "\uD83D\uDE80");
        form.add(Box.createVerticalStrut(14));
        JSlider stressSlider = addSlider(form, "Stress Level", "\uD83D\uDE0C", "\uD83D\uDE30");

        form.add(Box.createVerticalStrut(20));

        JLabel issueLabel = new JLabel("Any issues this week?");
        issueLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        issueLabel.setForeground(TEXT);
        issueLabel.setAlignmentX(LEFT_ALIGNMENT);
        form.add(issueLabel);
        form.add(Box.createVerticalStrut(8));

        String[] opts = {"Workload too heavy", "Unclear expectations", "Team conflicts", "Technical issues", "Personal challenges"};
        JCheckBox[] cbs = new JCheckBox[opts.length];
        for (int i = 0; i < opts.length; i++) {
            JCheckBox cb = new JCheckBox(opts[i]);
            cb.setFont(new Font("SansSerif", Font.PLAIN, 13));
            cb.setForeground(TEXT);
            cb.setOpaque(false);
            cb.setAlignmentX(LEFT_ALIGNMENT);
            cbs[i] = cb;
            form.add(cb);
        }

        form.add(Box.createVerticalStrut(16));

        JLabel commentLabel = new JLabel("Anything else? (anonymous)");
        commentLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        commentLabel.setForeground(TEXT);
        commentLabel.setAlignmentX(LEFT_ALIGNMENT);
        form.add(commentLabel);
        form.add(Box.createVerticalStrut(6));

        JTextArea commentArea = new JTextArea(3, 30);
        commentArea.setFont(new Font("SansSerif", Font.PLAIN, 13));
        commentArea.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        commentArea.setLineWrap(true);
        commentArea.setWrapStyleWord(true);
        JScrollPane cs = new JScrollPane(commentArea);
        cs.setBorder(null);
        cs.setAlignmentX(LEFT_ALIGNMENT);
        cs.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        form.add(cs);

        form.add(Box.createVerticalStrut(20));

        RoundedButton submitBtn = new RoundedButton("Submit Check-in \u2192", PRIMARY, Color.WHITE);
        submitBtn.setAlignmentX(LEFT_ALIGNMENT);
        submitBtn.addActionListener(e -> {
            StringBuilder issues = new StringBuilder();
            for (JCheckBox cb : cbs) {
                if (cb.isSelected()) {
                    if (issues.length() > 0) issues.append(", ");
                    issues.append(cb.getText());
                }
            }
            int week = LocalDate.now().get(java.time.temporal.WeekFields.of(java.util.Locale.getDefault()).weekOfYear());
            surveyManager.addResponse(new SurveyResponse(
                currentEmployee.getId(), week,
                moodSlider.getValue(), motivSlider.getValue(), stressSlider.getValue(),
                issues.toString(), commentArea.getText()));
            showThankYou(form);
        });
        form.add(submitBtn);

        wrapper.add(form);

        JScrollPane scroll = new JScrollPane(wrapper);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        outer.add(scroll, BorderLayout.CENTER);
        return outer;
    }

    private JSlider addSlider(JPanel parent, String label, String leftEmoji, String rightEmoji) {
        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 13));
        lbl.setForeground(TEXT);
        lbl.setAlignmentX(LEFT_ALIGNMENT);
        parent.add(lbl);
        parent.add(Box.createVerticalStrut(5));

        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        JLabel left = new JLabel(leftEmoji);
        left.setFont(new Font("SansSerif", Font.PLAIN, 18));

        JSlider slider = new JSlider(1, 10, 5);
        slider.setOpaque(false);
        slider.setPaintTicks(true);
        slider.setMajorTickSpacing(1);

        JLabel right = new JLabel(rightEmoji);
        right.setFont(new Font("SansSerif", Font.PLAIN, 18));

        row.add(left, BorderLayout.WEST);
        row.add(slider, BorderLayout.CENTER);
        row.add(right, BorderLayout.EAST);
        parent.add(row);
        return slider;
    }

    private void showThankYou(JPanel form) {
        form.removeAll();
        form.setLayout(new GridBagLayout());

        JPanel inner = new JPanel();
        inner.setOpaque(false);
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));

        // draw a checkmark circle
        JPanel check = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(LIGHT);
                g2.fillOval(0, 0, 72, 72);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(18, 38, 30, 52);
                g2.drawLine(30, 52, 54, 24);
                g2.dispose();
            }
        };
        check.setPreferredSize(new Dimension(72, 72));
        check.setMaximumSize(new Dimension(72, 72));
        check.setOpaque(false);
        check.setAlignmentX(CENTER_ALIGNMENT);

        JLabel thanks = new JLabel("Thank you!");
        thanks.setFont(new Font("SansSerif", Font.BOLD, 26));
        thanks.setForeground(PRIMARY);
        thanks.setAlignmentX(CENTER_ALIGNMENT);

        JLabel sub = new JLabel("Your response has been recorded. See you next week!");
        sub.setFont(new Font("SansSerif", Font.PLAIN, 14));
        sub.setForeground(TEXT_SEC);
        sub.setAlignmentX(CENTER_ALIGNMENT);

        inner.add(check);
        inner.add(Box.createVerticalStrut(18));
        inner.add(thanks);
        inner.add(Box.createVerticalStrut(8));
        inner.add(sub);

        form.add(inner);
        form.revalidate();
        form.repaint();
    }

    // --- MY PROFILE TAB ---
    private JPanel buildProfilePanel() {
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setBackground(BG);

        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0, 0, 0, 12));
                g2.fill(new RoundRectangle2D.Float(3, 4, getWidth() - 3, getHeight() - 3, 16, 16));
                g2.setColor(CARD_BG);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth() - 2, getHeight() - 2, 16, 16));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(36, 52, 36, 52));
        card.setPreferredSize(new Dimension(440, 420));

        // big avatar
        Color ac = avatarColor(currentEmployee.getName());
        JPanel avatar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ac);
                g2.fillOval(0, 0, 80, 80);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 26));
                FontMetrics fm = g2.getFontMetrics();
                String init = currentEmployee.getInitials();
                g2.drawString(init, (80 - fm.stringWidth(init)) / 2, (80 + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        avatar.setPreferredSize(new Dimension(80, 80));
        avatar.setMaximumSize(new Dimension(80, 80));
        avatar.setOpaque(false);
        avatar.setAlignmentX(CENTER_ALIGNMENT);

        JLabel nameL = new JLabel(currentEmployee.getName());
        nameL.setFont(new Font("SansSerif", Font.BOLD, 22));
        nameL.setForeground(TEXT);
        nameL.setAlignmentX(CENTER_ALIGNMENT);

        JLabel posL = new JLabel(currentEmployee.getPosition());
        posL.setFont(new Font("SansSerif", Font.PLAIN, 14));
        posL.setForeground(TEXT_SEC);
        posL.setAlignmentX(CENTER_ALIGNMENT);

        card.add(avatar);
        card.add(Box.createVerticalStrut(14));
        card.add(nameL);
        card.add(Box.createVerticalStrut(4));
        card.add(posL);
        card.add(Box.createVerticalStrut(28));

        addProfileRow(card, "\uD83D\uDCE7", "Email", currentEmployee.getEmail());
        addProfileRow(card, "\uD83D\uDCF1", "Phone", currentEmployee.getPhone());
        addProfileRow(card, "\uD83C\uDFE2", "Department", currentEmployee.getDepartment());
        addProfileRow(card, "\uD83C\uDD94", "Employee ID", "#" + currentEmployee.getId());

        card.add(Box.createVerticalStrut(28));

        RoundedButton hrBtn = new RoundedButton("\uD83D\uDCDE  Contact HR", new Color(0xEEF7F2), PRIMARY);
        hrBtn.setAlignmentX(CENTER_ALIGNMENT);
        hrBtn.addActionListener(e -> JOptionPane.showMessageDialog(this,
            "HR Department\nanna.berzina@company.lv\n+371 20000001", "Contact HR", JOptionPane.INFORMATION_MESSAGE));
        card.add(hrBtn);

        wrapper.add(card);
        return wrapper;
    }

    private void addProfileRow(JPanel p, String icon, String label, String value) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 3));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        JLabel ico = new JLabel(icon);
        ico.setFont(new Font("SansSerif", Font.PLAIN, 15));
        JLabel lbl = new JLabel(label + ":");
        lbl.setFont(new Font("SansSerif", Font.BOLD, 12));
        lbl.setForeground(TEXT_SEC);
        lbl.setPreferredSize(new Dimension(88, 22));
        JLabel val = new JLabel(value);
        val.setFont(new Font("SansSerif", Font.PLAIN, 13));
        val.setForeground(TEXT);
        row.add(ico); row.add(lbl); row.add(val);
        p.add(row);
    }
}
