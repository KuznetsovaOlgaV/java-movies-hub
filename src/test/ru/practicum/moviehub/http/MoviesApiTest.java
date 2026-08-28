package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.model.Movie;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Year;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MoviesApiTest {

    private static final String BASE = "http://localhost:8080";
    private static final String EXPECTED_CONTENT_TYPE = "application/json; charset=UTF-8";
    private static final Type MOVIE_LIST_TYPE = new ListOfMoviesTypeToken().getType();

    private static MoviesServer server;
    private static HttpClient client;
    private static Gson gson;

    @BeforeAll
    static void beforeAll() throws IOException {
        server = new MoviesServer();
        server.start();

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        gson = new Gson();
    }

    @AfterAll
    static void afterAll() {
        if (server != null) {
            server.stop();
        }
    }

    @BeforeEach
    void setUp() {
        server.getStore().clear();
    }

    // GET возвращает статус 200 и пустой массив, если хранилище пустое
    @Test
    void getMoviesWhenEmptyReturnsEmptyList() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());
        assertEquals(EXPECTED_CONTENT_TYPE, resp.headers().firstValue("Content-Type").orElse(""));
        assertEquals("[]", resp.body().trim());
    }

    // GET возвращает статус 200 и список всех добавленных фильмов
    @Test
    void getMoviesWhenHasMoviesReturnsListWithMovies() throws Exception {
        server.getStore().save(new Movie("Inception", 2010));
        server.getStore().save(new Movie("Interstellar", 2014));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());
        assertEquals(EXPECTED_CONTENT_TYPE, resp.headers().firstValue("Content-Type").orElse(""));
        List<Movie> movies = gson.fromJson(resp.body(), MOVIE_LIST_TYPE);
        assertEquals(2, movies.size());
    }

    // GET возвращает фильмы только за указанный год со статусом 200
    @Test
    void getMoviesByYearReturnsMoviesOfSpecifiedYear() throws Exception {
        server.getStore().save(new Movie("Inception", 2010));
        server.getStore().save(new Movie("Shutter Island", 2010));
        server.getStore().save(new Movie("Interstellar", 2014));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=2010"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());
        List<Movie> movies = gson.fromJson(resp.body(), MOVIE_LIST_TYPE);
        assertEquals(2, movies.size());
        assertTrue(movies.stream().allMatch(m -> m.getYear() == 2010));
    }

    // GET возвращает 200 и пустой массив, если фильмов за этот год нет
    @Test
    void getMoviesByYearWhenNoMoviesMatchReturnsEmptyList() throws Exception {
        server.getStore().save(new Movie("Inception", 2010));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=1999"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());
        assertEquals("[]", resp.body().trim());
    }

    // GET возвращает 400 Bad Request при нечисловом параметре года
    @Test
    void getMoviesByYearWhenYearIsNotNumberReturns400WithError() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=not_a_year"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode());
        assertErrorBodyHasErrorField(resp.body());
    }

    // POST успешно создает фильм с валидными данными, возвращает 201 Created и присвоенный ID
    @Test
    void postMovieValidDataCreatesMovieAndReturns201() throws Exception {
        Movie movie = new Movie("Dune", 2021);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(movie)))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(201, resp.statusCode());
        assertEquals(EXPECTED_CONTENT_TYPE, resp.headers().firstValue("Content-Type").orElse(""));

        Movie created = gson.fromJson(resp.body(), Movie.class);
        assertNotNull(created.getId());
        assertEquals("Dune", created.getTitle());
        assertEquals(2021, created.getYear());
    }

    // POST возвращает 422 при пустом названии или строке из пробелов
    @Test
    void postMovieEmptyOrBlankTitleReturns422WithError() throws Exception {
        String body = "{\"title\": \" \", \"year\": 2020}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, resp.statusCode());
        assertErrorBodyHasErrorField(resp.body());
    }

    // POST возвращает 422, если длина названия превышает 100 символов
    @Test
    void postMovieTitleExceeds100CharsReturns422WithError() throws Exception {
        String longTitle = "A".repeat(101);
        String body = gson.toJson(new Movie(longTitle, 2020));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, resp.statusCode());
        assertErrorBodyHasErrorField(resp.body());
    }

    // POST возвращает 422, если год выпуска раньше 1888
    @Test
    void postMovieYearLessThan1888Returns422WithError() throws Exception {
        String body = gson.toJson(new Movie("Old Movie", 1887));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, resp.statusCode());
        assertErrorBodyHasErrorField(resp.body());
    }

    // POST возвращает 422, если год выпуска больше чем текущий год + 1
    @Test
    void postMovieYearGreaterThanCurrentPlusOneReturns422WithError() throws Exception {
        int invalidFutureYear = Year.now().getValue() + 2;
        String body = gson.toJson(new Movie("Too Future Movie", invalidFutureYear));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, resp.statusCode());
        assertErrorBodyHasErrorField(resp.body());
    }

    // POST возвращает 415 Unsupported Media Type при Content-Type, отличном от application/json
    @Test
    void postMovieInvalidContentTypeReturns415WithError() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("title=Avatar&year=2009"))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(415, resp.statusCode());
        assertErrorBodyHasErrorField(resp.body());
    }

    // POST возвращает 400 Bad Request при синтаксически невалидном JSON
    @Test
    void postMovieInvalidJsonReturns400WithError() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{not_valid_json}"))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode());
        assertErrorBodyHasErrorField(resp.body());
    }

    // GET возвращает 200 OK и фильм по существующему ID
    @Test
    void getMovieByIdExistingIdReturns200AndMovie() throws Exception {
        Movie saved = server.getStore().save(new Movie("The Matrix", 1999));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + saved.getId()))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());
        assertEquals(EXPECTED_CONTENT_TYPE, resp.headers().firstValue("Content-Type").orElse(""));

        Movie movie = gson.fromJson(resp.body(), Movie.class);
        assertEquals(saved.getId(), movie.getId());
        assertEquals("The Matrix", movie.getTitle());
    }

    // GET возвращает 404 Not Found, если фильм с таким ID не существует
    @Test
    void getMovieByIdNonExistingIdReturns404WithError() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/999"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, resp.statusCode());
        assertErrorBodyHasErrorField(resp.body());
    }

    // GET возвращает 400 Bad Request, если передан нечисловой ID
    @Test
    void getMovieByIdNonNumericIdReturns400WithError() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/abc"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode());
        assertErrorBodyHasErrorField(resp.body());
    }

    // DELETE удаляет существующий фильм и возвращает статус 204 No Content
    @Test
    void deleteMovieExistingIdDeletesMovieAndReturns204() throws Exception {
        Movie saved = server.getStore().save(new Movie("Forrest Gump", 1994));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + saved.getId()))
                .DELETE()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(204, resp.statusCode());
        assertTrue(server.getStore().findById(saved.getId()).isEmpty());
    }

    // DELETE возвращает 404 Not Found при попытке удалить несуществующий фильм
    @Test
    void deleteMovieNonExistingIdReturns404WithError() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/999"))
                .DELETE()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, resp.statusCode());
        assertErrorBodyHasErrorField(resp.body());
    }

    // DELETE возвращает 400 Bad Request при нечисловом значении ID
    @Test
    void deleteMovieNonNumericIdReturns400WithError() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/invalid"))
                .DELETE()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode());
        assertErrorBodyHasErrorField(resp.body());
    }

    // возвращает 405 Method Not Allowed при неподдерживаемом HTTP-методе
    @Test
    void unsupportedMethodOnCollectionReturns405WithError() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(405, resp.statusCode());
        assertErrorBodyHasErrorField(resp.body());
    }

    // возвращает 405 Method Not Allowed при неподдерживаемом HTTP-методе
    @Test
    void unsupportedMethodOnItemReturns405WithError() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/1"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(405, resp.statusCode());
        assertErrorBodyHasErrorField(resp.body());
    }

    private void assertErrorBodyHasErrorField(String responseBody) {
        assertNotNull(responseBody);
        assertFalse(responseBody.isBlank(), "Тело ответа с ошибкой не должно быть пустым");
        JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
        assertTrue(json.has("error"), "Тело ошибки должно содержать поле 'error'");
        assertFalse(json.get("error").getAsString().isBlank(), "Поле 'error' не должно быть пустым");
    }
}