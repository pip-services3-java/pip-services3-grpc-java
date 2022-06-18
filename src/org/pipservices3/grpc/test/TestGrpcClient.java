package org.pipservices3.grpc.test;

import org.pipservices3.grpc.clients.GrpcClient;

/**
 * GRPC client used for automated testing.
 */
public class TestGrpcClient extends GrpcClient {
    public TestGrpcClient(io.grpc.ServiceDescriptor serviceDescriptor) {
        super(serviceDescriptor);
    }

    /**
     * Calls a remote method via GRPC protocol.
     *
     * @param methodName            a method name to called
     * @param correlationId     (optional) transaction id to trace execution through call chain.
     * @param request           (optional) request object.
     * @return the received result.
     */
    @Override
    public  <TRequest, TResponse> TResponse call(String methodName, String correlationId, TRequest request) {
        return super.call(methodName, correlationId, request);
    }
}
