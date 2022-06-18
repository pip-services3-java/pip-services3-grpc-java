package org.pipservices3.grpc.services;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.*;
import org.pipservices3.commons.config.ConfigParams;
import org.pipservices3.commons.errors.ApplicationException;
import org.pipservices3.commons.errors.InvalidStateException;
import org.pipservices3.commons.refer.Descriptor;
import org.pipservices3.commons.refer.References;
import org.pipservices3.grpc.Dummy;
import org.pipservices3.grpc.DummyController;
import org.pipservices3.grpc.dummies.DummiesGrpc;
import org.pipservices3.grpc.dummies.DummiesPageRequest;
import org.pipservices3.grpc.dummies.DummyIdRequest;
import org.pipservices3.grpc.dummies.DummyObjectRequest;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class DummyGrpcServiceTest {

    private static final ConfigParams grpcConfig = ConfigParams.fromTuples(
            "connection.protocol", "http",
            "connection.host", "localhost",
            "connection.port", 3000
    );

    Dummy _dummy1;
    Dummy _dummy2;

    ManagedChannel _channel;

    static DummyGrpcService service;

    DummiesGrpc.DummiesBlockingStub client;

    @BeforeClass
    public static void setupClass() throws ApplicationException {
        var ctrl = new DummyController();

        service = new DummyGrpcService();
        service.configure(grpcConfig);

        References references = References.fromTuples(
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
    public void setup() {
        _channel = ManagedChannelBuilder.forTarget("localhost:3000")
                // Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
                // needing certificates.
                .usePlaintext()
                .build();
        client = DummiesGrpc.newBlockingStub(_channel);

        _dummy1 = new Dummy(null, "Key 1", "Content 1");
        _dummy2 = new Dummy(null, "Key 1", "Content 1");
    }

    @After
    public void teardown() throws InterruptedException {
        _channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    public void testCrudOperations() {
        // Create one dummy
        var dummy = org.pipservices3.grpc.dummies.Dummy.newBuilder()
                .setKey(_dummy1.getKey())
                .setContent(_dummy1.getContent())
                .build();

        var request = DummyObjectRequest.newBuilder().setDummy(dummy).build();

        dummy = client.createDummy(request);

        assertNotNull(dummy);
        assertEquals(dummy.getContent(), _dummy1.getContent());
        assertEquals(dummy.getKey(), _dummy1.getKey());

        var dummy1 = new Dummy(dummy.getId(), dummy.getKey(), dummy.getContent());

        // Create another dummy
        dummy = org.pipservices3.grpc.dummies.Dummy.newBuilder()
                .setKey(_dummy2.getKey())
                .setContent(_dummy2.getContent())
                .build();

        request = DummyObjectRequest.newBuilder().setDummy(dummy).build();

        dummy = client.createDummy(request);

        assertNotNull(dummy);
        assertEquals(dummy.getContent(), _dummy2.getContent());
        assertEquals(dummy.getKey(), _dummy2.getKey());

        // Get all dummies
        var pageRequest = DummiesPageRequest.newBuilder().build();

        var dummies = client.getDummies(pageRequest);

        assertNotNull(dummies);
        assertEquals(dummies.getDataCount(), 2);

        // Update the dummy
        dummy1.setContent("Updated Content 1");

        dummy = org.pipservices3.grpc.dummies.Dummy.newBuilder()
                .setId(dummy1.getId())
                .setKey(dummy1.getKey())
                .setContent(dummy1.getContent())
                .build();

        request = DummyObjectRequest.newBuilder().setDummy(dummy).build();

        dummy = client.updateDummy(request);

        assertNotNull(dummy);
        assertEquals(dummy.getContent(), "Updated Content 1");
        assertEquals(dummy.getKey(), _dummy1.getKey());

        dummy1 = new Dummy(dummy.getId(), dummy.getKey(), dummy.getContent());

        // Delete dummy
        var requestId = DummyIdRequest.newBuilder().setDummyId(dummy1.getId()).build();

        dummy = client.deleteDummyById(requestId);

        // Try to get delete dummy
        requestId = DummyIdRequest.newBuilder().setDummyId(dummy1.getId()).build();

        dummy = client.getDummyById(requestId);

        assertEquals("", dummy.toString());

        assertEquals(service.getNumberOfCalls(), 6);
    }
}
