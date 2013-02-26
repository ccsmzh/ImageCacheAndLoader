package com.github.lianghanzhen.library.concurrent;

import android.os.Handler;
import android.os.Message;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class AsyncTaskScheduler<T extends AsyncTask<P, R>, P, R> {

    private static final int DEFAULT_CONCURRENTS = 2;
    private static final int DEFAULT_THREAD_PRIORITY = Thread.NORM_PRIORITY - 1;

    private static final int WHAT_FINISHED = 1;
    private static final int WHAT_ERROR = 2;

    private final T mAsyncTask;
    private final List<P> mRunningTasks;
    private final List<P> mWaitingTasks;

    private final ExecutorService mExecutorService;
    private final int mConcurrents;

    private final InternalHandler<T, P, R> mInternalHandler;
    private final List<AsyncTaskListener<P, R>> mAsyncTaskListeners;

    public AsyncTaskScheduler(T asyncTask) {
        this(asyncTask, DEFAULT_CONCURRENTS, DEFAULT_THREAD_PRIORITY);
    }

    public AsyncTaskScheduler(T asyncTask, int concurrents, final int threadPriority) {
        mAsyncTask = asyncTask;
        mRunningTasks = new ArrayList<P>();
        mWaitingTasks = new ArrayList<P>();

        mConcurrents = concurrents;
        mExecutorService = Executors.newFixedThreadPool(mConcurrents, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread newThread = new Thread(runnable);
                newThread.setPriority(threadPriority);
                return newThread;
            }
        });

        mInternalHandler = new InternalHandler<T, P, R>(this);
        mAsyncTaskListeners = new ArrayList<AsyncTaskListener<P, R>>();
    }

    public void addAsyncTask(P params) {
        if (mRunningTasks.contains(params) || mWaitingTasks.contains(params))
            return;

        if (mRunningTasks.size() < mConcurrents) {
            startRunAsyncTask(params);
        } else {
            mWaitingTasks.add(params);
        }
    }

    public void removeWaitingTask(P params) {
        mWaitingTasks.remove(params);
    }

    private void onAsyncTaskFinished(P params, R result) {
        mRunningTasks.remove(params);
        for (AsyncTaskListener listener : mAsyncTaskListeners) {
            listener.onAsyncTaskFinished(params, result);
        }
        scheduleNextAsyncTask();
    }

    private void onAsyncTaskError(P params, Throwable throwable) {
        mRunningTasks.remove(params);
        for (AsyncTaskListener listener : mAsyncTaskListeners) {
            listener.onAsyncTaskError(params, throwable);
        }
        scheduleNextAsyncTask();
    }

    private void scheduleNextAsyncTask() {
        int waitingSize = mWaitingTasks.size();
        int runningSize = mRunningTasks.size();
        while (waitingSize > 0 && runningSize < mConcurrents) {
            startRunAsyncTask(mWaitingTasks.remove(0));
        }
    }

    private void startRunAsyncTask(P params) {
        mRunningTasks.add(params);
        mExecutorService.execute(new InternalTask<T, P, R>(mInternalHandler, mAsyncTask, params));
    }

    /**
     * register an {@link AsyncTaskListener}
     * @param asyncTaskListener the listener you want to register
     */
    public void registerAsyncTaskListener(AsyncTaskListener<P, R> asyncTaskListener) {
        if (asyncTaskListener != null && !mAsyncTaskListeners.contains(asyncTaskListener)) {
            mAsyncTaskListeners.add(asyncTaskListener);
        }
    }

    /**
     * unregister an {@link AsyncTaskListener}
     * @param asyncTaskListener the listener you want to unregister
     */
    public void unregisterAsyncTaskListener(AsyncTaskListener<P, R> asyncTaskListener) {
        mAsyncTaskListeners.remove(asyncTaskListener);
    }

    private static class InternalTask<T extends AsyncTask<P, R>, P, R> implements Runnable {

        private final InternalHandler mInternalHandler;
        private final T mAsyncTask;
        private final P mParams;

        private InternalTask(InternalHandler<T, P, R> internalHandler, T asyncTask, P params) {
            mInternalHandler = internalHandler;
            mAsyncTask = asyncTask;
            mParams = params;
        }

        @Override
        public void run() {
            try {
                R result = mAsyncTask.doAsyncTask(mParams);
                mInternalHandler.obtainMessage(WHAT_FINISHED, new AsyncTaskResult<P, R>(mParams, result)).sendToTarget();
            } catch (Throwable throwable) {
                mInternalHandler.obtainMessage(WHAT_ERROR, new AsyncTaskError<P>(mParams, throwable)).sendToTarget();
            }
        }

    }

    private static class AsyncTaskResult<P, R> {
        private final P mParams;
        private final R mResult;

        private AsyncTaskResult(P mParams, R mResult) {
            this.mParams = mParams;
            this.mResult = mResult;
        }
    }

    private static class AsyncTaskError<P> {
        private final P mParams;
        private final Throwable throwable;

        private AsyncTaskError(P mParams, Throwable throwable) {
            this.mParams = mParams;
            this.throwable = throwable;
        }
    }

    private static class InternalHandler<T extends AsyncTask<P, R>, P, R> extends Handler {

        private final WeakReference<AsyncTaskScheduler<T, P, R>> mSchedulerRef;

        private InternalHandler(AsyncTaskScheduler<T, P, R> asyncTaskScheduler) {
            mSchedulerRef = new WeakReference<AsyncTaskScheduler<T, P, R>>(asyncTaskScheduler);
        }

        @Override
        public void handleMessage(Message msg) {
            AsyncTaskScheduler<T, P, R> scheduler = mSchedulerRef.get();
            if (scheduler != null) {
                switch (msg.what) {
                    case WHAT_FINISHED:
                        AsyncTaskResult<P, R> result = (AsyncTaskResult<P, R>) msg.obj;
                        scheduler.onAsyncTaskFinished(result.mParams, result.mResult);
                        break;
                    case WHAT_ERROR:
                        AsyncTaskError<P> error = (AsyncTaskError<P>) msg.obj;
                        scheduler.onAsyncTaskError(error.mParams, error.throwable);
                        break;
                }
            }
        }
    }

}
