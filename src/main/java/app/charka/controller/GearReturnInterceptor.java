package app.charka.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * R-08: предметы, инвентари и деньги правятся и с листа, и с вкладки «Снаряжение», а контроллеры возвращают на лист.
 * Если запрос пришёл со «Снаряжения» этого же персонажа, перенаправление на лист меняется на «Снаряжение» (якорь остаётся).
 */
class GearReturnInterceptor implements HandlerInterceptor {

    private static final Pattern SHEET = Pattern.compile("^redirect:/character/(\\d+)(#.*)?$");
    private static final Pattern GEAR = Pattern.compile("^/character/(\\d+)/gear$");

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) {
        if (modelAndView == null || modelAndView.getViewName() == null) {
            return;
        }
        Matcher sheet = SHEET.matcher(modelAndView.getViewName());
        if (!sheet.matches()) {
            return;
        }
        Matcher from = GEAR.matcher(Referers.back(request, ""));
        if (from.matches() && from.group(1).equals(sheet.group(1))) {
            modelAndView.setViewName("redirect:/character/" + sheet.group(1) + "/gear" + (sheet.group(2) == null ? "" : sheet.group(2)));
        }
    }
}
