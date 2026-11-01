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
 * Line chart of total tokens per second over the number of parallel chats, one line per series (one benchmark
 * run, usually one server setting). With a single series the per-chat speed is drawn on a second axis. Each
 * series' optimum, the stable point with the highest total throughput, is ringed; unstable points are hollow.
 * For an embedding benchmark the axes speak of requests and input tokens instead of chats. Pure painting, no
 * external library.
 */
public final class ThroughputChart extends JComponent {

    private static final long serialVersionUID = 1L;
    private static final int PAD_LEFT = 56;
    private static final int PAD_RIGHT = 56;
    private static final int PAD_TOP = 28;
    private static final int PAD_BOTTOM = 40;
    private static final int LEGEND_ROW = 16;

    /** Whether the points are parallel embedding requests (input tokens) rather than chats (generated tokens). */
    private boolean embedding;

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

    /** One line: a benchmark run under one server setting. */
    public static final class Series {
        final String name;
        final List<Point> points = new ArrayList<Point>();

        public Series(String name) {
            this.name = name == null ? "" : name;
        }

        public Series add(Point point) {
            points.add(point);
            Collections.sort(points, (a, b) -> Integer.compare(a.parallel, b.parallel));
            return this;
        }

        /** Parallelism of the stable point with the highest total throughput, or {@code null}. */
        Integer optimum() {
            Point best = null;
            for (Point p : points) {
                if (p.stable && (best == null || p.totalTps > best.totalTps)) {
                    best = p;
                }
            }
            return best == null ? null : best.parallel;
        }
    }

    private final List<Series> series = new ArrayList<Series>();

    public ThroughputChart() {
        setOpaque(false);
        setPreferredSize(new Dimension(480, 260));
        setMinimumSize(new Dimension(240, 160));
    }

    /** Replace all series. */
    public void setSeries(List<Series> newSeries) {
        series.clear();
        series.addAll(newSeries);
        repaint();
    }

    /** Start a new line that {@link #addPoint} appends to, for a run in progress. */
    public void startSeries(String name) {
        series.add(new Series(name));
        repaint();
    }

    /** Append one point to the most recently started series. */
    public void addPoint(Point point) {
        if (series.isEmpty()) {
            startSeries("");
        }
        series.get(series.size() - 1).add(point);
        repaint();
    }

    public void clear() {
        series.clear();
        repaint();
    }

    public int pointCount() {
        int count = 0;
        for (Series s : series) {
            count += s.points.size();
        }
        return count;
    }

