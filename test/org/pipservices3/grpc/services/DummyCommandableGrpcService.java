package org.pipservices3.grpc.services;

import org.pipservices3.commons.refer.Descriptor;

public class DummyCommandableGrpcService extends CommandableGrpcService {

    public DummyCommandableGrpcService() {
        super("dummy");
        this._dependencyResolver.put("controller", new Descriptor("pip-services-dummies", "controller", "default", "*", "*"));
    }
}
