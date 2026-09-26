package io.github.mawsonlakes790913.chineseoutputforge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordEncoderConfig {

    /**
     * パスワードのハッシュ化に使用するPasswordEncoderを生成する。
     * BCrypt方式を使用する。
     *
     * @return BCrypt方式のPasswordEncoder
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
