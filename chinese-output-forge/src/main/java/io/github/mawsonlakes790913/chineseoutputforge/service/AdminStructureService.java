package io.github.mawsonlakes790913.chineseoutputforge.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.mawsonlakes790913.chineseoutputforge.entity.Structure;
import io.github.mawsonlakes790913.chineseoutputforge.form.StructureForm;
import io.github.mawsonlakes790913.chineseoutputforge.repository.QuestionRepository;
import io.github.mawsonlakes790913.chineseoutputforge.repository.StructureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 管理者用の文法・構造管理に関する業務処理を行うService。
 * 文法・構造の一覧取得・追加・編集・削除を行う。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminStructureService {

    private final StructureRepository structureRepository;
    private final QuestionRepository questionRepository;

    private static final Long OTHER_STRUCTURE_ID = 23L;

    /**
     * 文法・構造をページング取得する。
     *
     * @param pageable ページング情報
     * @return 文法・構造のページ
     */
    public Page<Structure> getStructures(Pageable pageable) {

        return structureRepository.findStructures(pageable);
    }

    /**
     * 入力された情報をもとに新しい文法・構造を登録する。
     *
     * @param form 文法・構造追加画面の入力内容
     */
    @Transactional
    public void addStructure(StructureForm form) {

        // 文法・構造名の重複を確認
        if (structureRepository.existsByName(form.getName())) {
            throw new IllegalArgumentException(
                    "同じ名前の文法・構造がすでに登録されています。"
            );
        }

        // 新しい文法・構造を作成して入力内容を設定
        Structure structure = new Structure();
        applyStructureForm(structure, form);

        // 文法・構造を保存
        Structure savedStructure =
                structureRepository.save(structure);

        log.info(
                "文法・構造登録完了 structureId={}, name={}",
                savedStructure.getStructureId(),
                savedStructure.getName());
    }

    /**
     * 指定した文法・構造を取得する。
     *
     * @param structureId 文法・構造ID
     * @return 指定した文法・構造
     */
    public Structure getStructure(Long structureId) {

        return structureRepository.findById(structureId)
                .orElseThrow(() ->
                        new IllegalArgumentException("文法・構造が存在しません。")
                );
    }

    /**
     * 指定した文法・構造を入力内容で更新する。
     *
     * @param structureId 更新する文法・構造ID
     * @param structureForm 文法・構造編集画面の入力内容
     */
    @Transactional
    public void updateStructure(
            Long structureId,
            StructureForm structureForm) {

        // 更新対象の文法・構造を取得
        Structure structure = getStructure(structureId);

        // 文法・構造名の重複を確認
        if (structureRepository.existsByNameAndStructureIdNot(
                structureForm.getName(),
                structureId)) {

            throw new IllegalArgumentException("同じ名前の文法・構造がすでに登録されています。");
        }

        // 入力内容を文法・構造に設定
        applyStructureForm(structure, structureForm);

        log.info(
                "文法・構造更新完了 structureId={}, name={}",
                structureId,
                structure.getName());
    }

    /**
     * 指定した文法・構造を削除する。
     * 削除対象を使用している問題は、削除前に「その他」へ変更する。
     *
     * @param structureId 削除する文法・構造ID
     */
    @Transactional
    public void deleteStructure(Long structureId) {

        // 「その他」自体は削除不可
        if (structureId.equals(OTHER_STRUCTURE_ID)) {
            throw new IllegalArgumentException("「その他」は削除できません。");
        }

        // 削除対象の文法・構造を取得
        Structure targetStructure = getStructure(structureId);

        // 移行先の「その他」を取得
        Structure replacementStructure =
                structureRepository.findById(OTHER_STRUCTURE_ID)
                        .orElseThrow(() ->
                                new IllegalStateException("「その他」の文法・構造が存在しません。")
                        );

        // 削除対象を使用する問題を「その他」に変更
        questionRepository.replaceStructure(
                targetStructure,
                replacementStructure);

        // 文法・構造を削除
        structureRepository.delete(targetStructure);

        log.info(
                "文法・構造削除完了 structureId={}, name={}",
                structureId,
                targetStructure.getName());
    }

    /**
     * 指定した文法・構造の情報から編集フォームを作成する。
     *
     * @param structureId 文法・構造ID
     * @return 文法・構造編集画面のフォーム
     */
    public StructureForm createStructureForm(Long structureId) {

        // 文法・構造を取得
        Structure structure = getStructure(structureId);

        // 編集フォームを作成
        StructureForm form = new StructureForm();
        form.setName(structure.getName());
        form.setDescriptionZhCn(structure.getDescriptionZhCn());
        form.setDescriptionZhTw(structure.getDescriptionZhTw());

        return form;
    }

    /**
     * フォームの入力内容を文法・構造に設定する。
     *
     * @param structure 設定対象の文法・構造
     * @param form 文法・構造追加・編集画面の入力内容
     */
    private void applyStructureForm(
            Structure structure,
            StructureForm form) {

        structure.setName(form.getName());
        structure.setDescriptionZhCn(form.getDescriptionZhCn());
        structure.setDescriptionZhTw(form.getDescriptionZhTw());
    }
}
