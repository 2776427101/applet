package com.banxing.classassistant.importer;

import java.util.List;

/**
 * 名单里我们认识的列。
 *
 * <p>每个字段带一组别名 —— 这是「表头自动识别」的全部知识来源。
 * 真实的表格里同一列可能叫「手机号」「手机」「联系电话」「本人电话」，
 * 全列出来比写正则去猜靠谱得多，也更容易维护：加一个别名就是加一个字符串。
 *
 * <p><b>别名顺序有讲究</b>：匹配时按列表顺序试，越具体的写在越前面。
 * 比如「家人电话1」必须排在「电话」前面，否则「家人电话1」会先被「电话」吃掉，
 * 五个电话列全挤到同一个字段上。
 */
public enum ImportField {

    /** 姓名。唯一必填的列 —— 没有姓名的表不可能是学生名单 */
    NAME("姓名", true, List.of(
            "姓名", "学生姓名", "名字", "学生名字", "姓 名")),

    /** 学号。可选，但强烈建议有：没有学号就没法判断「这个人是新同学还是老同学」 */
    STUDENT_NO("学号", false, List.of(
            "学号", "学生学号", "学籍号", "学籍号码")),

    GENDER("性别", false, List.of("性别")),

    BIRTHDAY("生日", false, List.of(
            "生日", "出生日期", "出生年月日", "出生年月", "出生")),

    DORMITORY("宿舍", false, List.of(
            "宿舍", "宿舍号", "寝室", "寝室号", "住宿", "住宿信息")),

    /** 学生本人的手机号。别名里刻意不放「电话」两个字 —— 那样会和「家人电话」抢 */
    PHONE("手机号", false, List.of(
            "手机号码", "手机号", "手机", "本人电话", "本人手机", "联系电话", "联系方式")),

    WECHAT("微信号", false, List.of("微信号", "微信")),

    /** 家人电话 1。不带序号的「家人电话」「家长电话」默认算第一个 */
    FAMILY_PHONE1("家人电话1", false, List.of(
            "家人电话1", "家人电话 1", "家长电话1", "家庭电话1",
            "家人电话", "家长电话", "家庭电话", "家长联系方式", "紧急联系电话", "监护人电话")),

    FAMILY_PHONE2("家人电话2", false, List.of(
            "家人电话2", "家人电话 2", "家长电话2", "家庭电话2",
            "备用电话", "第二联系人电话", "其他联系电话")),

    TRAINING_DIRECTION("培训方向", false, List.of(
            "培训方向", "培养方向", "专业方向", "就业方向", "岗位方向", "方向")),

    NATION("民族", false, List.of("民族")),

    POLITICAL_STATUS("政治面貌", false, List.of(
            "政治面貌", "政治身份", "党团关系")),

    PARTY_ACTIVIST("入党积极分子", false, List.of(
            "入党积极分子", "积极分子", "是否积极分子")),

    /**
     * 班级。<b>不入库，只用来判断这一行是不是本班的人。</b>
     *
     * <p>为什么不入库：一个班 69 个人，每个人的行里都存一遍「628班」，
     * 将来班级改名就要改 69 行，还可能改漏几行导致数据自相矛盾。
     * 班级是「学生的属性」里唯一一个应该由 t_class 单点维护的字段。
     */
    CLASS_NAME("班级", false, List.of(
            "班级", "班级名称", "所属班级", "行政班", "所在班级"));

    /** 表头里显示的名字 */
    private final String label;

    /** 是不是必须有这一列才能导入 */
    private final boolean required;

    /** 别名，按「越具体越靠前」排 */
    private final List<String> aliases;

    ImportField(String label, boolean required, List<String> aliases) {
        this.label = label;
        this.required = required;
        this.aliases = aliases;
    }

    public String getLabel() {
        return label;
    }

    public boolean isRequired() {
        return required;
    }

    public List<String> getAliases() {
        return aliases;
    }

    /** 这个字段要不要写进 t_student。班级不写 */
    public boolean isPersisted() {
        return this != CLASS_NAME;
    }
}
