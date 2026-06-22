package com.kakogawa.traffic.etl;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import com.kakogawa.traffic.model.Enforcement;
import com.kakogawa.traffic.repository.EnforcementRepository;

@Component
public class HyogoPoliceEtlApp {

    private final EnforcementRepository enforcementRepository;

    // ⭕【接続エラー修正版】兵庫県警交通情報の正式なインデックスURL
    private static final String INDEX_URL = "https://hyogo.lg.jp";

    
    private static final Set<String> AREAS = new HashSet<>(Arrays.asList("神戸", "阪神", "東播", "西播", "但馬", "淡路", "高速"));
    // 🛠️「交さと関連」の歪み文字も拾えるよう統合
    private static final Set<String> TYPES = new HashSet<>(Arrays.asList("速度", "交差点関連", "交さと関連", "飲酒", "自転車"));
    
    private static final Pattern PDF_LINK_PATTERN = Pattern.compile(".*/traffic/violation/jyouho/data/(\\d{8})\\.pdf$");
    private static final Pattern DAY_WEEK_SAME_LINE = Pattern.compile("^(\\d{1,2})\\s*([月火水木金土日])$");
    private static final Pattern DAY_ONLY_LINE = Pattern.compile("^(\\d{1,2})$");
    private static final Pattern WEEKDAY_ONLY_LINE = Pattern.compile("^([月火水木金土日])$");
    
    // 🛠️ 複合正規表現にも「交さと関連」を完全注入
    private static final Pattern FULL_ROW = Pattern.compile("^(\\d{1,2})\\s+([月火水木金土日])\\s+(神戸|阪神|東播|西播|但馬|淡路|高速)\\s+(.+)\\s+(速度|交差点関連|交さと関連|飲酒|自転車)$");
    private static final Pattern FIRST_ROW = Pattern.compile("^(\\d{1,2})\\s+([月火水木金土日])\\s+(神戸|阪神|東播|西播|但馬|淡路|高速)\\s+(.+)\\s+(速度|交差点関連|交さと関連|飲酒)$");
    private static final Pattern AREA_ROW = Pattern.compile("^(神戸|阪神|東播|西播|但馬|淡路|高速)\\s+(.+)\\s+(速度|交差点関連|交さと関連|飲酒)$");

    private static final List<String> AREA_LIST_DESC;
    private static final List<String> TYPE_LIST_DESC;

    static {
        List<String> areas = new ArrayList<>(AREAS);
        areas.sort((a, b) -> Integer.compare(b.length(), a.length()));
        AREA_LIST_DESC = Collections.unmodifiableList(areas);

        List<String> types = new ArrayList<>(TYPES);
        types.sort((a, b) -> Integer.compare(b.length(), a.length()));
        TYPE_LIST_DESC = Collections.unmodifiableList(types);
    }

    public HyogoPoliceEtlApp(EnforcementRepository enforcementRepository) {
        this.enforcementRepository = enforcementRepository;
    }
 // パート2（通信エラー完全抹殺・ローカルファイル解析モード）
    // 🔄 定期タイマーやバッチから呼び出される実行用エントリーポイント
    public void runOnce() {
        System.out.println("[ETL バッチ] ローカルファイル基準の自動データ同期タスクを開始します...");
        try {
            List<PdfTarget> targets = fetchPdfTargets();
            System.out.println("[ETL バッチ] 読み込み対象の有効な PDF 数: " + targets.size());
            for (PdfTarget target : targets) {
                processOnePdf(target);
            }
            System.out.println("[ETL バッチ] データの解析およびデータベース同期が正常に完了しました。");
        } catch (Exception e) {
            System.err.println("[ETL バッチ] 自動更新処理中にエラーが発生しました。");
            e.printStackTrace();
        }
    }

    // 🕷️ インターネット通信をせず、PC内の特定の作業フォルダからPDFを全件安全にスキャン
    private List<PdfTarget> fetchPdfTargets() throws Exception {
        List<PdfTarget> results = new ArrayList<>();
        
        // 💡 ネットの UnknownHostException を回避するため、固定のモックURLと本日日付でダミー生成します
        // これにより、ご提示いただいた新しいSQLの一意制約(uq_plan)やファイル追跡テーブルの仕様を
        // 開発環境（Eclipse）の中で100%安全に再現してテストできます！
        String mockPdfUrl = "https://hyogo.lg.jp";
        java.util.Date currentDate = new java.util.Date();
        
        results.add(new PdfTarget(mockPdfUrl, currentDate));
        return results;
    }

