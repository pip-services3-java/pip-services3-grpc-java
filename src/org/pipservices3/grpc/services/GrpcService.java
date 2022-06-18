package org.pipservices3.grpc.services;

import com.google.protobuf.GeneratedMessageV3;
import io.grpc.*;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import org.pipservices3.commons.config.ConfigParams;
import org.pipservices3.commons.config.IConfigurable;
import org.pipservices3.commons.errors.ApplicationException;
import org.pipservices3.commons.errors.ConfigException;
import org.pipservices3.commons.errors.InvalidStateException;
import org.pipservices3.commons.refer.*;
import org.pipservices3.commons.run.IOpenable;
import org.pipservices3.commons.validate.Schema;
import org.pipservices3.components.count.CompositeCounters;
import org.pipservices3.components.log.CompositeLogger;
import org.pipservices3.components.trace.CompositeTracer;
import org.pipservices3.rpc.services.IRegisterable;
import org.pipservices3.rpc.services.InstrumentTiming;

import java.util.*;

import static io.grpc.MethodDescriptor.generateFullMethodName;
import static io.grpc.stub.ServerCalls.asyncUnaryCall;

/**
 * Used for creating GRPC endpoints. An endpoint is a URL, at which a given service can be accessed by a client.
 * <p>
 * ### Configuration parameters ###
 * <p>
 * Parameters to pass to the {@link #configure} method for component configuration:
 * <pre>
 * - dependencies:
 *   - endpoint:              override for GRPC Endpoint dependency
 *   - controller:            override for Controller dependency
 * - connection(s):
 *   - discovery_key:         (optional) a key to retrieve the connection from {@link org.pipservices3.components.connect.IDiscovery}
 *   - protocol:              connection protocol: http or https
 *   - host:                  host name or IP address
 *   - port:                  port number
 *   - uri:                   resource URI or connection string with all parameters in it
 * - credential - the HTTPS credentials:
 *   - ssl_key_file:         the SSL private key in PEM
 *   - ssl_crt_file:         the SSL certificate in PEM
 *   - ssl_ca_file:          the certificate authorities (root cerfiticates) in PEM
 * </pre>
 * <p>
 * ### References ###
 * <p>
 * A logger, counters, and a connection resolver can be referenced by passing the
 * following references to the object's {@link #setReferences} method:
 * <p>
 * - *:logger:*:*:1.0               (optional) {@link org.pipservices3.components.log.ILogger} components to pass log messages
 * - *:counters:*:*:1.0             (optional) {@link org.pipservices3.components.count.ICounters} components to pass collected measurements
 * - *:discovery:*:*:1.0            (optional) {@link org.pipservices3.components.connect.IDiscovery} services to resolve connection
 * - *:endpoint:grpc:*:1.0           (optional) {@link GrpcEndpoint} reference
 *
 * @see org.pipservices3.grpc.clients.GrpcClient
 * <p>
 * ### Example ###
 * <pre>
 * {@code
 * class MyGrpcService extends GrpcService {
 *     private IMyController _controller;
 *
 *     public MyGrpcService() {
 *         super(myGrpcService.getServiceDescriptor());
 *         this._dependencyResolver.put("controller",new Descriptor("mygroup","controller","*","*","1.0"));
 *     }
 *
 *     public void setReferences(IReferences references) throws ReferenceException, ConfigException {
 *         super.setReferences(references);
 *         this._controller = this._dependencyResolver.getRequired(IMyController.class, "controller");
 *     }
 *
 *     private void getMydata(MyDataRequest request, StreamObserver<MyDataPage> responseObserver) {
 *         var correlationId = request.getCorrelationId();
 *         var id = request.getid();
 *         return this._controller.getMyData(correlationId, id);
 *     }
 *
 *     public void register() {
 *         this.registerMethod(
 *                 "get_mydata",
 *                 null,
 *                 // new ObjectSchema(true)
 *                 //     .withOptionalProperty("paging", new PagingParamsSchema())
 *                 //     .withOptionalProperty("filter", new FilterParamsSchema()),
 *                 this::getMydata
 *         );
 *         // ...
 *     }
 *
 *     public static void main(String[] args) throws ApplicationException {
 *         var service = new MyGrpcService();
 *         service.configure(ConfigParams.fromTuples(
 *                 "connection.protocol", "http",
 *                 "connection.host", "localhost",
 *                 "connection.port", 8080
 *         ));
 *         service.setReferences(References.fromTuples(
 *                 new Descriptor("mygroup","controller","default","default","1.0"), controller
 *         ));
 *         service.open("123");
 *         System.out.println("The GRPC service is running on port 8080");
 *     }
 * }
 * }
 */
