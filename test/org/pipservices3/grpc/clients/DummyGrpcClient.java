package org.pipservices3.grpc.clients;

import org.pipservices3.commons.data.DataPage;
import org.pipservices3.commons.data.FilterParams;
import org.pipservices3.commons.data.PagingParams;
import org.pipservices3.grpc.Dummy;
import org.pipservices3.grpc.dummies.DummiesGrpc;

import java.util.ArrayList;
import java.util.Objects;

public class DummyGrpcClient extends GrpcClient implements IDummyClient {

    public DummyGrpcClient() {
        super(DummiesGrpc.getServiceDescriptor());
    }

    @Override
    public DataPage<Dummy> getDummies(String correlationId, FilterParams filter, PagingParams paging) {
        paging = paging != null ? paging : new PagingParams();
        var pagingParams = org.pipservices3.grpc.dummies.PagingParams.newBuilder();
        pagingParams.setSkip(paging.getSkip());
        pagingParams.setTake(paging.getTake().intValue());
        pagingParams.setTotal(paging.hasTotal());

        var request = org.pipservices3.grpc.dummies.DummiesPageRequest.newBuilder();
        request.setPaging(pagingParams);

        filter = filter != null ? filter : new FilterParams();
        var filterParams = request.getFilterMap();

        for (var propName : filter.keySet())
            filterParams.put(propName, filter.get(propName));

        this.instrument(correlationId, "dummy.get_page_by_filter");

        org.pipservices3.grpc.dummies.DummiesPage result = this.call("get_dummies",
                correlationId,
                request.build()
        );

        var data = new ArrayList<Dummy>();

        for (var item : result.getDataList())
            data.add(new Dummy(item.getId(), item.getKey(), item.getContent()));

        return new DataPage<>(data, result.getTotal());
    }

    @Override
    public Dummy getDummyById(String correlationId, String dummyId) {
        var request = org.pipservices3.grpc.dummies.DummyIdRequest.newBuilder();
        request.setDummyId(dummyId);

        this.instrument(correlationId, "dummy.get_one_by_id");

        org.pipservices3.grpc.dummies.Dummy result = this.call("get_dummy_by_id",
                correlationId,
                request.build()
        );

        if (result != null && Objects.equals(result.getId(), "") && result.getKey().equals(""))
            return null;

        return new Dummy(result.getId(), result.getKey(), result.getContent());
    }

    @Override
    public Dummy createDummy(String correlationId, Dummy dummy) {
        var dummyObj = org.pipservices3.grpc.dummies.Dummy.newBuilder();

        if (dummy.getId() != null)
            dummyObj.setId(dummy.getId());

        dummyObj.setKey(dummy.getKey());
        dummyObj.setContent(dummy.getContent());

        var request = org.pipservices3.grpc.dummies.DummyObjectRequest.newBuilder();
        request.setDummy(dummyObj);

        this.instrument(correlationId, "dummy.create");

        org.pipservices3.grpc.dummies.Dummy result = this.call("create_dummy",
                correlationId,
                request.build()
        );

        if (result != null && result.getId().equals("") && result.getKey().equals(""))
            return null;

        return new Dummy(result.getId(), result.getKey(), result.getContent());
    }

    @Override
    public Dummy updateDummy(String correlationId, Dummy dummy) {
        var dummyObj = org.pipservices3.grpc.dummies.Dummy.newBuilder();
        dummyObj.setId(dummy.getId());
        dummyObj.setKey(dummy.getKey());
        dummyObj.setContent(dummy.getContent());

        var request = org.pipservices3.grpc.dummies.DummyObjectRequest.newBuilder();
        request.setDummy(dummyObj);

        this.instrument(correlationId, "dummy.update");

        org.pipservices3.grpc.dummies.Dummy result = this.call("update_dummy",
                correlationId,
                request.build()
        );

        if (result != null && Objects.equals(result.getId(), "") && result.getKey().equals(""))
            return null;

        return new Dummy(result.getId(), result.getKey(), result.getContent());
    }

    @Override
    public Dummy deleteDummy(String correlationId, String dummyId) {
        var request = org.pipservices3.grpc.dummies.DummyIdRequest.newBuilder();
        request.setDummyId(dummyId);

        this.instrument(correlationId, "dummy.delete_by_id");

        org.pipservices3.grpc.dummies.Dummy result = this.call("delete_dummy_by_id",
                correlationId,
                request.build()
        );

        if (result != null && result.getId().equals("") && result.getKey().equals(""))
            return null;

        return new Dummy(result.getId(), result.getKey(), result.getContent());
    }
}
