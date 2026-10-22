# Attendance Management System

## Setup

1. Create the database and original `employees` and `attendance` tables from your SQL schema.
2. Run `database/attendance_checkout_migration.sql` once to add nullable checkout time and the checkout photo BLOB.
3. Edit the root `.env` with your MySQL URL, database username/password, and a strong `ADMIN_PASSWORD`. The local `.env` is ignored by Git; `.env.example` is the template.
4. Build the WAR with `mvn clean package` and deploy it to a Jakarta EE 8 / Servlet 4 container such as Tomcat 9. Start the container with the project root as its working directory so the app can load `.env`, or provide the four values as process environment variables.
5. Open the deployed app's root URL for employee attendance, or `/admin.html` for the admin login.

Camera access requires HTTPS, except on `localhost`. Check-in stores a JPEG in `attendance.photo`; check-out sets `check_out_at` and stores its JPEG in `attendance.checkout_photo`. Photos are limited to 2 MB each. Employee codes are generated from the employee ID (`EMP001`, `EMP002`, and so on).

The migration is intended for a database that still has the original schema and has not had these columns added already.