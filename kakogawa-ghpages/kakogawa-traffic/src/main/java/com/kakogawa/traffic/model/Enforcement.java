package com.kakogawa.traffic.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.springframework.format.annotation.DateTimeFormat;

@Entity
@Table(name = "violation_plans") // データベース上の格納テーブル名をMySQL仕様へ統合
public class Enforcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_file_id", nullable = true)
    private Long sourceFileId; // 自動更新の場合は親ファイルのID、手動投稿はNULL

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    @Column(name = "target_date", nullable = false)
    private LocalDateTime enforcementDate; // 画面・Controllerとの連動用の変数名

    @Column(name = "day_of_month", nullable = false)
    private Integer dayOfMonth;

    @Column(name = "weekday_jp", length = 1, nullable = false)
    private String weekdayJp;

    @Column(name = "area", length = 20, nullable = false)
    private String area;

    @Column(name = "route_name", length = 255, nullable = false)
    private String location; // 画面表示用の変数名(location)にMySQLのroute_nameを結びつけ

    @Column(name = "enforcement_type", length = 50)
    private String enforcementType;

    @Column(name = "raw_line", length = 500)
    private String rawLine;

    @Column(name = "poster_name", length = 100)
    private String posterName;

    @Column(name = "media_path", length = 255)
    private String mediaPath;

    // 💡 画面の詳細文表示用ロジック（detail.html側の記述を変更せず動作させます）
    public String getDescription() {
        if (this.sourceFileId != null) {
            return this.enforcementType + "取り締まり重点路線 (兵庫県警公式公開情報)\n" + (this.rawLine != null ? "[元データ]: " + this.rawLine : "");
        }
        return this.rawLine; // 手動投稿の場合は、詳細状況テキストがraw_lineに格納されています
    }

    public void setDescription(String description) {
        this.rawLine = description; // 手動投稿の詳細文をMySQLのraw_lineへ格納
    }

    // ===================================================================
    // 💡 ゲッター・セッター（タイポと不要な記号を完全に排除したクリーン版）
    // ===================================================================
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSourceFileId() { return sourceFileId; }
    public void setSourceFileId(Long sourceFileId) { this.sourceFileId = sourceFileId; }

    public LocalDateTime getEnforcementDate() { return enforcementDate; }
    public void setEnforcementDate(LocalDateTime enforcementDate) { this.enforcementDate = enforcementDate; }

    public Integer getDayOfMonth() { return dayOfMonth; }
    public void setDayOfMonth(Integer dayOfMonth) { this.dayOfMonth = dayOfMonth; }

    public String getWeekdayJp() { return weekdayJp; }
    public void setWeekdayJp(String weekdayJp) { this.weekdayJp = weekdayJp; }

    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getEnforcementType() { return enforcementType; }
    public void setEnforcementType(String enforcementType) { this.enforcementType = enforcementType; }

    public String getRawLine() { return rawLine; }
    public void setRawLine(String rawLine) { this.rawLine = rawLine; }

    public String getPosterName() { return posterName; }
    public void setPosterName(String posterName) { this.posterName = posterName; }

    public String getMediaPath() { return mediaPath; }
    public void setMediaPath(String mediaPath) { this.mediaPath = mediaPath; }
}
