package io.github.mawsonlakes790913.chineseoutputforge.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.mawsonlakes790913.chineseoutputforge.entity.Structure;
import io.github.mawsonlakes790913.chineseoutputforge.form.StructureForm;
import io.github.mawsonlakes790913.chineseoutputforge.repository.StructureRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminStructureService {

    private final StructureRepository structureRepository;

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
}
