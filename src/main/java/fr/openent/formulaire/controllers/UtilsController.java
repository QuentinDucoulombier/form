package fr.openent.formulaire.controllers;

import fr.openent.formulaire.helpers.RenderHelper;
import fr.openent.formulaire.helpers.upload_file.Attachment;
import fr.openent.formulaire.helpers.upload_file.FileHelper;
import fr.openent.formulaire.security.CreationRight;
import fr.wseduc.rs.ApiDoc;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.http.Renders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.storage.Storage;

import static fr.wseduc.webutils.Utils.handlerToAsyncHandler;

public class UtilsController extends ControllerHelper {
    private static final Logger log = LoggerFactory.getLogger(UtilsController.class);
    private final Storage storage;

    public UtilsController(Storage storage) {
        super();
        this.storage = storage;
    }

    @Get("/files/:idImage/info")
    @ApiDoc("Get image info from workspace for a specific image")
    @ResourceFilter(CreationRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void getInfoImg(final HttpServerRequest request) {
        String idImage = request.getParam("idImage");

        if (idImage == null || idImage.equals("")) {
            String message = "[Formulaire@getInfoImg] The image id must not be empty.";
            log.error(message);
            badRequest(request, message);
            return;
        }

        JsonObject action = new JsonObject().put("action", "getDocument").put("id", idImage);
        eb.request("org.entcore.workspace", action, handlerToAsyncHandler(infos -> {
            if (!infos.body().getString("status").equals("ok")) {
                String message = "[Formulaire@getInfoImg] Failed to get info for image with id : " + idImage;
                log.error(message);
                RenderHelper.internalError(request, infos.body().getJsonObject("result").toString());
            }
            else {
                Renders.renderJson(request, infos.body().getJsonObject("result"), 200);
            }
        }));
    }

    @Post("/files")
    @ApiDoc("Upload several files into the storage")
    @ResourceFilter(CreationRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void postMultipleFiles(final HttpServerRequest request) {
        String nbFiles = request.getHeader("Number-Files");
        int nbFilesToUpload = nbFiles != null ? Integer.parseInt(nbFiles) : 0;
        FileHelper.uploadMultipleFiles(nbFilesToUpload, request, storage, vertx)
            .onSuccess(files -> {
                JsonArray jsonFiles = new JsonArray();
                for (Attachment file : files) {
                    jsonFiles.add(file.toJson());
                }
                renderJson(request, jsonFiles);
            })
            .onFailure(err -> {
                log.error("[Formulaire@postMultipleImages] An error has occurred during upload files: " + err.getMessage());
                RenderHelper.internalError(request, err.getMessage());
            });
    }
}