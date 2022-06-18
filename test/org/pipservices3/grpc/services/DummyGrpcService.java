package org.pipservices3.grpc.services;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.stub.StreamObserver;
import org.pipservices3.commons.data.FilterParams;
import org.pipservices3.commons.data.PagingParams;
import org.pipservices3.commons.errors.ApplicationException;
import org.pipservices3.commons.errors.ApplicationExceptionFactory;
import org.pipservices3.commons.errors.ConfigException;
import org.pipservices3.commons.refer.Descriptor;

import org.pipservices3.commons.refer.IReferences;
import org.pipservices3.commons.refer.ReferenceException;
import org.pipservices3.grpc.dummies.*;
import org.pipservices3.grpc.Dummy;
import org.pipservices3.grpc.IDummyController;

public class DummyGrpcService extends GrpcService {
    private IDummyController _controller;
    private int _numberOfCalls = 0;

    public DummyGrpcService() {
        super(DummiesGrpc.getServiceDescriptor());
        this._dependencyResolver.put("controller", new Descriptor("pip-services-dummies", "controller", "default", "*", "*"));
    }

    public int getNumberOfCalls() {
        return this._numberOfCalls;
    }

    private <ReqT, RespT> ServerCall.Listener<ReqT> incrementNumberOfCalls(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        this._numberOfCalls++;
        return next.startCall(call, headers);
    }

    public void setReferences(IReferences references) throws ReferenceException, ConfigException {
        super.setReferences(references);
        this._controller = this._dependencyResolver.getOneRequired(IDummyController.class, "controller");
    }

    private void getPageByFilter(DummiesPageRequest request, StreamObserver<DummiesPage> responseObserver) {
        var filter = FilterParams.fromValue(request.getFilterMap());
        var paging = PagingParams.fromValue(request.getPaging());

        try {
            var page = _controller.getPageByFilter(request.getCorrelationId(), filter, paging);
            var reply = DummiesPage.newBuilder();

            page.getData().forEach(
                    (item) -> reply.addData(dummyToObject(item)).build()
            );

            responseObserver.onNext(reply.build());
            responseObserver.onCompleted();

        } catch (ApplicationException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void getOneById(org.pipservices3.grpc.dummies.DummyIdRequest request,
                           io.grpc.stub.StreamObserver<org.pipservices3.grpc.dummies.Dummy> responseObserver) {

        var result = this._controller.getOneById(
                request.getCorrelationId(),
                request.getDummyId()
        );

        responseObserver.onNext(this.dummyToObject(result));
        responseObserver.onCompleted();
    }

    public void create(org.pipservices3.grpc.dummies.DummyObjectRequest request,
                       io.grpc.stub.StreamObserver<org.pipservices3.grpc.dummies.Dummy> responseObserver) {

        var result = this._controller.create(
                request.getCorrelationId(),
                dummyToObject(request.getDummy())
        );

        responseObserver.onNext(this.dummyToObject(result));
        responseObserver.onCompleted();
    }

    public void update(org.pipservices3.grpc.dummies.DummyObjectRequest request,
                       io.grpc.stub.StreamObserver<org.pipservices3.grpc.dummies.Dummy> responseObserver) {
        var result = this._controller.update(
                request.getCorrelationId(),
                dummyToObject(request.getDummy())
        );

        responseObserver.onNext(this.dummyToObject(result));
        responseObserver.onCompleted();
    }

    public void deleteById(org.pipservices3.grpc.dummies.DummyIdRequest request,
                           io.grpc.stub.StreamObserver<org.pipservices3.grpc.dummies.Dummy> responseObserver) {
        var result = this._controller.deleteById(
                request.getCorrelationId(),
                request.getDummyId()
        );

        responseObserver.onNext(this.dummyToObject(result));
        responseObserver.onCompleted();
    }

    private org.pipservices3.grpc.dummies.Dummy dummyToObject(Dummy dummy) {
        if (dummy == null)
            return org.pipservices3.grpc.dummies.Dummy.getDefaultInstance();

        return org.pipservices3.grpc.dummies.Dummy.newBuilder()
                .setId(dummy.getId())
                .setContent(dummy.getContent())
                .setKey(dummy.getKey()).build();
    }

    private Dummy dummyToObject(org.pipservices3.grpc.dummies.Dummy dummy) {
        return new Dummy(dummy.getId(), dummy.getKey(), dummy.getContent());
    }

    @Override
    public void register() {
        this.registerInterceptor(this::incrementNumberOfCalls);

        this.registerMethod(
                "get_dummies",
                null,
                // new ObjectSchema(true)
                //     .withOptionalProperty("paging", new PagingParamsSchema())
                //     .withOptionalProperty("filter", new FilterParamsSchema()),
                this::getPageByFilter
        );

        this.registerMethod(
                "get_dummy_by_id",
                null,
                // new ObjectSchema(true)
                //     .withRequiredProperty("dummy_id", TypeCode.String),
                this::getOneById
        );

        this.registerMethod(
                "create_dummy",
                null,
                // new ObjectSchema(true)
                //     .withRequiredProperty("dummy", new DummySchema()),
                this::create
        );

        this.registerMethod(
                "update_dummy",
                null,
                // new ObjectSchema(true)
                //     .withRequiredProperty("dummy", new DummySchema()),
                this::update
        );

        this.registerMethod(
                "delete_dummy_by_id",
                null,
                // new ObjectSchema(true)
                //     .withRequiredProperty("dummy_id", TypeCode.String),
                this::deleteById
        );
    }
}
