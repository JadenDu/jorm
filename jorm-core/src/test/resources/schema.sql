CREATE TABLE IF NOT EXISTS `users` (
      id BIGINT PRIMARY KEY AUTO_INCREMENT,
      user_name VARCHAR(255),
      age INT,
      status VARCHAR(255),
      department VARCHAR(255)
);