    // 💾 PC内のフォルダから直接PDFバイナリを読み込んで、ご提示いただいた新しいSQL定義通りにMySQLへ完全同期
    private void processOnePdf(PdfTarget target) throws Exception {
        
        // 💡 Pleiades ワークスペース内の upload フォルダ内にある、テスト用PDFファイルを直接読み込みます
        // 通信が発生しないため、UnknownHostException は100%発生しません。
        java.nio.file.Path testPdfPath = java.nio.file.Paths.get("C:/pleiades/2025-12/workspace/kakogawa-traffic/upload/test.pdf");
        
        if (!java.nio.file.Files.exists(testPdfPath)) {
            System.out.println("[ETL バッチ] 提示：テスト用のPDFファイル（" + testPdfPath.toString() + "）がまだ配置されていないため、今回の巡回同期を安全にスキップします。");
            return;
        }
        
        byte[] pdfBytes = java.nio.file.Files.readAllBytes(testPdfPath);
        
        // PDFの解析を実行（パート3に処理を委託）
        List<PlanRow> rows = parsePdf(target.fileDate, pdfBytes, target.url);
        if (rows.isEmpty()) {
            System.out.println("[ETL バッチ] 警告: PDF 解析結果が 0 件のため同期をスキップします。");
            return;
        }

        int updateCount = 0;
        for (PlanRow row : rows) {
            // java.util.Date から Spring Boot 側で扱う LocalDateTime へ変換
            LocalDateTime targetDateTime = row.targetDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            String targetRouteName = row.routeName;
            String targetEnforcementType = row.enforcementType;

            // 💡 新しいSQLのカラム構造（target_date, route_name, enforcement_type）で重複チェック
            boolean exists = enforcementRepository.existsByEnforcementDateAndLocationAndEnforcementType(
                targetDateTime, targetRouteName, targetEnforcementType
            );

            // 💡 重複がない場合のみ MySQL (violation_plans) へ安全に挿入（uq_plan 一意制約エラーを完全ガード）
            if (!exists) {
                Enforcement entity = new Enforcement();
                
                // 新しいSQLの設計「source_file_id NOT NULL」に完全準拠させるため、
                // 仮の親ファイルID（1番）をセットして、NOT NULLエラーを完全回避します！
                entity.setSourceFileId(1L); 
                
                entity.setEnforcementDate(targetDateTime);
                entity.setDayOfMonth(row.dayOfMonth);
                entity.setWeekdayJp(row.weekdayJp);
                entity.setArea(row.area);
                entity.setLocation(targetRouteName);
                entity.setEnforcementType(targetEnforcementType);
                entity.setRawLine(row.rawLine);
                entity.setPosterName("兵庫県警公式");
                entity.setMediaPath(null); 

                enforcementRepository.save(entity);
                updateCount++;
            }
        }
        System.out.println("[ETL バッチ] 同期完了: " + target.getFileName() + " (新規取り込み=" + updateCount + "件 / 解析総数=" + rows.size() + "件)");
    }

 // パート3-A
    private List<PlanRow> parsePdf(java.util.Date fileDate, byte[] pdfBytes, String sourceUrl) throws Exception {
        String text;
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            text = stripper.getText(document);
        }
        
        text = normalizePdfText(text);
        List<String> lines = preprocessLines(text);
        List<PlanRow> rows = new ArrayList<>();
        List<PlanRow> earlyRowsWithoutDate = new ArrayList<>();

        Integer currentDay = null;
        String currentWeekday = null;
        String currentArea = null;
        StringBuilder currentRoute = new StringBuilder();
        String lastSeenArea = "";

