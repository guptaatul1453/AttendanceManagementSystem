package com.mycompany.attendancemanagementsystem;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;

import javax.json.Json;
import javax.json.JsonObject;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/api/attendance")
public class AttendanceServlet extends HttpServlet {
    private static final int MAX_PHOTO_BYTES = 2 * 1024 * 1024;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            JsonObject body = HttpJson.read(request);
            String code = body.getString("employeeCode", "").trim().toUpperCase();
            String action = body.getString("action", "");
            byte[] photo = decodePhoto(body.getString("photo", ""));

            if (code.isEmpty() || code.length() > 24) {
                HttpJson.error(response, HttpServletResponse.SC_BAD_REQUEST, "Enter a valid employee code.");
                return;
            }
            if (!"checkin".equals(action) && !"checkout".equals(action)) {
                HttpJson.error(response, HttpServletResponse.SC_BAD_REQUEST, "Choose check-in or check-out.");
                return;
            }

            try (Connection connection = Database.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    long employeeId;
                    String employeeName;
                    try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT id, full_name FROM employees WHERE employee_code = ? FOR UPDATE")) {
                        statement.setString(1, code);
                        try (ResultSet result = statement.executeQuery()) {
                            if (!result.next()) {
                                connection.rollback();
                                HttpJson.error(response, HttpServletResponse.SC_NOT_FOUND,
                                        "No employee was found for that code.");
                                return;
                            }
                            employeeId = result.getLong("id");
                            employeeName = result.getString("full_name");
                        }
                    }

                    if ("checkin".equals(action)) {
                        try (PreparedStatement statement = connection.prepareStatement(
                                "SELECT id FROM attendance WHERE employee_id = ? AND check_out_at IS NULL "
                                + "ORDER BY id DESC LIMIT 1 FOR UPDATE")) {
                            statement.setLong(1, employeeId);
                            try (ResultSet result = statement.executeQuery()) {
                                if (result.next()) {
                                    connection.rollback();
                                    HttpJson.error(response, HttpServletResponse.SC_CONFLICT,
                                            "This employee is already checked in.");
                                    return;
                                }
                            }
                        }
                        try (PreparedStatement statement = connection.prepareStatement(
                                "INSERT INTO attendance (employee_id, event_type, recorded_at, photo) "
                                + "VALUES (?, 'IN', CURRENT_TIMESTAMP, ?)")) {
                            statement.setLong(1, employeeId);
                            statement.setBytes(2, photo);
                            statement.executeUpdate();
                        }
                    } else {
                        long attendanceId;
                        try (PreparedStatement statement = connection.prepareStatement(
                                "SELECT id FROM attendance WHERE employee_id = ? AND check_out_at IS NULL "
                                + "ORDER BY id DESC LIMIT 1 FOR UPDATE")) {
                            statement.setLong(1, employeeId);
                            try (ResultSet result = statement.executeQuery()) {
                                if (!result.next()) {
                                    connection.rollback();
                                    HttpJson.error(response, HttpServletResponse.SC_CONFLICT,
                                            "There is no open check-in to check out.");
                                    return;
                                }
                                attendanceId = result.getLong("id");
                            }
                        }
                        try (PreparedStatement statement = connection.prepareStatement(
                                "UPDATE attendance SET check_out_at = CURRENT_TIMESTAMP, checkout_photo = ? "
                                + "WHERE id = ?")) {
                            statement.setBytes(1, photo);
                            statement.setLong(2, attendanceId);
                            statement.executeUpdate();
                        }
                    }
                    connection.commit();
                    HttpJson.write(response, HttpServletResponse.SC_OK, Json.createObjectBuilder()
                            .add("message", "checkin".equals(action)
                                    ? "Check-in recorded. Have a good day, " + employeeName + "."
                                    : "Check-out recorded. Thank you, " + employeeName + ".")
                            .add("employeeName", employeeName)
                            .build());
                } catch (SQLException | RuntimeException exception) {
                    connection.rollback();
                    throw exception;
                }
            }
        } catch (IllegalArgumentException exception) {
            HttpJson.error(response, HttpServletResponse.SC_BAD_REQUEST,
                    "The photo must be a JPEG image under 2 MB.");
        } catch (SQLException exception) {
            getServletContext().log("Attendance database operation failed", exception);
            HttpJson.error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Attendance could not be saved. Check the database configuration.");
        } catch (RuntimeException exception) {
            HttpJson.error(response, HttpServletResponse.SC_BAD_REQUEST, "The request could not be read.");
        }
    }

    private byte[] decodePhoto(String dataUrl) {
        String prefix = "data:image/jpeg;base64,";
        if (!dataUrl.startsWith(prefix) || dataUrl.length() > (MAX_PHOTO_BYTES * 4 / 3) + 128) {
            throw new IllegalArgumentException("Unsupported photo data");
        }
        byte[] photo = Base64.getDecoder().decode(dataUrl.substring(prefix.length()));
        if (photo.length < 4 || photo.length > MAX_PHOTO_BYTES
                || (photo[0] & 0xff) != 0xff || (photo[1] & 0xff) != 0xd8) {
            throw new IllegalArgumentException("Invalid JPEG data");
        }
        return photo;
    }
}