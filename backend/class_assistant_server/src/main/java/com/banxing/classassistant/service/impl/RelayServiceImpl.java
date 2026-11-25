package com.banxing.classassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.banxing.classassistant.common.BizException;
import com.banxing.classassistant.dto.RelayBatchDTO;
import com.banxing.classassistant.dto.RelayCardVO;
import com.banxing.classassistant.dto.RelayCreateDTO;
import com.banxing.classassistant.dto.RelayEntryDTO;
import com.banxing.classassistant.dto.RelayEntryVO;
import com.banxing.classassistant.dto.RelayVO;
import com.banxing.classassistant.entity.ContainerMember;
import com.banxing.classassistant.entity.ContainerMessage;
import com.banxing.classassistant.entity.Relay;
import com.banxing.classassistant.entity.RelayEntry;
import com.banxing.classassistant.entity.Student;
import com.banxing.classassistant.entity.SubjectContainer;
import com.banxing.classassistant.entity.User;
import com.banxing.classassistant.mapper.ContainerMessageMapper;
import com.banxing.classassistant.mapper.RelayEntryMapper;
import com.banxing.classassistant.mapper.RelayMapper;
import com.banxing.classassistant.mapper.StudentMapper;
import com.banxing.classassistant.mapper.UserMapper;
import com.banxing.classassistant.service.ContainerAuthService;
import com.banxing.classassistant.service.RelayService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 接龙实现。
 *
 * <p>四个关键决定：
 * <ol>
 *   <li><b>「已满」「已截止」不落库</b>。它们是时间和人数算出来的结果，
 *       落库就要有定时任务去改；没有定时任务时库里会躺着一批
 *       "看着还在进行、其实早过期"的接龙。现算一次比维护一个任务便宜。</li>
 *   <li><b>抢最后一个名额用行锁</b>（{@code SELECT ... FOR UPDATE}）。
 *       先查 count 再插入，两个人同时来会都看到 29/30，然后都插进去变成 31。
 *       班级群的并发量很小，锁一行完全够用，不需要分布式锁。</li>
 *   <li><b>重复提交不报错</b>，直接返回已有记录。报错会让用户以为没成功，
 *       继续点，然后产生"到底报没报上"的困惑。</li>
 *   <li><b>卡片状态不存进消息表</b>，每次拉消息时批量现算。
 *       存进去就要有人同步，同步漏一次卡片上的人数就永久错了。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class RelayServiceImpl implements RelayService {

    /** 手机号：和「编辑资料」用同一条规则，避免两处判定不一致 */
    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");

    private static final DateTimeFormatter DEADLINE_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    /** 单选最多几个选项 */
    private static final int MAX_OPTIONS = 10;

    private final RelayMapper relayMapper;
    private final RelayEntryMapper entryMapper;
    private final ContainerMessageMapper messageMapper;
    private final StudentMapper studentMapper;
    private final UserMapper userMapper;
    private final ContainerAuthService auth;
    private final ObjectMapper json;

    // ===============================================================
    // 创建
    // ===============================================================

    @Override
    @Transactional
    public RelayVO create(Long userId, Long containerId, RelayCreateDTO dto) {
        ContainerMember me = auth.requireMember(userId, containerId);
        SubjectContainer container = auth.requireContainer(containerId);

        int format = dto.getEntryFormat() == null ? Relay.FORMAT_SEQ : dto.getEntryFormat();
        if (format < Relay.FORMAT_SEQ || format > Relay.FORMAT_CHOICE) {
            throw new BizException(1001, "填写格式不正确");
        }

        String optionsJson = validateFormat(format, dto);

        if (dto.getMaxCount() != null && (dto.getMaxCount() < 1 || dto.getMaxCount() > 200)) {
            throw new BizException(1001, "人数上限要在 1 到 200 之间");
        }
        if (dto.getDeadline() != null && dto.getDeadline().isBefore(LocalDateTime.now())) {
            throw new BizException(1001, "截止时间已经过了");
        }

        Relay r = new Relay();
        r.setContainerId(containerId);
        /* 班级 id 从群上冗余过来：代填要拉学生名单，
           每次都从 container 反查只是多一次 join */
        r.setClassId(container.getClassId());
        r.setCreatorId(userId);
        r.setTitle(dto.getTitle().trim());
        r.setDescription(dto.getDescription() == null ? "" : dto.getDescription().trim());
        r.setEntryFormat(format);
        r.setFormatMin(dto.getFormatMin());
        r.setFormatMax(dto.getFormatMax());
        r.setOptions(optionsJson);
        r.setMaxCount(dto.getMaxCount());
        r.setDeadline(dto.getDeadline());
        r.setAllowSelfEdit(Boolean.FALSE.equals(dto.getAllowSelfEdit()) ? 0 : 1);
        r.setStatus(Relay.STATUS_ONGOING);
        r.setCreatedAt(LocalDateTime.now());
        relayMapper.insert(r);

        /* 卡片消息由服务端插入，不走通用的 send 接口 ——
           卡片不该由客户端自由构造，否则谁都能伪造一张"接龙卡" */
        postCard(containerId, userId, ContainerMessage.TYPE_RELAY, r.getId(), "[接龙] " + r.getTitle());

        return detail(userId, containerId, r.getId(), me);
    }

    /** 校验并规范化格式配置，返回要入库的 options JSON */
    private String validateFormat(int format, RelayCreateDTO dto) {
        if (format == Relay.FORMAT_NUMBER) {
            Integer min = dto.getFormatMin();
            Integer max = dto.getFormatMax();
            if (min != null && max != null && min > max) {
                throw new BizException(1001, "最小值不能大于最大值");
            }
            return null;
        }
        if (format == Relay.FORMAT_CHOICE) {
            List<String> raw = dto.getOptions();
            if (raw == null || raw.size() < 2) {
                throw new BizException(1001, "单选至少要 2 个选项");
            }
            if (raw.size() > MAX_OPTIONS) {
                throw new BizException(1001, "最多 " + MAX_OPTIONS + " 个选项");
            }
            List<String> cleaned = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (String s : raw) {
                String v = s == null ? "" : s.trim();
                if (!StringUtils.hasText(v)) {
                    throw new BizException(1001, "选项不能为空");
                }
                if (v.length() > 10) {
                    throw new BizException(1001, "每个选项最多 10 个字");
                }
                if (!seen.add(v)) {
                    throw new BizException(1001, "选项「" + v + "」重复了");
                }
                cleaned.add(v);
            }
            try {
                return json.writeValueAsString(cleaned);
            } catch (Exception e) {
                throw new BizException(5000, "选项保存失败");
            }
        }
        return null;
    }

    // ===============================================================
    // 详情
    // ===============================================================

    @Override
    public RelayVO detail(Long userId, Long containerId, Long relayId) {
        auth.requireMember(userId, containerId);
        return detail(userId, containerId, relayId, null);
    }

    /** 内部重载：create 里已经校验过成员，不必再查一次 */
    private RelayVO detail(Long userId, Long containerId, Long relayId, ContainerMember ignored) {
        Relay r = requireRelay(containerId, relayId);

        List<RelayEntry> rows = entryMapper.selectList(new LambdaQueryWrapper<RelayEntry>()
                .eq(RelayEntry::getRelayId, relayId)
                .eq(RelayEntry::getStatus, RelayEntry.STATUS_NORMAL)
                .orderByAsc(RelayEntry::getSeq));

        RelayVO vo = new RelayVO();
        fillCard(vo, r, rows, userId);
        vo.setDescription(r.getDescription() == null ? "" : r.getDescription());
        vo.setOptions(parseOptions(r.getOptions()));
        vo.setFormatMin(r.getFormatMin());
        vo.setFormatMax(r.getFormatMax());
        vo.setAllowSelfEdit(r.getAllowSelfEdit() != null && r.getAllowSelfEdit() == 1);

        List<RelayEntryVO> entries = new ArrayList<>(rows.size());
        Set<Long> pickedStudentIds = new HashSet<>();
        /* 没参与时给空串而不是 null：前端拿它直接绑输入框，
           null 会让 input 变成「未受控」，第一次输入时光标位置会跳 */
        vo.setMyValue("");
        for (RelayEntry e : rows) {
            RelayEntryVO ev = new RelayEntryVO();
            ev.setId(e.getId());
            ev.setSeq(e.getSeq());
            ev.setValue(e.getValue() == null ? "" : e.getValue());
            ev.setName(e.getNameSnapshot());
            ev.setStudentId(e.getStudentId());
            boolean isMine = Objects.equals(e.getUserId(), userId);
            ev.setMine(isMine);
            entries.add(ev);
            /* 我的填写单独给一份：详情页顶部那条「我的填写：3 份 · 修改」要用它，
               在长名单里 find 一次也能拿到，但那样前端就得写两套取数逻辑 */
            if (isMine) {
                vo.setMyValue(ev.getValue());
            }
            if (e.getStudentId() != null) {
                pickedStudentIds.add(e.getStudentId());
            }
        }
        vo.setEntries(entries);
        vo.setCandidates(loadCandidates(r, pickedStudentIds));
        return vo;
    }

    /**
     * 代填候选名单 = 这个班的学生。
     *
     * <p>课程群没有关联班级时返回空数组，前端据此隐藏「代填」入口 ——
     * 与其拿群成员（现在只有老师）冒充学生名单，不如明说没有。
     */
    private List<RelayVO.CandidateVO> loadCandidates(Relay r, Set<Long> picked) {
        if (r.getClassId() == null) {
            return Collections.emptyList();
        }
        List<Student> students = studentMapper.selectList(new LambdaQueryWrapper<Student>()
                .eq(Student::getClassId, r.getClassId())
                .eq(Student::getStatus, 1)
                .orderByAsc(Student::getId));
        List<RelayVO.CandidateVO> out = new ArrayList<>(students.size());
        for (Student s : students) {
            RelayVO.CandidateVO c = new RelayVO.CandidateVO();
            c.setStudentId(s.getId());
            c.setName(s.getName());
            c.setPicked(picked.contains(s.getId()));
            out.add(c);
        }
        return out;
    }

    // ===============================================================
    // 参与
    // ===============================================================

    @Override
    @Transactional
    public RelayCardVO join(Long userId, Long containerId, Long relayId, RelayEntryDTO dto) {
        ContainerMember me = auth.requireMember(userId, containerId);

        /* 行锁：两个人抢最后一个名额时，不加锁会都读到 29/30 然后都插进去 */
        Relay r = lockRelay(containerId, relayId);
        int count = countEntries(relayId);
        assertOpen(r, count);

        /* 已经参与过：直接返回当前状态，不报错。
           报错会让用户以为没成功，继续点，然后产生"到底报没报上"的困惑 */
        RelayEntry exists = entryMapper.selectOne(new LambdaQueryWrapper<RelayEntry>()
                .eq(RelayEntry::getRelayId, relayId)
                .eq(RelayEntry::getUserId, userId));
        if (exists != null) {
            return cardOf(r, userId);
        }

        String value = normalizeValue(r, dto.getValue());

        RelayEntry e = new RelayEntry();
        e.setRelayId(relayId);
        e.setUserId(userId);
        e.setSeq(nextSeq(relayId));
        e.setValue(value);
        e.setNameSnapshot(snapshotName(me, userId));
        e.setStatus(RelayEntry.STATUS_NORMAL);
        e.setCreatedAt(LocalDateTime.now());
        entryMapper.insert(e);

        return cardOf(r, userId);
    }

    @Override
    @Transactional
    public RelayCardVO batch(Long userId, Long containerId, Long relayId, RelayBatchDTO dto) {
        auth.requireMember(userId, containerId);
        Relay r = lockRelay(containerId, relayId);

        /* 代填是"替别人登记"，只有发起人（或群主/班委）能做。
           不加这条判断的话，任何群成员都能往名单里塞任意学生 */
        assertManager(r, userId, "只有发起人可以代填");

        if (dto.getEntries() == null || dto.getEntries().isEmpty()) {
            throw new BizException(1001, "至少要填一条");
        }

        int count = countEntries(relayId);
        assertOpen(r, count);
        if (r.getMaxCount() != null && count + dto.getEntries().size() > r.getMaxCount()) {
            throw new BizException(4101,
                    "还剩 " + (r.getMaxCount() - count) + " 个名额，这次要填 " + dto.getEntries().size() + " 条");
        }

        /* 先把已填的捞出来，重复的直接报错并指出在第几位 ——
           批量里悄悄跳过几条，老师会以为都填上了 */
        List<RelayEntry> existing = entryMapper.selectList(new LambdaQueryWrapper<RelayEntry>()
                .eq(RelayEntry::getRelayId, relayId)
                .isNotNull(RelayEntry::getStudentId));
        Map<Long, Integer> seqOf = new HashMap<>();
        for (RelayEntry e : existing) {
            seqOf.put(e.getStudentId(), e.getSeq());
        }

        int seq = nextSeq(relayId);
        for (RelayEntryDTO one : dto.getEntries()) {
            if (one.getStudentId() == null) {
                throw new BizException(1001, "代填要指定学生");
            }
            Integer already = seqOf.get(one.getStudentId());
            if (already != null) {
                throw new BizException(1006, studentName(one.getStudentId()) + " 已经在第 " + already + " 位了");
            }
            String value = normalizeValue(r, one.getValue());

            RelayEntry e = new RelayEntry();
            e.setRelayId(relayId);
            e.setStudentId(one.getStudentId());
            e.setSeq(seq++);
            e.setValue(value);
            e.setNameSnapshot(studentName(one.getStudentId()));
            e.setStatus(RelayEntry.STATUS_NORMAL);
            e.setCreatedAt(LocalDateTime.now());
            entryMapper.insert(e);
            seqOf.put(one.getStudentId(), e.getSeq());
        }

        return cardOf(r, userId);
    }

    @Override
    @Transactional
    public RelayCardVO updateEntry(Long userId, Long containerId, Long relayId, Long entryId, RelayEntryDTO dto) {
        auth.requireMember(userId, containerId);
        Relay r = lockRelay(containerId, relayId);

        RelayEntry e = entryMapper.selectById(entryId);
        if (e == null || !Objects.equals(e.getRelayId(), relayId)
                || e.getStatus() == null || e.getStatus() != RelayEntry.STATUS_NORMAL) {
            throw new BizException(1004, "这条记录不存在");
        }

        boolean mine = Objects.equals(e.getUserId(), userId);
        /* 代填行没有 userId，发起人改它不算"改别人" —— 那本来就是替人登记的记录。
           但自助行只有本人能改：允许发起人改，等于给了他一个改别人答案的口子 */
        boolean ownerEditingProxy = e.getStudentId() != null && Objects.equals(r.getCreatorId(), userId);
        if (!mine && !ownerEditingProxy) {
            throw new BizException(3002, "只能修改自己填的那条");
        }
        if (mine && r.getAllowSelfEdit() != null && r.getAllowSelfEdit() == 0) {
            throw new BizException(4103, "发起人不允许修改，重新提交也不行");
        }

        int count = countEntries(relayId);
        assertOpen(r, count);

        String value = normalizeValue(r, dto.getValue());
        entryMapper.update(null, new LambdaUpdateWrapper<RelayEntry>()
                .eq(RelayEntry::getId, entryId)
                .set(RelayEntry::getValue, value)
                .set(RelayEntry::getUpdatedAt, LocalDateTime.now()));

        return cardOf(r, userId);
    }

    @Override
    @Transactional
    public RelayCardVO close(Long userId, Long containerId, Long relayId) {
        auth.requireMember(userId, containerId);
        Relay r = lockRelay(containerId, relayId);
        assertManager(r, userId, "只有发起人可以结束接龙");

        if (r.getStatus() != null && r.getStatus() == Relay.STATUS_CANCELED) {
            throw new BizException(4103, "接龙已取消");
        }
        relayMapper.update(null, new LambdaUpdateWrapper<Relay>()
                .eq(Relay::getId, relayId)
                .set(Relay::getStatus, Relay.STATUS_CLOSED)
                .set(Relay::getUpdatedAt, LocalDateTime.now()));

        r.setStatus(Relay.STATUS_CLOSED);
        return cardOf(r, userId);
    }

    // ===============================================================
    // 卡片
    // ===============================================================

    @Override
    public Map<Long, RelayCardVO> cardMap(Long userId, Long containerId, List<Long> relayIds) {
        if (relayIds == null || relayIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Relay> relays = relayMapper.selectList(new LambdaQueryWrapper<Relay>()
                .eq(Relay::getContainerId, containerId)
                .in(Relay::getId, relayIds));
        if (relays.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> ids = relays.stream().map(Relay::getId).toList();

        /* 人数一次查完：在循环里对每条消息查一次 count 就是 N+1 */
        Map<Long, Integer> counts = new HashMap<>();
        QueryWrapper<RelayEntry> cq = new QueryWrapper<>();
        cq.select("relay_id", "COUNT(*) AS cnt")
                .in("relay_id", ids)
                .eq("status", RelayEntry.STATUS_NORMAL)
                .groupBy("relay_id");
        for (Map<String, Object> row : entryMapper.selectMaps(cq)) {
            counts.put(((Number) row.get("relay_id")).longValue(),
                    ((Number) row.get("cnt")).intValue());
        }

        /* 我参没参与，也一次查完 */
        Map<Long, RelayEntry> mine = new HashMap<>();
        List<RelayEntry> myEntries = entryMapper.selectList(new LambdaQueryWrapper<RelayEntry>()
                .in(RelayEntry::getRelayId, ids)
                .eq(RelayEntry::getUserId, userId)
                .eq(RelayEntry::getStatus, RelayEntry.STATUS_NORMAL));
        for (RelayEntry e : myEntries) {
            mine.put(e.getRelayId(), e);
        }

        Map<Long, RelayCardVO> out = new LinkedHashMap<>();
        for (Relay r : relays) {
            RelayCardVO c = new RelayCardVO();
            fillCard(c, r, counts.getOrDefault(r.getId(), 0), mine.get(r.getId()), userId);
            out.put(r.getId(), c);
        }
        return out;
    }

    private RelayCardVO cardOf(Relay r, Long userId) {
        int count = countEntries(r.getId());
        RelayEntry my = entryMapper.selectOne(new LambdaQueryWrapper<RelayEntry>()
                .eq(RelayEntry::getRelayId, r.getId())
                .eq(RelayEntry::getUserId, userId)
                .eq(RelayEntry::getStatus, RelayEntry.STATUS_NORMAL));
        RelayCardVO c = new RelayCardVO();
        fillCard(c, r, count, my, userId);
        return c;
    }

    /** 详情页用：手头已经有条目列表，不用再查一次 count */
    private void fillCard(RelayCardVO c, Relay r, List<RelayEntry> rows, Long userId) {
        RelayEntry my = null;
        for (RelayEntry e : rows) {
            if (Objects.equals(e.getUserId(), userId)) {
                my = e;
                break;
            }
        }
        fillCard(c, r, rows.size(), my, userId);
    }

    private void fillCard(RelayCardVO c, Relay r, int count, RelayEntry my, Long userId) {
        c.setId(r.getId());
        c.setTitle(r.getTitle());
        c.setStatus(effectiveStatus(r, count));
        c.setEntryCount(count);
        c.setMaxCount(r.getMaxCount());
        c.setEntryFormat(r.getEntryFormat());
        c.setDeadline(r.getDeadline());
        c.setDeadlineText(r.getDeadline() == null ? "" : r.getDeadline().format(DEADLINE_FMT));
        c.setJoined(my != null);
        c.setMyEntryId(my == null ? null : my.getId());
        c.setMine(Objects.equals(r.getCreatorId(), userId));
    }

    /**
     * 现算状态。库里只存"进行中 / 已结束 / 已取消"三种，
     * 已满和已截止是时间和人数算出来的。
     *
     * <p>顺序不能换：先看已结束/已取消，再看截止，最后看满员。
     * 反过来会把"已经手动结束、人数也满了"的接龙显示成「已满员」，
     * 用户会以为再等等还有位置。
     */
    private int effectiveStatus(Relay r, int count) {
        int s = r.getStatus() == null ? Relay.STATUS_ONGOING : r.getStatus();
        if (s == Relay.STATUS_CLOSED || s == Relay.STATUS_CANCELED) {
            return s;
        }
        if (r.getDeadline() != null && r.getDeadline().isBefore(LocalDateTime.now())) {
            return Relay.VIEW_EXPIRED;
        }
        if (r.getMaxCount() != null && count >= r.getMaxCount()) {
            return Relay.VIEW_FULL;
        }
        return Relay.STATUS_ONGOING;
    }

    /** 能不能继续填。不能时按具体原因报错 —— 一句"提交失败"等于什么都没说 */
    private void assertOpen(Relay r, int count) {
        int s = effectiveStatus(r, count);
        if (s == Relay.STATUS_ONGOING) {
            return;
        }
        switch (s) {
            case Relay.VIEW_FULL -> throw new BizException(4101, "已经满员了");
            case Relay.VIEW_EXPIRED -> throw new BizException(4102, "接龙已截止");
            case Relay.STATUS_CLOSED -> throw new BizException(4103, "接龙已结束");
            case Relay.STATUS_CANCELED -> throw new BizException(4103, "接龙已取消");
            default -> throw new BizException(4103, "接龙已关闭");
        }
    }

    /** 发起人，或群主/班委。群主能代为收尾，是因为发起人可能已经退群 */
    private void assertManager(Relay r, Long userId, String msg) {
        if (Objects.equals(r.getCreatorId(), userId)) {
            return;
        }
        ContainerMember m = auth.findMember(r.getContainerId(), userId);
        if (m != null && m.getStatus() != null && m.getStatus() == ContainerMember.STATUS_IN
                && (m.getRole() == ContainerMember.ROLE_OWNER || m.getRole() == ContainerMember.ROLE_ADMIN)) {
            return;
        }
        throw new BizException(3002, msg);
    }

    // ===============================================================
    // 小工具
    // ===============================================================

    private Relay requireRelay(Long containerId, Long relayId) {
        Relay r = relayMapper.selectById(relayId);
        /* 必须比对 containerId：只按 id 查会让 A 群的接龙被 B 群的人读到 */
        if (r == null || !Objects.equals(r.getContainerId(), containerId)) {
            throw new BizException(1004, "接龙不存在");
        }
        return r;
    }

    private Relay lockRelay(Long containerId, Long relayId) {
        Relay r = relayMapper.selectOne(new LambdaQueryWrapper<Relay>()
                .eq(Relay::getId, relayId)
                .last("FOR UPDATE"));
        if (r == null || !Objects.equals(r.getContainerId(), containerId)) {
            throw new BizException(1004, "接龙不存在");
        }
        return r;
    }

    private int countEntries(Long relayId) {
        Long n = entryMapper.selectCount(new LambdaQueryWrapper<RelayEntry>()
                .eq(RelayEntry::getRelayId, relayId)
                .eq(RelayEntry::getStatus, RelayEntry.STATUS_NORMAL));
        return n == null ? 0 : n.intValue();
    }

    private int nextSeq(Long relayId) {
        QueryWrapper<RelayEntry> qw = new QueryWrapper<>();
        qw.select("IFNULL(MAX(seq), 0)").eq("relay_id", relayId);
        List<Object> one = entryMapper.selectObjs(qw);
        int max = (one.isEmpty() || one.get(0) == null) ? 0 : ((Number) one.get(0)).intValue();
        return max + 1;
    }

    /** 按格式校验并规范化填写内容。返回入库的值（格式=序号 时是空串） */
    private String normalizeValue(Relay r, String raw) {
        String v = raw == null ? "" : raw.trim();
        int format = r.getEntryFormat() == null ? Relay.FORMAT_SEQ : r.getEntryFormat();

        if (format == Relay.FORMAT_SEQ) {
            return "";
        }
        if (!StringUtils.hasText(v)) {
            throw new BizException(1001, "请填写内容");
        }
        if (v.length() > 64) {
            throw new BizException(1001, "最多 64 个字");
        }

        switch (format) {
            case Relay.FORMAT_NUMBER -> {
                int n;
                try {
                    n = Integer.parseInt(v);
                } catch (NumberFormatException e) {
                    throw new BizException(1001, "请填数字");
                }
                if (r.getFormatMin() != null && n < r.getFormatMin()) {
                    throw new BizException(1001, "不能小于 " + r.getFormatMin());
                }
                if (r.getFormatMax() != null && n > r.getFormatMax()) {
                    throw new BizException(1001, "不能大于 " + r.getFormatMax());
                }
                return String.valueOf(n);
            }
            case Relay.FORMAT_PHONE -> {
                if (!PHONE.matcher(v).matches()) {
                    throw new BizException(1001, "手机号格式不对");
                }
                return v;
            }
            case Relay.FORMAT_TEXT -> {
                if (v.length() > 20) {
                    throw new BizException(1001, "最多 20 个字");
                }
                return v;
            }
            case Relay.FORMAT_CHOICE -> {
                if (!parseOptions(r.getOptions()).contains(v)) {
                    throw new BizException(1001, "请从选项里选一个");
                }
                return v;
            }
            default -> throw new BizException(1001, "填写格式不正确");
        }
    }

    private List<String> parseOptions(String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            return Collections.emptyList();
        }
        try {
            return json.readValue(rawJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            /* 选项解析不出来时返回空列表，而不是抛异常：
               接龙本身还能用（比如是数字格式），不该因为一个坏字段整页打不开 */
            return Collections.emptyList();
        }
    }

    private String snapshotName(ContainerMember m, Long userId) {
        User u = userMapper.selectById(userId);
        return auth.displayName(m == null ? null : m.getNickname(), u);
    }

    private String studentName(Long studentId) {
        Student s = studentMapper.selectById(studentId);
        return s == null ? "该学生" : s.getName();
    }

    /**
     * 往群里发一条卡片消息。
     *
     * <p>直接操作 mapper 而不调 {@code ContainerMessageService}：
     * 那个 Service 依赖本类（拉消息时要补卡片状态），
     * 反过来依赖它就是构造器循环依赖，Spring 启动就报错。
     *
     * <p>卡片消息不检查禁言：禁言拦的是"说话"，接龙是群功能。
     * 但发起人本身必须是成员，这一点在 create 里已经校验过了。
     */
    private void postCard(Long containerId, Long senderId, int type, Long refId, String fallback) {
        ContainerMessage m = new ContainerMessage();
        m.setContainerId(containerId);
        m.setSenderId(senderId);
        m.setContent(fallback);
        m.setType(type);
        m.setRefId(refId);
        m.setStatus(ContainerMessage.STATUS_NORMAL);
        m.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(m);
    }
}