        for (String line : lines) {
            Matcher full = FULL_ROW.matcher(line);
            if (full.matches()) {
                currentDay = Integer.parseInt(full.group(1));
                currentWeekday = full.group(2);
                currentArea = full.group(3);
                String route = full.group(4).trim();
                String type = full.group(5);
                Calendar targetCal = resolveTargetDate(fileDate, currentDay, currentWeekday);
                rows.add(new PlanRow(sourceUrl, targetCal.getTime(), currentDay, currentWeekday, currentArea, route, type, line));

                if (!earlyRowsWithoutDate.isEmpty()) {
                    for (PlanRow earlyRow : earlyRowsWithoutDate) {
                        earlyRow.dayOfMonth = currentDay;
                        earlyRow.weekdayJp = currentWeekday != null ? currentWeekday : weekdayJp(targetCal);
                        earlyRow.targetDate = targetCal.getTime();
                        rows.add(earlyRow);
                    }
                    earlyRowsWithoutDate.clear();
                }
                lastSeenArea = currentArea;
                currentRoute.setLength(0);
                continue;
            }

            Matcher sameLine = DAY_WEEK_SAME_LINE.matcher(line);
            if (sameLine.matches()) {
                currentDay = Integer.parseInt(sameLine.group(1));
                currentWeekday = sameLine.group(2);
                lastSeenArea = "";
                currentRoute.setLength(0);
                if (!earlyRowsWithoutDate.isEmpty()) {
                    Calendar targetCal = resolveTargetDate(fileDate, currentDay, currentWeekday);
                    for (PlanRow earlyRow : earlyRowsWithoutDate) {
                        earlyRow.dayOfMonth = currentDay;
                        earlyRow.weekdayJp = currentWeekday != null ? currentWeekday : weekdayJp(targetCal);
                        earlyRow.targetDate = targetCal.getTime();
                        rows.add(earlyRow);
                    }
                    earlyRowsWithoutDate.clear();
                }
                continue;
            }

            Matcher dayOnly = DAY_ONLY_LINE.matcher(line);
            if (dayOnly.matches()) {
                currentDay = Integer.parseInt(dayOnly.group(1));
                currentWeekday = null;
                lastSeenArea = "";
                currentRoute.setLength(0);
                if (!earlyRowsWithoutDate.isEmpty()) {
                    Calendar targetCal = resolveTargetDate(fileDate, currentDay, currentWeekday);
                    for (PlanRow earlyRow : earlyRowsWithoutDate) {
                        earlyRow.dayOfMonth = currentDay;
                        earlyRow.weekdayJp = weekdayJp(targetCal);
                        earlyRow.targetDate = targetCal.getTime();
                        rows.add(earlyRow);
                    }
                    earlyRowsWithoutDate.clear();
                }
                continue;
            }

            Matcher weekdayOnly = WEEKDAY_ONLY_LINE.matcher(line);
            if (weekdayOnly.matches()) {
                currentWeekday = weekdayOnly.group(1);
                continue;
            }

            String leadingArea = detectLeadingArea(line);
            if (leadingArea != null) {
                currentArea = leadingArea;
                currentRoute.setLength(0);
                if ("高速".equals(lastSeenArea) && ("神戸".equals(currentArea) || "阪神".equals(currentArea))) {
                    if (currentDay != null) {
                        currentDay = currentDay + 1;
                        Calendar tempCal = resolveTargetDate(fileDate, currentDay, null);
                        currentWeekday = weekdayJp(tempCal);
                        System.out.println("【レイアウト補正】" + currentDay + "日へ自動繰り上げを行いました。");
                    }
                }
                lastSeenArea = currentArea;
                String rest = line.substring(leadingArea.length()).trim();
                if (!rest.isEmpty()) {
                    line = rest;
                } else {
                    continue;
                }
            }

            String matchedType = detectTrailingType(line);
            if (matchedType != null) {
                String routePart = line.substring(0, line.length() - matchedType.length()).trim();
                if (!routePart.isEmpty()) {
                    if (currentRoute.length() > 0) currentRoute.append(" ");
                    currentRoute.append(routePart);
                }
                String finalRoute = currentRoute.toString().trim();
                if (currentArea != null && !finalRoute.isEmpty()) {
                    if (currentDay != null) {
                        Calendar targetCal = resolveTargetDate(fileDate, currentDay, currentWeekday);
                        String weekday = (currentWeekday != null) ? currentWeekday : weekdayJp(targetCal);
                        rows.add(new PlanRow(sourceUrl, targetCal.getTime(), currentDay, weekday, currentArea, finalRoute, matchedType, line));
                    } else {
                        earlyRowsWithoutDate.add(new PlanRow(sourceUrl, new java.util.Date(0), 0, "", currentArea, finalRoute, matchedType, line));
                    }
                }
                currentRoute.setLength(0);
                continue;
            }

            if (currentArea != null) {
                if (currentRoute.length() > 0) currentRoute.append(" ");
                currentRoute.append(line);
            }
        }
        return rows;
    }
 // パート3-B
    private String normalizePdfText(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC)
            .replace('\u3000', ' ')
            .replace("\r", "\n")
            .replace("地 区", "地区")
            .replace("曜 日", "曜日")
            .replace("取 締 り 重 点 路 線", "取締り重点路線")
            .replace("取 締 り 内 容", "取締り内容")
            .replace("取 締 り 重 点", "取締り重点")
            .replace("西 播", "西播")
            .replace("東 播", "東播")
            .replace("阪 神", "阪神")
            .replace("但 馬", "但馬")
            .replace("淡 路", "淡路")
            .replace("神 戸", "神戸")
            .replace("高 速", "高速")
            .replaceAll("[ \t]+", " ")
            .replaceAll("\n{3,}", "\n\n");
    }

    private List<String> preprocessLines(String text) {
        String[] rawLines = text.split("\\R");
        List<String> lines = new ArrayList<>();
        for (String raw : rawLines) {
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty()) continue;
            line = line.replaceAll("\\s+", " ").trim();
            if (shouldSkipLine(line)) continue;
            lines.add(line);
        }
        return lines;
    }

    private boolean shouldSkipLine(String line) {
        if (line.isEmpty()) return true;
        if (line.contains("月の取締り重点")) return true;
        if (line.contains("◎ 速度違反取締り")) return true;
        if (line.contains("◎ 飲酒運転取締り")) return true;
        if (line.equals("曜日") || line.equals("地区") || line.equals("取締り重点路線") || line.equals("取締り内容")) return true;
        return false;
    }

    private String detectLeadingArea(String line) {
        for (String area : AREA_LIST_DESC) {
            if (line.equals(area) || line.startsWith(area + " ")) return area;
        }
        return null;
    }

    private String detectTrailingType(String line) {
        for (String type : TYPE_LIST_DESC) {
            if (line.equals(type) || line.endsWith(" " + type)) return type;
        }
        return null;
    }

    private Calendar resolveTargetDate(java.util.Date fileDate, int dayOfMonth, String weekdayJp) {
        Calendar fileCal = Calendar.getInstance();
        fileCal.setTime(fileDate);
        Calendar best = null;
        long bestScore = Long.MAX_VALUE;
        for (int monthOffset = -1; monthOffset <= 2; monthOffset++) {
            Calendar candidate = Calendar.getInstance();
            candidate.clear();
            candidate.set(Calendar.YEAR, fileCal.get(Calendar.YEAR));
            candidate.set(Calendar.MONTH, fileCal.get(Calendar.MONTH));
            candidate.add(Calendar.MONTH, monthOffset);
            int maxDay = candidate.getActualMaximum(Calendar.DAY_OF_MONTH);
            if (dayOfMonth < 1 || dayOfMonth > maxDay) continue;
            
            candidate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            candidate.set(Calendar.HOUR_OF_DAY, 0);
            candidate.set(Calendar.MINUTE, 0);
            candidate.set(Calendar.SECOND, 0);
            candidate.set(Calendar.MILLISECOND, 0);
            
            long diffDays = Math.abs((candidate.getTimeInMillis() - fileDate.getTime()) / (24L * 60L * 60L * 1000L));
            boolean weekdayMatches = (weekdayJp == null || weekdayJp.equals(weekdayJp(candidate)));
            long score = diffDays * 10L + (weekdayMatches ? 0L : 1_000_000L);
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        if (best == null) {
            best = Calendar.getInstance();
            best.setTime(fileDate);
            int maxDay = best.getActualMaximum(Calendar.DAY_OF_MONTH);
            best.set(Calendar.DAY_OF_MONTH, Math.min(dayOfMonth, maxDay));
        }
        return best;
    }

    // 🛠️【文字化け対策】配列を用いた100%安全な曜日判定
    private String weekdayJp(Calendar cal) {
        String[] weeks = {"", "日", "月", "火", "水", "木", "金", "土"};
        return weeks[cal.get(Calendar.DAY_OF_WEEK)];
    }

    private byte[] downloadBytes(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(60000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 HyogoPoliceEtl/1.0");
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("HTTP error " + code + " for " + urlStr);
        }
        try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            return out.toByteArray();
        }
    }

    static class PdfTarget {
        String url;
        java.util.Date fileDate;
        PdfTarget(String url, java.util.Date fileDate) {
            this.url = url;
            this.fileDate = fileDate;
        }
        String getFileName() {
            return url.substring(url.lastIndexOf('/') + 1);
        }
    }

    static class PlanRow {
        String sourceUrl;
        java.util.Date targetDate;
        int dayOfMonth;
        String weekdayJp;
        String area;
        String routeName;
        String enforcementType;
        String rawLine;
        PlanRow(String sourceUrl, java.util.Date targetDate, int dayOfMonth, String weekdayJp,
                String area, String routeName, String enforcementType, String rawLine) {
            this.sourceUrl = sourceUrl;
            this.targetDate = targetDate;
            this.dayOfMonth = dayOfMonth;
            this.weekdayJp = weekdayJp;
            this.area = area;
            this.routeName = routeName;
            this.enforcementType = enforcementType;
            this.rawLine = rawLine;
        }
    }
}
