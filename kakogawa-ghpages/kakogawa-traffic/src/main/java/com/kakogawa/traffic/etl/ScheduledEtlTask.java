package com.kakogawa.traffic.etl;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledEtlTask {

    private final HyogoPoliceEtlApp etlApp;

    // 💡 Spring Boot が自動的に先ほどの実行エンジン（HyogoPoliceEtlApp）をここに注入します
    public ScheduledEtlTask(HyogoPoliceEtlApp etlApp) {
        this.etlApp = etlApp;
    }

    // 🔄【12時間おきの自動定期実行タイマー】
    // fixedRate = 43200000 ミリ秒（＝ちょうど 12 時間）ごとにバックグラウンドで自動作動します。
    // initialDelay = 10000 ミリ秒（＝アプリ起動の 10 秒後）に最初の 1 回目が自動で即実行されます。
    @Scheduled(fixedRate = 43200000, initialDelay = 10000)
    public void executeAutomaticEtl() {
        System.out.println("[定期タイマー発火] 12時間おきの自動データ同期スケジュールを実行します...");
        
        // 先ほど作ったエンジンの runOnce メソッドを安全に呼び出す
        etlApp.runOnce();
    }
}
