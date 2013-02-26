package com.github.lianghanzhen.library.concurrent;

/**
 * represent an async task that can schedule by {@link AsyncTaskScheduler}
 * @param <P> the params that this async task needs
 * @param <R> the result that this async task returns
 */
public interface AsyncTask<P, R> {

    R doAsyncTask(P params);

}
