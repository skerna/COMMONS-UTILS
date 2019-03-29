package io.skerna.commons.utils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

/**
 * A collection of static utility methods that extend the
 * {@link CompletableFuture Java completable future} API.
 *
 */
public final class CompletableFutures {

  private CompletableFutures() {
    throw new IllegalAccessError("This class must not be instantiated.");
  }

  /**
   * Returns a new {@link CompletableFuture} which completes to a list of all values of its input
   * stages, if all succeed.  The list of results is in the same order as the input stages.
   *
   * <p> If any of the given stages complete exceptionally, then the returned future also does so,
   * with a {@link CompletionException} holding this exception as its cause.
   *
   * <p> If no stages are provided, returns a future holding an empty list.
   *
   * @param stages the stages to combine
   * @param <T>    the common super-type of all of the input stages, that determines the monomorphic
   *               type of the output future
   * @return a future that completes to a list of the results of the supplied stages
   * @throws NullPointerException if the stages list or any of its elements are {@code null}
   * @since 0.1.0
   */
  public static <T> CompletableFuture<List<T>> allAsList(
      List<? extends CompletionStage<? extends T>> stages) {
    // We use traditional for-loops instead of streams here for performance reasons,
    // see AllAsListBenchmark

    @SuppressWarnings("unchecked") // generic array creation
    final CompletableFuture<? extends T>[] all = new CompletableFuture[stages.size()];
    for (int i = 0; i < stages.size(); i++) {
      all[i] = stages.get(i).toCompletableFuture();
    }
    return CompletableFuture.allOf(all)
        .thenApply(ignored -> {
          final List<T> result = new ArrayList<>(all.length);
          for (int i = 0; i < all.length; i++) {
            T value =  all[i].join();
            result.add(value);
          }
          return result;
        });
  }

  /**
   * Returns a new {@link CompletableFuture} which completes to a list of values of those input
   * stages that succeeded. The list of results is in the same order as the input stages. For failed
   * stages, the defaultValueMapper will be called, and the value returned from that function will
   * be put in the resulting list.
   *
   * <p>If no stages are provided, returns a future holding an empty list.
   *
   * @param stages the stages to combine.
   * @param defaultValueMapper a function that will be called when a future completes exceptionally
   * to provide a default value to place in the resulting list
   * @param <T>    the common type of all of the input stages, that determines the type of the
   *               output future
   * @return a future that completes to a list of the results of the supplied stages
   * @throws NullPointerException if the stages list or any of its elements are {@code null}
   */
  public static <T> CompletableFuture<List<T>> successfulAsList(
      List<? extends CompletionStage<T>> stages,
      Function<Throwable, ? extends T> defaultValueMapper) {
    return stages.stream()
        .map(f -> f.exceptionally(defaultValueMapper))
        .collect(joinList());
  }

  /**
   * Returns a new {@code CompletableFuture} that is already exceptionally completed with
   * the given exception.
   *
   * @param throwable the exception
   * @param <T>       an arbitrary type for the returned future; can be anything since the future
   *                  will be exceptionally completed and thus there will never be a value of type
   *                  {@code T}
   * @return a future that exceptionally completed with the supplied exception
   * @throws NullPointerException if the supplied throwable is {@code null}
   * @since 0.1.0
   */
  public static <T> CompletableFuture<T> exceptionallyCompletedFuture(Throwable throwable) {
    final CompletableFuture<T> future = new CompletableFuture<>();
    future.completeExceptionally(throwable);
    return future;
  }

