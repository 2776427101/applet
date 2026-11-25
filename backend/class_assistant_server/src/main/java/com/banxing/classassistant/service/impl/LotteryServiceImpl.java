package com.banxing.classassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.banxing.classassistant.common.BizException;
import com.banxing.classassistant.dto.LotteryCardVO;
import com.banxing.classassistant.dto.LotteryCreateDTO;
import com.banxing.classassistant.dto.LotteryVO;
import com.banxing.classassistant.entity.ContainerMember;
import com.banxing.classassistant.entity.ContainerMessage;
import com.banxing.classassistant.entity.Lottery;
import com.banxing.classassistant.entity.LotteryResult;
import com.banxing.classassistant.entity.Student;
import com.banxing.classassistant.entity.SubjectContainer;
import com.banxing.classassistant.mapper.ContainerMessageMapper;
import com.banxing.classassistant.mapper.LotteryMapper;
import com.banxing.classassistant.mapper.LotteryResultMapper;
import com.banxing.classassistant.mapper.StudentMapper;
import com.banxing.classassistant.service.ContainerAuthService;
import com.banxing.classassistant.service.LotteryService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * 抽签实现。
 *
 * <p>核心约束：<b>候选名单在发布时就快照下来</b>，之后开奖只用快照。
 * 现查名单会让"学生转班后历史结果对不上"，而且同一张卡两次刷新可能抽出不同的人。
 *
 * <p>随机用 {@code new Random(seed ^ round)}：种子存库，
 * 有人质疑"是不是内定"时把种子和名单给他，他能自己复现出同一批人。
 * 这比"我们真的是随机的"这句话有用得多。
 */
@Service
@RequiredArgsConstructor
public class LotteryServiceImpl implements LotteryService {

    private final LotteryMapper lotteryMapper;
    private final LotteryResultMapper resultMapper;
    private final ContainerMessageMapper messageMapper;
    private final StudentMapper studentMapper;
    private final ContainerAuthService auth;
    private final ObjectMapper json;

    // ===============================================================
    // 创建
    // ===============================================================

    @Override
    @Transactional
    public LotteryVO create(Long userId, Long containerId, LotteryCreateDTO dto) {
        auth.requireMember(userId, containerId);
        SubjectContainer container = auth.requireContainer(containerId);

        int kind = dto.getKind() == null ? Lottery.KIND_PICK : dto.getKind();
        if (kind != Lottery.KIND_PICK && kind != Lottery.KIND_GROUP) {
            throw new BizException(1001, "抽签类型不正确");
        }
        int source = dto.getSource() == null ? Lottery.SOURCE_CLASS : dto.getSource();

        List<PoolItem> pool = buildPool(container, source, dto.getStudentIds());
        if (pool.isEmpty()) {
            throw new BizException(1001, "候选名单是空的，先确认这个班有没有学生");
        }

        int drawCount = dto.getDrawCount() == null ? 1 : dto.getDrawCount();
        int groupCount = dto.getGroupCount() == null ? 2 : dto.getGroupCount();

        if (kind == Lottery.KIND_PICK) {
            if (drawCount > pool.size()) {
                throw new BizException(1001, "名单只有 " + pool.size() + " 人，抽不了 " + drawCount + " 个");
            }
        } else {
            if (groupCount > pool.size()) {
                throw new BizException(1001, "名单只有 " + pool.size() + " 人，分不了 " + groupCount + " 组");
            }
        }

        Lottery l = new Lottery();
        l.setContainerId(containerId);
        l.setClassId(container.getClassId());
        l.setCreatorId(userId);
        l.setKind(kind);
        l.setSource(source);
        l.setPoolSnapshot(writePool(pool));
        l.setDrawCount(drawCount);
        l.setGroupCount(groupCount);
        l.setDedupe(Boolean.FALSE.equals(dto.getDedupe()) ? 0 : 1);
        l.setPublicResult(Boolean.FALSE.equals(dto.getPublicResult()) ? 0 : 1);
        l.setStatus(Lottery.STATUS_WAITING);
        l.setRound(0);
        l.setSeed(new Random().nextLong());
        l.setVersion(0);
        l.setCreatedAt(LocalDateTime.now());
        lotteryMapper.insert(l);

        String title = kind == Lottery.KIND_PICK ? "随机点名" : "随机分组";
        postCard(containerId, userId, l.getId(), "[抽签] " + title);

        return detail(userId, containerId, l.getId());
    }

