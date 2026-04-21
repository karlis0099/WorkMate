package com.workmate.util;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

/**
 * Utility class for drawing the WorkMate logo programmatically.
 */
public class LogoDrawer {

    private static final Color PRIMARY_GREEN = new Color(0x2D6A4F);
    private static final Color LIGHT_GREEN = new Color(0x52B788);

    // draw logo centered at (x, y) with given size
    public static void drawLogo(Graphics2D g, int x, int y, int size) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // dark green background circle
        g2.setColor(PRIMARY_GREEN);
        g2.fillOval(x, y, size, size);

        // white people/community icon inside
        float stroke = Math.max(1.5f, size / 14f);
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        int pad = size / 5;
        int iconW = size - pad * 2;
        int iconH = size - pad * 2;
        int ix = x + pad;
        int iy = y + pad;

        int headR = iconW / 5;

        // left person head
        g2.drawOval(ix, iy, headR * 2, headR * 2);
        // right person head
        g2.drawOval(ix + iconW - headR * 2, iy, headR * 2, headR * 2);

        // left person body arc
        g2.drawArc(ix - headR / 2, iy + headR * 2 - 2, headR * 3, headR * 2, 0, 180);
        // right person body arc
        g2.drawArc(ix + iconW - headR * 2 - headR / 2, iy + headR * 2 - 2, headR * 3, headR * 2, 0, 180);

        g2.dispose();
    }

    // 16x16 tray icon
    public static Image createTrayImage() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(PRIMARY_GREEN);
        g2.fillOval(0, 0, 16, 16);
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawOval(1, 1, 5, 5);
        g2.drawOval(9, 1, 5, 5);
        g2.drawArc(0, 7, 7, 5, 0, 180);
        g2.drawArc(8, 7, 7, 5, 0, 180);
        g2.dispose();
        return img;
    }

    // create a BufferedImage of the logo at given size
    public static Image createLogoImage(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        drawLogo(g2, 0, 0, size);
        g2.dispose();
        return img;
    }
}
