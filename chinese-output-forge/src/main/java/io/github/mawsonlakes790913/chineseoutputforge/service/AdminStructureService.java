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

@Service
@RequiredArgsConstructor
public class AdminStructureService {

    private final StructureRepository structureRepository;
    private final QuestionRepository questionRepository;

    // 文法一覧取得
    public Page<Structure> getStructures(Pageable pageable) {

        return structureRepository.findStructures(pageable);
    }
    
    // 文法追加
    @Transactional
    public void addStructure(StructureForm form) {

        // 文法名の重複確認
        if (structureRepository.existsByName(form.getName())) {
            throw new IllegalArgumentException(
                    "同じ名前の文法・構造がすでに登録されています。"
            );
        }

        Structure structure = new Structure();

        structure.setName(form.getName());
        structure.setDescriptionZhCn(form.getDescriptionZhCn());
        structure.setDescriptionZhTw(form.getDescriptionZhTw());

        structureRepository.save(structure);
    }
    
    // 文法取得
    public Structure getStructure(Long structureId) {

        return structureRepository.findById(structureId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "文法・構造が存在しません。"
                        )
                );
    }

    // 文法編集
    @Transactional
    public void updateStructure(
            Long structureId,
            StructureForm structureForm) {

        Structure structure = getStructure(structureId);

        // 文法名の重複確認
        if (structureRepository.existsByNameAndStructureIdNot(
                structureForm.getName(),
                structureId)) {

            throw new IllegalArgumentException(
                    "同じ名前の文法・構造がすでに登録されています。"
            );
        }

        structure.setName(structureForm.getName());
        structure.setDescriptionZhCn(
                structureForm.getDescriptionZhCn());
        structure.setDescriptionZhTw(
                structureForm.getDescriptionZhTw());
    }
    
    // 文法削除
    @Transactional
    public void deleteStructure(Long structureId) {

        // 「その他」自体は削除不可
        if (structureId.equals(23L)) {
            throw new IllegalArgumentException(
                    "「その他」は削除できません。"
            );
        }

        // 削除対象の文法を取得
        Structure targetStructure =
                structureRepository.findById(structureId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "文法・構造が存在しません。"
                                )
                        );

        // 移行先の「その他」を取得
        Structure replacementStructure =
                structureRepository.findById(23L)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "「その他」の文法・構造が存在しません。"
                                )
                        );

        // 削除対象の文法を持つ問題を「その他」に変更
        questionRepository.replaceStructure(
                targetStructure,
                replacementStructure);

        // 文法を削除
        structureRepository.delete(targetStructure);
    }
}
