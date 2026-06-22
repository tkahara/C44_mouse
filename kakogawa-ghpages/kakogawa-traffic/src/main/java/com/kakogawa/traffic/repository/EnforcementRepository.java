package com.kakogawa.traffic.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.kakogawa.traffic.model.Enforcement;

@Repository
public interface EnforcementRepository extends JpaRepository<Enforcement, Long> {

    // 1. 全データを日付降順（最新順）で取得するメソッド
    List<Enforcement> findAllByOrderByEnforcementDateDesc();

    // 2. カレンダー機能で使用する特定範囲の日付抽出メソッド
    List<Enforcement> findByEnforcementDateBetweenOrderByEnforcementDateAsc(LocalDateTime start, LocalDateTime end);

    // 🔄【エラー解決の修正】MySQL上の変数名（target_date, route_name, enforcement_type）と100%一致させた重複判定メソッド
    // ※JavaのEntityクラス(Enforcement.java)のプロパティ名（enforcementDate, location, enforcementType）に対応しています
    boolean existsByEnforcementDateAndLocationAndEnforcementType(LocalDateTime date, String location, String type);
}
