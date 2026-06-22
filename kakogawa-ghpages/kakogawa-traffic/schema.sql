-- 1. ETL担当者様が使用するソースファイル追跡用管理テーブル（変更なし）
CREATE TABLE IF NOT EXISTS source_files (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  pdf_url VARCHAR(500) NOT NULL UNIQUE,
  file_name VARCHAR(100) NOT NULL,
  file_date DATE NOT NULL,
  content_hash CHAR(64) NOT NULL,
  last_checked_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. 🔄【Webアプリ＋ETL完全統合】自動更新と画面手動投稿を一緒に蓄積するメインテーブル
CREATE TABLE IF NOT EXISTS violation_plans (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  source_file_id BIGINT NULL, -- 💡手動投稿の場合はNULLになるためNULLを許可に変更
  target_date DATETIME NOT NULL, -- 💡カレンダー閲覧の仕様に合わせDATE型からDATETIME型へ拡張
  day_of_month INT NOT NULL,
  weekday_jp VARCHAR(1) NOT NULL,
  area VARCHAR(20) NOT NULL,
  route_name VARCHAR(255) NOT NULL,
  enforcement_type VARCHAR(50),
  raw_line VARCHAR(500),
  
  -- 💡画面からの手動投稿に必須なユーザー提供項目をMySQL仕様で安全に合流追記
  poster_name VARCHAR(100) DEFAULT '名無し',
  media_path VARCHAR(255) DEFAULT NULL,
  
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  
  -- 担当者様から預かった外部キー制約（ON DELETE CASCADEでPDF削除時に自動データも連動パージされます）
  CONSTRAINT fk_source_file FOREIGN KEY (source_file_id) REFERENCES source_files(id) ON DELETE CASCADE,
  -- 担当者様から預かった二重取り込み防止の一意制約キー
  UNIQUE KEY uq_plan (source_file_id, target_date, area, route_name, enforcement_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
