package io.skerna.commons.utils;

import org.hamcrest.CustomTypeSafeMatcher;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;

import static io.skerna.commons.utils.CompletableFutures.allAsList;
import static io.skerna.commons.utils.CompletableFutures.exceptionallyCompletedFuture;
import static org.hamcrest.core.Is.is;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Ronald Cárdenas
 * project: skerna-reactor created at 11/03/19
 **/
class CompletableFuturesTest {


    @Test
    public void test() throws Exception{
        CompletableFuture<String> completable = CompletableFuture.supplyAsync(()-> {
            if(new Random().nextBoolean()){
                throw new IllegalStateException("test");
            }
            return "aaaa";
        });

        completable.whenComplete(new BiConsumer<String, Throwable>() {
            @Override
            public void accept(String s, Throwable throwable) {
                if(throwable!=null){
                    System.out.println("ERROR" + throwable);
                }else {
                    System.out.println("RESULT" + s);
                }
            }
        });

    }
    @Test
    public void allAsList_empty() throws Exception {
        final List<CompletionStage<String>> input = emptyList();
        assertThat(allAsList(input), completesTo(emptyList()));
    }

    @Test
    public void allAsList_one() throws Exception {
        final String value = "a";
        final List<CompletionStage<String>> input = singletonList(completedFuture(value));
        assertThat(allAsList(input), completesTo(singletonList(value)));
    }

    @Test
    public void allAsList_multiple() throws Exception {
        final List<String> values = asList("a", "b", "c");
        final List<CompletableFuture<String>> input = values.stream()
                .map(CompletableFuture::completedFuture)
                .collect(toList());
        assertThat(allAsList(input), completesTo(values));
    }

    @Test
    public void allAsList_exceptional() throws Exception {
        final RuntimeException ex = new RuntimeException("boom");
        final List<CompletionStage<String>> input = asList(
                completedFuture("a"),
                exceptionallyCompletedFuture(ex),
                completedFuture("b")
        );
    }

    private static <T> Matcher<CompletionStage<T>> completesTo(final T expected) {
        return completesTo( is(expected));
    }

    private static <T> Matcher<CompletionStage<T>> completesTo(final Matcher<T> expected) {
        return new CustomTypeSafeMatcher<CompletionStage<T>>("completes to " + String.valueOf(expected)) {
            @Override
            protected boolean matchesSafely(CompletionStage<T> item) {
                try {
                    final T value = item.toCompletableFuture().get(1, SECONDS);
                    return expected.matches(value);
                } catch (Exception ex) {
                    return false;
                }
            }
        };

    }
}