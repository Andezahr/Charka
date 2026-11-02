package app.charka.system.cp2020;

import app.charka.model.counters.strategy.health.DiceRoller;

import java.util.ArrayList;
import java.util.List;

/**
 * Лайфпас CP2020 (с. 34–39): стиль, происхождение, семья, мотивация и событие на каждый год после 16.
 * Таблицы прочитаны с изображений страниц и пересказаны своими словами. Механика книги — в {@link Effect}:
 * деньги, потеря REF, COOL, ATTR; то, что требует выбора игрока (учитель, сэнсей), остаётся подсказкой в тексте.
 */
final class Cp2020Lifepath {

    static final int MIN_AGE = 16;
    static final int MAX_AGE = 99;

    /** Раздел: заголовок и строки */
    record Section(String title, List<String> lines) {
    }

    /** Родной язык на +8: название и семья языков из каталога навыков */
    record Language(String name, String family) {
    }

    /**
     * Что меняет событие: стат (код) на {@code delta} или деньги на {@code money}.
     */
    record Effect(String label, String stat, int delta, long money) {
    }

    record Result(int age, List<Section> sections, List<Language> languages, List<Effect> effects) {
    }

    private static final String[] CLOTHES = {"байкерская кожа", "джинсы", "корпоративные костюмы", "комбинезоны", "мини-юбки",
            "высокая мода", "камуфляж", "обычная одежда", "нагота", "стиль «бомж-шик»"};
    private static final String[] HAIR = {"ирокез", "длинные и растрёпанные", "короткие торчком", "дикие, во все стороны", "лысый",
            "полосатые", "крашеные", "аккуратные короткие", "короткие кудрявые", "длинные прямые"};
    private static final String[] AFFECTATIONS = {"татуировки", "зеркальные очки", "ритуальные шрамы", "перчатки с шипами",
            "кольца в носу", "серьги", "длинные ногти", "сапоги на шпильке", "странные линзы", "перчатки без пальцев"};

    private record Origin(String name, List<Language> languages) {
    }

    private static Language lang(String name, String family) {
        return new Language(name, family);
    }

    private static final Origin[] ORIGINS = {
            new Origin("англо-американец", List.of(lang("английский", "germanic"))),
            new Origin("африканец", List.of(lang("банту", "african"), lang("фанти", "african"), lang("конго", "african"),
                    lang("ашанти", "african"), lang("зулу", "african"), lang("суахили", "african"))),
            new Origin("японец или кореец", List.of(lang("японский", "japanese"), lang("корейский", "korean"))),
            new Origin("центральная Европа или бывший СССР", List.of(lang("болгарский", "slavic"), lang("русский", "slavic"),
                    lang("чешский", "slavic"), lang("польский", "slavic"), lang("украинский", "slavic"), lang("словацкий", "slavic"))),
            new Origin("житель тихоокеанских островов", List.of(lang("микронезийский", "pacific"), lang("тагальский", "pacific"),
                    lang("полинезийский", "pacific"), lang("малайский", "pacific"), lang("суданский", "pacific"),
                    lang("индонезийский", "pacific"), lang("гавайский", "pacific"))),
            new Origin("китаец или выходец из Юго-Восточной Азии", List.of(lang("бирманский", "sino_tibetan"),
                    lang("кантонский", "sino_tibetan"), lang("мандарин", "sino_tibetan"), lang("тайский", "sino_tibetan"),
                    lang("тибетский", "sino_tibetan"), lang("вьетнамский", "sino_tibetan"))),
            new Origin("чернокожий американец", List.of(lang("английский", "germanic"), lang("блэкфолк", "germanic"))),
            new Origin("латиноамериканец из США", List.of(lang("испанский", "romantic"), lang("английский", "germanic"))),
            new Origin("из Центральной или Южной Америки", List.of(lang("испанский", "romantic"), lang("португальский", "romantic"))),
            new Origin("европеец", List.of(lang("французский", "romantic"), lang("немецкий", "germanic"), lang("английский", "germanic"),
                    lang("испанский", "romantic"), lang("итальянский", "romantic"), lang("греческий", "greek"), lang("датский", "germanic"),
                    lang("голландский", "germanic"), lang("норвежский", "germanic"), lang("шведский", "germanic"), lang("финский", "baltic")))
    };

