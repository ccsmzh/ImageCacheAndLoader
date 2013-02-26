package com.github.lianghanzhen.library.concurrent;

public interface AsyncTaskListener<P, R> {

    void onAsyncTaskFinished(P params, R result);

    void onAsyncTaskError(P params, R result);

}
