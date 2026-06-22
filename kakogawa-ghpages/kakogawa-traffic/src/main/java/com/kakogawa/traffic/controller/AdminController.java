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
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.kakogawa.traffic.model.Enforcement;
import com.kakogawa.traffic.repository.EnforcementRepository;

@Controller
public class AdminController {

    @Autowired
    private EnforcementRepository repository;

    // 🛠️ WebMvcConfigの「./upload」と完全に一致させるようにデフォルト値を安全に指定
    @Value("${app.upload.dir:./upload}")
    private String uploadDir;

    @GetMapping("/login")
    public String login() {
        return "admin/login";
    }

    @GetMapping("/admin")
    public String index() {
        return "admin/index";
    }

    @GetMapping("/admin/calendar")
    public String showAdminCalendar(@RequestParam(value = "year", required = false) Integer year,
                                     @RequestParam(value = "month", required = false) Integer month,
                                     Model model) {
        LocalDate today = LocalDate.now();
        int currentYear = (year != null) ? year : today.getYear();
        int currentMonth = (month != null) ? month : today.getMonthValue();
        
        YearMonth yearMonth = YearMonth.of(currentYear, currentMonth);
        int dayOfWeekValue = yearMonth.atDay(1).getDayOfWeek().getValue();
        
        List<Map<String, Object>> calendarDays = new ArrayList<>();
        int leadSpaces = (dayOfWeekValue == 7) ? 0 : dayOfWeekValue;
        for (int i = 0; i < leadSpaces; i++) calendarDays.add(null);
        
        List<Enforcement> allData = repository.findAll();
        List<String> activeDates = new ArrayList<>();
        if (allData != null) {
            for (Enforcement info : allData) {
                if (info.getEnforcementDate() != null) {
                    activeDates.add(info.getEnforcementDate().toLocalDate().toString());
                }
            }
        }
        
        for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
            LocalDate date = LocalDate.of(currentYear, currentMonth, day);
            String dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            Map<String, Object> dayMap = new HashMap<>();
            dayMap.put("dayNum", day);
            dayMap.put("dateStr", dateStr);
            dayMap.put("hasData", activeDates.contains(dateStr));
            dayMap.put("isToday", date.equals(today));
            calendarDays.add(dayMap);
        }
        
        model.addAttribute("calendarDays", calendarDays);
        model.addAttribute("currentYear", currentYear);
        model.addAttribute("currentMonth", currentMonth);
        model.addAttribute("prevMonthYear", yearMonth.minusMonths(1).getYear());
        model.addAttribute("prevMonth", yearMonth.minusMonths(1).getMonthValue());
        model.addAttribute("nextMonthYear", yearMonth.plusMonths(1).getYear());
        model.addAttribute("nextMonth", yearMonth.plusMonths(1).getMonthValue());
        