    private static final String[] RANKING = {"корпоративный руководитель", "корпоративный менеджер", "корпоративный техник",
            "стая кочевников", "пиратский флот", "семья из банды", "криминальный босс", "беднота боевой зоны", "городские бездомные",
            "семья из аркологии"};
    private static final String[] PARENTS_HAPPENED = {"погибли на войне", "погибли в несчастном случае", "были убиты",
            "потеряли память и не помнят тебя", "ты их никогда не знал", "скрываются, чтобы защитить тебя",
            "оставили тебя у родственников ради безопасности", "ты вырос на улице и родителей не видел", "отдали тебя на усыновление",
            "продали тебя за деньги"};
    private static final String[] TRAGEDY = {"семья всё потеряла из-за предательства", "семья всё потеряла из-за плохого управления",
            "семью изгнали из дома, страны или корпорации", "семья в заключении, сбежал только ты", "семья пропала, ты — последний",
            "семью убили, выжил только ты", "семья давно замешана в заговоре: криминальный клан или революционная группа",
            "семью разметало по свету", "на семье проклятье наследственной вражды", "на тебе семейный долг: его надо вернуть"};
    private static final String[] CHILDHOOD = {"на улице без присмотра взрослых", "в безопасном корпоративном пригороде",
            "в стае кочевников, от города к городу", "в приходящем в упадок, некогда богатом районе", "в охраняемой корпоративной зоне в центре",
            "в сердце боевой зоны", "в маленьком городке или деревне далеко от города", "в большой аркологии", "в морском пиратском флоте",
            "на корпоративной ферме или в исследовательском центре"};
    private static final String[] SIBLING_FEELING = {"недолюбливает тебя", "недолюбливает тебя", "любит тебя", "любит тебя",
            "равнодушен", "равнодушен", "боготворит тебя", "боготворит тебя", "ненавидит тебя", "ненавидит тебя"};

    private static final String[] TRAITS = {"застенчивый и скрытный", "бунтарь, асоциальный, склонный к насилию",
            "высокомерный, гордый, отстранённый", "переменчивый, вспыльчивый, упрямый", "придирчивый, суетливый, нервный",
            "уравновешенный и серьёзный", "легкомысленный и взбалмошный", "хитрый и лживый", "интеллектуал, отстранённый",
            "дружелюбный и открытый"};
    private static final String[] PERSON = {"родитель", "брат или сестра", "возлюбленный", "друг", "ты сам", "питомец",
            "учитель или наставник", "публичная фигура", "личный герой", "никто"};
    private static final String[] VALUE = {"деньги", "честь", "данное слово", "честность", "знание", "месть", "любовь", "власть",
            "хорошо проводить время", "дружба"};
    private static final String[] PEOPLE = {"нейтрально", "нейтрально", "любит почти всех", "ненавидит почти всех",
            "люди — инструменты: использовать и выбросить", "каждый человек ценен", "люди — препятствия: уничтожать, если мешают",
            "людям нельзя доверять, ни на кого не полагаться", "стереть их всех и отдать мир тараканам", "люди прекрасны"};
    private static final String[] POSSESSION = {"оружие", "инструмент", "предмет одежды", "фотография", "книга или дневник",
            "запись", "музыкальный инструмент", "украшение", "игрушка", "письмо"};

