package io.skerna.commons.utils;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * @author Ronald Cárdenas
 * project: skerna-reactor created at 13/03/19
 **/
public class TestCompletables {

    @Test
    public void testCompletables(){
        CompletableFuture<String> tem  = create1Completable().thenCompose(aBoolean -> create2Compleatable());
        tem.whenComplete((s, throwable) -> {
            if (throwable != null) {
                System.out.println("ERROR");
                return;
            }
            System.out.println(s);
        }).join();



        create1Completable().thenApply((e)->{
            return create2Compleatable().join();
        }).whenComplete(new BiConsumer<String, Throwable>() {
            @Override
            public void accept(String s, Throwable throwable) {
                System.out.println(s);
            }
        }).join();
    }


    CompletableFuture<Boolean> create1Completable(){
        return CompletableFuture.supplyAsync(()-> {
            try {
                Thread.sleep(1000);
                return  true;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return true;
        });
    }

    CompletableFuture<String> create2Compleatable(){
        return CompletableFuture.supplyAsync(()->{
            return "Casa";
        });
    }
}
