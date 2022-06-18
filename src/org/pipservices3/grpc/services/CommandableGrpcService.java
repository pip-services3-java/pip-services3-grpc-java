package org.pipservices3.grpc.services;

import org.pipservices3.commons.commands.CommandSet;
import org.pipservices3.commons.commands.ICommandable;
import org.pipservices3.commons.convert.JsonConverter;
import org.pipservices3.commons.errors.ErrorDescriptionFactory;
import org.pipservices3.commons.errors.InvocationException;
import org.pipservices3.commons.refer.ReferenceException;
import org.pipservices3.commons.run.Parameters;
import org.pipservices3.grpc.commandable.CommandableGrpc;
import org.pipservices3.grpc.commandable.ErrorDescription;
import org.pipservices3.grpc.commandable.InvokeReply;
import org.pipservices3.grpc.commandable.InvokeRequest;


@FunctionalInterface
interface CommandFunction {
    Object apply(String correlationId, Object data);
}

/**
 * Abstract service that receives commands via GRPC protocol
 * to operations automatically generated for commands defined in {@link ICommandable}.
 * Each command is exposed as invoke method that receives command name and parameters.
 * <p>
 * Commandable services require only 3 lines of code to implement a robust external
 * GRPC-based remote interface.
 * <p>
 * ### Configuration parameters ###
 *
 * <pre>
 * - dependencies:
 *   - endpoint:              override for HTTP Endpoint dependency
 *   - controller:            override for Controller dependency
 * - connection(s):
 *   - discovery_key:         (optional) a key to retrieve the connection from {@link org.pipservices3.components.connect.IDiscovery}
 *   - protocol:              connection protocol: http or https
 *   - host:                  host name or IP address
 *   - port:                  port number
 *   - uri:                   resource URI or connection string with all parameters in it
 * </pre>
 * <p>
 * ### References ###
 * <p>
 * - *:logger:*:*:1.0              (optional) {@link org.pipservices3.components.log.ILogger} components to pass log messages
 * - *:counters:*:*:1.0          (optional) {@link org.pipservices3.components.count.ICounters} components to pass collected measurements
 * - *:discovery:*:*:1.0          (optional) {@link org.pipservices3.components.connect.IDiscovery} services to resolve connection
 * - *:endpoint:grpc:*:1.0<          (optional) {@link GrpcEndpoint} reference
 *
 * @see org.pipservices3.grpc.clients.CommandableGrpcClient
 * @see GrpcService
 * <p>
 * ### Example ###
 * <pre>
 * {@code
 * class MyCommandableGrpcService extends CommandableGrpcService {
 *
 *     public MyCommandableGrpcService() {
 *         super();
 *         this._dependencyResolver.put("controller",new Descriptor("mygroup","controller","*","*","1.0"));
 *     }
 *
 *     public static void main(String[] args) throws ApplicationException {
 *         var service = new MyCommandableGrpcService();
 *         service.configure(ConfigParams.fromTuples(
 *                 "connection.protocol", "http",
 *                 "connection.host", "localhost",
 *                 "connection.port", 8080
 *         ));
 *         service.setReferences(References.fromTuples(
 *                 new Descriptor("mygroup","controller","default","default","1.0"), controller
 *         ));
 *
 *         service.open("123");
 *         System.out.println("The GRPC service is running on port 8080");
 *     }
 * }
 * }
 */
public abstract class CommandableGrpcService extends GrpcService {
    private final String _name;

    /**
     * Creates a new instance of the service.
     *
     * @param name a service name.
     */
    public CommandableGrpcService(String name) {
        super(CommandableGrpc.getServiceDescriptor());

        _name = name != null ? name : CommandableGrpc.getServiceDescriptor().getName();
        this._dependencyResolver.put("controller", "none");
    }

