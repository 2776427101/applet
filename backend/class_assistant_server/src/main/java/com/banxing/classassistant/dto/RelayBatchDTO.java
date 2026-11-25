package com.banxing.classassistant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 代填补录：一次提交多条。
 *
 * <p>为什么要有批量接口：老师代填 57 个人的名单，一条条发请求要 57 个来回，
 * 弱网下能等到人放弃。批量提交一次事务，中途失败整批回滚 ——
 * 部分成功比全失败更难解释（"为什么第 23 个没进去"）。
 */
@Data
public class RelayBatchDTO {

    @NotEmpty(message = "至少要填一条")
    @Valid
    private List<RelayEntryDTO> entries;
}