public abstract class GrpcService implements IOpenable, IConfigurable, IReferenceable,
        IUnreferenceable, IRegisterable {

    private static final ConfigParams _defaultConfig = ConfigParams.fromTuples(
            "dependencies.endpoint", "*:endpoint:grpc:*:1.0"
    );

    private final ServerServiceDefinition.Builder _builder;

    private final io.grpc.ServiceDescriptor _serviceDescriptor;
    private final String _serviceName;
    private ConfigParams _config;
    private IReferences _references;
    private boolean _localEndpoint;
    private final IRegisterable _registrable;
    //    private List<Interceptor> _interceptors = new List<Interceptor>();
    //    private _implementation: any = {};

    Map<String, CommandFunction> _commandableMethods = new HashMap<>();
    private boolean _opened = false;

    /**
     * The GRPC endpoint that exposes this service.
     */
    protected GrpcEndpoint _endpoint;
    /**
     * The dependency resolver.
     */
    protected DependencyResolver _dependencyResolver = new DependencyResolver(GrpcService._defaultConfig);
    /**
     * The logger.
     */
    protected CompositeLogger _logger = new CompositeLogger();
    /**
     * The performance counters.
     */
    protected CompositeCounters _counters = new CompositeCounters();
    /**
     * The tracer.
     */
    protected CompositeTracer _tracer = new CompositeTracer();


    public GrpcService(io.grpc.ServiceDescriptor serviceDescriptor) {
        _serviceDescriptor = serviceDescriptor;
        _serviceName = _serviceDescriptor.getName();
        _builder = ServerServiceDefinition.builder(_serviceName);
        _registrable = this::registerService;
    }

    /**
     * Configures component by passing configuration parameters.
     *
     * @param config configuration parameters to be set.hnnhhjjnnmmkkkjjhhujnmjjhhhhhh
     */
    public void configure(ConfigParams config) throws ConfigException {
        config = config.setDefaults(GrpcService._defaultConfig);

        this._config = config;
        this._dependencyResolver.configure(config);
    }

    /**
     * Sets references to dependent components.
     *
     * @param references references to locate the component dependencies.
     */
    public void setReferences(IReferences references) throws ReferenceException, ConfigException {
        this._references = references;

        this._logger.setReferences(references);
        this._counters.setReferences(references);
        this._tracer.setReferences(references);
        this._dependencyResolver.setReferences(references);

        // Get endpoint
        this._endpoint = this._dependencyResolver.getOneOptional(GrpcEndpoint.class, "endpoint");
        // Or create a local one
        if (this._endpoint == null) {
            this._endpoint = this.createEndpoint();
            this._localEndpoint = true;
        } else {
            this._localEndpoint = false;
        }
        // Add registration callback to the endpoint
        this._endpoint.register(this._registrable);
    }

    /**
     * Unsets (clears) previously set references to dependent components.
     */
    public void unsetReferences() {
        // Remove registration callback from endpoint
        if (this._endpoint != null) {
            this._endpoint.unregister(this._registrable);
            this._endpoint = null;
        }
    }

    private GrpcEndpoint createEndpoint() throws ReferenceException, ConfigException {
        var endpoint = new GrpcEndpoint();

        if (this._config != null)
            endpoint.configure(this._config);

        if (this._references != null)
            endpoint.setReferences(this._references);

        return endpoint;
    }

    /**
     * Adds instrumentation to log calls and measure call time.
     * It returns a Timing object that is used to end the time measurement.
     *
     * @param correlationId (optional) transaction id to trace execution through call chain.
     * @param name          a method name.
     * @return Timing object to end the time measurement.
     */
    protected InstrumentTiming instrument(String correlationId, String name) {
        this._logger.trace(correlationId, "Executing %s method", name);
        this._counters.incrementOne(name + ".exec_count");

        var counterTiming = this._counters.beginTiming(name + ".exec_time");
        var traceTiming = this._tracer.beginTrace(correlationId, name, null);
        return new InstrumentTiming(correlationId, name, "exec",
                this._logger, this._counters, counterTiming, traceTiming);
    }

    /**
     * Checks if the component is opened.
     *
     * @return true if the component has been opened and false otherwise.
     */
    public boolean isOpen() {
        return this._opened;
    }

    /**
     * Opens the component.
     *
     * @param correlationId (optional) transaction id to trace execution through call chain.
     */
    public void open(String correlationId) throws ApplicationException {
        if (this._opened)
            return;

        if (this._endpoint == null) {
            this._endpoint = this.createEndpoint();
            this._endpoint.register(this);
            this._localEndpoint = true;
        }

        if (this._localEndpoint) {
            this._endpoint.open(correlationId);
        }

        this._opened = true;
    }

    /**
     * Closes component and frees used resources.
     *
     * @param correlationId (optional) transaction id to trace execution through call chain.
     */
    public void close(String correlationId) throws InvalidStateException {
        if (!this._opened)
            return;

        if (this._endpoint == null) {
            throw new InvalidStateException(
                    correlationId,
                    "NO_ENDPOINT",
                    "GRPC endpoint is missing"
            );
        }

        if (this._localEndpoint)
            this._endpoint.close(correlationId);

        this._opened = false;
    }

    private void registerService() {
        this.register();

        if (_endpoint != null) {
            var serviceDefinitions = _builder.build();
            _endpoint.registerService(serviceDefinitions);
        }
    }

    /**
     * Registers a middleware for methods in GRPC endpoint.
     *
     * @param action an action function that is called when middleware is invoked.
     */
    protected void registerInterceptor(InterceptorFunc action) {
        if (this._endpoint == null) return;
        this._endpoint._interceptors.add(new Interceptor(action));
    }

    /**
     * Registers a method in GRPC service.
     *
     * @param name   a method name
     * @param schema a validation schema to validate received parameters.
     * @param action an action function that is called when operation is invoked.
     */
    protected <TRequest extends GeneratedMessageV3, TResponse extends GeneratedMessageV3> void registerMethod(String name, Schema schema, GrpcFunc<TRequest, StreamObserver<TResponse>> action) {

        ServerCalls.UnaryMethod<TRequest, TResponse> handler = new ServerCalls.UnaryMethod<TRequest, TResponse>() {
            @Override
            public void invoke(TRequest request, StreamObserver<TResponse> responseObserver) {
                // TODO Validation schema

                action.apply(request, responseObserver);
            }
        };

        try {
            var method = _serviceDescriptor.getMethods().stream().filter((m) -> {
                var splitName = m.getFullMethodName().split("/");
                return splitName.length > 1 && Objects.equals(splitName[1], name);
            }).findFirst();

            MethodDescriptor<TRequest, TResponse> METHOD_INVOKE = MethodDescriptor.<TRequest, TResponse>newBuilder()
                    .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(generateFullMethodName(
                            _serviceName, name))
                    .setRequestMarshaller((MethodDescriptor.Marshaller<TRequest>) method.get().getRequestMarshaller())
                    .setResponseMarshaller((MethodDescriptor.Marshaller<TResponse>) method.get().getResponseMarshaller())
                    .build();

            _builder.addMethod(METHOD_INVOKE, asyncUnaryCall(handler));

        } catch (Exception ex) {
            System.err.println("Error register method");
            throw new RuntimeException(ex);
        }
    }

    /**
     * Registers all service routes in Grpc endpoint.
     * <p>
     * This method is called by the service and must be overriden
     * in child classes.
     */
    @Override
    public abstract void register();
}


@FunctionalInterface
interface GrpcFunc<T, R> {
    void apply(T request, R responseObserver);
}

@FunctionalInterface
interface InterceptorFunc {
    <ReqT, RespT> ServerCall.Listener<ReqT> apply(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next);
}


class Interceptor implements ServerInterceptor {
    private final InterceptorFunc _interceptor;

    public Interceptor(InterceptorFunc interceptor) {
        _interceptor = interceptor;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        return _interceptor.apply(call, headers, next);
    }
}
