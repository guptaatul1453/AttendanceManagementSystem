package com.mycompany.attendancemanagementsystem;

import java.io.IOException;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public final class HttpJson {
    private HttpJson() {
    }

    public static JsonObject read(HttpServletRequest request) throws IOException {
        return Json.createReader(request.getInputStream()).readObject();
    }

    public static void write(HttpServletResponse response, int status, JsonObject body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        Json.createWriter(response.getOutputStream()).writeObject(body);
    }

    public static void error(HttpServletResponse response, int status, String message) throws IOException {
        JsonObjectBuilder body = Json.createObjectBuilder().add("error", message);
        write(response, status, body.build());
    }
}