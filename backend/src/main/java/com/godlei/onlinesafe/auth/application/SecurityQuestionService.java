package com.godlei.onlinesafe.auth.application;

import com.godlei.onlinesafe.auth.domain.BuiltinSecurityQuestions;
import com.godlei.onlinesafe.auth.domain.SecurityQuestionType;
import com.godlei.onlinesafe.auth.domain.UserSecurityQuestion;
import com.godlei.onlinesafe.auth.infrastructure.UserSecurityQuestionRepository;
import com.godlei.onlinesafe.auth.web.SecurityQuestionRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class SecurityQuestionService {

    private final UserSecurityQuestionRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAnswerNormalizer answerNormalizer;

    public SecurityQuestionService(
            UserSecurityQuestionRepository repository,
            PasswordEncoder passwordEncoder,
            SecurityAnswerNormalizer answerNormalizer
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.answerNormalizer = answerNormalizer;
    }

    public void saveForRegistration(String ownerId, List<SecurityQuestionRequest> requests) {
        if (requests == null || requests.isEmpty() || requests.size() > 3) {
            throw new InvalidRegistrationException("SECURITY_QUESTION_COUNT_INVALID", "密保问题数量须为 1 至 3 道");
        }

        Set<String> fingerprints = new HashSet<>();
        int index = 0;
        for (SecurityQuestionRequest request : requests) {
            PreparedQuestion prepared = prepare(request);
            if (!fingerprints.add(prepared.fingerprint())) {
                throw new InvalidRegistrationException("SECURITY_QUESTION_DUPLICATE", "密保问题不能重复");
            }
            String normalized = answerNormalizer.normalize(request.answer());
            if (normalized.isBlank()) {
                throw new InvalidRegistrationException("SECURITY_ANSWER_EMPTY", "密保答案不能为空");
            }
            repository.save(UserSecurityQuestion.create(
                    ownerId,
                    prepared.type(),
                    prepared.code(),
                    prepared.text(),
                    passwordEncoder.encode(normalized),
                    index
            ));
            index++;
        }
    }

    public List<UserSecurityQuestion> listByOwner(String ownerId) {
        return repository.findByOwnerIdOrderBySortOrderAsc(ownerId);
    }

    public boolean verifyAll(List<UserSecurityQuestion> stored, List<String> answers) {
        if (stored == null || answers == null || stored.size() != answers.size()) {
            return false;
        }
        for (int i = 0; i < stored.size(); i++) {
            String normalized = answerNormalizer.normalize(answers.get(i));
            if (!passwordEncoder.matches(normalized, stored.get(i).getAnswerHash())) {
                return false;
            }
        }
        return true;
    }

    private PreparedQuestion prepare(SecurityQuestionRequest request) {
        if (request.questionType() == SecurityQuestionType.BUILTIN) {
            if (request.questionCode() == null || request.questionCode().isBlank()) {
                throw new InvalidRegistrationException("SECURITY_QUESTION_CODE_REQUIRED", "请选择内置密保问题");
            }
            String code = request.questionCode().trim();
            String text = BuiltinSecurityQuestions.textOf(code)
                    .orElseThrow(() -> new InvalidRegistrationException(
                            "SECURITY_QUESTION_CODE_UNKNOWN",
                            "未知的内置密保问题"
                    ));
            return new PreparedQuestion(SecurityQuestionType.BUILTIN, code, text, "BUILTIN:" + code);
        }

        if (request.questionType() == SecurityQuestionType.CUSTOM) {
            String text = request.questionText() == null ? "" : request.questionText().trim();
            if (text.length() < 4 || text.length() > 200) {
                throw new InvalidRegistrationException("SECURITY_QUESTION_TEXT_INVALID", "自定义问题长度为 4 至 200 字");
            }
            return new PreparedQuestion(SecurityQuestionType.CUSTOM, null, text, "CUSTOM:" + text.toLowerCase());
        }

        throw new InvalidRegistrationException("SECURITY_QUESTION_TYPE_INVALID", "密保问题类型无效");
    }

    private record PreparedQuestion(
            SecurityQuestionType type,
            String code,
            String text,
            String fingerprint
    ) {
    }
}
