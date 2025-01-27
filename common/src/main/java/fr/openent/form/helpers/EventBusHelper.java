package fr.openent.form.helpers;

import io.vertx.core.*;
import io.vertx.core.eventbus.*;
import io.vertx.core.json.*;

public class EventBusHelper {

    private EventBusHelper() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Call event bus with action
     * @param address   EventBus address
     * @param eb        EventBus
     * @param action    The action to perform
     * @return          Future with the body of the response from the eb
     */
    public static Future<JsonObject> requestJsonObject(String address, EventBus eb, JsonObject action) {
        Promise<JsonObject> promise = Promise.promise();
        eb.request(address, action, MessageResponseHelper.messageJsonObjectHandler(FutureHelper.handlerEither(promise)));
        return promise.future();
    }

    public static Future<JsonArray> requestJsonArray(String address, EventBus eb, JsonObject action) {
        Promise<JsonArray> promise = Promise.promise();
        eb.request(address, action, MessageResponseHelper.messageJsonArrayHandler(FutureHelper.handlerEither(promise)));
        return promise.future();
    }


}