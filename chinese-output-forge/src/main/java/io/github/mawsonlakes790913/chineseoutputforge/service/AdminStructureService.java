package io.github.mawsonlakes790913.chineseoutputforge.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import io.github.mawsonlakes790913.chineseoutputforge.entity.Structure;
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
}
