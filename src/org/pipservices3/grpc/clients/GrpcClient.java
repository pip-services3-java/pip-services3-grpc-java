package org.pipservices3.grpc.clients;

import com.google.api.client.util.IOUtils;
import io.grpc.*;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.ClientCalls;
import io.netty.handler.ssl.SslContext;
import org.pipservices3.commons.config.ConfigParams;
import org.pipservices3.commons.config.IConfigurable;
import org.pipservices3.commons.errors.ApplicationException;
import org.pipservices3.commons.errors.ConfigException;
import org.pipservices3.commons.errors.ConnectionException;
import org.pipservices3.commons.refer.IReferenceable;
import org.pipservices3.commons.refer.IReferences;
import org.pipservices3.commons.refer.ReferenceException;
import org.pipservices3.commons.run.IOpenable;
import org.pipservices3.components.count.CompositeCounters;
import org.pipservices3.components.log.CompositeLogger;
import org.pipservices3.components.trace.CompositeTracer;
import org.pipservices3.rpc.connect.HttpConnectionResolver;
import org.pipservices3.rpc.services.InstrumentTiming;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Abstract client that calls remove endpoints using GRPC protocol.
 * <p>
 * ### Configuration parameters ###
 * <pre>
 * - connection(s):
 *   - discovery_key:         (optional) a key to retrieve the connection from {@link org.pipservices3.components.connect.IDiscovery}
 *   - protocol:              connection protocol: http or https
 *   - host:                  host name or IP address
 *   - port:                  port number
 *   - uri:                   resource URI or connection string with all parameters in it
 * - options:
 *   - retries:               number of retries (default: 3)
 *   - connect_timeout:       connection timeout in milliseconds (default: 10 sec)
 *   - timeout:               invocation timeout in milliseconds (default: 10 sec)
 * </pre>
 * <p>
 * ### References ###
 * <p>
 * - *:logger:*:*:1.0         (optional) {@link org.pipservices3.components.log.ILogger} components to pass log messages
 * - *:counters:*:*:1.0         (optional) {@link org.pipservices3.components.count.ICounters} components to pass collected measurements
 * - *:discovery:*:*:1.0        (optional) {@link org.pipservices3.components.connect.IDiscovery} services to resolve connection
 *
 * @see org.pipservices3.grpc.services.GrpcService
 * @see org.pipservices3.grpc.services.CommandableGrpcService
 * <p>
 * ### Example ###
 * <pre>
 * {@code
 * class MyGrpcClient extends GrpcClient implements IMyClient {
 *
 *     public MyCommandableGrpcClient() {
 *         super(myGrpcService.getServiceDescriptor());
 *     }
 *
 *     // ...
 *
 *     @Override
 *     MyData getData(String correlationId, String id) {
 *         var request = GetDataRequest.newBuilder();
 *         request.setid(id);
 *
 *         this.instrument(correlationId, "myclient.get_data");
 *
 *         MyData result = this.call("get_data",
 *                 correlationId,
 *                 request.build()
 *         );
 *
 *         if (result != null && Objects.equals(result.getId(), ""))
 *             return null;
 *
 *         return new MyData(result.getId(), result.getKey(), result.getContent());
 *     }
 *
 *     public static void main(String[] args) throws ConfigException {
 *         var client = new MyCommandableGrpcClient();
 *         client.configure(ConfigParams.fromTuples(
 *                 "connection.protocol", "http",
 *                 "connection.host", "localhost",
 *                 "connection.port", 8080
 *         ));
 *
 *         var result = client.getData("123", "1");
 *     }
 * }
 * }
 */
public class GrpcClient implements IOpenable, IConfigurable, IReferenceable {
    private static final ConfigParams _defaultConfig = ConfigParams.fromTuples(
            "connection.protocol", "http",
            "connection.host", "0.0.0.0",
            "connection.port", 3000,

            "options.request_max_size", 1024 * 1024,
            "options.connect_timeout", 10000,
            "options.timeout", 10000,
            "options.retries", 3,
            "options.debug", true
    );

    private final io.grpc.ServiceDescriptor _serviceDescriptor;

    protected Channel _channel;
    /**
     * The connection resolver.
     */
    protected HttpConnectionResolver _connectionResolver = new HttpConnectionResolver();
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

    /**
     * The configuration options.
     */
    protected ConfigParams _options = new ConfigParams();
    /**
     * The connection timeout in milliseconds.
     */
    protected long _connectTimeout = 10000;
    /**
     * The invocation timeout in milliseconds.
     */
    protected long _timeout = 10000;
    /**
     * The remote service uri which is calculated on open.
     */
    protected String _uri;

    public GrpcClient(io.grpc.ServiceDescriptor serviceDescriptor) {
        _serviceDescriptor = serviceDescriptor;
    }

