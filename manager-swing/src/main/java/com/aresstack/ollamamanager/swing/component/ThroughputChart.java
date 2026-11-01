package com.aresstack.ollamamanager.swing.component;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Line chart of total and per-chat tokens per second over the number of parallel chats. The point with the
 * highest total throughput is marked as the optimum; unstable points are drawn hollow. Pure painting, no
 * external library.
 */
public final class ThroughputChart extends JComponent {

    private static final long serialVersionUID = 1L;
    private static final int PAD_LEFT = 56;
    private static final int PAD_RIGHT = 56;
    private static final int PAD_TOP = 28;
    private static final int PAD_BOTTOM = 40;

    /** One measured stage. */
    public static final class Point {
        final int parallel;
        final double totalTps;
        final double perChatTps;
        final boolean stable;

        public Point(int parallel, double totalTps, double perChatTps, boolean stable) {
            this.parallel = parallel;
            this.totalTps = totalTps;
            this.perChatTps = perChatTps;
            this.stable = stable;
        }
    }

    private final List<Point> points = new ArrayList<Point>();
    private Integer optimum;

    public ThroughputChart() {
        setOpaque(false);
        setPreferredSize(new Dimension(480, 240));
        setMinimumSize(new Dimension(240, 160));
    }

    /** Replace all points. {@code optimumParallel} marks the optimum, or {@code null} for none. */
    public void setPoints(List<Point> newPoints, Integer optimumParallel) {
        points.clear();
        points.addAll(newPoints);
        Collections.sort(points, (a, b) -> Integer.compare(a.parallel, b.parallel));
        optimum = optimumParallel;
        repaint();
    }

    /** Append one point while a run is in progress. */
    public void addPoint(Point point) {
        points.add(point);
        Collections.sort(points, (a, b) -> Integer.compare(a.parallel, b.parallel));
        optimum = null;
        for (Point p : points) {
            if (p.stable && (optimum == null || p.totalTps > totalOf(optimum))) {
                optimum = p.parallel;
            }
        }
        repaint();
    }

    public void clear() {
        points.clear();
        optimum = null;
        repaint();
    }

    public int pointCount() {
        return points.size();
    }

