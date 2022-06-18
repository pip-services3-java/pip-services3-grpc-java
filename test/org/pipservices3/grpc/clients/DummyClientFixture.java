package org.pipservices3.grpc.clients;

import org.pipservices3.commons.data.FilterParams;
import org.pipservices3.commons.data.PagingParams;
import org.pipservices3.grpc.Dummy;

import static org.junit.Assert.*;

public class DummyClientFixture {
    private final IDummyClient _client;

    public DummyClientFixture(IDummyClient client) {
        _client = client;
    }

    public void testCrudOperations() {
        var dummy1 = new Dummy(null, "Key 1", "Content 1");
        var dummy2 = new Dummy(null, "Key 2", "Content 2");

        // Create one dummy
        var dummy = this._client.createDummy(null, dummy1);
        assertEquals(dummy.getContent(), dummy1.getContent());
        assertEquals(dummy.getKey(), dummy1.getKey());

        dummy1 = dummy;

        // Create another dummy
        dummy = this._client.createDummy(null, dummy2);
        assertEquals(dummy.getContent(), dummy2.getContent());
        assertEquals(dummy.getKey(), dummy2.getKey());

        dummy2 = dummy;

        // Get all dummies
        var dummies = this._client.getDummies(
                null,
                new FilterParams(),
                new PagingParams(0, 5, false)
        );

        assertTrue(dummies.getData().size() >= 2);

        // Update the dummy
        dummy1.setContent("Updated Content 1");
        dummy = this._client.updateDummy(null, dummy1);
        assertEquals(dummy.getContent(), "Updated Content 1");
        assertEquals(dummy.getKey(), dummy1.getKey());

        dummy1 = dummy;

        // Delete dummy
        this._client.deleteDummy(null, dummy1.getId());

        // Try to get delete dummy
        dummy = this._client.getDummyById(null, dummy1.getId());
        assertNull(dummy);
    }
}
