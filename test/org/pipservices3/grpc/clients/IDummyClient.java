package org.pipservices3.grpc.clients;

import org.pipservices3.commons.data.DataPage;
import org.pipservices3.commons.data.FilterParams;
import org.pipservices3.commons.data.PagingParams;
import org.pipservices3.grpc.Dummy;

public interface IDummyClient {
    DataPage<Dummy> getDummies(String correlationId, FilterParams filter, PagingParams paging);
    Dummy getDummyById(String correlationId, String dummyId);
    Dummy createDummy(String correlationId, Dummy dummy);
    Dummy updateDummy(String correlationId, Dummy dummy);
    Dummy deleteDummy(String correlationId, String dummyId);
}
