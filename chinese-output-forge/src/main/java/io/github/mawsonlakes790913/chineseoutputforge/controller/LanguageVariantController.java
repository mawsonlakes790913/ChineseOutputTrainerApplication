package io.github.mawsonlakes790913.chineseoutputforge.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import jakarta.servlet.http.HttpSession;

@Controller
public class LanguageVariantController {

    @GetMapping("/language-variant")
    public String changeLanguageVariant(
            @RequestParam LanguageVariant languageVariant,
            HttpSession session) {
    	
        LanguageVariant current =
                (LanguageVariant) session.getAttribute("languageVariant");

        // 同じ言語なら変更処理をしない
        if (languageVariant == current) {
            return "redirect:/";
        }

        // 学習対象言語をSessionに保存
        session.setAttribute("languageVariant", languageVariant);

        return "redirect:/";
    }
}