package fr.openent.formulaire.controllers;

import fr.openent.formulaire.security.AccessRight;
import fr.openent.formulaire.security.CreationRight;
import fr.openent.formulaire.security.ResponseRight;
import fr.openent.formulaire.security.CustomShareAndOwner;
import fr.openent.formulaire.service.DistributionService;
import fr.openent.formulaire.service.FormService;
import fr.openent.formulaire.service.NotifyService;
import fr.openent.formulaire.service.ResponseService;
import fr.openent.formulaire.service.impl.DefaultDistributionService;
import fr.openent.formulaire.service.impl.DefaultFormService;
import fr.openent.formulaire.service.impl.DefaultNotifyService;
import fr.openent.formulaire.service.impl.DefaultResponseService;
import fr.wseduc.rs.*;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.notification.TimelineHelper;
import org.entcore.common.user.UserUtils;

import static fr.openent.form.core.constants.DistributionStatus.FINISHED;
import static fr.openent.form.core.constants.Fields.*;
import static fr.openent.form.core.constants.ShareRights.*;
import static fr.openent.form.helpers.RenderHelper.renderInternalError;
import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;

public class DistributionController extends ControllerHelper {
    private static final Logger log = LoggerFactory.getLogger(DistributionController.class);
    private final NotifyService notifyService;
    private final DistributionService distributionService;
    private final FormService formService;
    private final ResponseService responseService;

    public DistributionController(TimelineHelper timelineHelper) {
        super();
        this.notifyService = new DefaultNotifyService(timelineHelper, eb);
        this.distributionService = new DefaultDistributionService();
        this.formService = new DefaultFormService();
        this.responseService = new DefaultResponseService();
    }

