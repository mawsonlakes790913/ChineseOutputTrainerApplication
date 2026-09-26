package io.github.mawsonlakes790913.chineseoutputforge.dto;

import lombok.Data;

/**
 * ページネーション表示に必要な情報を保持するDTO。
 */
@Data
public class PaginationDto {

	    private int currentPage;
	    private int displayStartPage;
	    private int displayEndPage;
	    private boolean showFirstEllipsis;
	    private boolean showLastEllipsis;
}
