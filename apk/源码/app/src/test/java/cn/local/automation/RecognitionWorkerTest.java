package cn.local.automation;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class RecognitionWorkerTest {
    private static void await(CountDownLatch latch){
        try{assertTrue("Worker did not finish",latch.await(3,TimeUnit.SECONDS));}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new CancellationException();}
    }
    @Test public void pauseCancelsRunningWorkAndContinueDoesNotWaitForOldScan()throws Exception{
        RecognitionWorker worker=new RecognitionWorker();CountDownLatch started=new CountDownLatch(1),fresh=new CountDownLatch(1);
        AtomicInteger released=new AtomicInteger(),oldResult=new AtomicInteger();
        try{
            worker.submit(()->{started.countDown();await(new CountDownLatch(1));oldResult.incrementAndGet();},released::incrementAndGet);
            await(started);worker.cancel();
            worker.submit(()->{assertEquals(1,released.get());assertFalse(Thread.currentThread().isInterrupted());fresh.countDown();},released::incrementAndGet);
            await(fresh);
        }finally{worker.shutdown();assertTrue(worker.awaitTermination(3,TimeUnit.SECONDS));}
        assertEquals(0,oldResult.get());assertEquals(2,released.get());
    }
    @Test public void supersededQueuedFrameIsReleasedWithoutBeingRecognized()throws Exception{
        RecognitionWorker worker=new RecognitionWorker();CountDownLatch busy=new CountDownLatch(1),unblock=new CountDownLatch(1),done=new CountDownLatch(1);
        AtomicInteger abandoned=new AtomicInteger(),released=new AtomicInteger();
        try{
            worker.dispose(()->{busy.countDown();await(unblock);});await(busy);
            worker.submit(abandoned::incrementAndGet,released::incrementAndGet);
            worker.submit(done::countDown,released::incrementAndGet);
            assertEquals(1,released.get());unblock.countDown();await(done);
        }finally{unblock.countDown();worker.shutdown();assertTrue(worker.awaitTermination(3,TimeUnit.SECONDS));}
        assertEquals(0,abandoned.get());assertEquals(2,released.get());
    }
    @Test public void cleanupWaitsForNativeWorkAndBitmapRelease()throws Exception{
        RecognitionWorker worker=new RecognitionWorker();CountDownLatch busy=new CountDownLatch(1),unblock=new CountDownLatch(1),closed=new CountDownLatch(1);
        AtomicInteger released=new AtomicInteger();AtomicBoolean running=new AtomicBoolean();
        try{
            worker.submit(()->{running.set(true);busy.countDown();
                // Simulates a native call that can only stop on return to Java.
                while(unblock.getCount()>0)try{unblock.await();}catch(InterruptedException ignored){}
                running.set(false);
            },released::incrementAndGet);
            await(busy);worker.dispose(()->{assertFalse(running.get());assertEquals(1,released.get());closed.countDown();});
            assertEquals(1,closed.getCount());assertEquals(0,released.get());unblock.countDown();await(closed);
        }finally{unblock.countDown();worker.shutdown();assertTrue(worker.awaitTermination(3,TimeUnit.SECONDS));}
    }
    @Test public void repeatedPauseReleasesQueuedFrameOnlyOnce()throws Exception{
        RecognitionWorker worker=new RecognitionWorker();CountDownLatch busy=new CountDownLatch(1),unblock=new CountDownLatch(1);
        AtomicInteger runs=new AtomicInteger(),released=new AtomicInteger();
        try{
            worker.dispose(()->{busy.countDown();await(unblock);});await(busy);
            worker.submit(runs::incrementAndGet,released::incrementAndGet);worker.cancel();worker.cancel();
            assertEquals(1,released.get());
        }finally{unblock.countDown();worker.shutdown();assertTrue(worker.awaitTermination(3,TimeUnit.SECONDS));}
        assertEquals(0,runs.get());assertEquals(1,released.get());
    }
    @Test public void failedRecognitionStillReleasesAndAllowsNextTask()throws Exception{
        RecognitionWorker worker=new RecognitionWorker();CountDownLatch done=new CountDownLatch(1);AtomicInteger released=new AtomicInteger();
        try{
            worker.submit(()->{throw new CancellationException();},released::incrementAndGet);
            worker.dispose(()->{assertEquals(1,released.get());done.countDown();});await(done);
        }finally{worker.shutdown();assertTrue(worker.awaitTermination(3,TimeUnit.SECONDS));}
    }
}
