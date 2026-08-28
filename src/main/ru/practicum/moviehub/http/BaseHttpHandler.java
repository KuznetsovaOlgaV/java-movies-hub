package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import ru.practicum.moviehub.api.ErrorResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

public abstract class BaseHttpHandler implements HttpHandler {
    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String APPLICATION_JSON_UTF8 = "application/json; charset=UTF-8";
    public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    public static final int HTTP_OK = 200;
    public static final int HTTP_CREATED = 201;
    public static final int HTTP_NO_CONTENT = 204;
    public static final int HTTP_BAD_REQUEST = 400;
    public static final int HTTP_NOT_FOUND = 404;
    public static final int HTTP_METHOD_NOT_ALLOWED = 405;
    public static final int HTTP_UNSUPPORTED_MEDIA_TYPE = 415;
    public static final int HTTP_UNPROCESSABLE_ENTITY = 422;
    public static final int HTTP_INTERNAL_SERVER_ERROR = 500;

    protected final Gson gson = new Gson();

    protected void sendJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] responseBytes = json.getBytes(DEFAULT_CHARSET);
        ex.getResponseHeaders().set(HEADER_CONTENT_TYPE, APPLICATION_JSON_UTF8);
        ex.sendResponseHeaders(status, responseBytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    protected void sendNoContent(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set(HEADER_CONTENT_TYPE, APPLICATION_JSON_UTF8);
        ex.sendResponseHeaders(HTTP_NO_CONTENT, -1);
    }

    protected void sendError(HttpExchange ex, int status, String message) throws IOException {
        sendJson(ex, status, gson.toJson(new ErrorResponse(message)));
    }

    protected void sendValidationError(HttpExchange ex, List<String> details) throws IOException {
        sendJson(ex, HTTP_UNPROCESSABLE_ENTITY, gson.toJson(new ErrorResponse("Ошибка валидации", details)));
    }

    protected void sendMethodNotAllowed(HttpExchange ex) throws IOException {
        sendError(ex, HTTP_METHOD_NOT_ALLOWED, "Метод не поддерживается");
    }

    protected String readText(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), DEFAULT_CHARSET);
    }
}