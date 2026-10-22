package com.mycompany.attendancemanagementsystem;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import javax.servlet.ServletContext;

import io.github.cdimascio.dotenv.Dotenv;

public final class Database {
    private static volatile Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

    private Database() {
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
                value("DB_URL", "jdbc:mysql://localhost:3306/attendance_management?serverTimezone=UTC"),
                value("DB_USER", "root"),
                value("DB_PASSWORD", ""));
    }

    public static String adminPassword() {
        return value("ADMIN_PASSWORD", "");
    }

    public static void configure(ServletContext context) {
        Path envFile = findEnvFile(context);
        if (envFile != null) {
            dotenv = Dotenv.configure().directory(envFile.getParent().toString()).ignoreIfMissing().load();
            context.log("Loaded attendance configuration from " + envFile);
        }
    }

    private static Path findEnvFile(ServletContext context) {
        String configuredPath = System.getProperty("attendance.env.file");
        if (configuredPath == null || configuredPath.trim().isEmpty()) {
            configuredPath = System.getenv("ATTENDANCE_ENV_FILE");
        }
        if (configuredPath != null && !configuredPath.trim().isEmpty()) {
            Path configuredFile = Paths.get(configuredPath.trim()).toAbsolutePath().normalize();
            if (Files.isRegularFile(configuredFile)) {
                return configuredFile;
            }
        }

        Path[] roots = new Path[] {
            Paths.get(System.getProperty("user.dir", ".")),
            Paths.get(System.getProperty("catalina.base", ".")),
            realPath(context)
        };
        for (Path root : roots) {
            Path current = root == null ? null : root.toAbsolutePath().normalize();
            for (int depth = 0; current != null && depth < 8; depth++, current = current.getParent()) {
                Path candidate = current.resolve(".env");
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static Path realPath(ServletContext context) {
        String path = context.getRealPath("/");
        return path == null ? null : Paths.get(path);
    }

    private static String value(String key, String fallback) {
        String environmentValue = System.getenv(key);
        if (environmentValue != null) {
            return environmentValue;
        }
        String fileValue = dotenv.get(key);
        return fileValue == null ? fallback : fileValue;
    }
}