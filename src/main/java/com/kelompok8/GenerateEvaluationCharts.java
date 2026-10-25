package com.kelompok8;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/**
 * Generator Grafik Evaluasi Komparatif Kinerja Penjadwalan.
 *
 * Menghasilkan grafik beresolusi tinggi (Full HD / 1200x800) dengan tipografi modern
 * untuk visualisasi hasil eksperimen sesuai arahan Bu Henning:
 * 1. Makespan vs Jumlah Task (Sintetis 100 - 1.000)
 * 2. Makespan vs Jumlah Task (Sintetis Skala Besar 1.000 - 10.000)
 * 3. Makespan vs Jumlah Task (GoCJ Trace 100 - 1.000)
 * 4. Konsumsi Energi SPECpower (kWh) vs Jumlah Task
 * 5. Average Waiting Time (Waktu Antre) vs Jumlah Task
 * 6. Load Balance Imbalance (Std Dev Beban VM) vs Jumlah Task
 */
public class GenerateEvaluationCharts {

    private static final int WIDTH = 1200;
    private static final int HEIGHT = 750;
    private static final String IMAGES_DIR = "docs/images";

    public static void main(String[] args) throws Exception {
        Files.createDirectories(Path.of(IMAGES_DIR));
        System.out.println("Membangkitkan Grafik Evaluasi Metrik...");

        // 1. Chart Makespan Sintetis (100 - 1.000)
        generateLineChart(
                "results/batch_100_1000_results.csv",
                "Synthetic",
                "Makespan (detik)",
                "Jumlah Task (Cloudlets)",
                "Perbandingan Makespan vs Skala Task (Dataset Sintetis 100 - 1.000)",
                IMAGES_DIR + "/grafik_makespan_sintetis_100_1000.png",
                "AvgMakespan(s)");

        // 2. Chart Makespan Sintetis Skala Besar (1.000 - 10.000)
        generateLineChart(
                "results/synthetic_1000_10000_results.csv",
                "Synthetic",
                "Makespan (detik)",
                "Jumlah Task (Cloudlets)",
                "Perbandingan Makespan vs Skala Besar (Dataset Sintetis 1.000 - 10.000)",
                IMAGES_DIR + "/grafik_makespan_sintetis_1000_10000.png",
                "AvgMakespan(s)");

        // 3. Chart Makespan NASA iPSC (100 - 1.000)
        generateLineChart(
                "results/batch_100_1000_results.csv",
                "NASA-iPSC",
                "Makespan (detik)",
                "Jumlah Task (Cloudlets)",
                "Perbandingan Makespan vs Skala Task (Dataset NASA iPSC 100 - 1.000)",
                IMAGES_DIR + "/grafik_makespan_nasa_100_1000.png",
                "AvgMakespan(s)");

        // 3b. Chart Makespan GoCJ (100 - 1.000) [Optional / Tambahan jika ada]
        if (new File("results/gocj_100_1000_results.csv").exists()) {
            generateLineChart(
                    "results/gocj_100_1000_results.csv",
                    "GoCJ",
                    "Makespan (detik)",
                    "Jumlah Task (Cloudlets)",
                    "Perbandingan Makespan vs Skala Task (Dataset GoCJ 100 - 1.000)",
                    IMAGES_DIR + "/grafik_makespan_gocj_100_1000.png",
                    "AvgMakespan(s)");
        }

        // 4. Chart Energi SPECpower (1.000 - 10.000)
        generateLineChart(
                "results/synthetic_1000_10000_results.csv",
                "Synthetic",
                "Total Konsumsi Energi (kWh)",
                "Jumlah Task (Cloudlets)",
                "Perbandingan Konsumsi Energi SPECpower (Dataset Sintetis 1.000 - 10.000)",
                IMAGES_DIR + "/grafik_energi_specpower_1000_10000.png",
                "AvgEnergy(kWh)");

        // 5. Chart Waiting Time (NASA iPSC 100 - 1.000)
        generateLineChart(
                "results/batch_100_1000_results.csv",
                "NASA-iPSC",
                "Rata-rata Waktu Tunggu / Waiting Time (detik)",
                "Jumlah Task (Cloudlets)",
                "Evaluasi Eliminasi Waktu Antre (Dataset NASA iPSC 100 - 1.000)",
                IMAGES_DIR + "/grafik_waiting_time_nasa_100_1000.png",
                "AvgWaiting(s)");

        // 6. Chart Load Balance StdDev (1.000 - 10.000)
        generateLineChart(
                "results/synthetic_1000_10000_results.csv",
                "Synthetic",
                "Derajat Ketimpangan Beban / Load Balance (Std Dev)",
                "Jumlah Task (Cloudlets)",
                "Pemerataan Distribusi Beban Antar-VM (Dataset Sintetis 1.000 - 10.000)",
                IMAGES_DIR + "/grafik_load_balance_1000_10000.png",
                "AvgLoadBalanceStdDev");

        System.out.println("Seluruh grafik berhasil disimpan ke: " + IMAGES_DIR);
    }

