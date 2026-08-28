package ru.practicum.moviehub.http;

import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MoviesHandler extends BaseHttpHandler {
    private static final String MOVIES_COLLECTION_PATH = "/movies";
    private static final Pattern MOVIE_BY_ID_PATH_PATTERN = Pattern.compile("^/movies/([^/]+)$");
    private static final String QUERY_YEAR_PARAM_PREFIX = "year=";

    private static final String METHOD_GET = "GET";
    private static final String METHOD_POST = "POST";
    private static final String METHOD_DELETE = "DELETE";

    private static final int MAX_TITLE_LENGTH = 100;
    private static final int EARLIEST_CINEMA_YEAR = 1888;
    private static final int MAX_FUTURE_YEAR_OFFSET = 1;

    private final MoviesStore store;

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();

            if (MOVIES_COLLECTION_PATH.equals(path)) {
                handleCollectionEndpoint(ex, method);
            } else {
                Matcher matcher = MOVIE_BY_ID_PATH_PATTERN.matcher(path);
                if (matcher.matches()) {
                    String idParam = matcher.group(1);
                    handleItemEndpoint(ex, method, idParam);
                } else {
                    sendError(ex, HTTP_NOT_FOUND, "Ресурс не найден");
                }
            }
        } catch (Exception e) {
            sendError(ex, HTTP_INTERNAL_SERVER_ERROR, "Внутренняя ошибка сервера");
        }
    }

    private void handleCollectionEndpoint(HttpExchange ex, String method) throws IOException {
        switch (method) {
            case METHOD_GET:
                handleGetCollection(ex);
                break;
            case METHOD_POST:
                handlePostMovie(ex);
                break;
            default:
                sendMethodNotAllowed(ex);
        }
    }

    private void handleGetCollection(HttpExchange ex) throws IOException {
        String query = ex.getRequestURI().getQuery();
        if (query != null && !query.isBlank()) {
            if (query.startsWith(QUERY_YEAR_PARAM_PREFIX)) {
                String yearStr = query.substring(QUERY_YEAR_PARAM_PREFIX.length());
                try {
                    int year = Integer.parseInt(yearStr);
                    sendJson(ex, HTTP_OK, gson.toJson(store.findByYear(year)));
                } catch (NumberFormatException e) {
                    sendError(ex, HTTP_BAD_REQUEST, "Некорректный параметр запроса — 'year'");
                }
            } else {
                sendError(ex, HTTP_BAD_REQUEST, "Некорректный параметр запроса");
            }
        } else {
            sendJson(ex, HTTP_OK, gson.toJson(store.findAll()));
        }
    }

    private void handlePostMovie(HttpExchange ex) throws IOException {
        String contentType = ex.getRequestHeaders().getFirst(HEADER_CONTENT_TYPE);
        if (contentType == null || !contentType.contains("application/json")) {
            sendError(ex, HTTP_UNSUPPORTED_MEDIA_TYPE, "Неподдерживаемый тип данных (Content-Type)");
            return;
        }

        String body = readText(ex);
        Movie movie;
        try {
            movie = gson.fromJson(body, Movie.class);
            if (movie == null) {
                sendError(ex, HTTP_BAD_REQUEST, "Некорректный JSON");
                return;
            }
        } catch (JsonSyntaxException e) {
            sendError(ex, HTTP_BAD_REQUEST, "Некорректный JSON");
            return;
        }

        List<String> validationErrors = validateMovie(movie);
        if (!validationErrors.isEmpty()) {
            sendValidationError(ex, validationErrors);
            return;
        }

        Movie createdMovie = store.add(movie);
        sendJson(ex, HTTP_CREATED, gson.toJson(createdMovie));
    }

    private void handleItemEndpoint(HttpExchange ex, String method, String idParam) throws IOException {
        int id;
        try {
            id = Integer.parseInt(idParam);
        } catch (NumberFormatException e) {
            sendError(ex, HTTP_BAD_REQUEST, "Некорректный ID");
            return;
        }

        switch (method) {
            case METHOD_GET:
                Optional<Movie> movie = store.findById(id);
                if (movie.isPresent()) {
                    sendJson(ex, HTTP_OK, gson.toJson(movie.get()));
                } else {
                    sendError(ex, HTTP_NOT_FOUND, "Фильм не найден");
                }
                break;
            case METHOD_DELETE:
                if (store.deleteById(id)) {
                    sendNoContent(ex);
                } else {
                    sendError(ex, HTTP_NOT_FOUND, "Фильм не найден");
                }
                break;
            default:
                sendMethodNotAllowed(ex);
        }
    }

    private List<String> validateMovie(Movie movie) {
        List<String> validationErrors = new ArrayList<>();
        if (movie.getTitle() == null || movie.getTitle().trim().isEmpty()) {
            validationErrors.add("Название не должно быть пустым");
        } else if (movie.getTitle().length() > MAX_TITLE_LENGTH) {
            validationErrors.add("Длина названия не должна превышать " + MAX_TITLE_LENGTH + " символов");
        }

        int maxAllowedYear = Year.now().getValue() + MAX_FUTURE_YEAR_OFFSET;
        if (movie.getYear() < EARLIEST_CINEMA_YEAR || movie.getYear() > maxAllowedYear) {
            validationErrors.add("Год должен быть между " + EARLIEST_CINEMA_YEAR + " и " + maxAllowedYear);
        }
        return validationErrors;
    }
}