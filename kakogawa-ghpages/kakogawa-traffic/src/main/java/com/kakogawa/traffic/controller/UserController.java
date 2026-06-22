package com.kakogawa.traffic.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.kakogawa.traffic.model.Enforcement;
import com.kakogawa.traffic.repository.EnforcementRepository;

@Controller
@RequestMapping("/user")
public class UserController {

    private final EnforcementRepository repository;

    // 🛠️ WebMvcConfigの「./upload」と完全に一致させるようにデフォルト値を修正
    @Value("${app.upload.dir:./upload}")
    private String uploadDir;

    public UserController(EnforcementRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/post")
    public String postForm(Model model) {
        model.addAttribute("enforcement", new Enforcement());
        return "user/post";
    }

    @GetMapping("/terms")
    public String showTerms() {
        return "user/terms";
    }

    @PostMapping("/post/submit")
    public String submitPost(@ModelAttribute Enforcement enforcement, 
                             @RequestParam("file") MultipartFile file) throws IOException {
        
        if (enforcement.getPosterName() == null || enforcement.getPosterName().trim().isEmpty()) {
            enforcement.setPosterName("名無し");
        }

        if (enforcement.getEnforcementDate() != null) {
            LocalDateTime dt = enforcement.getEnforcementDate();
            enforcement.setDayOfMonth(dt.getDayOfMonth());
            
            String[] weeks = {"", "日", "月", "火", "水", "木", "金", "土"};
            Calendar cal = Calendar.getInstance();
            cal.set(dt.getYear(), dt.getMonthValue() - 1, dt.getDayOfMonth());
            enforcement.setWeekdayJp(weeks[cal.get(Calendar.DAY_OF_WEEK)]);
        }
        
        enforcement.setArea("東播"); 
        enforcement.setEnforcementType("ユーザー目撃");
        enforcement.setSourceFileId(null); 

        // 🛠️ ファイル保存処理の不具合修正
        if (!file.isEmpty()) {
            // セキュリティと重複防止のため、UUIDを付与したユニークなファイル名を生成
            String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
            
            // Pathsを使ってプロジェクトルートからの相対パス「./upload」を絶対パスに安全変換
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            
            // uploadフォルダが存在しない場合は自動で物理作成
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            
            // ファイルのフルパスを結合して物理保存を実行
            Path targetPath = uploadPath.resolve(fileName);
            file.transferTo(targetPath.toFile());
            
            // データベースのmediaPathカラムには「生成されたファイル名のみ」を保存
            enforcement.setMediaPath(fileName);
        }

        repository.save(enforcement);
        return "redirect:/user/list";
    }

    // 🔄【500エラー解決】全件一覧画面の表示処理
    @GetMapping("/list")
    public String list(Model model) {
        model.addAttribute("enforcements", repository.findAllByOrderByEnforcementDateDesc());
        model.addAttribute("selectedDate", "すべての期間");
        model.addAttribute("base64Util", Base64.getEncoder());
        return "user/list";
    }

    @GetMapping("/date/{dateStr}")
    public String showInfoByDate(@PathVariable("dateStr") String dateStr, Model model) {
        LocalDate date = LocalDate.parse(dateStr);
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        List<Enforcement> enforcements = repository.findByEnforcementDateBetweenOrderByEnforcementDateAsc(startOfDay, endOfDay);
        
        model.addAttribute("enforcements", enforcements);
        model.addAttribute("selectedDate", date.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")));
        model.addAttribute("base64Util", Base64.getEncoder()); 
        return "user/list";
    }

    @GetMapping("/calendar")
    public String showCalendar(@RequestParam(value = "year", required = false) Integer year,
                               @RequestParam(value = "month", required = false) Integer month,
                               Model model) {
        LocalDate today = LocalDate.now();
        int currentYear = (year != null) ? year : today.getYear();
        int currentMonth = (month != null) ? month : today.getMonthValue();

        YearMonth yearMonth = YearMonth.of(currentYear, currentMonth);
        int dayOfWeekValue = yearMonth.atDay(1).getDayOfWeek().getValue();

        List<String> calendarDays = new ArrayList<>();
        int leadingEmptyDays = (dayOfWeekValue == 7) ? 0 : dayOfWeekValue;
        for (int i = 0; i < leadingEmptyDays; i++) calendarDays.add("");
        for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) calendarDays.add(String.valueOf(day));

        List<Enforcement> allData = repository.findAll();
        Set<String> activeDateSet = new HashSet<>();
        if (allData != null) {
            for (Enforcement info : allData) {
                if (info.getEnforcementDate() != null) {
                    activeDateSet.add(info.getEnforcementDate().toLocalDate().toString());
                }
            }
        }

        model.addAttribute("calendarDays", calendarDays);
        model.addAttribute("currentYear", currentYear);
        model.addAttribute("currentMonth", currentMonth);
        model.addAttribute("activeDates", new ArrayList<>(activeDateSet));
        model.addAttribute("prevMonth", yearMonth.minusMonths(1));
        model.addAttribute("nextMonth", yearMonth.plusMonths(1));

        return "user/calendar";
    }

    @GetMapping("/detail/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Enforcement enforcement = repository.findById(id).orElseThrow();
        model.addAttribute("info", enforcement); 
        model.addAttribute("base64Util", Base64.getEncoder());
        return "user/detail";
    }
}
