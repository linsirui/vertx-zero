package io.vertx.up.exception;

import io.vertx.core.http.HttpStatusCode;

public class _500UnexpectedRpcException extends WebException {

    public _500UnexpectedRpcException(final Class<?> clazz,
                                      final Throwable ex) {
        super(clazz, ex.getMessage());
    }

    @Override
    public int getCode() {
        return -60019;
    }

    @Override
    public HttpStatusCode getStatus() {
        return HttpStatusCode.INTERNAL_SERVER_ERROR;
    }
}
