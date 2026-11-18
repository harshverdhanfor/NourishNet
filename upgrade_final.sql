USE nourishnet;

-- Safe to run on an existing NourishNet database, including if a column was added earlier.
SET @add_password_hash = IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Users' AND COLUMN_NAME = 'password_hash'),
  'SELECT 1',
  'ALTER TABLE Users ADD COLUMN password_hash VARCHAR(255) NULL'
);
PREPARE nourishnet_stmt FROM @add_password_hash;
EXECUTE nourishnet_stmt;
DEALLOCATE PREPARE nourishnet_stmt;

SET @add_pickup_at = IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Donations' AND COLUMN_NAME = 'pickup_at'),
  'SELECT 1',
  'ALTER TABLE Donations ADD COLUMN pickup_at DATETIME NULL'
);
PREPARE nourishnet_stmt FROM @add_pickup_at;
EXECUTE nourishnet_stmt;
DEALLOCATE PREPARE nourishnet_stmt;

SET @add_best_by_at = IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Donations' AND COLUMN_NAME = 'best_by_at'),
  'SELECT 1',
  'ALTER TABLE Donations ADD COLUMN best_by_at DATETIME NULL'
);
PREPARE nourishnet_stmt FROM @add_best_by_at;
EXECUTE nourishnet_stmt;
DEALLOCATE PREPARE nourishnet_stmt;

CREATE TABLE IF NOT EXISTS Notifications (
  notification_id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  message VARCHAR(250) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  is_read BOOLEAN NOT NULL DEFAULT FALSE,
  FOREIGN KEY (user_id) REFERENCES Users(user_id)
);