  /**
   * Collect a stream of {@link CompletionStage}s into a single future holding a list of the
   * joined entities.
   *
   * <p> Usage:
   *
   * <pre>{@code
   * collection.stream()
   *     .map(this::someAsyncFunc)
   *     .collect(joinList())
   *     .thenApply(this::consumeList)
   * }</pre>
   *
   * <p> The generated {@link CompletableFuture} will complete to a list of all entities, in the
   * order they were encountered in the original stream.  Similar to
   * {@link CompletableFuture#allOf(CompletableFuture[])}, if any of the input futures complete
   * exceptionally, then the returned CompletableFuture also does so, with a
   * {@link CompletionException} holding this exception as its cause.
   *
   * @param <T> the common super-type of all of the input stages, that determines the monomorphic
   *            type of the output future
   * @param <S> the implementation of {@link CompletionStage} that the stream contains
   * @return a new {@link CompletableFuture} according to the rules outlined in the method
   * description
   * @throws NullPointerException if any future in the stream is {@code null}
   * @since 0.1.0
   */
  public static <T, S extends CompletionStage<? extends T>>
  Collector<S, ?, CompletableFuture<List<T>>> joinList() {
    return collectingAndThen(toList(), CompletableFutures::allAsList);
  }

  /**
   * Checks that a stage is completed.
   *
   * @param stage the {@link CompletionStage} to check
   * @throws IllegalStateException if the stage is not completed
   * @since 0.1.0
   */
  public static void checkCompleted(CompletionStage<?> stage) {
    if (!stage.toCompletableFuture().isDone()) {
      throw new IllegalStateException("future was not completed");
    }
  }

  /**
   * Gets the value of a completed stage.
   *
   * @param stage a completed {@link CompletionStage}
   * @param <T>   the type of the value that the stage completes into
   * @return the value of the stage if it has one
   * @throws IllegalStateException if the stage is not completed
   * @since 0.1.0
   */
  public static <T> T getCompleted(CompletionStage<T> stage) {
    CompletableFuture<T> future = stage.toCompletableFuture();
    checkCompleted(future);
    return future.join();
  }

  /**
   * Gets the exception from an exceptionally completed future
   * @param stage an exceptionally completed {@link CompletionStage}
   * @param <T>   the type of the value that the stage completes into
   * @return the exception the stage has completed with
   * @throws IllegalStateException if the stage is not completed exceptionally
   * @throws CancellationException if the stage was cancelled
   * @throws UnsupportedOperationException if the {@link CompletionStage} does not
   * support the {@link CompletionStage#toCompletableFuture()} operation
   */
  public static <T> Throwable getException(CompletionStage<T> stage) {
    CompletableFuture<T> future = stage.toCompletableFuture();
    if (!future.isCompletedExceptionally()) {
      throw new IllegalStateException("future was not completed exceptionally");
    }
    try {
      future.join();
      return null;
    } catch (CompletionException x) {
      return x.getCause();
    }
  }

  /**
   * Returns a new stage that, when this stage completes either normally or exceptionally, is
   * executed with this stage's result and exception as arguments to the supplied function.
   *
   * <p> When this stage is complete, the given function is invoked with the result (or {@code null}
   * if none) and the exception (or {@code null} if none) of this stage as arguments, and the
   * function's result is used to complete the returned stage.
   *
   * <p> This differs from
   * {@link CompletionStage#handle(BiFunction)} in that the
   * function should return a {@link CompletionStage} rather than the value
   * directly.
   *
   * @param stage the {@link CompletionStage} to compose
   * @param fn    the function to use to compute the value of the
   *              returned {@link CompletionStage}
   * @param <T>   the type of the input stage's value.
   * @param <U>   the function's return type
   * @return the new {@link CompletionStage}
   * @since 0.1.0
   */
  public static <T, U> CompletionStage<U> handleCompose(
      CompletionStage<T> stage,
      BiFunction<? super T, Throwable, ? extends CompletionStage<U>> fn) {
    return dereference(stage.handle(fn));
  }