    /**
     * 候选名单。
     *
     * <p>{@code source=1} 但群没有关联班级时直接报错，而不是退化成"群成员"——
     * 群里现在只有老师，退化的结果是"从 1 个人里抽 3 个"，
     * 报错至少能让人知道该手动选名单。
     */
    private List<PoolItem> buildPool(SubjectContainer container, int source, List<Long> manualIds) {
        List<PoolItem> pool = new ArrayList<>();

        if (source == Lottery.SOURCE_MANUAL) {
            if (manualIds == null || manualIds.isEmpty()) {
                throw new BizException(1001, "请至少勾选一个人");
            }
            Set<Long> uniq = new LinkedHashSet<>(manualIds);
            List<Student> students = studentMapper.selectBatchIds(uniq);
            if (students.size() != uniq.size()) {
                throw new BizException(1001, "勾选的名单里有学生不存在");
            }
            for (Student s : students) {
                /* 有班级时校验归属：不然能拿别班的学生来抽签 */
                if (container.getClassId() != null && !Objects.equals(s.getClassId(), container.getClassId())) {
                    throw new BizException(3002, s.getName() + " 不在这个班");
                }
                pool.add(new PoolItem(s.getId(), s.getName()));
            }
            /* selectBatchIds 不保证顺序，按勾选顺序重排，结果更好解释 */
            Map<Long, PoolItem> byId = new HashMap<>();
            for (PoolItem p : pool) {
                byId.put(p.getId(), p);
            }
            pool.clear();
            for (Long id : uniq) {
                pool.add(byId.get(id));
            }
            return pool;
        }

        if (container.getClassId() == null) {
            throw new BizException(1001, "这个群没有关联班级，请手动选择名单");
        }
        List<Student> students = studentMapper.selectList(new LambdaQueryWrapper<Student>()
                .eq(Student::getClassId, container.getClassId())
                .eq(Student::getStatus, 1)
                .orderByAsc(Student::getId));
        for (Student s : students) {
            pool.add(new PoolItem(s.getId(), s.getName()));
        }
        return pool;
    }

    // ===============================================================
    // 开奖
    // ===============================================================

    @Override
    @Transactional
    public LotteryVO draw(Long userId, Long containerId, Long lotteryId) {
        auth.requireMember(userId, containerId);

        /* 行锁：两个人同时点「开始抽」时，不加锁会各抽一批、各写一批结果，
           名单里出现两套"这一轮的中签者" */
        Lottery l = lotteryMapper.selectOne(new LambdaQueryWrapper<Lottery>()
                .eq(Lottery::getId, lotteryId)
                .last("FOR UPDATE"));
        if (l == null || !Objects.equals(l.getContainerId(), containerId)) {
            throw new BizException(1004, "抽签不存在");
        }
        assertManager(l, userId);

        if (l.getStatus() != null && l.getStatus() == Lottery.STATUS_CANCELED) {
            throw new BizException(4103, "抽签已取消");
        }

        List<PoolItem> pool = readPool(l.getPoolSnapshot());
        int round = (l.getRound() == null ? 0 : l.getRound()) + 1;

        if (l.getKind() != null && l.getKind() == Lottery.KIND_GROUP) {
            doGroup(l, pool, round);
        } else {
            doPick(l, pool, round);
        }

        lotteryMapper.update(null, new LambdaUpdateWrapper<Lottery>()
                .eq(Lottery::getId, lotteryId)
                .eq(Lottery::getVersion, l.getVersion() == null ? 0 : l.getVersion())
                .set(Lottery::getStatus, Lottery.STATUS_DRAWN)
                .set(Lottery::getRound, round)
                .set(Lottery::getDrawnAt, LocalDateTime.now())
                .set(Lottery::getVersion, (l.getVersion() == null ? 0 : l.getVersion()) + 1));

        return detail(userId, containerId, lotteryId);
    }

