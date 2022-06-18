package org.pipservices3.grpc.test;

import org.pipservices3.grpc.clients.CommandableGrpcClient;

public class TestCommandableGrpcClient  extends CommandableGrpcClient {
    /**
     * Creates a new instance of the client.
     *
     * @param name     a service name.
     */
    public TestCommandableGrpcClient(String name) {
        super(name);
    }

    /**
     * Calls a remote method via GRPC commadable protocol.
     * The call is made via Invoke method and all parameters are sent in args object.
     * The complete route to remote method is defined as serviceName + "." + name.
     *
     * @param returnType        generic type of the return object
     * @param name              a name of the command to call.
     * @param correlationId     (optional) transaction id to trace execution through call chain.
     * @param params            command parameters.
     * @return the received result.
     */
    @Override
    protected <T> T callCommand(Class<T> returnType, String name, String correlationId, Object params) {
        return super.callCommand(returnType, name, correlationId, params);
    }
}