  /**
   * Returns a new stage that, when this stage completes
   * exceptionally, is executed with this stage's exception as the
   * argument to the supplied function.  Otherwise, if this stage
   * completes normally, then the returned stage also completes
   * normally with the same value.
   *
   * <p>This differs from
   * {@link CompletionStage#exceptionally(Function)}
   * in that the function should return a {@link CompletionStage} rather than
   * the value directly.
   *
   * @param stage the {@link CompletionStage} to compose
   * @param fn    the function to use to compute the value of the
   *              returned {@link CompletionStage} if this stage completed
   *              exceptionally
   * @param <T>   the type of the input stage's value.
   * @return the new {@link CompletionStage}
   * @since 0.1.0
   */
  public static <T> CompletionStage<T> exceptionallyCompose(
      CompletionStage<T> stage,
      Function<Throwable, ? extends CompletionStage<T>> fn) {
    return dereference(wrap(stage).exceptionally(fn));
  }

  /**
   * This takes a stage of a stage of a value and returns a plain stage of a value.
   *
   * @param stage a {@link CompletionStage} of a {@link CompletionStage} of a value
   * @param <T>   the type of the inner stage's value.
   * @return the {@link CompletionStage} of the value
   * @since 0.1.0
   */
  public static <T> CompletionStage<T> dereference(
      CompletionStage<? extends CompletionStage<T>> stage) {
    return stage.thenCompose(Function.identity());
  }

  private static <T> CompletionStage<CompletionStage<T>> wrap(CompletionStage<T> future) {
    //noinspection unchecked
    return future.thenApply(CompletableFuture::completedFuture);
  }

  /**
   * Combines multiple stages by applying a function.
   *
   * @param a        the first stage.
   * @param b        the second stage.
   * @param function the combining function.
   * @param <R>      the type of the combining function's return value.
   * @param <A>      the type of the first stage's value.
   * @param <B>      the type of the second stage's value.
   * @return a stage that completes into the return value of the supplied function.
   * @since 0.1.0
   */
  public static <R, A, B> CompletionStage<R> combine(
      CompletionStage<A> a, CompletionStage<B> b,
      BiFunction<A, B, R> function) {
    return a.thenCombine(b, function);
  }

  /**
   * Polls an external resource periodically until it returns a non-empty result.
   *
   * <p> The polling task should return {@code Optional.empty()} until it becomes available, and
   * then {@code Optional.of(result)}.  If the polling task throws an exception or returns null,
   * that will cause the result future to complete exceptionally.
   *
   * <p> Canceling the returned future will cancel the scheduled polling task as well.
   *
   * <p> Note that on a ScheduledThreadPoolExecutor the polling task might remain allocated for up
   * to {@code frequency} time after completing or being cancelled.  If you have lots of polling
   * operations or a long polling frequency, consider setting {@code removeOnCancelPolicy} to true.
   * See {@link java.util.concurrent.ScheduledThreadPoolExecutor#setRemoveOnCancelPolicy(boolean)}.
   *
   * @param pollingTask     the polling task
   * @param frequency       the frequency to run the polling task at
   * @param executorService the executor service to schedule the polling task on
   * @param <T>             the type of the result of the polling task, that will be returned when
   *                        the task succeeds.
   * @return a future completing to the result of the polling task once that becomes available
   */
  public static <T> CompletableFuture<T> poll(
      final Supplier<Optional<T>> pollingTask,
      final Duration frequency,
      final ScheduledExecutorService executorService) {
    final CompletableFuture<T> result = new CompletableFuture<>();
    final ScheduledFuture<?> scheduled = executorService.scheduleAtFixedRate(
        () -> pollTask(pollingTask, result), 0, frequency.toMillis(), TimeUnit.MILLISECONDS);
    result.whenComplete((r, ex) -> scheduled.cancel(true));
    return result;
  }

  private static <T> void pollTask(
      final Supplier<Optional<T>> pollingTask,
      final CompletableFuture<T> resultFuture) {
    try {
      pollingTask.get().ifPresent(resultFuture::complete);
    } catch (Exception ex) {
      resultFuture.completeExceptionally(ex);
    }
  }

}