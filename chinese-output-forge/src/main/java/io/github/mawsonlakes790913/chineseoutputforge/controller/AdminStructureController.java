package io.github.mawsonlakes790913.chineseoutputforge.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import io.github.mawsonlakes790913.chineseoutputforge.dto.PaginationDto;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Structure;
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
}
