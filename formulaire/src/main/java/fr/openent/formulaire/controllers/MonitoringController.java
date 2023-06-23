package fr.openent.formulaire.controllers;

import fr.openent.form.helpers.UtilsHelper;
import fr.openent.formulaire.service.MonitoringService;
import fr.openent.formulaire.service.impl.DefaultMonitoringService;
import fr.wseduc.rs.ApiDoc;
import fr.wseduc.rs.Get;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.http.filter.SuperAdminFilter;

import static fr.openent.form.core.constants.Fields.*;

public class MonitoringController extends ControllerHelper {
    private static final Logger log = LoggerFactory.getLogger(MonitoringController.class);
    private final MonitoringService monitoringService;


    public MonitoringController() {
        super();
        this.monitoringService = new DefaultMonitoringService();

    }

    @Get("/positions/duplicates")
    @ApiDoc("Get questions or sections with duplicated position")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(SuperAdminFilter.class)
    public void getPositionDuplicates(final HttpServerRequest request) {
        monitoringService.getFormIdsWithPositionDuplicates()
            .compose(formIdsInfos -> {
                JsonArray formIds = UtilsHelper.getByProp(formIdsInfos, FORM_ID);
                return monitoringService.getPositionDuplicates(formIds);
            })
            .onSuccess(result -> {
                if (result.isEmpty()) {
                    String message = "No duplicates found.";
                    result.add(new JsonObject().put(MESSAGE, message));
                }
                renderJson(request, result);
            })
            .onFailure(err -> {
                log.error(err.getMessage());
                renderError(request);
            });
    }

    @Get("/positions/duplicates/clean")
    @ApiDoc("Reset duplicated position according to their id")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(SuperAdminFilter.class)
    public void cleanPositionDuplicates(final HttpServerRequest request) {
        monitoringService.getFormIdsWithPositionDuplicates()
            .compose(formIdsInfos -> {
                JsonArray formIds = UtilsHelper.getByProp(formIdsInfos, FORM_ID);
                return monitoringService.cleanPositionDuplicates(formIds);
            })
            .onSuccess(result -> {
                if (result.isEmpty()) {
                    String message = "No duplicates found to clean.";
                    result.add(new JsonObject().put(MESSAGE, message));
                }
                renderJson(request, result.getJsonArray(2));
            })
            .onFailure(err -> {
                log.error("[Formulaire@MonitoringController::cleanPositionDuplicates] Fail to clean duplicates : " + err.getMessage());
                renderError(request);
            });
    }

    @Get("/scripts")
    @ApiDoc("Get all scripts")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    @ResourceFilter(SuperAdminFilter.class)
    public void getAllScripts(HttpServerRequest request) {
        monitoringService.getScripts()
                .onSuccess(result -> {
                    if (result.isEmpty()) {
                        String message = "No script found.";
                        result.add(new JsonObject().put(MESSAGE, message));
                    }
                    renderJson(request, result);
                })
                .onFailure(err -> {
                    log.error(err.getMessage());
                    renderError(request);
                });
    }
}