    public static void generateLineChart(
            String csvPath, String targetDataset, String yLabel, String xLabel,
            String title, String outputPath, String valueColumn) throws IOException {

        List<DataPoint> peftPoints = new ArrayList<>();
        List<DataPoint> fcfsPoints = new ArrayList<>();
        List<DataPoint> rrPoints   = new ArrayList<>();

        try (BufferedReader br = Files.newBufferedReader(Path.of(csvPath))) {
            String headerLine = br.readLine();
            String[] headers = headerLine.split(",");
            int algoIdx = getColIdx(headers, "Algorithm");
            int datasetIdx = getColIdx(headers, "Dataset");
            int taskCountIdx = getColIdx(headers, "TaskCount");
            int valIdx = getColIdx(headers, valueColumn);

            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(",");
                if (parts.length <= valIdx) continue;

                String algo = parts[algoIdx].trim();
                String ds = parts[datasetIdx].trim();
                int tasks = Integer.parseInt(parts[taskCountIdx].trim());
                double val = Double.parseDouble(parts[valIdx].trim());

                if (ds.equalsIgnoreCase(targetDataset)) {
                    DataPoint dp = new DataPoint(tasks, val);
                    if (algo.equalsIgnoreCase("PEFT")) peftPoints.add(dp);
                    else if (algo.equalsIgnoreCase("FCFS")) fcfsPoints.add(dp);
                    else if (algo.equalsIgnoreCase("RR") || algo.equalsIgnoreCase("ROUNDROBIN")) rrPoints.add(dp);
                }
            }
        }

        peftPoints.sort(Comparator.comparingInt(p -> p.x));
        fcfsPoints.sort(Comparator.comparingInt(p -> p.x));
        rrPoints.sort(Comparator.comparingInt(p -> p.x));

        // Render BufferedImage
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        // Background
        g.setColor(new Color(250, 252, 255));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        int padLeft = 110;
        int padRight = 50;
        int padTop = 110;
        int padBottom = 110;

        int plotW = WIDTH - padLeft - padRight;
        int plotH = HEIGHT - padTop - padBottom;

        // Plot Box Background
        g.setColor(Color.WHITE);
        g.fillRoundRect(padLeft, padTop, plotW, plotH, 8, 8);
        g.setColor(new Color(220, 226, 235));
        g.drawRoundRect(padLeft, padTop, plotW, plotH, 8, 8);

        // Calculate Min and Max
        double maxY = 0.0;
        for (DataPoint p : peftPoints) maxY = Math.max(maxY, p.y);
        for (DataPoint p : fcfsPoints) maxY = Math.max(maxY, p.y);
        for (DataPoint p : rrPoints) maxY = Math.max(maxY, p.y);
        maxY = maxY == 0.0 ? 1.0 : maxY * 1.15; // 15% headroom

        // Draw Grid Lines
        int numYDivs = 6;
        g.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        FontMetrics fm = g.getFontMetrics();

        for (int i = 0; i <= numYDivs; i++) {
            double yVal = (maxY / numYDivs) * i;
            int yPos = padTop + plotH - (int) ((yVal / maxY) * plotH);

            g.setColor(new Color(238, 242, 246));
            g.drawLine(padLeft + 1, yPos, padLeft + plotW - 1, yPos);

            g.setColor(new Color(110, 120, 135));
            String lbl = String.format("%,.1f", yVal);
            if (maxY > 100) lbl = String.format("%,.0f", yVal);
            g.drawString(lbl, padLeft - fm.stringWidth(lbl) - 12, yPos + 4);
        }

        // X Labels
        int nPoints = peftPoints.size();
        for (int i = 0; i < nPoints; i++) {
            int xVal = peftPoints.get(i).x;
            int xPos = padLeft + (int) ((double) i / (nPoints - 1) * plotW);

            g.setColor(new Color(238, 242, 246));
            g.drawLine(xPos, padTop + 1, xPos, padTop + plotH - 1);

            g.setColor(new Color(110, 120, 135));
            String lbl = String.format("%,d", xVal);
            g.drawString(lbl, xPos - fm.stringWidth(lbl) / 2, padTop + plotH + 22);
        }

