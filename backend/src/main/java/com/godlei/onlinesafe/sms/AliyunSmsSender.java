package com.godlei.onlinesafe.sms;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponse;
import com.aliyun.teaopenapi.models.Config;
import com.godlei.onlinesafe.auth.application.SmsException;
import com.godlei.onlinesafe.auth.domain.SmsPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 阿里云「号码认证」短信验证码发送（dypnsapi / SendSmsVerifyCode）。
 * <p>赠送模板 CODE（如 100001）只能走本接口，不能走普通短信 Dysmsapi.SendSms。
 */
public class AliyunSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(AliyunSmsSender.class);

    private final SmsProperties properties;
    private final Client client;

    public AliyunSmsSender(SmsProperties properties) {
        this.properties = properties;
        try {
            Config config = new Config()
                    .setAccessKeyId(properties.accessKeyId())
                    .setAccessKeySecret(properties.accessKeySecret());
            config.endpoint = "dypnsapi.aliyuncs.com";
            this.client = new Client(config);
        } catch (Exception exception) {
            throw new IllegalStateException("初始化阿里云号码认证客户端失败", exception);
        }
    }

    @Override
    public void send(String phoneE164, SmsPurpose purpose, String code) {
        String templateCode = switch (purpose) {
            case REGISTER -> properties.templateRegister();
            case RESET_PASSWORD -> properties.templateReset();
        };
        if (properties.signName().isBlank() || templateCode.isBlank()) {
            throw new SmsException("SMS_CONFIG_INVALID", "短信服务未正确配置");
        }

        String phoneNumber = toMainlandMobile(phoneE164);
        int expireMinutes = Math.max(1, (int) Math.ceil(properties.codeTtlSeconds() / 60.0));
        try {
            SendSmsVerifyCodeRequest request = new SendSmsVerifyCodeRequest()
                    .setPhoneNumber(phoneNumber)
                    .setSignName(properties.signName())
                    .setTemplateCode(templateCode)
                    // 自管验证码明文下发；min 与有效期文案一致
                    .setTemplateParam("{\"code\":\"" + code + "\",\"min\":\"" + expireMinutes + "\"}")
                    .setCodeLength((long) properties.codeLength())
                    .setValidTime((long) properties.codeTtlSeconds())
                    .setInterval((long) properties.sendIntervalSeconds())
                    .setReturnVerifyCode(false);
            SendSmsVerifyCodeResponse response = client.sendSmsVerifyCode(request);
            String responseCode = response.getBody() == null ? null : response.getBody().getCode();
            Boolean success = response.getBody() == null ? null : response.getBody().getSuccess();
            if (!Boolean.TRUE.equals(success) && !"OK".equalsIgnoreCase(responseCode)) {
                String message = response.getBody() == null ? "未知错误" : response.getBody().getMessage();
                log.warn("阿里云认证短信发送失败 phone={} purpose={} code={} message={}",
                        mask(phoneE164), purpose, responseCode, message);
                throw new SmsException(
                        "SMS_SEND_FAILED",
                        "验证码发送失败（" + responseCode + "），请检查号码认证赠送签名与模板 CODE"
                );
            }
            log.info("阿里云认证短信已发送 phone={} purpose={}", mask(phoneE164), purpose);
        } catch (SmsException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("阿里云认证短信调用异常 phone={} purpose={}", mask(phoneE164), purpose, exception);
            throw new SmsException("SMS_SEND_FAILED", "验证码发送失败，请稍后再试");
        }
    }

    static String toMainlandMobile(String phoneE164) {
        if (phoneE164 != null && phoneE164.startsWith("+86") && phoneE164.length() == 14) {
            return phoneE164.substring(3);
        }
        throw new SmsException("PHONE_FORMAT_INVALID", "手机号格式不正确");
    }

    private static String mask(String phoneE164) {
        if (phoneE164 == null || phoneE164.length() < 8) {
            return "****";
        }
        return phoneE164.substring(0, phoneE164.length() - 8) + "****" + phoneE164.substring(phoneE164.length() - 4);
    }
}
