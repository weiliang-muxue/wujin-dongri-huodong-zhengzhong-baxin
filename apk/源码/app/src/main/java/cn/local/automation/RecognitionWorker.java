package cn.local.automation;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** One owned frame at a time. Cancellation also releases frames still in the queue. */
public final class RecognitionWorker {
    private final ThreadPoolExecutor executor=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<>());
    private Job current;

    public synchronized void submit(Runnable recognize,Runnable release){
        cancel();
        Job job=new Job(recognize,release);current=job;
        try{executor.execute(job);}catch(RejectedExecutionException e){job.cancel();current=null;throw e;}
    }
    public synchronized void cancel(){
        if(current!=null){current.cancel();executor.remove(current);current=null;}
    }
    /** Disposal stays behind any running native call, so its Mats cannot be freed early. */
    public synchronized void dispose(Runnable cleanup){cancel();executor.execute(cleanup);}
    public synchronized void shutdown(){cancel();executor.shutdown();}
    public boolean awaitTermination(long timeout,TimeUnit unit)throws InterruptedException{return executor.awaitTermination(timeout,unit);}
    public static void checkCancelled(){if(Thread.currentThread().isInterrupted())throw new CancellationException("Recognition cancelled");}

    private static final class Job implements Runnable {
        private final Runnable recognize,release;
        private final AtomicBoolean released=new AtomicBoolean();
        private Thread runner;
        private boolean cancelled,started;
        Job(Runnable recognize,Runnable release){this.recognize=recognize;this.release=release;}
        synchronized void cancel(){
            cancelled=true;
            if(runner!=null)runner.interrupt();
            if(!started)releaseOnce();
        }
        private void releaseOnce(){if(released.compareAndSet(false,true))release.run();}
        @Override public void run(){
            synchronized(this){if(cancelled)return;started=true;runner=Thread.currentThread();}
            try{checkCancelled();recognize.run();}catch(CancellationException ignored){}
            finally{
                synchronized(this){runner=null;}
                try{releaseOnce();}finally{Thread.interrupted();}
            }
        }
    }
}
