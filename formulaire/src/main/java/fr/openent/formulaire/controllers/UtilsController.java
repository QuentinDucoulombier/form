package fr.openent.formulaire.controllers;

import fr.openent.formulaire.helpers.upload_file.*;
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

import static fr.openent.form.core.constants.EbFields.WORKSPACE_ADDRESS;
import static fr.openent.form.core.constants.Fields.*;
import static fr.openent.form.helpers.RenderHelper.renderInternalError;
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
        String idImage = request.getParam(PARAM_ID_IMAGE);

        if (idImage == null || idImage.equals("")) {
            String message = "[Formulaire@UtilsController::getInfoImg] The image id must not be empty.";
            log.error(message);
            badRequest(request, message);
            return;
        }

        JsonObject action = new JsonObject().put(ACTION, "getDocument").put(ID, idImage);
        eb.request(WORKSPACE_ADDRESS, action, handlerToAsyncHandler(infos -> {
            if (!infos.body().getString(STATUS).equals(OK)) {
                String message = "[Formulaire@UtilsController::getInfoImg] Failed to get info for image with id : " + idImage;
                log.error(message);
                renderInternalError(request, infos.body().getJsonObject(RESULT).toString());
            }
            else {
                Renders.renderJson(request, infos.body().getJsonObject(RESULT), 200);
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
                log.error("[Formulaire@UtilsController::postMultipleImages] An error has occurred during upload files : " + err.getMessage());
                renderInternalError(request, err.getMessage());
            });
    }
}