        // Title and Subtitle
        g.setColor(new Color(24, 43, 73));
        g.setFont(new Font("Segoe UI", Font.BOLD, 22));
        g.drawString(title, padLeft, 50);

        g.setColor(new Color(100, 116, 139));
        g.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        g.drawString("Mata Kuliah SOKA 2026 — Kelompok 8 (Simulator CloudSim Plus 8.0, 2 Datacenter, 3x Run Rata-rata)",
                padLeft, 76);

        // Y Label
        g.setColor(new Color(51, 65, 85));
        g.setFont(new Font("Segoe UI", Font.BOLD, 13));
        AffineTransform orig = g.getTransform();
        g.rotate(-Math.PI / 2);
        g.drawString(yLabel, -(padTop + plotH / 2 + fm.stringWidth(yLabel) / 2), 35);
        g.setTransform(orig);

        // X Label
        g.drawString(xLabel, padLeft + plotW / 2 - fm.stringWidth(xLabel) / 2, padTop + plotH + 55);

        // Draw Lines
        Color colPeft = new Color(34, 197, 94);   // Emerald Green
        Color colFcfs = new Color(239, 68, 68);   // Red
        Color colRr   = new Color(59, 130, 246);  // Blue

        drawSeries(g, fcfsPoints, colFcfs, maxY, padLeft, padTop, plotW, plotH, false);
        drawSeries(g, rrPoints, colRr, maxY, padLeft, padTop, plotW, plotH, false);
        drawSeries(g, peftPoints, colPeft, maxY, padLeft, padTop, plotW, plotH, true); // Primary

        // Legend at bottom
        drawLegend(g, padLeft, padTop + plotH + 70, colPeft, colFcfs, colRr);

        g.dispose();
        ImageIO.write(img, "png", new File(outputPath));
        System.out.println("  [Grafik] Tersimpan: " + outputPath);
    }

    private static void drawSeries(
            Graphics2D g, List<DataPoint> points, Color color, double maxY,
            int padLeft, int padTop, int plotW, int plotH, boolean isThick) {

        if (points.size() < 2) return;
        int n = points.size();

        g.setStroke(new BasicStroke(isThick ? 3.5f : 2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(color);

        Path2D.Double path = new Path2D.Double();
        for (int i = 0; i < n; i++) {
            double xPos = padLeft + ((double) i / (n - 1) * plotW);
            double yPos = padTop + plotH - ((points.get(i).y / maxY) * plotH);
            if (i == 0) path.moveTo(xPos, yPos);
            else path.lineTo(xPos, yPos);
        }
        g.draw(path);

        // Draw Points
        int r = isThick ? 6 : 5;
        for (int i = 0; i < n; i++) {
            int xPos = padLeft + (int) ((double) i / (n - 1) * plotW);
            int yPos = padTop + plotH - (int) ((points.get(i).y / maxY) * plotH);

            g.setColor(Color.WHITE);
            g.fillOval(xPos - r, yPos - r, r * 2, r * 2);
            g.setColor(color);
            g.setStroke(new BasicStroke(2.0f));
            g.drawOval(xPos - r, yPos - r, r * 2, r * 2);
        }
    }

    private static void drawLegend(Graphics2D g, int x, int y, Color peft, Color fcfs, Color rr) {
        g.setFont(new Font("Segoe UI", Font.BOLD, 13));
        int curX = x;

        // PEFT
        g.setColor(peft);
        g.fillRoundRect(curX, y, 16, 16, 4, 4);
        g.setColor(new Color(30, 41, 59));
        g.drawString("PEFT (Diusulkan - Optimal)", curX + 24, y + 13);
        curX += 230;

        // FCFS
        g.setColor(fcfs);
        g.fillRoundRect(curX, y, 16, 16, 4, 4);
        g.setColor(new Color(30, 41, 59));
        g.drawString("FCFS (Baseline 1)", curX + 24, y + 13);
        curX += 180;

        // RR
        g.setColor(rr);
        g.fillRoundRect(curX, y, 16, 16, 4, 4);
        g.setColor(new Color(30, 41, 59));
        g.drawString("Round Robin (Baseline 2)", curX + 24, y + 13);
    }

    private static int getColIdx(String[] headers, String name) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].trim().equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    public static class DataPoint {
        public int x;
        public double y;
        public DataPoint(int x, double y) {
            this.x = x;
            this.y = y;
        }
    }
}
