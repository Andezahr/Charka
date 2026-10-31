package app.charka.system;

import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.system.cp2020.Cp2020System;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GameSystemRegistry")
class GameSystemRegistryTest {

    private GameSystemRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new GameSystemRegistry(List.of(new GenericSystem(), new Cp2020System(null, null)));
    }

    @Test
    @DisplayName("находит обе системы по коду")
    void findsByCode() {
        assertThat(registry.get("GENERIC")).isInstanceOf(GenericSystem.class);
        assertThat(registry.get("CP2020")).isInstanceOf(Cp2020System.class);
        assertThat(registry.getAll()).hasSize(2);
    }

    @Test
    @DisplayName("неизвестный код — ошибка")
    void unknownCode() {
        assertThatThrownBy(() -> registry.get("DND5E"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DND5E");
        assertThatThrownBy(() -> registry.get(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("система персонажа — система кампании; без кампании или с неизвестной — универсальная")
    void forCharacter() {
        Character character = new Character();
        assertThat(registry.forCharacter(character).getCode()).isEqualTo(GenericSystem.CODE);

        Campaign campaign = new Campaign();
        campaign.setSystem(Cp2020System.CODE);
        character.setCampaign(campaign);
        assertThat(registry.forCharacter(character).getCode()).isEqualTo(Cp2020System.CODE);

        campaign.setSystem("REMOVED");
        assertThat(registry.forCharacter(character).getCode()).isEqualTo(GenericSystem.CODE);
    }

    @Test
    @DisplayName("валюта и счётчики по умолчанию у систем")
    void currencyAndDefaults() {
        assertThat(registry.get("CP2020").getCurrency().symbol()).isEqualTo("eb");
        assertThat(registry.get("CP2020").getDefaultCounterTypes())
                .containsExactly("CP2020_STATS", "CP2020_ARMOR", "CP2020_HEALTH", "CP2020_IP");
        assertThat(registry.get("GENERIC").getDefaultCounterTypes()).isEmpty();
        assertThat(registry.get("CP2020").getQuickDice()).containsExactly("d10", "d6", "2d6", "3d6");
        assertThat(registry.get("GENERIC").getQuickDice()).contains("d20", "d100");
    }
}
