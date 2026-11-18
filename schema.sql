CREATE DATABASE IF NOT EXISTS nourishnet;
USE nourishnet;

CREATE TABLE Users (
  user_id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  email VARCHAR(100) UNIQUE NOT NULL,
  phone VARCHAR(15),
  role ENUM('DONOR','VOLUNTEER','NGO') NOT NULL,
  password_hash VARCHAR(255) NULL
);

CREATE TABLE Organizations (
  org_id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  org_name VARCHAR(100) NOT NULL,
  address VARCHAR(200),
  FOREIGN KEY (user_id) REFERENCES Users(user_id)
);

CREATE TABLE Volunteers (
  volunteer_id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  org_id INT,
  vehicle VARCHAR(30),
  FOREIGN KEY (user_id) REFERENCES Users(user_id),
  FOREIGN KEY (org_id) REFERENCES Organizations(org_id)
);

CREATE TABLE Donations (
  donation_id INT AUTO_INCREMENT PRIMARY KEY,
  donor_id INT NOT NULL,
  food_type VARCHAR(100) NOT NULL,
  quantity INT NOT NULL,
  location VARCHAR(200) NOT NULL,
  pickup_time VARCHAR(30) NOT NULL,
  best_by VARCHAR(30),
  pickup_at DATETIME NULL,
  best_by_at DATETIME NULL,
  status ENUM('AVAILABLE','ACCEPTED','COLLECTED','DELIVERED') DEFAULT 'AVAILABLE',
  FOREIGN KEY (donor_id) REFERENCES Users(user_id)
);

CREATE TABLE PickupRequests (
  request_id INT AUTO_INCREMENT PRIMARY KEY,
  donation_id INT NOT NULL,
  volunteer_id INT NOT NULL,
  requested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (donation_id) REFERENCES Donations(donation_id),
  FOREIGN KEY (volunteer_id) REFERENCES Volunteers(volunteer_id)
);

CREATE TABLE Deliveries (
  delivery_id INT AUTO_INCREMENT PRIMARY KEY,
  request_id INT NOT NULL,
  org_id INT NOT NULL,
  delivered_at TIMESTAMP NULL,
  FOREIGN KEY (request_id) REFERENCES PickupRequests(request_id),
  FOREIGN KEY (org_id) REFERENCES Organizations(org_id)
);

CREATE TABLE Notifications (
  notification_id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  message VARCHAR(250) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  is_read BOOLEAN NOT NULL DEFAULT FALSE,
  FOREIGN KEY (user_id) REFERENCES Users(user_id)
);

-- sample data for the demo
INSERT INTO Users(name,email,phone,role) VALUES
 ('Hostel A Mess','hostela@mail.com','9000000001','DONOR'),
 ('Hope NGO','hope@mail.com','9000000002','NGO'),
 ('Ravi','ravi@mail.com','9000000003','VOLUNTEER');
INSERT INTO Organizations(user_id,org_name,address) VALUES (2,'Hope NGO','Chengalpattu');
INSERT INTO Volunteers(user_id,org_id,vehicle) VALUES (3,1,'Bike');
INSERT INTO Donations(donor_id,food_type,quantity,location,pickup_time,best_by)
 VALUES (1,'Cooked Rice',30,'Hostel A','8:00 PM','10:00 PM');
