package io.github.mawsonlakes790913.chineseoutputforge.config;

import java.util.Locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;


@Configuration
public class LocaleConfig implements WebMvcConfigurer {

    /**
     * アプリケーションで使用するLocaleResolverを設定する。
     * 言語設定はセッション単位で保持する。
     *
     * @return LocaleResolver
     */
    @Bean
    LocaleResolver localeResolver() {

        SessionLocaleResolver resolver = new SessionLocaleResolver();

        // デフォルトは日本語
        resolver.setDefaultLocale(Locale.JAPANESE);

        return resolver;
    }

    /**
     * URLパラメータによる言語切り替えを行うInterceptorを設定する。
     *
     * @return LocaleChangeInterceptor
     */
    @Bean
    LocaleChangeInterceptor localeChangeInterceptor() {

        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();

        // ?lang=ja などの lang を監視
        interceptor.setParamName("lang");

        return interceptor;
    }

    /**
     * 言語切り替え用のInterceptorをSpring MVCに登録する。
     *
     * @param registry Interceptorの登録先
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }
}
