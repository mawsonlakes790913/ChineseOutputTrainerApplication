package io.github.mawsonlakes790913.chineseoutputforge.service;

import java.util.List;

import org.springframework.stereotype.Service;

import io.github.mawsonlakes790913.chineseoutputforge.entity.Structure;
import io.github.mawsonlakes790913.chineseoutputforge.repository.StructureRepository;
import lombok.RequiredArgsConstructor;

/**
 * 中国語学習問題で使用する文法・構造の取得に関する業務処理を行うService。
 */
@Service
@RequiredArgsConstructor
public class StructureService {

    private final StructureRepository structureRepository;

    /**
     * すべての文法・構造を取得する。
     *
     * @return 文法・構造の一覧
     */
    public List<Structure> findStructures() {
        return structureRepository.findAll();
    }
}
