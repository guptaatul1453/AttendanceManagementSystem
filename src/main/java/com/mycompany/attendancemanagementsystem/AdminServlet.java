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
            writeDatabaseError(response, exception);
        } catch (RuntimeException exception) {
            HttpJson.error(response, HttpServletResponse.SC_BAD_REQUEST, "The request could not be read.");
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!"/api/employees".equals(request.getPathInfo())) {
            HttpJson.error(response, HttpServletResponse.SC_NOT_FOUND, "Not found.");
            return;
        }
        if (!isAuthenticated(request)) {
            HttpJson.error(response, HttpServletResponse.SC_UNAUTHORIZED, "Admin login required.");
            return;
        }
        try {
            updateEmployee(request, response);
        } catch (SQLException exception) {
            writeDatabaseError(response, exception);
        } catch (RuntimeException exception) {
            HttpJson.error(response, HttpServletResponse.SC_BAD_REQUEST, "The request could not be read.");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!"/api/employees".equals(request.getPathInfo())) {
            HttpJson.error(response, HttpServletResponse.SC_NOT_FOUND, "Not found.");
            return;
        }
        if (!isAuthenticated(request)) {
            HttpJson.error(response, HttpServletResponse.SC_UNAUTHORIZED, "Admin login required.");
            return;
        }
        try {
            deleteEmployee(request, response);
        } catch (SQLException exception) {
            writeDatabaseError(response, exception);
        } catch (RuntimeException exception) {
            HttpJson.error(response, HttpServletResponse.SC_BAD_REQUEST, "The request could not be read.");
        }
    }

    private void writeDatabaseError(HttpServletResponse response, SQLException exception) throws IOException {
        if (exception.getSQLState() != null && exception.getSQLState().startsWith("23")) {
            HttpJson.error(response, HttpServletResponse.SC_CONFLICT, "Employee code already exists.");
            return;
        }
        getServletContext().log("Admin database operation failed", exception);
        HttpJson.error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "The request could not be completed. Check the database configuration.");
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
            "SELECT a.id, e.employee_code, e.full_name, e.department, e.position_title, a.recorded_at, a.check_out_at "
                + "FROM attendance a JOIN employees e ON e.id = a.employee_id WHERE 1 = 1");
        if (query != null && !query.trim().isEmpty()) {
            sql.append(" AND (e.employee_code LIKE ? OR e.full_name LIKE ? OR e.department LIKE ? OR e.position_title LIKE ?)");
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
                            .add("positionTitle", result.getString("position_title"))
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
                        "SELECT id, employee_code, full_name, department, position_title, email "
                        + "FROM employees ORDER BY full_name")) {
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    JsonObjectBuilder employee = Json.createObjectBuilder()
                            .add("id", result.getLong("id"))
                            .add("employeeCode", result.getString("employee_code"))
                            .add("fullName", result.getString("full_name"))
                            .add("department", result.getString("department"))
                            .add("positionTitle", result.getString("position_title"));
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
        String position = body.getString("positionTitle", "").trim();
        String email = body.getString("email", "").trim();
        String code = body.getString("employeeCode", "").trim().toUpperCase(Locale.ROOT);
        if (!validEmployeeFields(name, department, position, email, response)) {
            return;
        }
        if (!code.isEmpty() && !validEmployeeCode(code)) {
            HttpJson.error(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Employee code must be 1–24 letters, numbers, hyphens, or underscores.");
            return;
        }
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (!code.isEmpty() && employeeCodeExists(connection, code, 0)) {
                    connection.rollback();
                    HttpJson.error(response, HttpServletResponse.SC_CONFLICT, "That employee code is already in use.");
                    return;
                }
                long id;
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO employees (employee_code, full_name, department, position_title, email, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)", PreparedStatement.RETURN_GENERATED_KEYS)) {
                    if (code.isEmpty()) {
                        statement.setNull(1, java.sql.Types.VARCHAR);
                    } else {
                        statement.setString(1, code);
                    }
                    statement.setString(2, name);
                    statement.setString(3, department);
                    statement.setString(4, position);
                    if (email.isEmpty()) {
                        statement.setNull(5, java.sql.Types.VARCHAR);
                    } else {
                        statement.setString(5, email);
                    }
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("Employee id was not generated");
                        }
                        id = keys.getLong(1);
                    }
                }
                if (code.isEmpty()) {
                    code = String.format(Locale.ROOT, "EMP%03d", id);
                    if (employeeCodeExists(connection, code, id)) {
                        connection.rollback();
                        HttpJson.error(response, HttpServletResponse.SC_CONFLICT,
                                "Generated employee code is already in use; enter a custom code instead.");
                        return;
                    }
                    try (PreparedStatement statement = connection.prepareStatement(
                            "UPDATE employees SET employee_code = ? WHERE id = ?")) {
                        statement.setString(1, code);
                        statement.setLong(2, id);
                        statement.executeUpdate();
                    }
                }
                connection.commit();
                HttpJson.write(response, HttpServletResponse.SC_CREATED, Json.createObjectBuilder()
                        .add("employeeCode", code)
                        .add("fullName", name)
                        .add("positionTitle", position)
                        .build());
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private void updateEmployee(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, IOException {
        JsonObject body = HttpJson.read(request);
        long id = body.getJsonNumber("id").longValue();
        String code = body.getString("employeeCode", "").trim().toUpperCase(Locale.ROOT);
        String name = body.getString("fullName", "").trim();
        String department = body.getString("department", "").trim();
        String position = body.getString("positionTitle", "").trim();
        String email = body.getString("email", "").trim();
        if (id < 1 || !validEmployeeCode(code)) {
            HttpJson.error(response, HttpServletResponse.SC_BAD_REQUEST, "Enter a valid employee ID and code.");
            return;
        }
        if (!validEmployeeFields(name, department, position, email, response)) {
            return;
        }
        try (Connection connection = Database.getConnection()) {
            if (employeeCodeExists(connection, code, id)) {
                HttpJson.error(response, HttpServletResponse.SC_CONFLICT, "That employee code is already in use.");
                return;
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE employees SET employee_code = ?, full_name = ?, department = ?, "
                    + "position_title = ?, email = ? WHERE id = ?")) {
                statement.setString(1, code);
                statement.setString(2, name);
                statement.setString(3, department);
                statement.setString(4, position);
                if (email.isEmpty()) {
                    statement.setNull(5, java.sql.Types.VARCHAR);
                } else {
                    statement.setString(5, email);
                }
                statement.setLong(6, id);
                if (statement.executeUpdate() == 0) {
                    HttpJson.error(response, HttpServletResponse.SC_NOT_FOUND, "Employee not found.");
                    return;
                }
            }
        }
        HttpJson.write(response, HttpServletResponse.SC_OK, Json.createObjectBuilder()
                .add("employeeCode", code)
                .add("fullName", name)
                .build());
    }

    private void deleteEmployee(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, IOException {
        String idValue = request.getParameter("id");
        if (idValue == null || !idValue.matches("\\d+")) {
            HttpJson.error(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid employee ID.");
            return;
        }
        long id = Long.parseLong(idValue);
        try (Connection connection = Database.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT 1 FROM attendance WHERE employee_id = ? LIMIT 1")) {
                statement.setLong(1, id);
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        HttpJson.error(response, HttpServletResponse.SC_CONFLICT,
                                "This employee has attendance history and cannot be deleted.");
                        return;
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM employees WHERE id = ?")) {
                statement.setLong(1, id);
                if (statement.executeUpdate() == 0) {
                    HttpJson.error(response, HttpServletResponse.SC_NOT_FOUND, "Employee not found.");
                    return;
                }
            }
        }
        HttpJson.write(response, HttpServletResponse.SC_OK, Json.createObjectBuilder().add("ok", true).build());
    }

    private boolean employeeCodeExists(Connection connection, String code, long exceptId) throws SQLException {
        String sql = "SELECT 1 FROM employees WHERE employee_code = ? AND id <> ? LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            statement.setLong(2, exceptId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private boolean validEmployeeCode(String code) {
        return code.matches("[A-Z0-9_-]{1,24}");
    }

    private boolean validEmployeeFields(String name, String department, String position, String email,
            HttpServletResponse response) throws IOException {
        if (name.isEmpty() || name.length() > 120 || department.isEmpty() || department.length() > 120
                || position.isEmpty() || position.length() > 120 || email.length() > 180) {
            HttpJson.error(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Name, department, and position are required. Check field lengths.");
            return false;
        }
        return true;
    }
}