package com.mycompany.attendancemanagementsystem;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Locale;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@WebServlet("/admin/*")
public class AdminServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getPathInfo();
        if (path == null || "/".equals(path)) {
            response.sendRedirect(request.getContextPath() + "/admin.html");
            return;
        }
        if (!isAuthenticated(request)) {
            HttpJson.error(response, HttpServletResponse.SC_UNAUTHORIZED, "Admin login required.");
            return;
        }
        try {
            if ("/api/attendance".equals(path)) {
                attendance(request, response);
            } else if ("/api/employees".equals(path)) {
                employees(response);
            } else if ("/api/photo".equals(path)) {
                photo(request, response);
            } else {
                HttpJson.error(response, HttpServletResponse.SC_NOT_FOUND, "Not found.");
            }
        } catch (SQLException exception) {
            getServletContext().log("Admin database operation failed", exception);
            HttpJson.error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "The request could not be completed. Check the database configuration.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getPathInfo();
        try {
            if ("/login".equals(path)) {
                login(request, response);
                return;
            }
            if ("/logout".equals(path)) {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                HttpJson.write(response, HttpServletResponse.SC_OK,
                        Json.createObjectBuilder().add("ok", true).build());
                return;
            }
            if (!isAuthenticated(request)) {
                HttpJson.error(response, HttpServletResponse.SC_UNAUTHORIZED, "Admin login required.");
                return;
            }
            if ("/api/employees".equals(path)) {
                createEmployee(request, response);
            } else {
                HttpJson.error(response, HttpServletResponse.SC_NOT_FOUND, "Not found.");
            }
        } catch (SQLException exception) {
            getServletContext().log("Admin database operation failed", exception);
            HttpJson.error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "The request could not be completed. Check the database configuration.");
        } catch (RuntimeException exception) {
            HttpJson.error(response, HttpServletResponse.SC_BAD_REQUEST, "The request could not be read.");
        }
    }

    private void login(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String expected = Database.adminPassword();
        String supplied = HttpJson.read(request).getString("password", "");
        if (expected.isEmpty() || !constantTimeEquals(expected, supplied)) {
            HttpJson.error(response, HttpServletResponse.SC_UNAUTHORIZED,
                    expected.isEmpty() ? "Set ADMIN_PASSWORD in .env before logging in." : "Incorrect password.");
            return;
        }
        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        request.getSession(true).setAttribute("attendanceAdmin", Boolean.TRUE);
        HttpJson.write(response, HttpServletResponse.SC_OK, Json.createObjectBuilder().add("ok", true).build());
    }

    private boolean constantTimeEquals(String expected, String supplied) {
        try {
            byte[] expectedHash = MessageDigest.getInstance("SHA-256").digest(expected.getBytes("UTF-8"));
            byte[] suppliedHash = MessageDigest.getInstance("SHA-256").digest(supplied.getBytes("UTF-8"));
            return MessageDigest.isEqual(expectedHash, suppliedHash);
        } catch (NoSuchAlgorithmException | java.io.UnsupportedEncodingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private boolean isAuthenticated(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && Boolean.TRUE.equals(session.getAttribute("attendanceAdmin"));
    }

    private void attendance(HttpServletRequest request, HttpServletResponse response) throws SQLException, IOException {
        String query = request.getParameter("q");
        String date = request.getParameter("date");
        String status = request.getParameter("status");
        StringBuilder sql = new StringBuilder(
                "SELECT a.id, e.employee_code, e.full_name, e.department, a.recorded_at, a.check_out_at "
                + "FROM attendance a JOIN employees e ON e.id = a.employee_id WHERE 1 = 1");
        if (query != null && !query.trim().isEmpty()) {
            sql.append(" AND (e.employee_code LIKE ? OR e.full_name LIKE ? OR e.department LIKE ?)");
        }
        if (date != null && date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            sql.append(" AND DATE(a.recorded_at) = ?");
        }
        if ("open".equals(status)) {
            sql.append(" AND a.check_out_at IS NULL");
        } else if ("complete".equals(status)) {
            sql.append(" AND a.check_out_at IS NOT NULL");
        }
        sql.append(" ORDER BY a.recorded_at DESC LIMIT 250");

        JsonArrayBuilder rows = Json.createArrayBuilder();
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int parameter = 1;
            if (query != null && !query.trim().isEmpty()) {
                String pattern = "%" + query.trim() + "%";
                statement.setString(parameter++, pattern);
                statement.setString(parameter++, pattern);
                statement.setString(parameter++, pattern);
            }
            if (date != null && date.matches("\\d{4}-\\d{2}-\\d{2}")) {
                statement.setString(parameter, date);
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    JsonObjectBuilder row = Json.createObjectBuilder()
                            .add("id", result.getLong("id"))
                            .add("employeeCode", result.getString("employee_code"))
                            .add("fullName", result.getString("full_name"))
                            .add("department", result.getString("department"))
                            .add("checkin", result.getTimestamp("recorded_at").toLocalDateTime().toString());
                    Timestamp checkout = result.getTimestamp("check_out_at");
                    if (checkout == null) {
                        row.addNull("checkout");
                    } else {
                        row.add("checkout", checkout.toLocalDateTime().toString());
                    }
                    rows.add(row.build());
                }
            }
        }
        HttpJson.write(response, HttpServletResponse.SC_OK, Json.createObjectBuilder().add("rows", rows).build());
    }

    private void employees(HttpServletResponse response) throws SQLException, IOException {
        JsonArrayBuilder rows = Json.createArrayBuilder();
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT id, employee_code, full_name, department, email FROM employees ORDER BY full_name")) {
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    JsonObjectBuilder employee = Json.createObjectBuilder()
                            .add("id", result.getLong("id"))
                            .add("employeeCode", result.getString("employee_code"))
                            .add("fullName", result.getString("full_name"))
                            .add("department", result.getString("department"));
                    String email = result.getString("email");
                    if (email == null) {
                        employee.addNull("email");
                    } else {
                        employee.add("email", email);
                    }
                    rows.add(employee.build());
                }
            }
        }
        HttpJson.write(response, HttpServletResponse.SC_OK, Json.createObjectBuilder().add("employees", rows).build());
    }

    private void photo(HttpServletRequest request, HttpServletResponse response) throws SQLException, IOException {
        String id = request.getParameter("id");
        String type = request.getParameter("type");
        if (id == null || !id.matches("\\d+")
                || (!"checkin".equals(type) && !"checkout".equals(type))) {
            HttpJson.error(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid photo request.");
            return;
        }
        String column = "checkin".equals(type) ? "photo" : "checkout_photo";
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT " + column + " FROM attendance WHERE id = ?")) {
            statement.setLong(1, Long.parseLong(id));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || result.getBytes(1) == null) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                byte[] image = result.getBytes(1);
                response.setContentType("image/jpeg");
                response.setHeader("Cache-Control", "private, no-store");
                response.setContentLength(image.length);
                response.getOutputStream().write(image);
            }
        }
    }

    private void createEmployee(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, IOException {
        JsonObject body = HttpJson.read(request);
        String name = body.getString("fullName", "").trim();
        String department = body.getString("department", "").trim();
        String email = body.getString("email", "").trim();
        if (name.isEmpty() || name.length() > 120 || department.isEmpty() || department.length() > 120
                || email.length() > 180) {
            HttpJson.error(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Name and department are required. Check field lengths.");
            return;
        }
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long id;
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO employees (employee_code, full_name, department, email, created_at) "
                        + "VALUES (NULL, ?, ?, ?, CURRENT_TIMESTAMP)", PreparedStatement.RETURN_GENERATED_KEYS)) {
                    statement.setString(1, name);
                    statement.setString(2, department);
                    if (email.isEmpty()) {
                        statement.setNull(3, java.sql.Types.VARCHAR);
                    } else {
                        statement.setString(3, email);
                    }
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("Employee id was not generated");
                        }
                        id = keys.getLong(1);
                    }
                }
                String code = String.format(Locale.ROOT, "EMP%03d", id);
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE employees SET employee_code = ? WHERE id = ?")) {
                    statement.setString(1, code);
                    statement.setLong(2, id);
                    statement.executeUpdate();
                }
                connection.commit();
                HttpJson.write(response, HttpServletResponse.SC_CREATED, Json.createObjectBuilder()
                        .add("employeeCode", code)
                        .add("fullName", name)
                        .build());
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }
}