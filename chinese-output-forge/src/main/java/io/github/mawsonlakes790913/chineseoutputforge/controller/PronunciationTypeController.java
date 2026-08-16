package io.github.mawsonlakes790913.chineseoutputforge.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import io.github.mawsonlakes790913.chineseoutputforge.constant.PronunciationType;
import jakarta.servlet.http.HttpSession;

@Controller
public class PronunciationTypeController {

    @GetMapping("/pronunciation-type")
    public String changePronunciationType(
            @RequestParam PronunciationType pronunciationType,
            HttpSession session) {

        session.setAttribute("pronunciationType", pronunciationType);

        return "redirect:/settings";
    }
}
