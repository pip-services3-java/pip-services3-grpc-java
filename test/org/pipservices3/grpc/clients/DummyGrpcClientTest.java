package org.pipservices3.grpc.clients;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.pipservices3.commons.config.ConfigParams;
import org.pipservices3.commons.errors.ApplicationException;
import org.pipservices3.commons.errors.ConfigException;
import org.pipservices3.commons.errors.InvalidStateException;
import org.pipservices3.commons.refer.Descriptor;
import org.pipservices3.commons.refer.ReferenceException;
import org.pipservices3.commons.refer.References;
import org.pipservices3.grpc.DummyController;
import org.pipservices3.grpc.services.DummyCommandableGrpcService;
import org.pipservices3.grpc.services.DummyGrpcService;

public class DummyGrpcClientTest {
    private static final ConfigParams grpcConfig = ConfigParams.fromTuples(
            "connection.protocol", "http",
            "connection.host", "localhost",
            "connection.port", 3000
    );

    static DummyGrpcService service;
    DummyGrpcClient client;
    DummyClientFixture fixture;

    @BeforeClass
    public static void setupClass() throws ApplicationException {
        var ctrl = new DummyController();

        service = new DummyGrpcService();
        service.configure(grpcConfig);

        var references = References.fromTuples(
                new Descriptor("pip-services-dummies", "controller", "default", "default", "1.0"), ctrl,
                new Descriptor("pip-services-dummies", "service", "grpc", "default", "1.0"), service
        );
        service.setReferences(references);

        service.open(null);
    }

    @AfterClass
    public static void teardownClass() throws InvalidStateException {
        service.close(null);
    }

    @Before
    public void setup() throws ApplicationException {
        client = new DummyGrpcClient();
        fixture = new DummyClientFixture(client);

        client.configure(grpcConfig);
        client.setReferences(new References());
        client.open(null);
    }

    @Test
    public void testCrudOperations() {
        fixture.testCrudOperations();
    }
}
