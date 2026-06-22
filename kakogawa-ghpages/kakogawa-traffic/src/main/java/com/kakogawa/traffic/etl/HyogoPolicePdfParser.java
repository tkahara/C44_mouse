package com.kakogawa.traffic.etl;

import java.text.Normalizer;
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

@Component
public class HyogoPolicePdfParser {

    private static final Set<String> AREAS = new HashSet<>(Arrays.asList("神戸", "阪神", "東播", "西播", "但馬", "淡路", "高速"));
    private static final List<String> AREA_LIST_DESC;
    private static final List<String> TYPE_LIST_DESC;
    private static final Set<String> TYPES = new HashSet<>(Arrays.asList("速度", "交さと関連", "飲酒", "自転車", "交差点関連"));
    
    private static final Pattern DAY_WEEK_SAME_LINE = Pattern.compile("^(\\d{1,2})\\s*([月火水木金土日])$");
    private static final Pattern DAY_ONLY_LINE = Pattern.compile("^(\\d{1,2})$");
    private static final Pattern WEEKDAY_ONLY_LINE = Pattern.compile("^([月火水木金土日])$");
    
    private static final Pattern FULL_ROW = Pattern.compile("^(\\d{1,2})\\s+([月火水木金土日])\\s+(神戸|阪神|東播|西播|但馬|淡路|高速)\\s+(.+)\\s+(速度|交さと関連|飲酒|自転車|交差点関連)$");

    static {
        List<String> areas = new ArrayList<>(AREAS);
        areas.sort((a, b) -> Integer.compare(b.length(), a.length()));
        AREA_LIST_DESC = Collections.unmodifiableList(areas);
        
        List<String> types = new ArrayList<>(TYPES);
        types.sort((a, b) -> Integer.compare(b.length(), a.length()));
        TYPE_LIST_DESC = Collections.unmodifiableList(types);
    }

    // 📄 PDFBoxを使用したコアなデータ解析ロジック
    public List<PlanRow> parsePdf(java.util.Date fileDate, byte[] pdfBytes, String sourceUrl) throws Exception {
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
                        earlyRow.weekdayJp = (currentWeekday != null) ? currentWeekday : weekdayJp(targetCal);
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
                        earlyRow.weekdayJp = (currentWeekday != null) ? currentWeekday : weekdayJp(targetCal);
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
            // 5. 地区名の検出とレイアウト補正
            String leadingArea = detectLeadingArea(line);
            if (leadingArea != null) {
                currentArea = leadingArea;
                currentRoute.setLength(0);
                
                // 【レイアウト補正】高速地区から神戸・阪神地区へ切り替わる際の自動繰り上げ処理
                if ("高速".equals(lastSeenArea) && ("神戸".equals(currentArea) || "阪神".equals(currentArea))) {
                    if (currentDay != null) {
                        currentDay = currentDay + 1;
                        Calendar tempCal = resolveTargetDate(fileDate, currentDay, null);
                        currentWeekday = weekdayJp(tempCal);
                        System.out.println("[ETLレイアウト補正] " + currentDay + "日へ自動繰り上げ補正を行いました。");
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
            
            // 6. 取締内容の検出とレコード保存
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

    // 📋 文字の全角半角や不自然なスペースの歪みを綺麗に整えるクレンジングメソッド
    private String normalizePdfText(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC)
                .replace('\u3000', ' ')
                .replace("\r", "\n")
                .replace("地 区", "地区")
                .replace("曜 日", "曜日")
                .replace("取 締 り 重 点 路 線", "取締り重点路線")
                .replace("取 締 り 内 容", "取締り内容")
                .replace("取 締 り 重 点", "取締り重点")
                .replaceAll("[ \t]+", " ")
                .replaceAll("\n{3,}", "\n\n");
    }

    private List<String> preprocessLines(String text) {
        String[] rawLines = text.split("\\R");
        List<String> lines = new ArrayList<>();
        for (String raw : rawLines) {
            String line = (raw == null) ? "" : raw.trim();
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

    // 💡 ファイル公開日を基準に、対象の日付が何年何月何日になるのかをスコア計算で安全に特定するロジック
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
            best.set(Calendar.DAY_OF_MONTH, Math.min(dayOfMonth, best.getActualMaximum(Calendar.DAY_OF_MONTH)));
        }
        return best;
    }

    private String weekdayJp(Calendar cal) {
        String[] weeks = {"", "日", "月", "火", "水", "木", "金", "土"};
        return weeks[cal.get(Calendar.DAY_OF_WEEK)];
    }

    // 💡 内部用データ保持クラス
    static class PlanRow {
        String sourceUrl;
        java.util.Date targetDate;
        int dayOfMonth;
        String weekdayJp;
        String area;
        String routeName;
        String enforcementType;
        String rawLine;
        PlanRow(String sourceUrl, java.util.Date targetDate, int dayOfMonth, String weekdayJp, String area, String routeName, String enforcementType, String rawLine) {
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
