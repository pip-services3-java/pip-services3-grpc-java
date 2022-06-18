package org.pipservices3.grpc;

import org.pipservices3.commons.commands.*;
import org.pipservices3.commons.data.AnyValueMap;
import org.pipservices3.commons.data.FilterParams;
import org.pipservices3.commons.data.PagingParams;
import org.pipservices3.commons.run.Parameters;
import org.pipservices3.commons.validate.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Map;

public class DummyCommandSet extends CommandSet {

    private final IDummyController _controller;

    public DummyCommandSet(IDummyController controller) {
        _controller = controller;

        addCommand(makeGetPageByFilterCommand());
        addCommand(makeGetOneByIdCommand());
        addCommand(makeCreateCommand());
        addCommand(makeUpdateCommand());
        addCommand(makeDeleteByIdCommand());
    }

    private ICommand makeGetPageByFilterCommand() {
        return new Command("get_dummies",
                new ObjectSchema()
                        .withOptionalProperty("correlation_id", String.class)
                        .withOptionalProperty("filter", new FilterParamsSchema())
                        .withOptionalProperty("paging", new PagingParamsSchema()),
                (correlationId, args) -> {
                    FilterParams filter = FilterParams.fromValue(args.get("filter"));
                    PagingParams paging = PagingParams.fromValue(args.get("paging"));

                    return _controller.getPageByFilter(correlationId, filter, paging);
                });
    }

    private ICommand makeGetOneByIdCommand() {
        return new Command("get_dummy_by_id",
                new ObjectSchema()
                        .withRequiredProperty("dummy_id", org.pipservices3.commons.convert.TypeCode.String),
                (correlationId, args) -> {
                    String dummyId = args.getAsString("dummy_id");

                    return _controller.getOneById(correlationId, dummyId);
                });
    }

    private ICommand makeCreateCommand() {
        return new Command("create_dummy", new DummySchema(),
                (correlationId, args) -> {
                    Dummy dummy = extractDummy(args);
                    return _controller.create(correlationId, dummy);
                });
    }

    private ICommand makeUpdateCommand() {
        return new Command("update_dummy", new DummySchema(),
                (correlationId, args) -> {
                    Dummy dummy = extractDummy(args);
                    return _controller.update(correlationId, dummy);
                });
    }

    private ICommand makeDeleteByIdCommand() {
        return new Command("delete_dummy",
                new ObjectSchema()
                        .withRequiredProperty("dummy_id", org.pipservices3.commons.convert.TypeCode.String),
                (correlationId, args) -> {
                    String dummyId = args.getAsString("dummy_id");
                    return _controller.deleteById(correlationId, dummyId);

                }
        );
    }

    private static Dummy extractDummy(Parameters args) {
        String id = args.getAsNullableString("id");
        String key = args.getAsNullableString("key");
        String content = args.getAsNullableString("content");

        return new Dummy(id, key, content);
    }
}
