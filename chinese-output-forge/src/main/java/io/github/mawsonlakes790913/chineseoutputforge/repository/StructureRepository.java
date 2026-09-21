package io.github.mawsonlakes790913.chineseoutputforge.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import io.github.mawsonlakes790913.chineseoutputforge.entity.Structure;

public interface StructureRepository
        extends JpaRepository<Structure, Long> {

	// 登録されているすべての構文IDを昇順で取得
	@Query(value = """
		    SELECT DISTINCT structure_id
		    FROM structure
		    ORDER BY structure_id
		    """,
		    nativeQuery = true)
		List<Long> findAllStructureIds();
	
	// 構文を構文IDの昇順でページング取得
	@Query("""
	        SELECT s
	        FROM Structure s
	        ORDER BY s.structureId ASC
	        """)
	Page<Structure> findStructures(Pageable pageable);
	
	// 指定した構文名が既に存在するか確認
	boolean existsByName(String name);
	
	// 指定した構文ID以外に同じ構文名が存在するか確認
    boolean existsByNameAndStructureIdNot(
            String name,
            Long structureId);
}
