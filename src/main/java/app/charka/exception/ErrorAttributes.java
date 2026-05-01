package app.charka.exception;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.WebRequest;
import java.util.Map;

@Component
public class ErrorAttributes extends DefaultErrorAttributes {
    @Override
    public Map<String, Object> getErrorAttributes(WebRequest request, ErrorAttributeOptions options) {
        // Разрешаем только безопасные поля
        ErrorAttributeOptions quiet = options.including(
                ErrorAttributeOptions.Include.MESSAGE,
                ErrorAttributeOptions.Include.PATH,
                ErrorAttributeOptions.Include.STATUS,
                ErrorAttributeOptions.Include.ERROR
        );
        return super.getErrorAttributes(request, quiet);
    }
}