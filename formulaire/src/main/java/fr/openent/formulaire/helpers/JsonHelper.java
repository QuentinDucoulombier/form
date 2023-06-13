package fr.openent.formulaire.helpers;

import fr.wseduc.webutils.http.Renders;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class JsonHelper {
    private static final Logger log = LoggerFactory.getLogger(ApiVersionHelper.class);
    public static void bodyToJsonArrayNoXss(final HttpServerRequest request, final Handler<JsonArray> handler) {
        request.bodyHandler(body -> {
            try {
                InputStream inputStream = new ByteArrayInputStream(body.getBytes());
                InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
                StringBuilder stringBuilder = new StringBuilder();
                char[] readBuffer = new char[4096];
                int bytesRead;
                while ((bytesRead = reader.read(readBuffer)) != -1) {
                    stringBuilder.append(readBuffer, 0, bytesRead);
                }
                String obj = stringBuilder.toString();

                JsonArray json = new fr.wseduc.webutils.collections.JsonArray(obj);
                handler.handle(json);
            } catch (IOException e) {
                Renders.badRequest(request, e.getMessage());
            }
        });
        resumeQuietly(request);
    }


    private static void resumeQuietly(HttpServerRequest request) {
        try {
            request.resume();
        } catch (Exception err) {
            log.error("[Formulaire@JsonHelper::resumeQuietly] Failed to resume request" , err);
        }
    }
}