        return "admin/calendar";
    }

    // 🛠️【文字化け修復】全件一覧（日付指定なし）
    @GetMapping("/admin/list")
    public String showAdminListAll(Model model) {
        List<Enforcement> list = repository.findAllByOrderByEnforcementDateDesc();
        model.addAttribute("enforcements", list);
        model.addAttribute("selectedDate", "すべての期間");
        return "admin/list";
    }

    // 日付指定の一覧
    @GetMapping("/admin/list/{date}")
    public String showAdminList(@PathVariable("date") String date, Model model) {
        LocalDate localDate = LocalDate.parse(date);
        List<Enforcement> list = repository.findByEnforcementDateBetweenOrderByEnforcementDateAsc(
                localDate.atStartOfDay(), 
                localDate.atTime(LocalTime.MAX));
        model.addAttribute("enforcements", list);
        model.addAttribute("selectedDate", date);
        return "admin/list";
    }

    @GetMapping("/admin/input")
    public String showInputForm(Model model) {
        model.addAttribute("enforcement", new Enforcement());
        return "admin/input";
    }

    // 🛠️【文字化け修復＆アップロード正常化】公式新規登録
    @PostMapping("/admin/input/submit")
    public String processAdminInput(@ModelAttribute Enforcement enforcement,
                                    @RequestParam(value = "mediaFile", required = false) MultipartFile mediaFile,
                                    @RequestParam("dateInput") String dateInput,
                                    @RequestParam("timeInput") String timeInput) throws IOException {
        
        LocalDateTime parsedDateTime = LocalDateTime.parse(dateInput + "T" + timeInput);
        enforcement.setEnforcementDate(parsedDateTime);
        enforcement.setPosterName("管理者");
        enforcement.setDayOfMonth(parsedDateTime.getDayOfMonth());
        
        String[] weeks = {"", "日", "月", "火", "水", "木", "金", "土"};
        Calendar cal = Calendar.getInstance();
        cal.set(parsedDateTime.getYear(), parsedDateTime.getMonthValue() - 1, parsedDateTime.getDayOfMonth());
        enforcement.setWeekdayJp(weeks[cal.get(Calendar.DAY_OF_WEEK)]);
        
        enforcement.setArea("東播");
        enforcement.setEnforcementType("公式発表");
        enforcement.setSourceFileId(null);
        
        // 🛠️ ファイル保存物理パスの修正
        if (mediaFile != null && !mediaFile.isEmpty()) {
            String savedFileName = UUID.randomUUID().toString() + "_" + mediaFile.getOriginalFilename();
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            
            Path targetPath = uploadPath.resolve(savedFileName);
            mediaFile.transferTo(targetPath.toFile());
            enforcement.setMediaPath(savedFileName);
        }
        
        repository.save(enforcement);
        return "redirect:/admin/calendar";
    }

    @GetMapping("/admin/edit/{id}")
    public String showEditForm(@PathVariable("id") Long id, Model model) {
        repository.findById(id).ifPresent(info -> {
            model.addAttribute("enforcement", info);
            model.addAttribute("dateInput", info.getEnforcementDate().toLocalDate());
            model.addAttribute("timeInput", info.getEnforcementDate().toLocalTime());
        });
        return "admin/edit";
    }

    // 🛠️【文字化け修復＆アップロード正常化】クレンジング編集送信
    @PostMapping("/admin/edit/submit")
    public String processEdit(@ModelAttribute Enforcement enforcement,
                              @RequestParam(value = "mediaFile", required = false) MultipartFile mediaFile,
                              @RequestParam("dateInput") String dateInput,
                              @RequestParam("timeInput") String timeInput) throws IOException {
        
        Enforcement existing = repository.findById(enforcement.getId()).orElseThrow();
        LocalDateTime parsedDateTime = LocalDateTime.parse(dateInput + "T" + timeInput);
        
        existing.setEnforcementDate(parsedDateTime);
        existing.setLocation(enforcement.getLocation());
        existing.setDescription(enforcement.getDescription());
        existing.setPosterName(enforcement.getPosterName());
        existing.setDayOfMonth(parsedDateTime.getDayOfMonth());
        
        String[] weeks = {"", "日", "月", "火", "水", "木", "金", "土"};
        Calendar cal = Calendar.getInstance();
        cal.set(parsedDateTime.getYear(), parsedDateTime.getMonthValue() - 1, parsedDateTime.getDayOfMonth());
        existing.setWeekdayJp(weeks[cal.get(Calendar.DAY_OF_WEEK)]);
        
        // 🛠️ 編集時のファイル保存物理パスの修正
        if (mediaFile != null && !mediaFile.isEmpty()) {
            String savedFileName = UUID.randomUUID().toString() + "_" + mediaFile.getOriginalFilename();
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            
            Path targetPath = uploadPath.resolve(savedFileName);
            mediaFile.transferTo(targetPath.toFile());
            existing.setMediaPath(savedFileName);
        }
        
        repository.save(existing);
        return "redirect:/admin/calendar";
    }

    @GetMapping("/admin/delete/{id}")
    public String processDelete(@PathVariable("id") Long id) {
        repository.deleteById(id);
        return "redirect:/admin/calendar";
    }
}
