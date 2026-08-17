package io.github.mawsonlakes790913.chineseoutputforge.config;

import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
	
	@Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
	
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
	    
	    // アクセス制御の設定
	    http.authorizeHttpRequests(authorize -> authorize
	            .requestMatchers(
	                    PathRequest.toStaticResources().atCommonLocations()
	            ).permitAll()
	            .requestMatchers("/").permitAll()
	            .requestMatchers("/login").permitAll()
	            .requestMatchers("/practice/**").permitAll()
	            .requestMatchers("/signup", "/signup/**").permitAll()
	            .requestMatchers("/complete").permitAll()
	            .anyRequest().authenticated()
	        )
	        
	        // ログインの設定
	        .formLogin(login -> login
	            .loginPage("/login")
	            .usernameParameter("loginId")
	            .passwordParameter("password")
	            .defaultSuccessUrl("/", false)
	            .permitAll()
	        )
	        
	        // ログアウトの設定
	        .logout(logout -> logout
	            .logoutUrl("/logout")
	        );
	    
	    // CSRFを無効化
	    http.csrf(csrf -> csrf.disable());
	    
	    return http.build();
	}

}