    private double totalOf(int parallel) {
        for (Point p : points) {
            if (p.parallel == parallel) {
                return p.totalTps;
            }
        }
        return 0;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(Theme.baseFont());
            int width = getWidth();
            int height = getHeight();
            int plotX = PAD_LEFT;
            int plotY = PAD_TOP;
            int plotW = Math.max(10, width - PAD_LEFT - PAD_RIGHT);
            int plotH = Math.max(10, height - PAD_TOP - PAD_BOTTOM);
            FontMetrics fm = g.getFontMetrics();

            if (points.isEmpty()) {
                g.setColor(Theme.mutedForeground());
                String text = "Run a benchmark to see tokens per second over parallel chats.";
                g.drawString(text, (width - fm.stringWidth(text)) / 2, height / 2);
                return;
            }

            int maxParallel = 1;
            double maxTotal = 1;
            double maxPerChat = 1;
            for (Point p : points) {
                maxParallel = Math.max(maxParallel, p.parallel);
                maxTotal = Math.max(maxTotal, p.totalTps);
                maxPerChat = Math.max(maxPerChat, p.perChatTps);
            }
            maxTotal = niceCeiling(maxTotal);
            maxPerChat = niceCeiling(maxPerChat);

            Color line = Theme.lineColor();
            Color muted = Theme.mutedForeground();
            Color totalColor = Theme.color(Theme.Tone.INFO);
            Color perChatColor = Theme.color(Theme.Tone.WARNING);

            // Grid and left axis (total tokens/s).
            for (int i = 0; i <= 4; i++) {
                int y = plotY + plotH - (int) Math.round(plotH * i / 4.0);
                g.setColor(line);
                g.drawLine(plotX, y, plotX + plotW, y);
                g.setColor(totalColor);
                String label = format(maxTotal * i / 4);
                g.drawString(label, plotX - 6 - fm.stringWidth(label), y + fm.getAscent() / 2 - 1);
                g.setColor(perChatColor);
                g.drawString(format(maxPerChat * i / 4), plotX + plotW + 6, y + fm.getAscent() / 2 - 1);
            }
            // Bottom axis: number of parallel chats.
            g.setColor(muted);
            for (Point p : points) {
                int x = xOf(p.parallel, maxParallel, plotX, plotW);
                String label = String.valueOf(p.parallel);
                g.drawString(label, x - fm.stringWidth(label) / 2, plotY + plotH + fm.getAscent() + 4);
            }
            String xTitle = "parallel chats";
            g.drawString(xTitle, plotX + (plotW - fm.stringWidth(xTitle)) / 2,
                plotY + plotH + fm.getAscent() * 2 + 8);
            g.setColor(totalColor);
            g.drawString("total tokens/s", plotX, plotY - 10);
            g.setColor(perChatColor);
            String right = "tokens/s per chat";
            g.drawString(right, plotX + plotW - fm.stringWidth(right), plotY - 10);

            drawSeries(g, perChatColor, maxParallel, maxPerChat, plotX, plotY, plotW, plotH, false);
            drawSeries(g, totalColor, maxParallel, maxTotal, plotX, plotY, plotW, plotH, true);

            if (optimum != null) {
                for (Point p : points) {
                    if (p.parallel == optimum) {
                        int x = xOf(p.parallel, maxParallel, plotX, plotW);
                        int y = yOf(p.totalTps, maxTotal, plotY, plotH);
                        g.setColor(Theme.color(Theme.Tone.SUCCESS));
                        g.setStroke(new BasicStroke(2f));
                        g.draw(new Ellipse2D.Double(x - 8, y - 8, 16, 16));
                        String label = "optimum: " + p.parallel + (p.parallel == 1 ? " chat, " : " chats, ")
                            + format(p.totalTps) + " tok/s";
                        int lx = Math.min(x + 12, plotX + plotW - fm.stringWidth(label));
                        g.drawString(label, Math.max(plotX, lx), Math.max(plotY + fm.getAscent(), y - 12));
                    }
                }
            }
        } finally {
            g.dispose();
        }
    }

    private void drawSeries(Graphics2D g, Color color, int maxParallel, double max, int plotX, int plotY,
                            int plotW, int plotH, boolean total) {
        g.setColor(color);
        g.setStroke(new BasicStroke(2f));
        Path2D.Double path = new Path2D.Double();
        boolean first = true;
        for (Point p : points) {
            double value = total ? p.totalTps : p.perChatTps;
            int x = xOf(p.parallel, maxParallel, plotX, plotW);
            int y = yOf(value, max, plotY, plotH);
            if (first) {
                path.moveTo(x, y);
                first = false;
            } else {
                path.lineTo(x, y);
            }
        }
        g.draw(path);
        for (Point p : points) {
            double value = total ? p.totalTps : p.perChatTps;
            int x = xOf(p.parallel, maxParallel, plotX, plotW);
            int y = yOf(value, max, plotY, plotH);
            Ellipse2D dot = new Ellipse2D.Double(x - 4, y - 4, 8, 8);
            if (p.stable) {
                g.fill(dot);
            } else {
                g.setColor(Theme.background());
                g.fill(dot);
                g.setColor(Theme.color(Theme.Tone.ERROR));
                g.draw(dot);
                g.setColor(color);
            }
        }
    }

    private static int xOf(int parallel, int maxParallel, int plotX, int plotW) {
        if (maxParallel <= 1) {
            return plotX + plotW / 2;
        }
        return plotX + (int) Math.round((parallel - 1) * (double) plotW / (maxParallel - 1));
    }

    private static int yOf(double value, double max, int plotY, int plotH) {
        return plotY + plotH - (int) Math.round(Math.max(0, Math.min(1, value / max)) * plotH);
    }

    static double niceCeiling(double value) {
        if (value <= 0) {
            return 1;
        }
        double magnitude = Math.pow(10, Math.floor(Math.log10(value)));
        double normalized = value / magnitude;
        double step = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10;
        return step * magnitude;
    }

    static String format(double value) {
        return value >= 100 ? String.format(Locale.ROOT, "%.0f", value) : String.format(Locale.ROOT, "%.1f", value);
    }
}