    /** Colour of series {@code index}; stable across repaints so legend and line agree. */
    static Color seriesColor(int index) {
        Theme.Tone[] tones = {Theme.Tone.INFO, Theme.Tone.SUCCESS, Theme.Tone.ERROR, Theme.Tone.WARNING,
            Theme.Tone.NEUTRAL};
        Color base = Theme.color(tones[index % tones.length]);
        int round = index / tones.length;
        return round == 0 ? base : Theme.blend(base, Theme.foreground(), Math.min(0.6f, 0.3f * round));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(Theme.baseFont());
            FontMetrics fm = g.getFontMetrics();
            int width = getWidth();
            int height = getHeight();
            boolean single = series.size() == 1;
            int legendHeight = single ? 0 : LEGEND_ROW * series.size();
            int plotX = PAD_LEFT;
            int plotY = PAD_TOP;
            int plotW = Math.max(10, width - PAD_LEFT - PAD_RIGHT);
            int plotH = Math.max(10, height - PAD_TOP - PAD_BOTTOM - legendHeight);

            if (pointCount() == 0) {
                g.setColor(Theme.mutedForeground());
                String text = "Run a benchmark to see tokens per second over parallel " + unitPlural() + ".";
                g.drawString(text, (width - fm.stringWidth(text)) / 2, height / 2);
                return;
            }

            int maxParallel = 1;
            double maxTotal = 1;
            double maxPerChat = 1;
            List<Integer> ticks = new ArrayList<Integer>();
            for (Series s : series) {
                for (Point p : s.points) {
                    maxParallel = Math.max(maxParallel, p.parallel);
                    maxTotal = Math.max(maxTotal, p.totalTps);
                    maxPerChat = Math.max(maxPerChat, p.perChatTps);
                    if (!ticks.contains(p.parallel)) {
                        ticks.add(p.parallel);
                    }
                }
            }
            Collections.sort(ticks);
            maxTotal = niceCeiling(maxTotal);
            maxPerChat = niceCeiling(maxPerChat);

            Color line = Theme.lineColor();
            Color muted = Theme.mutedForeground();
            Color totalColor = single ? seriesColor(0) : Theme.foreground();
            Color perChatColor = Theme.color(Theme.Tone.WARNING);

            // Grid and left axis (total tokens/s); right axis (per chat) only for a single series.
            for (int i = 0; i <= 4; i++) {
                int y = plotY + plotH - (int) Math.round(plotH * i / 4.0);
                g.setColor(line);
                g.drawLine(plotX, y, plotX + plotW, y);
                g.setColor(totalColor);
                String label = format(maxTotal * i / 4);
                g.drawString(label, plotX - 6 - fm.stringWidth(label), y + fm.getAscent() / 2 - 1);
                if (single) {
                    g.setColor(perChatColor);
                    g.drawString(format(maxPerChat * i / 4), plotX + plotW + 6, y + fm.getAscent() / 2 - 1);
                }
            }
            g.setColor(muted);
            int lastLabelEnd = Integer.MIN_VALUE;
            for (int tick : ticks) {
                int x = xOf(tick, maxParallel, plotX, plotW);
                String label = String.valueOf(tick);
                int start = x - fm.stringWidth(label) / 2;
                if (start > lastLabelEnd + 4) {
                    g.drawString(label, start, plotY + plotH + fm.getAscent() + 4);
                    lastLabelEnd = start + fm.stringWidth(label);
                }
            }
            String xTitle = "parallel " + unitPlural();
            g.drawString(xTitle, plotX + (plotW - fm.stringWidth(xTitle)) / 2,
                plotY + plotH + fm.getAscent() * 2 + 8);
            g.setColor(totalColor);
            g.drawString(embedding ? "total input tokens/s" : "total tokens/s", plotX, plotY - 10);
            if (single) {
                g.setColor(perChatColor);
                String right = "tokens/s per " + unit();
                g.drawString(right, plotX + plotW - fm.stringWidth(right), plotY - 10);
                drawSeries(g, series.get(0), perChatColor, maxParallel, maxPerChat, plotX, plotY, plotW, plotH,
                    false);
            }
            for (int i = 0; i < series.size(); i++) {
                drawSeries(g, series.get(i), seriesColor(i), maxParallel, maxTotal, plotX, plotY, plotW, plotH,
                    true);
            }
            for (int i = 0; i < series.size(); i++) {
                drawOptimum(g, series.get(i), seriesColor(i), maxParallel, maxTotal, plotX, plotY, plotW, plotH,
                    fm, single);
            }
            if (!single) {
                int y = plotY + plotH + fm.getAscent() * 2 + 14 + fm.getAscent();
                for (int i = 0; i < series.size(); i++) {
                    g.setColor(seriesColor(i));
                    g.fillRect(plotX, y - fm.getAscent() + 3, 10, 10);
                    g.setColor(Theme.foreground());
                    Series s = series.get(i);
                    Integer optimum = s.optimum();
                    String text = s.name + (optimum == null ? "" : "   optimum " + optimum + " " + unitPlural() + ", "
                        + format(totalAt(s, optimum)) + " tok/s");
                    g.drawString(text, plotX + 16, y);
                    y += LEGEND_ROW;
                }
            }
        } finally {
            g.dispose();
        }
    }

    /** Label the axes for embedding requests ({@code true}) or chats ({@code false}). */
    public void setEmbedding(boolean embedding) {
        if (this.embedding != embedding) {
            this.embedding = embedding;
            repaint();
        }
    }

    public boolean isEmbedding() {
        return embedding;
    }

    private String unit() {
        return embedding ? "request" : "chat";
    }

    private String unitPlural() {
        return embedding ? "requests" : "chats";
    }

    private static double totalAt(Series s, int parallel) {
        for (Point p : s.points) {
            if (p.parallel == parallel) {
                return p.totalTps;
            }
        }
        return 0;
    }

    private void drawOptimum(Graphics2D g, Series s, Color color, int maxParallel, double max, int plotX,
                             int plotY, int plotW, int plotH, FontMetrics fm, boolean label) {
        Integer optimum = s.optimum();
        if (optimum == null) {
            return;
        }
        int x = xOf(optimum, maxParallel, plotX, plotW);
        int y = yOf(totalAt(s, optimum), max, plotY, plotH);
        g.setColor(label ? Theme.color(Theme.Tone.SUCCESS) : color);
        g.setStroke(new BasicStroke(2f));
        g.draw(new Ellipse2D.Double(x - 8, y - 8, 16, 16));
        if (label) {
            String text = "optimum: " + optimum + " " + (optimum == 1 ? unit() : unitPlural()) + ", "
                + format(totalAt(s, optimum)) + " tok/s";
            int lx = Math.min(x + 12, plotX + plotW - fm.stringWidth(text));
            g.drawString(text, Math.max(plotX, lx), Math.max(plotY + fm.getAscent(), y - 12));
        }
    }

    private void drawSeries(Graphics2D g, Series s, Color color, int maxParallel, double max, int plotX, int plotY,
                            int plotW, int plotH, boolean total) {
        g.setColor(color);
        g.setStroke(new BasicStroke(2f));
        Path2D.Double path = new Path2D.Double();
        boolean first = true;
        for (Point p : s.points) {
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
        for (Point p : s.points) {
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
