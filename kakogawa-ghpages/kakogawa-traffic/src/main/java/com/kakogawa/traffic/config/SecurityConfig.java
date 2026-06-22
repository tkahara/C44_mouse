package com.kakogawa.traffic.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests((requests) -> requests
                // 🌐 公開エリア：トップ、一般ユーザー用画面、アップロードファイル、ETLなどは全員アクセス許可
                // 🛠️【バグ修正】「/upload/**」を解放リストに追加し、ブラウザからの画像アクセスを許可
                .requestMatchers("/", "/user/**", "/upload/**", "/uploads/**", "/error").permitAll()
                // 🛠️ 管理エリア：/adminから始まるURLはすべてログイン認証を必須にする
                .requestMatchers("/admin/**").authenticated()
                .anyRequest().authenticated()
            )
            .formLogin((form) -> form
                // 🔄【ログイン画面の設定】AdminControllerでマッピングしたログイン画面を指定
                .loginPage("/login")
                // ログインフォームのPOST送信先を明示指定
                .loginProcessingUrl("/login")
                // 🔄【超重要・ログイン成功時の着地先】認証成功後は必ず管理メニューTOP（/admin）へ強制転送する
                .defaultSuccessUrl("/admin", true) 
                .permitAll()
            )
            .logout((logout) -> logout
                .logoutUrl("/admin/logout")
                .logoutSuccessUrl("/")
                .permitAll()
            );

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        // 🔒 システムの管理者ログイン情報を定義（ID: admin / PW: password）
        UserDetails adminUser = User.withDefaultPasswordEncoder()
            .username("admin")
            .password("password")
            .roles("ADMIN")
            .build();

        return new InMemoryUserDetailsManager(adminUser);
    }
}
