package fermiumbooter.rebooter.discovery;

import fermiumbooter.rebooter.Reference;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

final class FingerprintCollector {
    private static final int WORKER_COUNT = 4;
    private static final AtomicInteger THREAD_NUMBER = new AtomicInteger();

    private FingerprintCollector() {
    }

    static Batch collect(
            Collection<File> candidates,
            JarDiscoveryCache cache,
            JarDiscoveryCache.ContentFingerprinter firstFingerprinter) {
        Map<File, Result> results = new HashMap<>();
        List<Request> requests = new ArrayList<>();
        for (File candidate : candidates) {
            if (!cache.hasLoadedEntry(candidate)) continue;
            try {
                requests.add(new Request(candidate, JarDiscoveryCache.stamp(candidate)));
            } catch (IOException e) {
                results.put(candidate, Result.failure(e));
            }
        }
        if (requests.isEmpty()) {
            return new Batch(results, 0, 0L, 0L);
        }

        long started = System.nanoTime();
        if (requests.size() == 1) {
            Request request = requests.get(0);
            results.put(request.file, hash(request, firstFingerprinter));
        } else {
            results.putAll(hashParallel(requests, firstFingerprinter));
        }
        return new Batch(
                results,
                requests.size(),
                fingerprintedBytes(requests, results),
                System.nanoTime() - started);
    }

    private static Map<File, Result> hashParallel(
            List<Request> requests,
            JarDiscoveryCache.ContentFingerprinter firstFingerprinter) {
        AtomicInteger nextRequest = new AtomicInteger();
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(
                WORKER_COUNT,
                FingerprintCollector::newWorkerThread);
        try {
            if (executor.prestartAllCoreThreads() != WORKER_COUNT) {
                throw new IllegalStateException("Unable to start all fingerprint workers");
            }
        } catch (RuntimeException e) {
            executor.shutdownNow();
            Reference.LOGGER.warn("Unable to start parallel fingerprint workers; using one worker", e);
            return hashSequential(requests, firstFingerprinter);
        }
        try {
            List<Future<List<CompletedRequest>>> workers = new ArrayList<>(WORKER_COUNT);
            for (int workerIndex = 0; workerIndex < WORKER_COUNT; workerIndex++) {
                JarDiscoveryCache.ContentFingerprinter fingerprinter = workerIndex == 0
                        ? firstFingerprinter
                        : new JarDiscoveryCache.ContentFingerprinter();
                workers.add(executor.submit(() -> hashRequests(requests, nextRequest, fingerprinter)));
            }

            Map<File, Result> results = new HashMap<>();
            for (Future<List<CompletedRequest>> worker : workers) {
                for (CompletedRequest completed : worker.get()) {
                    results.put(completed.file, completed.result);
                }
            }
            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while fingerprinting discovery candidates", e);
        } catch (ExecutionException e) {
            throw propagate(e.getCause());
        } finally {
            executor.shutdownNow();
        }
    }

    private static Map<File, Result> hashSequential(
            List<Request> requests,
            JarDiscoveryCache.ContentFingerprinter fingerprinter) {
        Map<File, Result> results = new HashMap<>();
        for (Request request : requests) results.put(request.file, hash(request, fingerprinter));
        return results;
    }

    private static List<CompletedRequest> hashRequests(
            List<Request> requests,
            AtomicInteger nextRequest,
            JarDiscoveryCache.ContentFingerprinter fingerprinter) {
        List<CompletedRequest> completed = new ArrayList<>();
        int requestIndex;
        while ((requestIndex = nextRequest.getAndIncrement()) < requests.size()) {
            Request request = requests.get(requestIndex);
            completed.add(new CompletedRequest(request.file, hash(request, fingerprinter)));
        }
        return completed;
    }

    private static Result hash(
            Request request,
            JarDiscoveryCache.ContentFingerprinter fingerprinter) {
        try {
            byte[] fingerprint = fingerprinter.fingerprint(request.file);
            return Result.success(request.stamp, fingerprint, fingerprinter.bytesRead());
        } catch (IOException e) {
            return Result.failure(e, fingerprinter.bytesRead());
        }
    }

    private static long fingerprintedBytes(List<Request> requests, Map<File, Result> results) {
        long bytes = 0L;
        for (Request request : requests) bytes += results.get(request.file).bytesRead();
        return bytes;
    }

    private static Thread newWorkerThread(Runnable task) {
        Thread thread = new Thread(
                task,
                "Rebooter Fingerprint " + THREAD_NUMBER.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    }

    private static RuntimeException propagate(Throwable cause) {
        if (cause instanceof RuntimeException) return (RuntimeException) cause;
        if (cause instanceof Error) throw (Error) cause;
        return new IllegalStateException("Unexpected fingerprint worker failure", cause);
    }

    static final class Batch {
        private final Map<File, Result> results;
        private final int fingerprintCount;
        private final long fingerprintBytes;
        private final long elapsedNanos;

        private Batch(
                Map<File, Result> results,
                int fingerprintCount,
                long fingerprintBytes,
                long elapsedNanos) {
            this.results = Collections.unmodifiableMap(new HashMap<>(results));
            this.fingerprintCount = fingerprintCount;
            this.fingerprintBytes = fingerprintBytes;
            this.elapsedNanos = elapsedNanos;
        }

        Result result(File file) {
            return this.results.get(file);
        }

        int fingerprintCount() {
            return this.fingerprintCount;
        }

        long fingerprintBytes() {
            return this.fingerprintBytes;
        }

        long elapsedNanos() {
            return this.elapsedNanos;
        }
    }

    static final class Result {
        private final JarDiscoveryCache.FileStamp stamp;
        private final byte[] fingerprint;
        private final IOException failure;
        private final long bytesRead;

        private Result(
                JarDiscoveryCache.FileStamp stamp,
                byte[] fingerprint,
                IOException failure,
                long bytesRead) {
            this.stamp = stamp;
            this.fingerprint = fingerprint;
            this.failure = failure;
            this.bytesRead = bytesRead;
        }

        private static Result success(
                JarDiscoveryCache.FileStamp stamp,
                byte[] fingerprint,
                long bytesRead) {
            return new Result(stamp, fingerprint, null, bytesRead);
        }

        private static Result failure(IOException failure) {
            return failure(failure, 0L);
        }

        private static Result failure(IOException failure, long bytesRead) {
            return new Result(null, null, failure, bytesRead);
        }

        JarDiscoveryCache.FileStamp stamp() {
            return this.stamp;
        }

        byte[] fingerprint() {
            return this.fingerprint;
        }

        IOException failure() {
            return this.failure;
        }

        long bytesRead() {
            return this.bytesRead;
        }
    }

    private static final class Request {
        private final File file;
        private final JarDiscoveryCache.FileStamp stamp;

        private Request(File file, JarDiscoveryCache.FileStamp stamp) {
            this.file = file;
            this.stamp = stamp;
        }
    }

    private static final class CompletedRequest {
        private final File file;
        private final Result result;

        private CompletedRequest(File file, Result result) {
            this.file = file;
            this.result = result;
        }
    }
}
