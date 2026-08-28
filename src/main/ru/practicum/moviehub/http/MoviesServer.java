package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpServer;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.net.InetSocketAddress;

public class MoviesServer {
    public static final int DEFAULT_PORT = 8080;
    private static final String MOVIES_CONTEXT_PATH = "/movies";

    private final HttpServer server;
    private final MoviesStore store;
    private final int port;

    public MoviesServer() {
        this(new MoviesStore(), DEFAULT_PORT);
    }

    public MoviesServer(MoviesStore store) {
        this(store, DEFAULT_PORT);
    }

    public MoviesServer(MoviesStore store, int port) {
        this.store = store;
        this.port = port;
        try {
            this.server = HttpServer.create(new InetSocketAddress(this.port), 0);
            this.server.createContext(MOVIES_CONTEXT_PATH, new MoviesHandler(this.store));
        } catch (IOException e) {
            throw new RuntimeException("Не удалось создать HTTP-сервер", e);
        }
    }

    public void start() {
        server.start();
        System.out.println("Сервер запущен на порту " + port);
    }

    public void stop() {
        server.stop(0);
        System.out.println("Сервер остановлен");
    }

    public MoviesStore getStore() {
        return store;
    }
}