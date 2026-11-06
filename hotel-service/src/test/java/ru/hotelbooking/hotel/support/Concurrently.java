package ru.hotelbooking.hotel.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Starts all tasks at the same moment from separate threads and collects their outcomes. */
public final class Concurrently {
    private Concurrently() {}

    public static List<Outcome> run(List<Callable<String>> tasks) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (Callable<String> task : tasks) {
                futures.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    return task.call();
                                }));
            }
            start.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<String> future : futures) {
                try {
                    outcomes.add(new Outcome(future.get(10, TimeUnit.SECONDS), null));
                } catch (java.util.concurrent.ExecutionException failure) {
                    outcomes.add(new Outcome(null, failure.getCause()));
                }
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    public record Outcome(String value, Throwable failure) {}
}
