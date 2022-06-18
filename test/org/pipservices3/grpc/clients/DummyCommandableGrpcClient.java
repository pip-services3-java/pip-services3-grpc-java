package org.pipservices3.grpc.clients;

import org.pipservices3.commons.data.DataPage;
import org.pipservices3.commons.data.FilterParams;
import org.pipservices3.commons.data.PagingParams;
import org.pipservices3.grpc.Dummy;

import java.util.Map;

public class DummyCommandableGrpcClient extends CommandableGrpcClient implements IDummyClient {

    public DummyCommandableGrpcClient() {
        super("dummy");
    }

    @Override
    public DataPage<Dummy> getDummies(String correlationId, FilterParams filter, PagingParams paging) {
        return this.callCommand(DataPage.class,
                "get_dummies",
                correlationId,
                Map.of("filter", filter,
                        "paging", paging)
        );
    }

    @Override
    public Dummy getDummyById(String correlationId, String dummyId) {
        return this.callCommand(Dummy.class,
                "get_dummy_by_id",
                correlationId,
                Map.of("dummy_id", dummyId)
        );
    }

    @Override
    public Dummy createDummy(String correlationId, Dummy dummy) {
        return this.callCommand(
                Dummy.class,
                "create_dummy",
                correlationId,
                dummy
        );
    }

    @Override
    public Dummy updateDummy(String correlationId, Dummy dummy) {
        return this.callCommand(
                Dummy.class,
                "update_dummy",
                correlationId,
                dummy
        );
    }

    @Override
    public Dummy deleteDummy(String correlationId, String dummyId) {
        return this.callCommand(
                Dummy.class,
                "delete_dummy",
                correlationId,
                Map.of(
                        "dummy_id", dummyId
                )
        );
    }
}
