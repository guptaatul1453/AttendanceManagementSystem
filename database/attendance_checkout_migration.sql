USE attendance_management;

ALTER TABLE attendance
    ADD COLUMN check_out_at DATETIME NULL AFTER recorded_at,
    ADD COLUMN checkout_photo MEDIUMBLOB NULL AFTER photo;