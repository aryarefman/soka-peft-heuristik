package com.kelompok8;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

/**
 * Menggambar diagram arsitektur (Datacenter -> Host -> VM -> Cloudlet) langsung dari
 * konfigurasi {@link Skenario}, sehingga gambar selalu sesuai dengan kode.
 *
 * Keluaran: docs/images/{skenario}/arsitektur_cloud.png (font Poppins dari folder fonts/).
 * Argumen: [3dc|2dc]
 */
public class RenderArsitektur {

    private static final int BASE_W = 530;
    private static final double SCALE = 2.0;
    private static final int CARD_W = 156;
    private static final int CARD_GAP = 16;

    private static final Color DARK = new Color(15, 23, 42);
    private static final Color MUTED = new Color(100, 116, 139);
    private static final Color BORDER = new Color(30, 41, 59);
    private static final Color BADGE_BG = new Color(246, 247, 249);
    private static final Color BADGE_TEXT = new Color(75, 85, 99);
    private static final Color ACCENT = new Color(225, 112, 0);

    private static Font regular, medium, semi, bold;

    public static void main(String[] args) throws Exception {
        Skenario s = Skenario.dariArgs(args);
        regular = Font.createFont(Font.TRUETYPE_FONT, new File("fonts/Poppins-Regular.ttf"));
        medium = Font.createFont(Font.TRUETYPE_FONT, new File("fonts/Poppins-Medium.ttf"));
        semi = Font.createFont(Font.TRUETYPE_FONT, new File("fonts/Poppins-SemiBold.ttf"));
        bold = Font.createFont(Font.TRUETYPE_FONT, new File("fonts/Poppins-Bold.ttf"));

        // Jumlah host per tipe dan jumlah VM per kelas, dihitung dari skenario
        Map<Integer, Integer> hostCount = new TreeMap<>();
        Map<String, int[]> vmClasses = new LinkedHashMap<>(); // kelas -> {count, pes, ramMB}
        for (Skenario.DcSpec dc : s.datacenters) {
            for (int t : dc.hostPlan()) hostCount.merge(t, 1, Integer::sum);
            vmClasses.merge(dc.vmClass(), new int[] {dc.vmCount(), dc.vmPes(), dc.vmRamMB()},
                    (a, b) -> new int[] {a[0] + b[0], a[1], a[2]});
        }

        int hostRows = (hostCount.size() + 2) / 3;
        int baseH = 640 + hostRows * 110;
        BufferedImage img = new BufferedImage((int) (BASE_W * SCALE), (int) (baseH * SCALE), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.scale(SCALE, SCALE);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, BASE_W, baseH);

        int cx = BASE_W / 2;
        g.setColor(DARK);
        g.setFont(bold.deriveFont(22f));
        drawCentered(g, "Arsitektur Cloud (" + s.id + ")", cx, 38);
        drawBreadcrumb(g, cx, 60);

        // Datacenter
        int y = 90;
        drawBox(g, cx - 150, y, 300, 92);
        g.setColor(DARK);
        g.setFont(semi.deriveFont(15.5f));
        drawCentered(g, "Datacenter", cx, y + 30);
        g.setColor(MUTED);
        g.setFont(regular.deriveFont(12f));
        drawCentered(g, s.datacenters.size() + " datacenter heterogen", cx, y + 52);
        g.setFont(regular.deriveFont(10.5f));
        drawCentered(g, String.join(" · ", s.datacenters.stream()
                .map(dc -> dc.name().replaceAll("^DC-\\d+: ", "")).toList()), cx, y + 72);
        y += 92;

        int hostsPerDc = s.datacenters.get(0).hostPlan().length;
        y = pillWithArrow(g, List.of(String.format("menaungi %d host heterogen (%d host / DC, %d PE / host)",
                s.totalHosts(), hostsPerDc, s.pesPerHost)), cx, y);

        // Host
        List<Integer> types = new ArrayList<>(hostCount.keySet());
        for (int row = 0; row < hostRows; row++) {
            List<Integer> rowTypes = types.subList(row * 3, Math.min(types.size(), row * 3 + 3));
            int x = rowStartX(rowTypes.size());
            for (int t : rowTypes) {
                drawCard(g, x, y, 94, "Tipe " + Skenario.HOST_TYPE_NAMES[t], "×" + hostCount.get(t),
                        String.format("%,d MIPS/core", s.hostTypes[t][0]).replace(',', '.'),
                        s.hostTypes[t][1] + " GB RAM · 10 Gbps");
                x += CARD_W + CARD_GAP;
            }
            y += 94 + 16;
        }
        y -= 16;

        int vmsPerHost = s.totalVms() / s.totalHosts();
        String penempatan = s.totalVms() % s.totalHosts() == 0
                ? "tepat " + vmsPerHost + " VM per host, tidak ada host menganggur"
                : "tersebar merata, tidak ada host menganggur";
        y = pillWithArrow(g, List.of("VM dialokasikan ke host secara round-robin", penempatan), cx, y);

        // VM
        int x = rowStartX(vmClasses.size());
        for (var e : vmClasses.entrySet()) {
            int[] v = e.getValue();
            drawCard(g, x, y, 82, e.getKey(), "×" + v[0], v[1] + " PE · " + (v[2] / 1024) + " GB RAM", null);
            x += CARD_W + CARD_GAP;
        }
        y += 82;

        y = pillWithArrow(g, List.of("setiap task dieksekusi pada 1 VM, non-preemptive"), cx, y);

        // Cloudlet
        drawBox(g, cx - 210, y, 420, 118);
        g.setColor(ACCENT);
        g.setFont(bold.deriveFont(38f));
        drawCentered(g, "1.000", cx, y + 46);
        g.setColor(DARK);
        g.setFont(semi.deriveFont(15.5f));
        drawCentered(g, "Cloudlet / Task", cx, y + 73);
        g.setColor(MUTED);
        g.setFont(regular.deriveFont(11.5f));
        drawCentered(g, "per eksperimen utama · bag-of-tasks, panjang bervariasi (MI)", cx, y + 97);
        g.dispose();

        Path out = Path.of(s.imagesDir(), "arsitektur_cloud.png");
        Files.createDirectories(out.getParent());
        // Potong kanvas sesuai tinggi konten
        int usedH = (int) ((y + 118 + 24) * SCALE);
        ImageIO.write(img.getSubimage(0, 0, img.getWidth(), Math.min(usedH, img.getHeight())), "png", out.toFile());
        System.out.println("Diagram arsitektur tersimpan: " + out);
    }

