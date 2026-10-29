package com.banxing.classassistant.service;

import com.banxing.classassistant.dto.ScoreImportCommitDTO;
import com.banxing.classassistant.dto.ScoreImportPreviewVO;
import com.banxing.classassistant.dto.ScoreImportResultVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 成绩表导入。
 *
 * <p>和 {@link StudentImportService} 一样分两段：preview 只算不写，commit 才落库。
 *
 * <p><b>为什么成绩导入比名单导入更需要预览</b>：名单导入最坏的结果是多出几十个学生，
 * 删掉就完了；成绩导入最坏的结果是把 47 个学生已有的分数覆盖成空，
 * 而且没有「撤销」。所以预览这一步必须把「会覆盖多少」「会清空多少」算给老师看。
 */
public interface ScoreImportService {

    /**
     * 上传成绩表并预览，<b>不写库</b>。
     *
     * @param examId 要导入到哪场考试。必须属于当前老师的班
     */
    ScoreImportPreviewVO preview(Long userId, Long examId, MultipartFile file);

    /** 确认导入 */
    ScoreImportResultVO commit(Long userId, ScoreImportCommitDTO dto);
}