    /** 随机点名：抽 N 人，结果追加到本轮 */
    private void doPick(Lottery l, List<PoolItem> pool, int round) {
        int need = l.getDrawCount() == null ? 1 : l.getDrawCount();

        List<PoolItem> candidates = new ArrayList<>(pool);
        if (l.getDedupe() != null && l.getDedupe() == 1) {
            Set<Long> drawn = new LinkedHashSet<>();
            for (LotteryResult r : resultMapper.selectList(new LambdaQueryWrapper<LotteryResult>()
                    .eq(LotteryResult::getLotteryId, l.getId()))) {
                if (r.getStudentId() != null) {
                    drawn.add(r.getStudentId());
                }
            }
            candidates.removeIf(p -> drawn.contains(p.getId()));
        }

        if (candidates.size() < need) {
            throw new BizException(4105,
                    candidates.isEmpty() ? "名单已经抽完了，先重置记录" : "只剩 " + candidates.size() + " 人可抽了");
        }

        Collections.shuffle(candidates, rnd(l, round));
        for (int i = 0; i < need; i++) {
            PoolItem p = candidates.get(i);
            LotteryResult r = new LotteryResult();
            r.setLotteryId(l.getId());
            r.setRound(round);
            r.setStudentId(p.getId());
            r.setStudentName(p.getName());
            r.setSortNo(i + 1);
            resultMapper.insert(r);
        }
    }

    /** 随机分组：整体替换结果，不追加 —— 分组是"重新分一次"，不是"再分一组" */
    private void doGroup(Lottery l, List<PoolItem> pool, int round) {
        int groups = l.getGroupCount() == null ? 2 : l.getGroupCount();
        if (groups > pool.size()) {
            throw new BizException(1001, "名单只有 " + pool.size() + " 人，分不了 " + groups + " 组");
        }

        List<PoolItem> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, rnd(l, round));

        resultMapper.delete(new LambdaQueryWrapper<LotteryResult>()
                .eq(LotteryResult::getLotteryId, l.getId()));

