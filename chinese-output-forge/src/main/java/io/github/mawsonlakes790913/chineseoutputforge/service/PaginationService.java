package io.github.mawsonlakes790913.chineseoutputforge.service;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import io.github.mawsonlakes790913.chineseoutputforge.dto.PaginationDto;


@Service
public class PaginationService {
	
	public PaginationDto createPagination(Page<?> page) {

	    // 現在のページ番号(0始まり)
	    int currentPage = page.getNumber();

	    // ページ番号の最小値・最大値
	    int startPage = 0;
	    int endPage = page.getTotalPages() - 1;

	    // 現在ページの前後2ページを表示範囲とする
	    int displayStartPage =
	            Math.max(startPage, currentPage - 2);

	    int displayEndPage =
	            Math.min(endPage, currentPage + 2);

	    // 表示ページ数が5ページ未満の場合は不足分を補う
	    int shortage = 0;

	    // 先頭側に寄っている場合は右側へ表示範囲を広げる
	    if (displayStartPage == startPage) {

	        shortage = 4 - (displayEndPage - displayStartPage);

	        displayEndPage =
	                Math.min(endPage,
	                        displayEndPage + shortage);

	    // 末尾側に寄っている場合は左側へ表示範囲を広げる
	    } else if (displayEndPage == endPage) {

	        shortage = 4 - (displayEndPage - displayStartPage);

	        displayStartPage =
	                Math.max(startPage,
	                        displayStartPage - shortage);
	    }

	    // 先頭・末尾の省略記号(...)を表示するか判定
	    boolean showFirstEllipsis =
	            displayStartPage >= 3;

	    boolean showLastEllipsis =
	            displayEndPage <= endPage - 3;

	    // ページネーション情報をDTOへ格納
	    PaginationDto pagination = new PaginationDto();

	    pagination.setCurrentPage(currentPage);
	    pagination.setDisplayStartPage(displayStartPage);
	    pagination.setDisplayEndPage(displayEndPage);
	    pagination.setShowFirstEllipsis(showFirstEllipsis);
	    pagination.setShowLastEllipsis(showLastEllipsis);

	    return pagination;
	}
}
