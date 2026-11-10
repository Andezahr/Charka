package app.charka.system;

import java.util.List;

/**
 * Раздел справки правил (A-11): «Модификаторы атаки, с. 99» и строки «Цель неподвижна · +4».
 * Содержимое собирает система из своих классов правил, а не текстом в шаблоне.
 *
 * @param page страница книги; null — своё правило
 */
public record Reference(String title, String page, List<Line> lines) {

    /**
     * @param value число или короткий итог; null — строка-пояснение
     */
    public record Line(String label, String value) {
    }
}
