package app.charka.map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * M-21: помощник переноса карты с картинки в текст {@link MapText} — сетка с номерами клеток поверх картинки и нарезка
 * на куски, чтобы агент читал картинку клетка за клеткой, а не угадывал координаты. Без Spring, в обычной сборке не
 * запускается (имя без «Test»):
 * <pre>
 * ./mvnw -q test -Dtest=MapImageGrid -Dimage=plan.png                       # оценить шаг сетки
 * ./mvnw -q test -Dtest=MapImageGrid -Dimage=plan.png -Dcell=70 -Doffset=12,8 -Dtile=16
 * ./mvnw -q test -Dtest=MapImageGrid -Dimage=shot.png -Dcrop=50,240,700,450   # сначала вырезать область карты
 * </pre>
 * {@code cell} — сторона клетки в пикселях, {@code offset} — где левый верхний угол первой клетки, {@code tile} — клеток
 * в куске. Итог в {@code target/map-image/}: {@code grid.png} (вся картинка с сеткой), {@code tile-<ряд>-<столбец>.png}
 * (куски с номерами клеток по краям), {@code info.txt} (размер, оценка шага сетки).
 */
class MapImageGrid {

    @Test
    void grid() throws IOException {
        String image = System.getProperty("image");
        Assumptions.assumeTrue(image != null, "-Dimage=путь к картинке");
        BufferedImage source = ImageIO.read(new File(image));
        if (source == null) {
            throw new IllegalArgumentException("Не картинка: " + image);
        }
        // -Dcrop=x,y,w,h — только область карты (без рамки, легенды, интерфейса)
        String crop = System.getProperty("crop");
        if (crop != null) {
            String[] c = crop.split(",");
            source = source.getSubimage(Integer.parseInt(c[0].strip()), Integer.parseInt(c[1].strip()),
                    Integer.parseInt(c[2].strip()), Integer.parseInt(c[3].strip()));
        }
        Path out = Path.of("target", "map-image");
        Files.createDirectories(out);
        int guessX = period(source, true);
        int guessY = period(source, false);
        double cell = Double.parseDouble(System.getProperty("cell", String.valueOf(Math.max(8, Math.max(guessX, guessY)))));
        String[] offset = System.getProperty("offset", "0,0").split(",");
        int ox = Integer.parseInt(offset[0].strip());
        int oy = Integer.parseInt(offset[1].strip());
        int tile = Integer.parseInt(System.getProperty("tile", "16"));
        int cols = (int) ((source.getWidth() - ox) / cell);
        int rows = (int) ((source.getHeight() - oy) / cell);

        BufferedImage marked = overlay(source, cell, ox, oy, cols, rows);
        ImageIO.write(marked, "png", out.resolve("grid.png").toFile());
        int tiles = 0;
        for (int ty = 0; ty * tile < rows; ty++) {
            for (int tx = 0; tx * tile < cols; tx++) {
                int x0 = (int) Math.round(ox + tx * tile * cell);
                int y0 = (int) Math.round(oy + ty * tile * cell);
                int w = Math.min(source.getWidth() - x0, (int) Math.round(Math.min(tile, cols - tx * tile) * cell));
                int h = Math.min(source.getHeight() - y0, (int) Math.round(Math.min(tile, rows - ty * tile) * cell));
                ImageIO.write(withMargin(marked.getSubimage(x0, y0, w, h), tx * tile, ty * tile, cell), "png",
                        out.resolve("tile-" + ty + "-" + tx + ".png").toFile());
                tiles++;
            }
        }
        String info = "Картинка " + source.getWidth() + "×" + source.getHeight() + " px\n"
                + "Оценка шага сетки: по ширине " + guessX + " px, по высоте " + guessY + " px (0 — сетка не видна)\n"
                + "Взято: клетка " + cell + " px, начало " + ox + "," + oy + " → " + cols + "×" + rows + " клеток\n"
                + "Куски по " + tile + " клеток: " + tiles + " шт. в " + out.toAbsolutePath() + "\n";
        Files.writeString(out.resolve("info.txt"), info, StandardCharsets.UTF_8);
        System.out.println(info);
    }

