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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import io.github.mawsonlakes790913.chineseoutputforge.dto.PaginationDto;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Structure;
import io.github.mawsonlakes790913.chineseoutputforge.form.StructureForm;
import io.github.mawsonlakes790913.chineseoutputforge.service.AdminStructureService;
import io.github.mawsonlakes790913.chineseoutputforge.service.PaginationService;
import lombok.RequiredArgsConstructor;

/**
 * 管理者用の文法・構造管理に関するリクエストを処理するController。
 * 文法・構造の一覧表示・追加・編集・削除を行う。
 */
@Controller
@RequiredArgsConstructor
public class AdminStructureController {

    private final AdminStructureService adminStructureService;
    private final PaginationService paginationService;

    /**
     * 文法・構造の一覧を取得し、管理者用文法・構造一覧画面を表示する。
     *
     * @param pageable ページング情報
     * @param model 画面に渡すデータ
     * @return 管理者用文法・構造一覧画面のビュー名
     */
    @GetMapping("/admin/structure/list")
    public String getStructureList(
            @PageableDefault(page = 0, size = 50) Pageable pageable,
            Model model) {

        // 文法・構造一覧をページ単位で取得
        Page<Structure> structurePage =
                adminStructureService.getStructures(pageable);

        // ページネーション情報を作成
        PaginationDto pagination =
                paginationService.createPagination(structurePage);

        // 文法・構造一覧とページ情報を画面へ渡す
        model.addAttribute(
                "structureList",
                structurePage.getContent());
        model.addAttribute(
                "page",
                structurePage);
        model.addAttribute(
                "pagination",
                pagination);

        return "/admin/structure/list";
    }
    
    /**
     * 文法・構造追加画面を表示する。
     *
     * @param structureForm 文法・構造追加フォーム
     * @return 文法・構造追加画面のビュー名
     */
    @GetMapping("/admin/structure/add")
    public String getStructureAdd(
            @ModelAttribute StructureForm structureForm) {

        return "/admin/structure/add";
    }

    /**
     * 入力された内容を検証し、新しい文法・構造を追加する。
     * 入力エラーまたは追加できない場合は追加画面を再表示する。
     *
     * @param structureForm 文法・構造追加フォーム
     * @param bindingResult バリデーション結果
     * @param redirectAttributes リダイレクト後に渡すFlash属性
     * @return 文法・構造追加画面または一覧画面へのリダイレクト先
     */
    @PostMapping("/admin/structure/add")
    public String postStructureAdd(
            @Validated @ModelAttribute StructureForm structureForm,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        // バリデーションエラーがある場合は入力画面を再表示
        if (bindingResult.hasErrors()) {
            return "/admin/structure/add";
        }

        // 文法・構造を登録
        try {
            adminStructureService.addStructure(structureForm);

        } catch (IllegalArgumentException e) {

            // 登録エラーをフォームへ設定して入力画面を再表示
            bindingResult.rejectValue(
                    "name",
                    "structure.add.error",
                    e.getMessage());

            return "/admin/structure/add";
        }

        // 登録完了メッセージを設定
        redirectAttributes.addFlashAttribute(
                "successMessage",
                "文法・構造を追加しました。");

        return "redirect:/admin/structure/list";
    }
    
    /**
     * 指定された文法・構造の情報を取得し、編集画面を表示する。
     *
     * @param structureId 編集する文法・構造のID
     * @param model 画面に渡すデータ
     * @return 文法・構造編集画面のビュー名
     */
    @GetMapping("/admin/structure/edit")
    public String getStructureEdit(
            @RequestParam Long structureId,
            Model model) {

        // 変更前の情報から編集フォームの初期値を作成して画面へ渡す
        StructureForm structureForm =
                adminStructureService.createStructureForm(
                        structureId);
        model.addAttribute(
                "structureForm",
                structureForm);

        return "/admin/structure/edit";
    }

    /**
     * 入力された内容を検証し、指定された文法・構造を更新する。
     * 入力エラーまたは更新できない場合は編集画面を再表示する。
     *
     * @param structureId 編集する文法・構造のID
     * @param structureForm 文法・構造編集フォーム
     * @param bindingResult バリデーション結果
     * @param redirectAttributes リダイレクト後に渡すFlash属性
     * @return 文法・構造編集画面または一覧画面へのリダイレクト先
     */
    @PostMapping("/admin/structure/edit")
    public String postStructureEdit(
            @RequestParam Long structureId,
            @Validated @ModelAttribute StructureForm structureForm,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        // バリデーションエラーがある場合は編集画面を再表示
        if (bindingResult.hasErrors()) {
            return "/admin/structure/edit";
        }

        // 文法・構造を更新
        try {
            adminStructureService.updateStructure(
                    structureId,
                    structureForm);

        } catch (IllegalArgumentException e) {

            // 更新エラーをフォームへ設定して編集画面を再表示
            bindingResult.rejectValue(
                    "name",
                    "structure.edit.error",
                    e.getMessage());

            return "/admin/structure/edit";
        }

        // 編集完了メッセージを設定
        redirectAttributes.addFlashAttribute(
                "successMessage",
                "文法・構造を編集しました。");

        return "redirect:/admin/structure/list";
    }
    
    /**
     * 指定された文法・構造を削除する。
     * 削除できない場合はエラーメッセージを設定して一覧画面へ戻る。
     *
     * @param structureId 削除する文法・構造のID
     * @param redirectAttributes リダイレクト後に渡すFlash属性
     * @return 文法・構造一覧画面へのリダイレクト先
     */
    @PostMapping("/admin/structure/delete")
    public String postStructureDelete(
            @RequestParam Long structureId,
            RedirectAttributes redirectAttributes) {

        // 指定された文法・構造を削除
        try {
            adminStructureService.deleteStructure(structureId);

        } catch (IllegalArgumentException | IllegalStateException e) {

            // 削除エラーメッセージを設定して一覧画面へ戻る
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    e.getMessage());

            return "redirect:/admin/structure/list";
        }

        // 削除完了メッセージを設定
        redirectAttributes.addFlashAttribute(
                "successMessage",
                "文法・構造を削除しました。");

        return "redirect:/admin/structure/list";
    }
}
