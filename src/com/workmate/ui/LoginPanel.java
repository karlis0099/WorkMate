package com.workmate.ui;

import com.workmate.model.Employee;
import com.workmate.model.EmployeeDirectory;
import com.workmate.util.LogoDrawer;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;

public class LoginPanel extends JPanel {

    private static final Color BG = new Color(0xF8F8F6);
    private static final Color PRIMARY = new Color(0x2D6A4F);
    private static final Color TEXT = new Color(0x1A1A1A);
    private static final Color TEXT_SEC = new Color(0x6B6B6B);
    private static final Color BORDER = new Color(0xE8E8E4);

    private JComboBox<String> employeeDropdown;
    private EmployeeDirectory directory;
    private LoginCallback callback;

    public interface LoginCallback {
        void onEmployeeLogin(Employee emp);
        void onManagerLogin();
    }

    public LoginPanel(EmployeeDirectory directory, LoginCallback callback) {
        this.directory = directory;
        this.callback = callback;
        setBackground(BG);
        setLayout(new GridBagLayout());
        buildUI();
    }

    private void buildUI() {
        // card panel with rounded corners
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // subtle shadow
                g2.setColor(new Color(0, 0, 0, 12));
                g2.fill(new RoundRectangle2D.Float(3, 4, getWidth() - 3, getHeight() - 3, 20, 20));
                g2.setColor(Color.WHITE);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth() - 2, getHeight() - 2, 20, 20));
                g2.setColor(BORDER);
                g2.setStroke(new BasicStroke(1));
                g2.draw(new RoundRectangle2D.Float(0, 0, getWidth() - 2, getHeight() - 2, 20, 20));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(44, 52, 44, 52));
        card.setPreferredSize(new Dimension(420, 480));

        // logo
        JPanel logoPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                LogoDrawer.drawLogo(g2, 10, 0, 72);
                g2.dispose();
            }
        };
        logoPanel.setOpaque(false);
        logoPanel.setPreferredSize(new Dimension(92, 72));
        logoPanel.setMaximumSize(new Dimension(92, 72));
        logoPanel.setAlignmentX(CENTER_ALIGNMENT);
        card.add(logoPanel);

        card.add(Box.createVerticalStrut(18));

        JLabel title = new JLabel("WorkMate");
        title.setFont(new Font("SansSerif", Font.BOLD, 30));
        title.setForeground(PRIMARY);
        title.setAlignmentX(CENTER_ALIGNMENT);
        card.add(title);

        JLabel subtitle = new JLabel("Employee Wellbeing Platform");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 14));
        subtitle.setForeground(TEXT_SEC);
        subtitle.setAlignmentX(CENTER_ALIGNMENT);
        card.add(subtitle);

        card.add(Box.createVerticalStrut(36));

        JLabel selectLabel = new JLabel("Select your name");
        selectLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        selectLabel.setForeground(TEXT_SEC);
        selectLabel.setAlignmentX(LEFT_ALIGNMENT);
        card.add(selectLabel);

        card.add(Box.createVerticalStrut(8));

        ArrayList<Employee> emps = directory.getAllEmployees();
        String[] names = emps.stream().map(Employee::getName).toArray(String[]::new);
        employeeDropdown = new JComboBox<>(names);
        employeeDropdown.setFont(new Font("SansSerif", Font.PLAIN, 14));
        employeeDropdown.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        employeeDropdown.setAlignmentX(LEFT_ALIGNMENT);
        employeeDropdown.setBackground(Color.WHITE);
        card.add(employeeDropdown);

        card.add(Box.createVerticalStrut(24));

        RoundedButton empBtn = new RoundedButton("Continue as Employee", PRIMARY, Color.WHITE);
        empBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        empBtn.setAlignmentX(LEFT_ALIGNMENT);
        empBtn.addActionListener(e -> {
            String selected = (String) employeeDropdown.getSelectedItem();
            Employee emp = directory.findByName(selected);
            if (emp != null) callback.onEmployeeLogin(emp);
        });
        card.add(empBtn);

        card.add(Box.createVerticalStrut(12));

        RoundedButton mgrBtn = new RoundedButton("Manager Login", new Color(0xEEF7F2), PRIMARY);
        mgrBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        mgrBtn.setAlignmentX(LEFT_ALIGNMENT);
        mgrBtn.addActionListener(e -> {
            JPasswordField pwf = new JPasswordField(16);
            int result = JOptionPane.showConfirmDialog(this,
                new Object[]{"Enter manager password:", pwf},
                "Manager Login", JOptionPane.OK_CANCEL_OPTION);
            if (result == JOptionPane.OK_OPTION) {
                String pwd = new String(pwf.getPassword());
                if ("admin".equals(pwd)) {
                    callback.onManagerLogin();
                } else {
                    JOptionPane.showMessageDialog(this, "Wrong password!", "Access Denied", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        card.add(mgrBtn);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        add(card, gbc);
    }
}
