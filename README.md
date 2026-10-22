# Daywork Attendance

## Setup

1. Create the database and original `employees` and `attendance` tables from your SQL schema.
2. Run `database/attendance_checkout_migration.sql` once for checkout time/photo and `database/employee_position_migration.sql` once for employee position tags.
3. Edit the root `.env` with your MySQL URL, database username/password, and a strong `ADMIN_PASSWORD`. The local `.env` is ignored by Git; `.env.example` is the template.
4. Build the WAR with `mvn clean package` and deploy it to a Jakarta EE 8 / Servlet 4 container such as Tomcat 9. The app searches the deployed application and parent directories for `.env`; set `ATTENDANCE_ENV_FILE` or `-Dattendance.env.file` to provide an explicit path, or use process environment variables.
5. Open the deployed app's root URL for employee attendance, or `/admin.html` for the admin login.

Camera access requires HTTPS, except on `localhost`. Check-in stores a JPEG in `attendance.photo`; check-out sets `check_out_at` and stores its JPEG in `attendance.checkout_photo`. Photos are limited to 2 MB each. Admins may supply a unique employee code or leave it blank to generate `EMP001`, `EMP002`, and so on; position is required. Employees with attendance history cannot be deleted.

Run each migration once against the original schema before deploying the updated app.