package org.pipservices3.grpc.services;

@FunctionalInterface
public interface GrpcFunc<T, R> {
    void apply(T request, R responseObserver);
}