        /* 除不尽时前 remainder 组多一个人，而不是全塞进最后一组 ——
           最后一组人特别多，会被学生当成"老师故意的" */
        int base = shuffled.size() / groups;
        int remainder = shuffled.size() % groups;
        int idx = 0;
        for (int g = 1; g <= groups; g++) {
            int size = base + (g <= remainder ? 1 : 0);
            for (int k = 0; k < size; k++) {
                PoolItem p = shuffled.get(idx++);
                LotteryResult r = new LotteryResult();
                r.setLotteryId(l.getId());
                r.setRound(round);
                r.setStudentId(p.getId());
                r.setStudentName(p.getName());
                r.setGroupNo(g);
                r.setSortNo(k + 1);
                resultMapper.insert(r);
            }
        }
    }

    private Random rnd(Lottery l, int round) {
        long seed = l.getSeed() == null ? 0L : l.getSeed();
        return new Random(seed ^ (round * 0x9E3779B97F4A7C15L));
    }

    // ===============================================================
    // 查询
    // ===============================================================

    @Override
    public LotteryVO detail(Long userId, Long containerId, Long lotteryId) {
        auth.requireMember(userId, containerId);
        Lottery l = lotteryMapper.selectById(lotteryId);
        if (l == null || !Objects.equals(l.getContainerId(), containerId)) {
            throw new BizException(1004, "抽签不存在");
        }

        boolean mine = Objects.equals(l.getCreatorId(), userId);
        boolean visible = mine || (l.getPublicResult() != null && l.getPublicResult() == 1);

        LotteryVO vo = new LotteryVO();
        List<LotteryResult> results = visible
                ? resultMapper.selectList(new LambdaQueryWrapper<LotteryResult>()
                        .eq(LotteryResult::getLotteryId, lotteryId)
                        .orderByAsc(LotteryResult::getRound)
                        .orderByAsc(LotteryResult::getGroupNo)
                        .orderByAsc(LotteryResult::getSortNo))
                : Collections.emptyList();

        fillCard(vo, l, results, mine);
        vo.setRemaining(remaining(l, results));

        if (visible) {
            if (l.getKind() != null && l.getKind() == Lottery.KIND_GROUP) {
                vo.setGroups(toGroups(results));
            } else {
                vo.setRounds(toRounds(results));
            }
        } else {
            vo.setGroups(Collections.emptyList());
            vo.setRounds(Collections.emptyList());
        }
        return vo;
    }

    @Override
    public Map<Long, LotteryCardVO> cardMap(Long userId, Long containerId, List<Long> lotteryIds) {
        if (lotteryIds == null || lotteryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Lottery> rows = lotteryMapper.selectList(new LambdaQueryWrapper<Lottery>()
                .eq(Lottery::getContainerId, containerId)
                .in(Lottery::getId, lotteryIds));
        if (rows.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> ids = rows.stream().map(Lottery::getId).toList();
        List<LotteryResult> all = resultMapper.selectList(new LambdaQueryWrapper<LotteryResult>()
                .in(LotteryResult::getLotteryId, ids)
                .orderByAsc(LotteryResult::getRound)
                .orderByAsc(LotteryResult::getSortNo));

        Map<Long, List<LotteryResult>> byLottery = new HashMap<>();
        for (LotteryResult r : all) {
            byLottery.computeIfAbsent(r.getLotteryId(), k -> new ArrayList<>()).add(r);
        }

        Map<Long, LotteryCardVO> out = new LinkedHashMap<>();
        for (Lottery l : rows) {
            boolean mine = Objects.equals(l.getCreatorId(), userId);
            List<LotteryResult> rs = byLottery.getOrDefault(l.getId(), Collections.emptyList());
            /* 结果不公开时，非发起人拿到的是空列表 —— 判断在服务端，
               不靠前端"别渲染"来保护 */
            boolean visible = mine || (l.getPublicResult() != null && l.getPublicResult() == 1);
            LotteryCardVO c = new LotteryCardVO();
            fillCard(c, l, visible ? rs : Collections.emptyList(), mine);
            out.put(l.getId(), c);
        }
        return out;
    }

    private void fillCard(LotteryCardVO c, Lottery l, List<LotteryResult> results, boolean mine) {
        c.setId(l.getId());
        c.setKind(l.getKind());
        c.setStatus(l.getStatus());
        c.setPoolSize(readPool(l.getPoolSnapshot()).size());
        c.setDrawCount(l.getDrawCount());
        c.setGroupCount(l.getGroupCount());
        c.setMine(mine);

        if (l.getKind() != null && l.getKind() == Lottery.KIND_GROUP) {
            int groups = l.getGroupCount() == null ? 2 : l.getGroupCount();
            int size = readPool(l.getPoolSnapshot()).size();
            int per = groups == 0 ? 0 : size / groups;
            c.setGroupSummary(l.getStatus() != null && l.getStatus() == Lottery.STATUS_DRAWN
                    ? groups + " 组 · 每组约 " + per + " 人"
                    : "");
            c.setLatestNames(Collections.emptyList());
        } else {
            c.setGroupSummary("");
            c.setLatestNames(latestRoundNames(results));
        }
    }

    /** 最新一轮的名字。点名卡片只显示这一轮，不显示历史 */
    private List<String> latestRoundNames(List<LotteryResult> results) {
        int max = 0;
        for (LotteryResult r : results) {
            int rd = r.getRound() == null ? 0 : r.getRound();
            if (rd > max) {
                max = rd;
            }
        }
        if (max == 0) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>();
        for (LotteryResult r : results) {
            if (r.getRound() != null && r.getRound() == max) {
                names.add(r.getStudentName());
            }
        }
        return names;
    }

    private List<LotteryVO.RoundVO> toRounds(List<LotteryResult> results) {
        Map<Integer, List<String>> byRound = new LinkedHashMap<>();
        for (LotteryResult r : results) {
            int rd = r.getRound() == null ? 1 : r.getRound();
            byRound.computeIfAbsent(rd, k -> new ArrayList<>()).add(r.getStudentName());
        }
        List<LotteryVO.RoundVO> out = new ArrayList<>();
        for (Map.Entry<Integer, List<String>> e : byRound.entrySet()) {
            LotteryVO.RoundVO r = new LotteryVO.RoundVO();
            r.setRound(e.getKey());
            r.setNames(e.getValue());
            out.add(r);
        }
        return out;
    }

    private List<LotteryVO.GroupVO> toGroups(List<LotteryResult> results) {
        Map<Integer, List<String>> byGroup = new LinkedHashMap<>();
        for (LotteryResult r : results) {
            int g = r.getGroupNo() == null ? 1 : r.getGroupNo();
            byGroup.computeIfAbsent(g, k -> new ArrayList<>()).add(r.getStudentName());
        }
        List<LotteryVO.GroupVO> out = new ArrayList<>();
        for (Map.Entry<Integer, List<String>> e : byGroup.entrySet()) {
            LotteryVO.GroupVO g = new LotteryVO.GroupVO();
            g.setGroupNo(e.getKey());
            g.setNames(e.getValue());
            out.add(g);
        }
        return out;
    }

    /** 去重模式下还能抽几个人；不分模式都返回，前端只在点名模式下用 */
    private int remaining(Lottery l, List<LotteryResult> results) {
        int size = readPool(l.getPoolSnapshot()).size();
        if (l.getDedupe() == null || l.getDedupe() == 0) {
            return size;
        }
        Set<Long> drawn = new HashSet<>();
        for (LotteryResult r : results) {
            if (r.getStudentId() != null) {
                drawn.add(r.getStudentId());
            }
        }
        return Math.max(0, size - drawn.size());
    }

    // ===============================================================
    // 小工具
    // ===============================================================

    /** 开奖权限：发起人，或群主 / 班委。群主能代为开奖是因为发起人可能已经退群 */
    private void assertManager(Lottery l, Long userId) {
        if (Objects.equals(l.getCreatorId(), userId)) {
            return;
        }
        ContainerMember m = auth.findMember(l.getContainerId(), userId);
        if (m != null && m.getStatus() != null && m.getStatus() == ContainerMember.STATUS_IN
                && (m.getRole() == ContainerMember.ROLE_OWNER || m.getRole() == ContainerMember.ROLE_ADMIN)) {
            return;
        }
        throw new BizException(3002, "只有发起人可以开奖");
    }

    private String writePool(List<PoolItem> pool) {
        try {
            return json.writeValueAsString(pool);
        } catch (Exception e) {
            throw new BizException(5000, "名单保存失败");
        }
    }

    private List<PoolItem> readPool(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Collections.emptyList();
        }
        try {
            return json.readValue(raw, new TypeReference<List<PoolItem>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * 往群里发抽签卡片。
     *
     * <p>和接龙同理：直接操作 mapper，不调 ContainerMessageService，
     * 否则构造器循环依赖。
     */
    private void postCard(Long containerId, Long senderId, Long refId, String fallback) {
        ContainerMessage m = new ContainerMessage();
        m.setContainerId(containerId);
        m.setSenderId(senderId);
        m.setContent(fallback);
        m.setType(ContainerMessage.TYPE_LOTTERY);
        m.setRefId(refId);
        m.setStatus(ContainerMessage.STATUS_NORMAL);
        m.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(m);
    }

    /** 名单快照里的一项。字段名短，因为它要存进 TEXT 列里 */
    @Data
    public static class PoolItem {
        private Long id;
        private String name;

        public PoolItem() {
        }

        public PoolItem(Long id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