    /** Сетка поверх картинки: тонкие линии, каждая пятая — толще, номера клеток по верху и слева */
    private static BufferedImage overlay(BufferedImage source, double cell, int ox, int oy, int cols, int rows) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        for (int i = 0; i <= cols; i++) {
            g.setColor(i % 5 == 0 ? new Color(255, 0, 255, 200) : new Color(255, 0, 255, 90));
            g.setStroke(new BasicStroke(i % 5 == 0 ? 2f : 1f));
            g.drawLine((int) Math.round(ox + i * cell), oy, (int) Math.round(ox + i * cell), (int) Math.round(oy + rows * cell));
        }
        for (int j = 0; j <= rows; j++) {
            g.setColor(j % 5 == 0 ? new Color(255, 0, 255, 200) : new Color(255, 0, 255, 90));
            g.setStroke(new BasicStroke(j % 5 == 0 ? 2f : 1f));
            g.drawLine(ox, (int) Math.round(oy + j * cell), (int) Math.round(ox + cols * cell), (int) Math.round(oy + j * cell));
        }
        g.dispose();
        return copy;
    }

    /** Кусок с полями, где подписаны номера клеток: столбцы сверху, ряды слева */
    private static BufferedImage withMargin(BufferedImage part, int firstX, int firstY, double cell) {
        int margin = 28;
        BufferedImage out = new BufferedImage(part.getWidth() + margin, part.getHeight() + margin, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.drawImage(part, margin, margin, null);
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) Math.max(9, Math.min(14, cell / 3))));
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        for (int i = 0; i * cell < part.getWidth(); i++) {
            g.drawString(String.valueOf(firstX + i), (int) Math.round(margin + i * cell + 2), margin - 8);
        }
        for (int j = 0; j * cell < part.getHeight(); j++) {
            g.drawString(String.valueOf(firstY + j), 2, (int) Math.round(margin + j * cell + cell / 2 + 4));
        }
        g.dispose();
        return out;
    }

    /**
     * Шаг сетки по автокорреляции яркостных перепадов вдоль оси; 0 — повторения не видно.
     */
    static int period(BufferedImage image, boolean alongX) {
        int length = alongX ? image.getWidth() : image.getHeight();
        int across = alongX ? image.getHeight() : image.getWidth();
        double[] profile = new double[length];
        for (int i = 1; i < length; i++) {
            double sum = 0;
            for (int j = 0; j < across; j += Math.max(1, across / 400)) {
                int a = alongX ? image.getRGB(i, j) : image.getRGB(j, i);
                int b = alongX ? image.getRGB(i - 1, j) : image.getRGB(j, i - 1);
                sum += Math.abs(brightness(a) - brightness(b));
            }
            profile[i] = sum;
        }
        double mean = 0;
        for (double v : profile) {
            mean += v / length;
        }
        int maxLag = Math.min(300, length / 3);
        if (maxLag < 6) {
            return 0;
        }
        double[] score = new double[maxLag + 2];
        double top = 0;
        for (int lag = 5; lag <= maxLag + 1 && lag < length; lag++) {
            double sum = 0;
            for (int i = 0; i + lag < length; i++) {
                sum += (profile[i] - mean) * (profile[i + lag] - mean);
            }
            score[lag] = sum / (length - lag);
            if (lag <= maxLag) {
                top = Math.max(top, score[lag]);
            }
        }
        // кратные шагу сетки сдвиги тоже коррелируют: берём самый короткий заметный пик
        for (int lag = 6; lag <= maxLag; lag++) {
            if (score[lag] > 0 && score[lag] >= 0.5 * top && score[lag] >= score[lag - 1] && score[lag] >= score[lag + 1]) {
                return lag;
            }
        }
        return 0;
    }

    private static double brightness(int rgb) {
        return ((rgb >> 16) & 0xff) * 0.3 + ((rgb >> 8) & 0xff) * 0.59 + (rgb & 0xff) * 0.11;
    }
}
