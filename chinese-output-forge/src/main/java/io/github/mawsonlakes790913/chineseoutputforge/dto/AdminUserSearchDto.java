package io.github.mawsonlakes790913.chineseoutputforge.dto;

import io.github.mawsonlakes790913.chineseoutputforge.constant.AccountStatus;
import lombok.Data;

/**
 * 管理者用ユーザー一覧の検索条件を保持するDTO。
 */
@Data
public class AdminUserSearchDto {

    private String loginId;
    private AccountStatus accountStatus;
    private String email;
}