    private static int rowStartX(int cards) {
        int rowW = cards * CARD_W + (cards - 1) * CARD_GAP;
        return (BASE_W - rowW) / 2;
    }

    /** Pil keterangan di antara dua lapisan, diikuti panah ke bawah. Mengembalikan y lapisan berikutnya. */
    private static int pillWithArrow(Graphics2D g, List<String> lines, int cx, int yTop) {
        g.setFont(regular.deriveFont(11.5f));
        FontMetrics fm = g.getFontMetrics();
        int tw = lines.stream().mapToInt(fm::stringWidth).max().orElse(0);
        int bh = fm.getHeight() * lines.size() + 10;
        int by = yTop + 18;
        g.setColor(BADGE_BG);
        g.fillRoundRect(cx - tw / 2 - 14, by, tw + 28, bh, 6, 6);
        g.setColor(BADGE_TEXT);
        for (int i = 0; i < lines.size(); i++) {
            drawCentered(g, lines.get(i), cx, by + 5 + fm.getAscent() + i * fm.getHeight() - 1);
        }
        int next = by + bh + 36;
        g.setColor(MUTED);
        g.setStroke(new BasicStroke(1.2f));
        g.drawLine(cx, by + bh, cx, next);
        g.fillPolygon(new int[] {cx, cx - 4, cx + 4}, new int[] {next, next - 8, next - 8}, 3);
        return next;
    }

    private static void drawCard(Graphics2D g, int x, int y, int h, String name, String count, String spec1, String spec2) {
        drawBox(g, x, y, CARD_W, h);
        Font titleF = semi.deriveFont(14.5f);
        Font countF = regular.deriveFont(12.5f);
        int tw = g.getFontMetrics(titleF).stringWidth(name);
        int cw = g.getFontMetrics(countF).stringWidth(" " + count);
        int startX = x + (CARD_W - tw - cw) / 2;
        g.setColor(DARK);
        g.setFont(titleF);
        g.drawString(name, startX, y + 33);
        g.setColor(MUTED);
        g.setFont(countF);
        g.drawString(" " + count, startX + tw, y + 33);
        g.setFont(regular.deriveFont(11.5f));
        drawCentered(g, spec1, x + CARD_W / 2, y + 58);
        if (spec2 != null) drawCentered(g, spec2, x + CARD_W / 2, y + 78);
    }

    private static void drawBox(Graphics2D g, int x, int y, int w, int h) {
        RoundRectangle2D rect = new RoundRectangle2D.Float(x, y, w, h, 12, 12);
        g.setColor(Color.WHITE);
        g.fill(rect);
        g.setColor(BORDER);
        g.setStroke(new BasicStroke(1.3f));
        g.draw(rect);
    }

    private static void drawBreadcrumb(Graphics2D g, int cx, int cy) {
        g.setFont(medium.deriveFont(12.5f));
        FontMetrics fm = g.getFontMetrics();
        String[] parts = {"Datacenter", "Host", "VM", "Cloudlet / Task"};
        int arrowW = 14, spacing = 8;
        int totalW = Arrays.stream(parts).mapToInt(fm::stringWidth).sum() + (parts.length - 1) * (arrowW + 2 * spacing);
        int x = cx - totalW / 2;
        g.setColor(MUTED);
        g.setStroke(new BasicStroke(1.2f));
        for (int i = 0; i < parts.length; i++) {
            g.drawString(parts[i], x, cy);
            x += fm.stringWidth(parts[i]);
            if (i < parts.length - 1) {
                x += spacing;
                int ay = cy - fm.getAscent() / 3;
                g.drawLine(x, ay, x + arrowW, ay);
                g.drawLine(x + arrowW - 3, ay - 3, x + arrowW, ay);
                g.drawLine(x + arrowW - 3, ay + 3, x + arrowW, ay);
                x += arrowW + spacing;
            }
        }
    }

    private static void drawCentered(Graphics2D g, String text, int x, int y) {
        g.drawString(text, x - g.getFontMetrics().stringWidth(text) / 2, y);
    }
}
