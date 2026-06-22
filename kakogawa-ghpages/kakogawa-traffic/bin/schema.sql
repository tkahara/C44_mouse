-- 取締り情報テーブルの作成
CREATE TABLE IF NOT EXISTS enforcement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,          -- 主キー（自動連番ID）
    enforcement_date TIMESTAMP NOT NULL,          -- 取締り日時
    location VARCHAR(255) NOT NULL,               -- 取締り場所
    description TEXT,                             -- 文字情報（詳細・内容）
    media_path VARCHAR(255),                      -- 画像・動画のファイル保存パス
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 投稿受付日時
);
