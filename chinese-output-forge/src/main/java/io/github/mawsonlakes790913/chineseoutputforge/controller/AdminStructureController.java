package io.github.mawsonlakes790913.chineseoutputforge.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import io.github.mawsonlakes790913.chineseoutputforge.dto.PaginationDto;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Structure;
import io.github.mawsonlakes790913.chineseoutputforge.form.StructureForm;
import io.github.mawsonlakes790913.chineseoutputforge.service.AdminStructureService;
import io.github.mawsonlakes790913.chineseoutputforge.service.PaginationService;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class AdminStructureController {

    private final AdminStructureService adminStructureService;
    private final PaginationService paginationService;

    // 文法一覧
    @GetMapping("/admin/structure/list")
    public String getStructureList(
            @PageableDefault(page = 0, size = 50) Pageable pageable,
            Model model) {

        Page<Structure> structureList =
                adminStructureService.getStructures(pageable);

        PaginationDto pagination =
                paginationService.createPagination(structureList);

        model.addAttribute(
                "structureList",
                structureList.getContent());

        model.addAttribute(
                "page",
                structureList);

        model.addAttribute(
                "pagination",
                pagination);

        return "/admin/structure/list";
    }
    
 // 文法追加画面
    @GetMapping("/admin/structure/add")
    public String getStructureAdd(
            @ModelAttribute StructureForm structureForm) {

        return "/admin/structure/add";
    }

    // 文法追加
    @PostMapping("/admin/structure/add")
    public String addStructure(
            @Validated @ModelAttribute StructureForm structureForm,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        // 入力エラー
        if (bindingResult.hasErrors()) {
            return "/admin/structure/add";
        }

        try {

            adminStructureService.addStructure(structureForm);

        } catch (IllegalArgumentException e) {

        	    bindingResult.rejectValue(
        	            "name",
        	            "structure.add.error",
        	            e.getMessage());

        	    return "/admin/structure/add";
        	}

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "文法・構造を追加しました。");

        return "redirect:/admin/structure/list";
    }
}