    @Get("/distributions")
    @ApiDoc("List all the distributions of the forms sent by me")
    @ResourceFilter(CreationRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void listBySender(HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                String message = "[Formulaire@listBySender] User not found in session.";
                log.error(message);
                unauthorized(request, message);
                return;
            }
            distributionService.listBySender(user, arrayResponseHandler(request));
        });
    }

    @Get("/distributions/listMine")
    @ApiDoc("List all the distributions of the forms sent to me")
    @ResourceFilter(ResponseRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void listByResponder(HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                String message = "[Formulaire@listByResponder] User not found in session.";
                log.error(message);
                unauthorized(request, message);
                return;
            }
            distributionService.listByResponder(user, arrayResponseHandler(request));
        });
    }

    @Get("/distributions/forms/:formId/list")
    @ApiDoc("List all the distributions of a specific form")
    @ResourceFilter(CreationRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void listByForm(HttpServerRequest request) {
        String formId = request.getParam(PARAM_FORM_ID);
        distributionService.listByForm(formId, arrayResponseHandler(request));
    }

    @Get("/distributions/forms/:formId/listMine")
    @ApiDoc("List all the distributions for a specific form sent to me")
    @ResourceFilter(AccessRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void listByFormAndResponder(HttpServerRequest request) {
        String formId = request.getParam(PARAM_FORM_ID);
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                String message = "[Formulaire@listByFormAndResponder] User not found in session.";
                log.error(message);
                unauthorized(request, message);
                return;
            }
            distributionService.listByFormAndResponder(formId, user, arrayResponseHandler(request));
        });
    }

    @Get("/distributions/forms/:formId/list/:status")
    @ApiDoc("List all the distributions of a specific form with a specific status")
    @ResourceFilter(CreationRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void listByFormAndStatus(HttpServerRequest request) {
        String formId = request.getParam(PARAM_FORM_ID);
        String status = request.getParam(STATUS);
        String nbLines = request.params().get(PARAM_NB_LINES);
        distributionService.listByFormAndStatus(formId, status, nbLines, arrayResponseHandler(request));
    }

    @Get("/distributions/forms/:formId/questions/:questionId/list/:status")
    @ApiDoc("List all the distributions of a specific question of a specific form with a specific status")
    @ResourceFilter(CreationRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void listByFormAndStatusAndQuestion(HttpServerRequest request) {
        String formId = request.getParam(PARAM_FORM_ID);
        String status = request.getParam(STATUS);
        String questionId = request.getParam(PARAM_QUESTION_ID);
        String nbLines = request.params().get(PARAM_NB_LINES);
        distributionService.listByFormAndStatusAndQuestion(formId, status, questionId, nbLines, arrayResponseHandler(request));
    }

    @Get("/distributions/forms/:formId/count")
    @ApiDoc("Get the number of distributions for a specific form")
    @ResourceFilter(CustomShareAndOwner.class)
    @SecuredAction(value = CONTRIB_RESOURCE_RIGHT, type = ActionType.RESOURCE)
    public void count(HttpServerRequest request) {
        String formId = request.getParam(PARAM_FORM_ID);
        distributionService.countFinished(formId, defaultResponseHandler(request));
    }

    @Get("/distributions/:distributionId")
    @ApiDoc("Get a specific distribution by id")
    @ResourceFilter(CustomShareAndOwner.class)
    @SecuredAction(value = RESPONDER_RESOURCE_RIGHT, type = ActionType.RESOURCE)
    public void get(HttpServerRequest request) {
        String distributionId = request.getParam(PARAM_DISTRIBUTION_ID);
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                String message = "[Formulaire@getDistribution] User not found in session.";
                log.error(message);
                unauthorized(request, message);
                return;
            }

            distributionService.get(distributionId, distributionEvt -> {
                if (distributionEvt.isLeft()) {
                    String message = "[Formulaire@getDistribution] Fail to get distribution with id " + distributionId;
                    renderInternalError(request, distributionEvt, message);
                    return;
                }
                if (distributionEvt.right().getValue().isEmpty()) {
                    String message = "[Formulaire@getDistribution] No distribution found for id " + distributionId;
                    log.error(message);
                    notFound(request, message);
                    return;
                }

                JsonObject distribution = distributionEvt.right().getValue();
                String ownerDistribution = distribution.getString(RESPONDER_ID);

                // Check that distribution is owned by the connected user
                if (ownerDistribution == null || !ownerDistribution.equals(user.getUserId())) {
                    String message = "[Formulaire@getDistribution] You're not owner of the distribution with id " + distributionId;
                    log.error(message);
                    unauthorized(request, message);
                    return;
                }

                renderJson(request, distribution);
            });
        });
    }

    @Get("/distributions/forms/:formId")
    @ApiDoc("Get a specific distribution by form, responder and status")
    @ResourceFilter(CustomShareAndOwner.class)
    @SecuredAction(value = RESPONDER_RESOURCE_RIGHT, type = ActionType.RESOURCE)
    public void getByFormResponderAndStatus(HttpServerRequest request) {
        String formId = request.getParam(PARAM_FORM_ID);
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                String message = "[Formulaire@getByFormResponderAndStatus] User not found in session.";
                log.error(message);
                unauthorized(request, message);
                return;
            }
            distributionService.getByFormResponderAndStatus(formId, user, defaultResponseHandler(request));
        });
    }

    @Post("/distributions/:distributionId/add")
    @ApiDoc("Create a new distribution based on an already existing one")
    @ResourceFilter(CustomShareAndOwner.class)
    @SecuredAction(value = RESPONDER_RESOURCE_RIGHT, type = ActionType.RESOURCE)
    public void add(HttpServerRequest request) {
        String distributionId = request.getParam(PARAM_DISTRIBUTION_ID);
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                String message = "[Formulaire@addDistribution] User not found in session.";
                log.error(message);
                unauthorized(request, message);
                return;
            }

            distributionService.get(distributionId, distributionEvt -> {
                if (distributionEvt.isLeft()) {
                    log.error("[Formulaire@addDistribution] Fail to get distribution with id " + distributionId);
                    renderInternalError(request, distributionEvt);
                    return;
                }
                if (distributionEvt.right().getValue().isEmpty()) {
                    String message = "[Formulaire@addDistribution] No distribution found for id " + distributionId;
                    log.error(message);
                    notFound(request, message);
                    return;
                }

                JsonObject distribution = distributionEvt.right().getValue();
                String formId = distribution.getInteger(FORM_ID).toString();
                String ownerDistribution = distribution.getString(RESPONDER_ID);

                // Check that distribution is owned by the connected user
                if (ownerDistribution == null || !ownerDistribution.equals(user.getUserId())) {
                    String message = "[Formulaire@addDistribution] " + user.getUserId() + " is not owner of the distribution with id " + distributionId;
                    log.error(message);
                    unauthorized(request, message);
                    return;
                }

                JsonObject infos = new JsonObject();
                formService.get(formId, user)
                    .compose(form -> {
                        infos.put("isMultiple", form.getBoolean(MULTIPLE));
                        return distributionService.countMyFinished(formId, user);
                    })
                    .compose(nbMyFinished -> {
                        // If not multiple and user has already responded then he's not allowed to respond again
                        if (!infos.getBoolean("isMultiple") && nbMyFinished.getInteger(COUNT) > 0) {
                            String message = "[Formulaire@addDistribution] A finished distribution already exists for this responder for form with id " + formId;
                            unauthorized(request, message);
                            return Future.failedFuture(message);
                        }

                        return distributionService.countMyToDo(formId, user);
                    })
                    .compose(nbMyToDo -> {
                        // If user has already started a response then he should continue that one
                        if (nbMyToDo.getInteger(COUNT) > 0) {
                            String message = "[Formulaire@addDistribution] An unfinished distribution already exists for this responder for form with id " + formId;
                            conflict(request, message);
                            return Future.failedFuture(message);
                        }

                        return distributionService.add(distribution);
                    })
                    .onSuccess(result -> renderJson(request, result))
                    .onFailure(err -> {
                        log.error(err.getMessage());
                        if (!request.isEnded()) renderError(request);
                    });
            });
        });
    }

    @Post("/distributions/:distributionId/duplicate")
    @ApiDoc("Duplicate a distribution and its responses based on distributionId")
    @ResourceFilter(CustomShareAndOwner.class)
    @SecuredAction(value = RESPONDER_RESOURCE_RIGHT, type = ActionType.RESOURCE)
    public void duplicateWithResponses(HttpServerRequest request) {
        String distributionId = request.getParam(PARAM_DISTRIBUTION_ID);
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                String message = "[Formulaire@duplicateWithResponses] User not found in session.";
                log.error(message);
                unauthorized(request, message);
                return;
            }

            distributionService.get(distributionId, distributionEvt -> {
                if (distributionEvt.isLeft()) {
                    log.error("[Formulaire@duplicateWithResponses] Fail to get distribution with id " + distributionId);
                    renderInternalError(request, distributionEvt);
                    return;
                }
                if (distributionEvt.right().getValue().isEmpty()) {
                    String message = "[Formulaire@duplicateWithResponses] No distribution found for id " + distributionId;
                    log.error(message);
                    notFound(request, message);
                    return;
                }

                String ownerDistribution = distributionEvt.right().getValue().getString(RESPONDER_ID);

                // Check that distribution is owned by the connected user
                if (ownerDistribution == null || !ownerDistribution.equals(user.getUserId())) {
                    String message = "[Formulaire@duplicateWithResponses] You're not owner of the distribution with id " + distributionId;
                    log.error(message);
                    unauthorized(request, message);
                    return;
                }

                distributionService.duplicateWithResponses(distributionId)
                        .onSuccess(result -> {
                            Renders.renderJson(request, result);
                        })
                        .onFailure(err -> {
                            Renders.renderError(request);
                            log.error(String.format("[Form@%s::duplicateWithResponses] Failed to create a new distribution with ON_CHANGE status",
                                    this.getClass().getSimpleName()));
                        });

            });
        });
    }

    @Put("/distributions/:distributionId")
    @ApiDoc("Update a specific distribution")
    @ResourceFilter(CustomShareAndOwner.class)
    @SecuredAction(value = RESPONDER_RESOURCE_RIGHT, type = ActionType.RESOURCE)
    public void update(HttpServerRequest request) {
        String distributionId = request.getParam(PARAM_DISTRIBUTION_ID);
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                String message = "[Formulaire@updateDistribution] User not found in session.";
                log.error(message);
                unauthorized(request, message);
                return;
            }

            RequestUtils.bodyToJson(request, distribution -> {
                if (distribution == null || distribution.isEmpty()) {
                    log.error("[Formulaire@updateDistribution] No distribution to update.");
                    noContent(request);
                    return;
                }

                // Check that distribution ids are corresponding
                if (!distribution.getInteger(ID).toString().equals(distributionId)) {
                    String message = "[Formulaire@updateDistribution] Distribution ids in URL and payload don't match : " +
                            distributionId + " and " + distribution.getInteger(ID);
                    log.error(message);
                    badRequest(request, message);
                    return;
                }

                distributionService.get(distributionId, distributionEvt -> {
                    if (distributionEvt.isLeft()) {
                        log.error("[Formulaire@updateDistribution] Fail to get distribution with id " + distributionId);
                        renderInternalError(request, distributionEvt);
                        return;
                    }
                    if (distributionEvt.right().getValue().isEmpty()) {
                        String message = "[Formulaire@updateDistribution] No distribution found for id " + distributionId;
                        log.error(message);
                        notFound(request, message);
                        return;
                    }

                    String ownerDistribution = distributionEvt.right().getValue().getString(RESPONDER_ID);

                    // Check that distribution is owned by the connected user
                    if (ownerDistribution == null || !ownerDistribution.equals(user.getUserId())) {
                        String message = "[Formulaire@updateDistribution] You're not owner of the distribution with id " + distributionId;
                        log.error(message);
                        unauthorized(request, message);
                        return;
                    }

                    distributionService.update(distributionId, distribution, updateDistributionEvt -> {
                        if (updateDistributionEvt.isLeft()) {
                            log.error("[Formulaire@updateDistribution] Error in updating distribution " + distributionId);
                            renderInternalError(request, updateDistributionEvt);
                            return;
                        }

                        JsonObject finalDistribution = updateDistributionEvt.right().getValue();

                        if (finalDistribution.getString(STATUS).equals(FINISHED)) {
                            String formId = finalDistribution.getInteger(FORM_ID).toString();
                            formService.get(formId, user, formEvt -> {
                                if (formEvt.isLeft()) {
                                    log.error("[Formulaire@updateDistribution] Error in getting form with id " + formId);
                                    renderInternalError(request, formEvt);
                                    return;
                                }
                                if (formEvt.right().getValue().isEmpty()) {
                                    String message = "[Formulaire@updateDistribution] No form found for id " + formId;
                                    log.error(message);
                                    notFound(request, message);
                                    return;
                                }

                                JsonObject form = formEvt.right().getValue();
                                if (form.getBoolean(RESPONSE_NOTIFIED)) {
                                    formService.listManagers(form.getInteger(ID).toString(), listManagersEvt -> {
                                        if (listManagersEvt.isLeft()) {
                                            log.error("[Formulaire@updateDistribution] Error in listing managers for form with id " + formId);
                                            renderInternalError(request, listManagersEvt);
                                            return;
                                        }

                                        JsonArray managers = listManagersEvt.right().getValue();
                                        JsonArray managerIds = new JsonArray();
                                        for (int i = 0; i < managers.size(); i++) {
                                            managerIds.add(managers.getJsonObject(i).getString(ID));
                                        }

                                        notifyService.notifyResponse(request, form, managerIds);
                                        renderJson(request, finalDistribution, 200);
                                    });
                                }
                                else {
                                    renderJson(request, finalDistribution, 200);
                                }
                            });
                        }
                        else {
                            renderJson(request, finalDistribution, 200);
                        }
                    });
                });

            });
        });
    }

    @Delete("/distributions/:distributionId/replace/:originalDistributionId")
    @ApiDoc("Delete a specific distribution and update the new one")
    @ResourceFilter(CustomShareAndOwner.class)
    @SecuredAction(value = RESPONDER_RESOURCE_RIGHT, type = ActionType.RESOURCE)
    public void replace(HttpServerRequest request) {
        String distributionId = request.getParam(PARAM_DISTRIBUTION_ID);
        String originalDistributionId = request.getParam(PARAM_ORIGINAL_DISTRIBUTION_ID);
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                String message = "[Formulaire@replaceDistribution] User not found in session.";
                log.error(message);
                unauthorized(request, message);
                return;
            }

            distributionService.get(originalDistributionId, originalDistributionEvt -> {
                if (originalDistributionEvt.isLeft()) {
                    log.error("[Formulaire@replaceDistribution] Error in getting distribution with id " + originalDistributionId);
                    renderInternalError(request, originalDistributionEvt);
                    return;
                }
                if (originalDistributionEvt.right().getValue().isEmpty()) {
                    String message = "[Formulaire@replaceDistribution] No distribution found for id " + originalDistributionId;
                    log.error(message);
                    notFound(request, message);
                    return;
                }

                JsonObject originalDistribution = originalDistributionEvt.right().getValue();

                // Check that distribution is owned by the connected user
                if (!originalDistribution.getString(RESPONDER_ID).equals(user.getUserId())) {
                    String message = "[Formulaire@replaceDistribution] You're not owner of the distribution with id " + originalDistributionId;
                    log.error(message);
                    unauthorized(request, message);
                    return;
                }

                distributionService.get(distributionId, distributionEvt -> {
                    if (distributionEvt.isLeft()) {
                        log.error("[Formulaire@replaceDistribution] Error in getting distribution with id " + distributionId);
                        renderInternalError(request, distributionEvt);
                        return;
                    }
                    if (distributionEvt.right().getValue().isEmpty()) {
                        String message = "[Formulaire@replaceDistribution] No distribution found for id " + distributionId;
                        log.error(message);
                        notFound(request, message);
                        return;
                    }

                    JsonObject distribution = distributionEvt.right().getValue();

                    // Check that distribution is owned by the connected user
                    if (!distribution.getString(RESPONDER_ID).equals(user.getUserId())) {
                        String message = "[Formulaire@replaceDistribution] You're not owner of the distribution with id " + distributionId;
                        log.error(message);
                        unauthorized(request, message);
                        return;
                    }

                    distributionService.delete(originalDistributionId, deleteDistribEvt -> {
                        if (deleteDistribEvt.isLeft()) {
                            log.error("[Formulaire@replaceDistribution] Error in deleting distribution with id " + originalDistributionId);
                            renderInternalError(request, deleteDistribEvt);
                            return;
                        }

                        responseService.deleteMultipleByDistribution(originalDistributionId, deleteRepEvt -> {
                            if (deleteRepEvt.isLeft()) {
                                log.error("[Formulaire@replaceDistribution] Error in deleting responses for distribution with id " + originalDistributionId);
                                renderInternalError(request, deleteRepEvt);
                                return;
                            }

                            distribution.put(STATUS, FINISHED);
                            distributionService.update(distributionId, distribution, defaultResponseHandler(request));
                        });
                    });
                });
            });
        });
    }

    @Delete("/distributions/:distributionId")
    @ApiDoc("Delete a specific distribution")
    @ResourceFilter(CustomShareAndOwner.class)
    @SecuredAction(value = MANAGER_RESOURCE_RIGHT, type = ActionType.RESOURCE)
    public void delete(HttpServerRequest request) {
        String distributionId = request.getParam(PARAM_DISTRIBUTION_ID);
        distributionService.delete(distributionId, defaultResponseHandler(request));
    }
}