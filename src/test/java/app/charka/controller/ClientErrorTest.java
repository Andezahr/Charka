package app.charka.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
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
        String script = new ClassPathResource("static/js/app.js").getContentAsString(StandardCharsets.UTF_8);

        assertThat(script).contains("const REQUEST_TIMEOUT = 20000;", "controller.abort()", "// A-14: что бы ни случилось с ответом",
                "safely('chat', initChat);", "safely('initPage', initPage);");
    }
}
