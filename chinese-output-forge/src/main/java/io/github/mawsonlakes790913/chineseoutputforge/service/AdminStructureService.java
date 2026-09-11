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
}