    private static final String[] RESPONSE = {"очистить своё имя", "очистить своё имя", "пережить и забыть", "пережить и забыть",
            "найти виновных и заставить заплатить", "найти виновных и заставить заплатить", "вернуть по праву своё",
            "вернуть по праву своё", "спасти, если можно, всех, кто был втянут", "спасти, если можно, всех, кто был втянут"};
    private static final String[] FRIEND = {"как старший брат или сестра", "как младший брат или сестра", "учитель или наставник",
            "партнёр или коллега", "бывший возлюбленный", "бывший враг", "как приёмный родитель", "родственник",
            "старый друг детства, снова вместе", "познакомились на общем интересе"};
    private static final String[] ENEMY = {"бывший друг", "бывший возлюбленный", "родственник", "враг с детства", "твой подчинённый",
            "твой начальник", "партнёр или коллега", "член бустерганга", "корпоративный руководитель", "госчиновник"};
    private static final String[] CAUSE = {"один заставил другого потерять лицо или положение",
            "один стал причиной потери возлюбленного, друга или родственника", "серьёзное унижение",
            "обвинение в трусости или другом пороке", null, "один бросил или предал другого",
            "отказ от работы или от романа", "просто невзлюбили друг друга", "соперники в любви", "один сорвал планы другого"};
    private static final String[] DISABILITY = {"потерян глаз", "потерян глаз", "потеряна рука", "потеряна рука",
            "тяжёлые шрамы", "тяжёлые шрамы"};
    private static final String[] WHO_ANGRY = {"они ненавидят тебя", "они ненавидят тебя", "они ненавидят тебя", "они ненавидят тебя",
            "ты ненавидишь их", "ты ненавидишь их", "ты ненавидишь их", "взаимно", "взаимно", "взаимно"};
    private static final String[] FACE_TO_FACE = {"в бешенстве убить", "в бешенстве убить", "избегать", "избегать",
            "ударить в спину, исподтишка", "ударить в спину, исподтишка", "не замечать", "не замечать", "разнести словами",
            "разнести словами"};
    private static final String[] ENEMY_FORCES = {"только сам", "только сам", "только сам", "сам и несколько друзей",
            "сам и несколько друзей", "целая банда", "целая банда", "небольшая корпорация", "крупная корпорация",
            "целое госагентство"};
    private static final String[] TRAGIC_LOVE = {"возлюбленный погиб в несчастном случае", "возлюбленный бесследно исчез",
            "не сложилось", "между вами встала личная цель или вендетта", "возлюбленного похитили", "возлюбленный сошёл с ума",
            "возлюбленный покончил с собой", "возлюбленный погиб в драке", "соперник увёл", "возлюбленный в тюрьме или изгнан"};
    private static final String[] PROBLEM_LOVE = {"друзья или семья возлюбленного ненавидят тебя",
            "друзья или семья возлюбленного готовы на всё, чтобы избавиться от тебя", "твои друзья или семья ненавидят возлюбленного",
            "у одного из вас есть соперник", "вы чем-то разлучены", "вы постоянно ссоритесь", "вы профессиональные соперники",
            "один из вас безумно ревнив", "один из вас «гуляет»", "разное происхождение и семьи"};
    private static final String[] MUTUAL = {"тебя всё ещё любят", "ты всё ещё любишь", "вы всё ещё любите друг друга",
            "ты ненавидишь", "тебя ненавидят", "вы ненавидите друг друга", "вы друзья", "чувств не осталось, всё кончено",
            "тебе он нравится, а тебя ненавидят", "ты нравишься, а ты ненавидишь"};

    private Cp2020Lifepath() {
    }

    private static String pick(String[] table, DiceRoller dice) {
        return table[dice.d10() - 1];
    }

    private static String sex(DiceRoller dice) {
        return dice.d10() % 2 == 0 ? "мужчина" : "женщина";
    }

