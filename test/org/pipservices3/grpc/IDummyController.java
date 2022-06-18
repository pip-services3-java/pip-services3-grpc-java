package org.pipservices3.grpc;

import org.pipservices3.commons.data.*;
import org.pipservices3.commons.errors.*;

public interface IDummyController {

    DataPage<Dummy> getPageByFilter(String correlationId, FilterParams filter, PagingParams paging) throws ApplicationException;

    Dummy getOneById(String correlationId, String id);

    Dummy create(String correlationId, Dummy entity);

    Dummy update(String correlationId, Dummy entity);

    Dummy deleteById(String correlationId, String id);
}
