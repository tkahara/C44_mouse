package com.kakogawa.traffic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // 💡【重要】これを追記することで、12時間おきの定期タイマー（Scheduled）が自動で作動するようになります
public class TrafficApplication {
    public static void main(String[] args) {
        // Spring Bootアプリケーションを起動する命令
        SpringApplication.run(TrafficApplication.class, args);
    }
}
