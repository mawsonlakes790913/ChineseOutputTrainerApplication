package io.github.mawsonlakes790913.chineseoutputforge.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import io.github.mawsonlakes790913.chineseoutputforge.entity.Structure;

/**
 * 中国語学習問題で使用する文法・構造情報の永続化を行うRepository。
 */
public interface StructureRepository
extends JpaRepository<Structure, Long> {

    /**
     * 指定した名前の文法・構造が既に存在するか確認する。
     *
     * @param name 文法・構造名
     * @return 同じ名前の文法・構造が存在する場合はtrue
     */
	boolean existsByName(String name);
	
    /**
     * 指定した文法・構造ID以外に同じ名前の文法・構造が存在するか確認する。
     *
     * @param name 文法・構造名
     * @param structureId 除外する文法・構造ID
     * @return 同じ名前の文法・構造が存在する場合はtrue
     */
	boolean existsByNameAndStructureIdNot(
	    String name,
	    Long structureId);
	
    /**
     * 登録されているすべての文法・構造IDを昇順で取得する。
     *
     * @return 文法・構造IDの一覧
     */
	@Query(value = """
	    SELECT DISTINCT structure_id
	    FROM structure
	    ORDER BY structure_id
	    """,
	    nativeQuery = true)
	List<Long> findAllStructureIds();
	
    /**
     * 文法・構造をIDの昇順でページング取得する。
     *
     * @param pageable ページング情報
     * @return 文法・構造のページ
     */
	@Query(value = """
	    SELECT *
	    FROM structure
	    ORDER BY structure_id ASC
	    """,
	    nativeQuery = true)
	Page<Structure> findStructures(Pageable pageable);

}
