package app.charka.system.cp2020;

import app.charka.map.MapAudit;
import app.charka.map.MapDoc;
import app.charka.map.MapGenerator;
import app.charka.map.MapRules;
import app.charka.map.MapText;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * M-21: проверка карт для агента и автора, без Spring — запускается за секунды после сборки:
 * <pre>
 * ./mvnw -q test -Dtest=MapFileCheck -Dmap=путь/к/карте.map.txt   # один файл или папка
 * ./mvnw -q test -Dtest=MapFileCheck -Dmap=gen:office             # шаблон генератора
 * ./mvnw -q test -Dtest=MapFileCheck                              # все карты src/main/resources/maps
 * </pre>
 * Итог печатается и пишется в {@code target/map-check.txt}. Ошибки чтения — с номерами строк, предупреждения
 * формата и проверка {@link MapAudit}. Имя класса без «Test» — в обычной сборке не запускается.
 */
class MapFileCheck {

    private final MapRules rules = new Cp2020MapRules();

    @Test
    void check() throws IOException {
        String target = System.getProperty("map", "src/main/resources/maps");
        StringBuilder out = new StringBuilder();
        if (target.startsWith("gen:")) {
            String code = target.substring(4);
            MapGenerator.Template template = MapGenerator.template(code);
            report(out, "шаблон " + code, MapText.Parsed.class.cast(null), new MapGenerator()
                    .generate(code, template.width(), template.height(), 1, rules), List.of());
        } else {
            Path path = Path.of(target);
            List<Path> files = new ArrayList<>();
            if (Files.isDirectory(path)) {
                try (Stream<Path> list = Files.list(path)) {
                    list.filter(file -> file.toString().endsWith(MapText.EXTENSION)).sorted().forEach(files::add);
                }
            } else {
                files.add(path);
            }
            for (Path file : files) {
                try {
                    MapText.Parsed parsed = MapText.parse(Files.readString(file, StandardCharsets.UTF_8), rules);
                    report(out, file.toString(), parsed, parsed.doc(), parsed.warnings());
                } catch (IllegalArgumentException e) {
                    out.append("=== ").append(file).append(" — НЕ ЧИТАЕТСЯ\n").append(e.getMessage()).append("\n\n");
                }
            }
        }
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/map-check.txt"), out.toString(), StandardCharsets.UTF_8);
        System.out.println(out);
    }

    private void report(StringBuilder out, String title, MapText.Parsed parsed, MapDoc doc, List<String> warnings) {
        MapAudit.Report report = MapAudit.of(doc, rules);
        out.append("=== ").append(title);
        if (parsed != null && parsed.name() != null) {
            out.append(" — ").append(parsed.name());
        }
        out.append('\n');
        warnings.forEach(warning -> out.append("⚠ формат: ").append(warning).append('\n'));
        out.append(report.text());
        out.append("Итог: ").append(warnings.isEmpty() && report.warnings() == 0 ? "можно играть"
                : "замечаний " + (warnings.size() + report.warnings())).append("\n\n");
    }
}
