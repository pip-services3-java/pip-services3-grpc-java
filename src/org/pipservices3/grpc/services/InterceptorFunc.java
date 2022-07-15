package org.pipservices3.grpc.services;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;

@FunctionalInterface
public interface InterceptorFunc {
    <ReqT, RespT> ServerCall.Listener<ReqT> apply(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next);
}