    /**
     * Configures component by passing configuration parameters.
     *
     * @param config configuration parameters to be set.
     */
    @Override
    public void configure(ConfigParams config) throws ConfigException {
        config = config.setDefaults(GrpcClient._defaultConfig);
        this._connectionResolver.configure(config);
        this._options = this._options.override(config.getSection("options"));

        this._connectTimeout = config.getAsLongWithDefault("options.connect_timeout", this._connectTimeout);
        this._timeout = config.getAsLongWithDefault("options.timeout", this._timeout);
    }

    /**
     * Sets references to dependent components.
     *
     * @param references references to locate the component dependencies.
     */
    @Override
    public void setReferences(IReferences references) throws ReferenceException, ConfigException {
        this._logger.setReferences(references);
        this._counters.setReferences(references);
        this._tracer.setReferences(references);
        this._connectionResolver.setReferences(references);
    }

    /**
     * Adds instrumentation to log calls and measure call time.
     * It returns a CounterTiming object that is used to end the time measurement.
     *
     * @param correlationId (optional) transaction id to trace execution through call chain.
     * @param name          a method name.
     * @return CounterTiming object to end the time measurement.
     */
    protected InstrumentTiming instrument(String correlationId, String name) {
        this._logger.trace(correlationId, "Executing %s method", name);
        this._counters.incrementOne(name + ".call_time");

        var counterTiming = this._counters.beginTiming(name + ".call_time");
        var traceTiming = this._tracer.beginTrace(correlationId, name, null);
        return new InstrumentTiming(correlationId, name, "exec",
                this._logger, this._counters, counterTiming, traceTiming);
    }

    /**
     * Checks if the component is opened.
     *
     * @return true if the component has been opened and false otherwise.
     */
    @Override
    public boolean isOpen() {
        return this._channel != null;
    }

    /**
     * Opens the component.
     *
     * @param correlationId (optional) transaction id to trace execution through call chain.
     */
    @Override
    public void open(String correlationId) throws ApplicationException {
        if (this.isOpen()) {
            return;
        }

        var connection = this._connectionResolver.resolve(correlationId);

        var host = connection.getHost();
        var port = connection.getPort();

        try {

            if (Objects.equals(connection.getAsStringWithDefault("protocol", "http"), "https")) {
                var sslKeyPath = connection.getAsNullableString("ssl_key_file");
                var sslCrtPath = connection.getAsNullableString("ssl_crt_file");
                var sslCaPath = connection.getAsNullableString("ssl_ca_file");

//                var ca = new ArrayList<>();
//
//                if (sslCaPath != null) {
//                    try(var caFile = new FileInputStream(sslCaPath)) {
//                        var caText = new String(caFile.readAllBytes(), StandardCharsets.UTF_8);
//                        var crtIndex = caText.lastIndexOf("-----BEGIN CERTIFICATE-----");
//                        if (crtIndex > -1) {
//                            ca.add(caText.substring(crtIndex));
//                            caText = caText.substring(0, crtIndex);
//                        }
//                    }
//                }
                SslContext sslContext = GrpcSslContexts.forClient()
                        // if server's cert doesn't chain to a standard root
                        .trustManager(new File(sslCaPath))
                        .keyManager(new File(sslCrtPath), new File(sslKeyPath)) // client cert
                        .build();

                _channel = NettyChannelBuilder.forAddress(host, port)
                        .sslContext(sslContext)
                        .build();
            } else {
                _channel = ManagedChannelBuilder.forAddress(host, port)
                        // Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
                        // needing certificates.
                        .usePlaintext()
                        .build();
            }
        } catch (Exception ex) {
            this._channel = null;
            throw new ConnectionException(
                    correlationId,
                    "CANNOT_CONNECT",
                    "Opening GRPC client failed"
            ).wrap(ex).withDetails("url", this._uri);
        }

    }

    /**
     * Closes component and frees used resources.
     *
     * @param correlationId (optional) transaction id to trace execution through call chain.
     */
    @Override
    public void close(String correlationId) throws ApplicationException {
        if (this._channel != null) {
            // Eat exceptions
            try {
                this._logger.debug(correlationId, "Closed GRPC service at %s", this._uri);
            } catch (Exception ex) {
                this._logger.warn(correlationId, "Failed while closing GRPC service: %s", ex);
            }

            this._channel = null;
            this._uri = null;
        }
    }

    /**
     * Calls a remote method via GRPC protocol.
     *
     * @param methodName    a method name to called
     * @param correlationId (optional) transaction id to trace execution through call chain.
     * @param request       (optional) request object.
     * @return the received result.
     */
    protected <TRequest, TResponse> TResponse call(String methodName, String correlationId, TRequest request) {
        var method = _serviceDescriptor.getMethods().stream().filter((m) -> {
            var splitName = m.getFullMethodName().split("/");
            return splitName.length > 1 && Objects.equals(splitName[1], methodName);
        }).findFirst();

        return ClientCalls.blockingUnaryCall(
                _channel,
                (MethodDescriptor<TRequest, TResponse>) method.get(),
                CallOptions.DEFAULT, request
        );
    }
}
