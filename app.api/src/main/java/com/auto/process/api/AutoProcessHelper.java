package com.auto.process.api;

/**
 * Created by Jack on 10/27/17.
 */

public interface AutoProcessHelper<T> {
    void bind(T host);
    void unbind(T host);
}
