package org.pipservices3.grpc.services;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerServiceDefinition;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyServerBuilder;
import io.netty.handler.ssl.SslContext;
import org.pipservices3.commons.config.ConfigParams;
import org.pipservices3.commons.config.IConfigurable;
import org.pipservices3.commons.errors.*;
import org.pipservices3.commons.refer.IReferenceable;
import org.pipservices3.commons.refer.IReferences;
import org.pipservices3.commons.refer.ReferenceException;
import org.pipservices3.commons.run.IOpenable;
import org.pipservices3.components.count.CompositeCounters;
import org.pipservices3.components.log.CompositeLogger;
import org.pipservices3.grpc.commandable.CommandableGrpc;
import org.pipservices3.grpc.commandable.InvokeReply;
import org.pipservices3.rpc.connect.HttpConnectionResolver;
import org.pipservices3.rpc.services.IRegisterable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Used for creating GRPC endpoints. An endpoint is a URL, at which a given service can be accessed by a client.
 * <p>
 * ### Configuration parameters ###
 * <p>
 * Parameters to pass to the {@link #configure} method for component configuration:
 * <pre>
 * - connection(s) - the connection resolver's connections:
 *     - "connection.discovery_key" - the key to use for connection resolving in a discovery service;
 *     - "connection.protocol" - the connection's protocol;
 *     - "connection.host" - the target host;
 *     - "connection.port" - the target port;
 *     - "connection.uri" - the target URI.
 * - credential - the HTTPS credentials:
 *     - "credential.ssl_key_file" - the SSL private key in PEM
 *     - "credential.ssl_crt_file" - the SSL certificate in PEM
 *     - "credential.ssl_ca_file" - the certificate authorities (root cerfiticates) in PEM
 * </pre>
 * <p>
 * ### References ###
 * <p>
 * A logger, counters, and a connection resolver can be referenced by passing the
 * following references to the object's {@link #setReferences} method:
 * <p>
 * - logger: *:logger:*:*:1.0
 * - counters: *:counters:*:*:1.0"
 * - discovery: *:discovery:*:*:1.0" (for the connection resolver).
 * <p>
 * ### Example ###
 * <pre>
 * {@code
 * public MyMethod(ConfigParams config, IReferences references) {
 *     let endpoint = new HttpEndpoint();
 *     if (this._config)
 *         endpoint.configure(this._config);
 *     if (this._references)
 *         endpoint.setReferences(this._references);
 *     ...
 *     this._endpoint.open(correlationId);
 * }
 * }
 */
public class GrpcEndpoint implements IOpenable, IConfigurable, IReferenceable {

    private static final ConfigParams _defaultConfig = ConfigParams.fromTuples(
            "connection.protocol", "http",
            "connection.host", "0.0.0.0",
            "connection.port", 3000,

            "credential.ssl_key_file", null,
            "credential.ssl_crt_file", null,
            "credential.ssl_ca_file", null,

            "options.maintenance_enabled", false,
            "options.request_max_size", 1024 * 1024,
            "options.file_max_size", 200 * 1024 * 1024,
            "options.connect_timeout", 60000,
            "options.debug", true
    );

    protected final List<Interceptor> _interceptors = new ArrayList<>();
    private ServerBuilder<? extends ServerBuilder<?>> _builder;
    private Server _server;
    private final HttpConnectionResolver _connectionResolver = new HttpConnectionResolver();
    private final CompositeLogger _logger = new CompositeLogger();
    private final CompositeCounters _counters = new CompositeCounters();
    private boolean _maintenanceEnabled = false;
    private long _fileMaxSize = 200 * 1024 * 1024;
    private String _uri;
    private List<IRegisterable> _registrations = new ArrayList<>();

    /**
     * Configures this HttpEndpoint using the given configuration parameters.
     *
     * <pre>
     * __Configuration parameters:__
     * - __connection(s)__ - the connection resolver's connections;
     *     - "connection.discovery_key" - the key to use for connection resolving in a discovery service;
     *     - "connection.protocol" - the connection's protocol;
     *     - "connection.host" - the target host;
     *     - "connection.port" - the target port;
     *     - "connection.uri" - the target URI.
     *     - "credential.ssl_key_file" - SSL private key in PEM
     *     - "credential.ssl_crt_file" - SSL certificate in PEM
     *     - "credential.ssl_ca_file" - Certificate authority (root certificate) in PEM
     * </pre>
     *
     * @param config configuration parameters, containing a "connection(s)" section.
     * @see ConfigParams (in the PipServices "Commons" package)
     */
    @Override
    public void configure(ConfigParams config) throws ConfigException {
        config = config.setDefaults(GrpcEndpoint._defaultConfig);
        this._connectionResolver.configure(config);

        this._maintenanceEnabled = config.getAsBooleanWithDefault("options.maintenance_enabled", this._maintenanceEnabled);
        this._fileMaxSize = config.getAsLongWithDefault("options.file_max_size", this._fileMaxSize);
    }

    /**
     * Sets references to this endpoint's logger, counters, and connection resolver.
     *
     * <pre>
     * References:
     * - logger: *:logger:\*:\*:1.0
     * - counters: *:counters:\*:\*:1.0
     * - discovery: *:discovery:\*:\*:1.0 (for the connection resolver)
     * </pre>
     *
     * @param references an IReferences object, containing references to a logger, counters,
     *                   and a connection resolver.
     * @see IReferences (in the PipServices "Commons" package)
     */
    @Override
    public void setReferences(IReferences references) throws ReferenceException, ConfigException {
        this._logger.setReferences(references);
        this._counters.setReferences(references);
        this._connectionResolver.setReferences(references);
    }

    /**
     * @return whether or not this endpoint is open with an actively listening GRPC server.
     */
    @Override
    public boolean isOpen() {
        return this._server != null;
    }

    /**
     * Opens a connection using the parameters resolved by the referenced connection
     * resolver and creates a GRPC server (service) using the set options and parameters.
     *
     * @param correlationId (optional) transaction id to trace execution through call chain.
     */
    @Override
    public void open(String correlationId) throws ApplicationException {
        if (this.isOpen()) {
            return;
        }

        var connection = this._connectionResolver.resolve(correlationId);

        this._uri = connection.getAsString("uri");
        var port = connection.getAsInteger("port");

        try {
            // TODO add credentials
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

                _builder = NettyServerBuilder.forPort(port)
                        .addService(new CommandableImpl())
                        .sslContext(sslContext);
            } else {
                // Create instance of express application
                _builder = ServerBuilder.forPort(port)
                        .addService(new CommandableImpl());
            }

            // Start operations
            performRegistrations();

            _server = _builder.build();
            _server.start();

            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                    GrpcEndpoint.this.close(correlationId);
//                    System.err.println("*** grpc server shut down");
                }
            });
        } catch (Exception ex) {
            this._server = null;

            throw new ConnectionException(
                    correlationId,
                    "CANNOT_CONNECT",
                    "Opening GRPC service failed"
            ).wrap(ex).withDetails("url", this._uri);
        }
    }

    /**
     * Closes this endpoint and the GRPC server (service) that was opened earlier.
     *
     * @param correlationId (optional) transaction id to trace execution through call chain.
     */
    @Override
    public void close(String correlationId) {
        if (_server != null) {
            this._uri = null;

            try {
                _server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
                this._logger.debug(correlationId, "Closed GRPC service at %s", this._uri);
                this._server = null;
            } catch (InterruptedException ex) {
                this._logger.warn(correlationId, "Failed while closing GRPC service: %s", ex);
                throw new RuntimeException(ex);
            }
        }
    }

    /**
     * Registers a registerable object for dynamic endpoint discovery.
     *
     * @param registration the registration to add.
     * @see IRegisterable
     */
    public void register(IRegisterable registration) {
        this._registrations.add(registration);
    }

    /**
     * Unregisters a registerable object, so that it is no longer used in dynamic
     * endpoint discovery.
     *
     * @param registration the registration to remove.
     * @see IRegisterable
     */
    public void unregister(IRegisterable registration) {
        this._registrations = this._registrations.stream().filter(r -> r != registration).toList();
    }

    private void performRegistrations() {
        for (var registration : this._registrations) {
            registration.register();
        }

        // register interceptors in service
        _interceptors.forEach((interceptor) -> _builder.intercept(interceptor));
    }

    class CommandableImpl extends CommandableGrpc.CommandableImplBase {
        @Override
        public void invoke(org.pipservices3.grpc.commandable.InvokeRequest request,
                           io.grpc.stub.StreamObserver<org.pipservices3.grpc.commandable.InvokeReply> responseObserver) {
            InvokeReply reply;

            reply = InvokeReply.newBuilder().setResultJsonBytes(request.getArgsJsonBytes()).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }

    /**
     * Registers a service with related implementation
     *
     * @param service a GRPC service object.
     */
    public void registerService(ServerServiceDefinition service) {
        this._builder.addService(service);
    }

}
