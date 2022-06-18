package org.pipservices3.grpc;

import org.pipservices3.commons.convert.TypeCode;
import org.pipservices3.commons.validate.ArraySchema;
import org.pipservices3.commons.validate.ObjectSchema;

public class DummySchema extends ObjectSchema {

    public DummySchema() {
        withOptionalProperty("id", TypeCode.String);
        withRequiredProperty("key", TypeCode.String);
        withOptionalProperty("content", TypeCode.String);
    }
}
