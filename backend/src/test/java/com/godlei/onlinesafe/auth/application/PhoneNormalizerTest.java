package com.godlei.onlinesafe.auth.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneNormalizerTest {

    private final PhoneNormalizer normalizer = new PhoneNormalizer();

    @Test
    void normalizesMainlandChinaNumber() {
        assertThat(normalizer.normalize("138 0013 8000")).isEqualTo("+8613800138000");
        assertThat(normalizer.normalize("0086-13800138000")).isEqualTo("+8613800138000");
    }

    @Test
    void rejectsNonMainlandChinaNumber() {
        assertThatThrownBy(() -> normalizer.normalize("+14155552671"))
                .isInstanceOf(InvalidRegistrationException.class);
    }

    @Test
    void rejectsInvalidChinaMobilePrefix() {
        assertThatThrownBy(() -> normalizer.normalize("12800138000"))
                .isInstanceOf(InvalidRegistrationException.class);
    }

    @Test
    void rejectsInvalidPhoneNumber() {
        assertThatThrownBy(() -> normalizer.normalize("12345"))
                .isInstanceOf(InvalidRegistrationException.class)
                .hasMessage("手机号格式不正确");
    }
}
