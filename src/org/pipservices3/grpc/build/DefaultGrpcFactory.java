package org.pipservices3.grpc.build;

import org.pipservices3.commons.refer.Descriptor;
import org.pipservices3.components.build.Factory;

import org.pipservices3.grpc.services.GrpcEndpoint;

/**
 * Creates GRPC components by their descriptors.
 *
 * @see Factory
 * @see GrpcEndpoint
 */
public class DefaultGrpcFactory extends Factory {
    private static final Descriptor GrpcEndpointDescriptor = new Descriptor("pip-services", "endpoint", "grpc", "*", "1.0");

    public DefaultGrpcFactory() {
        super();
        this.registerAsType(DefaultGrpcFactory.GrpcEndpointDescriptor, GrpcEndpoint.class);
    }
}