    /**
     * @param age возраст; null — 2D6 + 16 (с. 36)
     */
    static Result generate(Integer age, DiceRoller dice) {
        int years = age != null ? Math.clamp(age, MIN_AGE, MAX_AGE) : dice.roll(6) + dice.roll(6) + MIN_AGE;
        List<Section> sections = new ArrayList<>();
        List<Effect> effects = new ArrayList<>();

        sections.add(new Section("Стиль", List.of("Одежда: " + pick(CLOTHES, dice), "Причёска: " + pick(HAIR, dice),
                "Особенность: " + pick(AFFECTATIONS, dice))));

        Origin origin = ORIGINS[dice.d10() - 1];
        sections.add(new Section("Происхождение", List.of("Корни: " + origin.name(),
                "Родной язык на +8 — из: " + String.join(", ", origin.languages().stream().map(Language::name).toList()),
                "Уличный жаргон знает и так")));

        List<String> family = new ArrayList<>();
        family.add("Семья: " + pick(RANKING, dice));
        if (dice.d10() <= 6) {
            family.add("Оба родителя живы");
        } else {
            family.add("С родителями: " + pick(PARENTS_HAPPENED, dice));
        }
        if (dice.d10() <= 6) {
            family.add("Семья в опасности: " + pick(TRAGEDY, dice));
        } else {
            family.add("Положение семьи в порядке");
        }
        family.add("Детство: " + pick(CHILDHOOD, dice));
        int siblings = dice.d10();
        if (siblings > 7) {
            family.add("Единственный ребёнок");
        } else {
            for (int i = 0; i < siblings; i++) {
                String who = dice.d10() % 2 == 0 ? "сестра" : "брат";
                int ageRoll = dice.d10();
                String relative = ageRoll <= 5 ? "старше" : ageRoll <= 9 ? "младше" : "близнец";
                family.add("Сиблинг: " + who + ", " + relative + ", " + pick(SIBLING_FEELING, dice));
            }
        }
        sections.add(new Section("Семья", family));

        sections.add(new Section("Мотивация", List.of("Характер: " + pick(TRAITS, dice), "Дороже всех: " + pick(PERSON, dice),
                "Ценит больше всего: " + pick(VALUE, dice), "К людям: " + pick(PEOPLE, dice),
                "Самая ценная вещь: " + pick(POSSESSION, dice))));

        List<String> events = new ArrayList<>();
        for (int year = MIN_AGE + 1; year <= years; year++) {
            events.add(year + ": " + event(dice, effects));
        }
        sections.add(new Section("События, возраст " + years, events.isEmpty() ? List.of("Ничего: ровно 16") : events));
        return new Result(years, sections, origin.languages(), effects);
    }

    /** Событие года, с. 36: 1–3 беды и удачи, 4–6 друзья и враги, 7–8 роман, 9–10 ничего */
    private static String event(DiceRoller dice, List<Effect> effects) {
        int roll = dice.d10();
        if (roll <= 3) {
            return dice.d10() % 2 == 0 ? lucky(dice, effects) : disaster(dice, effects) + " Решил: " + pick(RESPONSE, dice) + ".";
        }
        if (roll <= 6) {
            return dice.d10() <= 5 ? friend(dice) : enemy(dice);
        }
        if (roll <= 8) {
            return romance(dice);
        }
        return "Ничего не случилось.";
    }

    private static String disaster(DiceRoller dice, List<Effect> effects) {
        int sub = dice.d10();
        switch (dice.d10()) {
            case 1 -> {
                long lost = sub * 100L;
                effects.add(new Effect("Потеря или долг", null, 0, -lost));
                return "Беда: потерял " + lost + " eb; не можешь заплатить — это долг, деньгами или кровью.";
            }
            case 2 -> {
                return "Беда: тюрьма или заложник, " + sub + " мес.";
            }
            case 3 -> {
                effects.add(new Effect("Болезнь или зависимость", Cp2020StatsStrategy.REF, -1, 0));
                return "Беда: болезнь или зависимость, REF −1 навсегда.";
            }
            case 4 -> {
                return "Беда: предательство — " + (sub <= 3 ? "тебя шантажируют" : sub <= 7 ? "раскрыта твоя тайна"
                        : "предал близкий друг, в любви или в работе") + ".";
            }
            case 5 -> {
                if (sub <= 4) {
                    effects.add(new Effect("Обезображен", Cp2020StatsStrategy.ATTR, -5, 0));
                    return "Беда: несчастный случай, обезображен, ATTR −5.";
                }
                int months = dice.d10();
                return "Беда: несчастный случай — " + (sub <= 6 ? "в больнице " + months + " мес."
                        : sub <= 8 ? "потерял память о " + months + " мес. этого года"
                        : "кошмары каждую ночь (8 из 10)") + ".";
            }
            case 6 -> {
                return "Беда: погиб близкий — " + (sub <= 5 ? "несчастный случай" : sub <= 8 ? "убит неизвестными"
                        : "убит, и ты знаешь кем, но нет доказательств") + ".";
            }
            case 7 -> {
                String[] charges = {"кража", "кража", "кража", "трусость", "трусость", "убийство", "убийство", "убийство",
                        "насилие", "ложь или предательство"};
                return "Беда: ложное обвинение — " + charges[sub - 1] + ".";
            }
            case 8 -> {
                return "Беда: охотится закон — " + (sub <= 3 ? "пара местных копов" : sub <= 6 ? "вся местная полиция"
                        : sub <= 8 ? "полиция штата или ополчение" : "ФБР или национальная полиция") + ".";
            }
            case 9 -> {
                return "Беда: охотится корпорация — " + (sub <= 3 ? "маленькая местная фирма" : sub <= 6 ? "корпорация штата"
                        : sub <= 8 ? "национальная корпорация" : "транснациональный гигант с армией") + ".";
            }
            default -> {
                if (sub <= 3) {
                    effects.add(new Effect("Нервное расстройство", Cp2020StatsStrategy.REF, -1, 0));
                    return "Беда: нервное расстройство от биочумы, REF −1.";
                }
                if (sub <= 7) {
                    effects.add(new Effect("Тревога и фобии", Cp2020StatsStrategy.COOL, -1, 0));
                    return "Беда: тревога и фобии, COOL −1.";
                }
                effects.add(new Effect("Психоз", Cp2020StatsStrategy.COOL, -1, 0));
                effects.add(new Effect("Психоз", Cp2020StatsStrategy.REF, -1, 0));
                return "Беда: тяжёлый психоз, COOL −1, REF −1.";
            }
        }
    }

