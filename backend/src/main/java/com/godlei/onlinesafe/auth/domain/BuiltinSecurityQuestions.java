package com.godlei.onlinesafe.auth.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 系统内置密保题库（常量，不落库）。
 */
public final class BuiltinSecurityQuestions {

    public record BuiltinQuestion(String code, String text) {
    }

    private static final List<BuiltinQuestion> QUESTIONS = List.of(
            new BuiltinQuestion("PET_NAME", "你第一只宠物叫什么名字？"),
            new BuiltinQuestion("BIRTH_CITY", "你出生在哪个城市？"),
            new BuiltinQuestion("PRIMARY_SCHOOL", "你小学母校的名称是什么？"),
            new BuiltinQuestion("MOTHER_MAIDEN", "你母亲的姓氏是什么？"),
            new BuiltinQuestion("FAVORITE_TEACHER", "对你影响最大的老师叫什么？"),
            new BuiltinQuestion("FIRST_JOB", "你第一份工作的公司或单位名称是什么？"),
            new BuiltinQuestion("CHILDHOOD_NICKNAME", "你小时候的绰号是什么？"),
            new BuiltinQuestion("FAVORITE_BOOK", "你最喜欢的一本书叫什么？")
    );

    private static final Map<String, String> BY_CODE = new LinkedHashMap<>();

    static {
        for (BuiltinQuestion question : QUESTIONS) {
            BY_CODE.put(question.code(), question.text());
        }
    }

    private BuiltinSecurityQuestions() {
    }

    public static List<BuiltinQuestion> all() {
        return QUESTIONS;
    }

    public static Optional<String> textOf(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_CODE.get(code));
    }

    public static boolean isKnown(String code) {
        return code != null && BY_CODE.containsKey(code);
    }
}
