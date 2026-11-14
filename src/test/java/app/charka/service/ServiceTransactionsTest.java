package app.charka.service;

import app.charka.support.CharkaTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.context.ApplicationContext;


import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @Transactional} объявлен на интерфейсах сервисов: без прокси изменения сущностей не сохранились бы.
 */
@CharkaTest
@DisplayName("Транзакции сервисов")
class ServiceTransactionsTest {

    @Autowired
    private ApplicationContext context;

    @ParameterizedTest(name = "{0}")
    @ValueSource(classes = {CharacterService.class, ChronicleService.class, InventoryService.class,
            ItemService.class, MoneyService.class, WoundService.class})
    @DisplayName("аннотация на интерфейсе создаёт транзакционный прокси")
    void proxied(Class<?> service) {
        assertThat(AopUtils.isAopProxy(context.getBean(service))).isTrue();
    }
}
