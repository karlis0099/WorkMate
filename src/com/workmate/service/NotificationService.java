package com.workmate.service;

import java.awt.*;
import javax.swing.*;

public class NotificationService {

    public static void showTrayNotification(TrayIcon trayIcon, String title, String message) {
        if (trayIcon != null) {
            trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO);
        }
    }

    public static void showManagerAlert(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "WorkMate Alert", JOptionPane.WARNING_MESSAGE);
    }
}
