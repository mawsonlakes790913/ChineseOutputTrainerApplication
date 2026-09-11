package io.github.mawsonlakes790913.chineseoutputforge.dto;

import io.github.mawsonlakes790913.chineseoutputforge.constant.AccountStatus;
import lombok.Data;

@Data
public class AdminUserSearchDto {

    private String loginId;

    private AccountStatus accountStatus;
}
