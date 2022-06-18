package org.pipservices3.grpc.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.*;
import org.pipservices3.commons.config.ConfigParams;
import org.pipservices3.commons.convert.JsonConverter;
import org.pipservices3.commons.data.DataPage;
import org.pipservices3.commons.errors.ApplicationException;
import org.pipservices3.commons.errors.ConfigException;
import org.pipservices3.commons.errors.InvalidStateException;
import org.pipservices3.commons.refer.Descriptor;
import org.pipservices3.commons.refer.ReferenceException;
import org.pipservices3.commons.refer.References;
import org.pipservices3.grpc.Dummy;
import org.pipservices3.grpc.DummyController;
import org.pipservices3.grpc.commandable.CommandableGrpc;
import org.pipservices3.grpc.commandable.InvokeRequest;
import org.pipservices3.grpc.dummies.DummiesGrpc;
import org.pipservices3.grpc.dummies.DummiesPageRequest;
import org.pipservices3.grpc.dummies.DummyIdRequest;
import org.pipservices3.grpc.dummies.DummyObjectRequest;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class DummyCommandableGrpcServiceTest {

    Dummy _dummy1;
    Dummy _dummy2;

    ManagedChannel _channel;

    static ConfigParams grpcConfig = ConfigParams.fromTuples(
            "connection.protocol", "http",
            "connection.host", "localhost",
            "connection.port", 3001
    );

    static DummyCommandableGrpcService service;

    CommandableGrpc.CommandableBlockingStub client;

    @BeforeClass
    public static void setupClass() throws ApplicationException {
        var ctrl = new DummyController();
        service = new DummyCommandableGrpcService();
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
        _channel = ManagedChannelBuilder.forTarget("localhost:3001")
                // Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
                // needing certificates.
                .usePlaintext()
                .build();
        client = CommandableGrpc.newBlockingStub(_channel);

        _dummy1 = new Dummy(null, "Key 1", "Content 1");
        _dummy2 = new Dummy(null, "Key 1", "Content 1");
    }

    @After
    public void teardown() throws InterruptedException {
        _channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    public void testCrudOperations() throws IOException {
        // Create one dummy
        var request = InvokeRequest.newBuilder()
                .setArgsJson(JsonConverter.toJson(_dummy1))
                .setMethod("dummy.create_dummy")
                .setArgsEmpty(false)
                .build();

        var response = client.invoke(request);
        assertFalse(response.getResultEmpty());
        assertFalse(response.getResultJson().isEmpty());

        var dummy = JsonConverter.fromJson(Dummy.class, response.getResultJson());

        assertNotNull(dummy);
        assertEquals(dummy.getContent(), _dummy1.getContent());
        assertEquals(dummy.getKey(), _dummy1.getKey());

        var dummy1 = dummy;

        // Create another dummy
        request = InvokeRequest.newBuilder()
                .setArgsJson(JsonConverter.toJson(_dummy2))
                .setMethod("dummy.create_dummy")
                .setArgsEmpty(false)
                .build();

        response = client.invoke(request);
        assertFalse(response.getResultEmpty());
        assertFalse(response.getResultJson().isEmpty());

        dummy = JsonConverter.fromJson(Dummy.class, response.getResultJson());

        assertNotNull(dummy);
        assertEquals(dummy.getContent(), _dummy2.getContent());
        assertEquals(dummy.getKey(), _dummy2.getKey());

        // Get all dummies
        request = InvokeRequest.newBuilder()
                .setArgsJson("{}")
                .setMethod("dummy.get_dummies")
                .setArgsEmpty(false)
                .build();

        response = client.invoke(request);

        assertFalse(response.getResultEmpty());
        assertFalse(response.getResultJson().isEmpty());

        var dummies = JsonConverter.fromJson(DataPage.class, response.getResultJson());

        assertNotNull(dummies);
        assertEquals(dummies.getData().size(), 2);

        // Update the dummy
        dummy1.setContent("Updated Content 1");

        request = InvokeRequest.newBuilder()
                .setArgsJson(JsonConverter.toJson(dummy1))
                .setMethod("dummy.update_dummy")
                .setArgsEmpty(false)
                .build();

        response = client.invoke(request);
        assertFalse(response.getResultEmpty());
        assertFalse(response.getResultJson().isEmpty());
        dummy = JsonConverter.fromJson(Dummy.class, response.getResultJson());

        assertNotNull(dummy);
        assertEquals(dummy.getContent(), "Updated Content 1");
        assertEquals(dummy.getKey(), _dummy1.getKey());

        dummy1 = dummy;

        // Delete dummy
        request = InvokeRequest.newBuilder()
                .setArgsJson("{\"dummy_id\":\"" + dummy1.getId() + "\"}")
                .setMethod("dummy.delete_dummy")
                .setArgsEmpty(false)
                .build();

        response = client.invoke(request);

        assertEquals("", response.getError().getMessage());

        // Try to get delete dummy
        request = InvokeRequest.newBuilder()
                .setArgsJson("{\"dummy_id\":\"" + dummy1.getId() + "\"}")
                .setMethod("dummy.get_dummy_by_id")
                .setArgsEmpty(false)
                .build();

        response = client.invoke(request);

        assertEquals("", response.getError().getMessage());
        assertTrue(response.getResultEmpty());
    }
}
