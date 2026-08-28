package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class MoviesStore {
    private final Map<Integer, Movie> movies = new ConcurrentHashMap<>();
    private int sequenceGenerator = 0;

    public synchronized Movie save(Movie movie) {
        if (movie.getId() == null) {
            movie.setId(++sequenceGenerator);
        }
        movies.put(movie.getId(), movie);
        return movie;
    }

    public synchronized Movie add(Movie movie) {
        return save(movie);
    }

    public Optional<Movie> findById(int id) {
        return Optional.ofNullable(movies.get(id));
    }

    public List<Movie> findAll() {
        return new ArrayList<>(movies.values());
    }

    public List<Movie> findByYear(int year) {
        return movies.values().stream()
                .filter(movie -> movie.getYear() == year)
                .collect(Collectors.toList());
    }

    public boolean deleteById(int id) {
        return movies.remove(id) != null;
    }

    public synchronized void clear() {
        movies.clear();
        sequenceGenerator = 0;
    }
}