    /**
     * Registers a commandable method in this objects GRPC server (service) by the given name.,
     *
     * @param method the GRPC method name.
     * @param action the action to perform at the given route.
     */
    protected void registerCommadableMethod(String method, CommandFunction action) {
        _commandableMethods.put(method, action);
    }

    private void invokeCommand(InvokeRequest request, io.grpc.stub.StreamObserver<InvokeReply> responseObserver) {
        var method = request.getMethod();
        var correlationId = request.getCorrelationId();
        var action = _commandableMethods.get(method);

        // Handle method not found
        if (action == null) {
            var err = new InvocationException(correlationId, "METHOD_NOT_FOUND", "Method " + method + " was not found")
                    .withDetails("method", method);

            responseObserver.onNext(InvokeReply.newBuilder().setError(createErrorResponse(err)).build());
            responseObserver.onCompleted();
            createErrorResponse(err);
            return;
        }

        try {
            // Convert arguments
            var argsEmpty = request.getArgsEmpty();
            var argsJson = request.getArgsJson();
            var args = !argsEmpty && !argsJson.isEmpty()
                    ? Parameters.fromJson(argsJson)
                    : new Parameters();

            // Todo: Validate schema
            //var schema = this._commandableSchemas[method];
            //if (schema)
            //{
            //    //...
            //}

            // Call command action
            var result = action.apply(correlationId, args);

            // Process result and generate response
            var response = InvokeReply.newBuilder().setResultEmpty(result == null);

            if (result != null)
                response.setResultJson(JsonConverter.toJson(result));

            if (result instanceof Exception)
                response.setError(createErrorResponse((Exception) result));

            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
        } catch (Exception ex) {
            // Handle unexpected exception
            var err = new InvocationException(correlationId, "METHOD_FAILED", "Method " + method + " failed")
                    .wrap(ex).withDetails("method", method);

            responseObserver.onNext(InvokeReply.newBuilder().setError(createErrorResponse(err)).build());
            responseObserver.onCompleted();
            createErrorResponse(err);
        }
    }

    private ErrorDescription createErrorResponse(Exception ex) {
        // ErrorDescriptionFactory.create(ex)
        var errDescr = ErrorDescriptionFactory.create(ex);
        var protoErr = ErrorDescription
                .newBuilder()
                .setStatus(errDescr.getStatus());
        if (errDescr.getCause() != null)
            protoErr.setCause(errDescr.getCause());
        if (errDescr.getCorrelationId() != null)
            protoErr.setCorrelationId(errDescr.getCorrelationId());
        if (errDescr.getMessage() != null)
            protoErr.setMessage(errDescr.getMessage());
        if (errDescr.getCause() != null)
            protoErr.setCause(errDescr.getCause());
        if (errDescr.getStackTrace() != null)
            protoErr.setStackTrace(errDescr.getStackTrace());
        if (errDescr.getCategory() != null)
            protoErr.setCategory(errDescr.getCategory());
        if (errDescr.getCode() != null)
            protoErr.setCode(errDescr.getCode());

        return protoErr.build();
    }

    /**
     * Registers all service routes in HTTP endpoint.
     */
    @Override
    public void register() {
        ICommandable controller = null;
        try {
            controller = this._dependencyResolver.getOneRequired(ICommandable.class, "controller");
        } catch (ReferenceException e) {
            throw new RuntimeException(e);
        }

        CommandSet _commandSet = controller.getCommandSet();

        var commands = _commandSet.getCommands();

        registerMethod("invoke", null, this::invokeCommand);

        for (org.pipservices3.commons.commands.ICommand command : commands) {
            var method = "" + this._name + '.' + command.getName();

            this.registerCommadableMethod(method, (correlationId, args) -> {
                var timing = this.instrument(correlationId, method);
                try {
                    return command.execute(correlationId, (Parameters) args);
                } catch (Exception ex) {
                    timing.endFailure(ex);
                    return ex;
                } finally {
                    timing.endTiming();
                }
            });
        }
    }
}
