package app.charka.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InPlaceRedirectFilterTest {

    private final InPlaceRedirectFilter filter = new InPlaceRedirectFilter();

    /** Ответ контроллера на redirect: так его отправляет сам Spring, с якорем в адресе */
    private MockHttpServletResponse redirect(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            try {
                new RedirectView("/character/5#item-7", true)
                        .render(Map.of(), (MockHttpServletRequest) req, (HttpServletResponse) res);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        return response;
    }

    @Nested
    class Redirects {

        @Test
        void inPlaceRequestGetsAddressWithAnchorInHeader() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/character/5/money");
            request.addHeader(InPlaceRedirectFilter.REQUEST_HEADER, "1");

            MockHttpServletResponse response = redirect(request);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NO_CONTENT);
            assertThat(response.getHeader(InPlaceRedirectFilter.LOCATION_HEADER)).isEqualTo("/character/5#item-7");
            assertThat(response.getRedirectedUrl()).isNull();
        }

        @Test
        void browserRequestGetsOrdinaryRedirect() throws Exception {
            MockHttpServletResponse response = redirect(new MockHttpServletRequest("POST", "/character/5/money"));

            assertThat(response.getRedirectedUrl()).isEqualTo("/character/5#item-7");
            assertThat(response.getHeader(InPlaceRedirectFilter.LOCATION_HEADER)).isNull();
        }
    }

    @Nested
    class OtherResponses {

        @Test
        void pageIsPassedThroughUnchanged() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/character/5");
            request.addHeader(InPlaceRedirectFilter.REQUEST_HEADER, "1");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, (req, res) -> res.getWriter().write("<html></html>"));

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(response.getContentAsString()).isEqualTo("<html></html>");
            assertThat(response.getHeader(InPlaceRedirectFilter.LOCATION_HEADER)).isNull();
        }
    }
}