    private static String lucky(DiceRoller dice, List<Effect> effects) {
        return switch (dice.d10()) {
            case 1 -> {
                int sub = dice.d10();
                yield "Удача: связи в городской власти — " + (sub <= 4 ? "в полиции" : sub <= 7 ? "в прокуратуре" : "в мэрии") + ".";
            }
            case 2, 3 -> {
                long won = dice.d10() * 100L;
                effects.add(new Effect("Удачное дело", null, 0, won));
                yield "Удача: " + won + " eb.";
            }
            case 4 -> "Удача: сэнсей — боевое искусство на +2 или +1 к своему (поднимите на странице навыков).";
            case 5 -> "Удача: учитель — +1 к навыку INT или новый навык INT на +2 (поднимите на странице навыков).";
            case 6 -> "Удача: влиятельный корпорат должен услугу.";
            case 7 -> "Удача: стая кочевников дружит — услуга в месяц, как Семья +2.";
            case 8 -> "Удача: друг в полиции — сведения, как Знание улиц +2 по полицейским делам.";
            case 9 -> "Удача: местный бустерганг почему-то любит тебя — услуга в месяц, как Семья +2.";
            default -> "Удача: боевой учитель — +1 к оружейному навыку (не боевые искусства и не драка) или новый на +2.";
        };
    }

    private static String friend(DiceRoller dice) {
        return "Друг (" + sex(dice) + "): " + pick(FRIEND, dice) + ".";
    }

    private static String enemy(DiceRoller dice) {
        String who = sex(dice) + ", " + pick(ENEMY, dice);
        String cause = CAUSE[dice.d10() - 1];
        if (cause == null) {
            cause = "увечье — " + DISABILITY[dice.roll(6) - 1];
        }
        return "Враг (" + who + "): " + cause + "; " + pick(WHO_ANGRY, dice) + "; при встрече пострадавший будет "
                + pick(FACE_TO_FACE, dice) + "; против тебя — " + pick(ENEMY_FORCES, dice) + ".";
    }

    private static String romance(DiceRoller dice) {
        int roll = dice.d10();
        if (roll <= 4) {
            return "Роман: счастливая любовь.";
        }
        if (roll == 5) {
            return "Роман: трагедия — " + pick(TRAGIC_LOVE, dice) + "; теперь: " + pick(MUTUAL, dice) + ".";
        }
        if (roll <= 7) {
            return "Роман с проблемами: " + pick(PROBLEM_LOVE, dice) + ".";
        }
        return "Роман: короткие интрижки.";
    }
}
