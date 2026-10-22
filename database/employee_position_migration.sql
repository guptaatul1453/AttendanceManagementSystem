USE attendance_management;

ALTER TABLE employees
    ADD COLUMN position_title VARCHAR(120) NOT NULL DEFAULT '' AFTER department;