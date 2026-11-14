package app.charka.controller;

import app.charka.support.CharkaTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;



import org.springframework.core.io.ClassPathResource;

import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@CharkaTest
@DisplayName("Зависания на телефоне (A-14)")
class ClientErrorTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("Ошибка скрипта принимается в журнал сервера")
    void reported() throws Exception {
        mockMvc.perform(post("/client-error").param("page", "/character/1").param("message", "x is null").param("stack", "at initPage"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("У каждого запроса app.js есть срок, форма разблокируется в finally, части страницы настраиваются по отдельности")
    void scriptGuards() throws Exception {
        String script = frontScript();

        assertThat(script).contains("const REQUEST_TIMEOUT = 20000;", "controller.abort()", "// A-14: что бы ни случилось с ответом",
                "safely('chat', initChat);", "safely('initPage', initPage);");
    }

    /** ARC-02: фронт по частям — все части в порядке страницы (fragments/layout.html) */
    static String frontScript() throws java.io.IOException {
        StringBuilder all = new StringBuilder();
        for (String part : java.util.List.of("core", "page", "poll", "forms", "navigation", "battle", "map-draw", "battle-actions", "editor")) {
            all.append(new ClassPathResource("static/js/" + part + ".js").getContentAsString(StandardCharsets.UTF_8)).append(System.lineSeparator());
        }
        return all.toString();
    }
}
