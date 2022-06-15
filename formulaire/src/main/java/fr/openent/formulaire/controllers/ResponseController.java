package fr.openent.formulaire.controllers;

import fr.openent.formulaire.security.AccessRight;
import fr.openent.formulaire.security.ShareAndOwner;
import fr.openent.formulaire.service.DistributionService;
import fr.openent.formulaire.service.QuestionChoiceService;
import fr.openent.formulaire.service.QuestionService;
import fr.openent.formulaire.service.ResponseService;
import fr.openent.formulaire.service.impl.DefaultDistributionService;
import fr.openent.formulaire.service.impl.DefaultQuestionChoiceService;
import fr.openent.formulaire.service.impl.DefaultQuestionService;
import fr.openent.formulaire.service.impl.DefaultResponseService;
import fr.wseduc.rs.*;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.user.UserUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;

import static fr.openent.form.core.constants.DistributionStatus.FINISHED;
import static fr.openent.form.core.constants.ShareRights.CONTRIB_RESOURCE_RIGHT;
import static fr.openent.form.core.constants.ShareRights.RESPONDER_RESOURCE_RIGHT;
import static fr.openent.form.helpers.RenderHelper.*;
import static fr.openent.form.helpers.UtilsHelper.getIds;
import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;

public class ResponseController extends ControllerHelper {
    private static final Logger log = LoggerFactory.getLogger(ResponseController.class);
    private final ResponseService responseService;
    private final QuestionService questionService;
    private final QuestionChoiceService questionChoiceService;
    private final DistributionService distributionService;
    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("dd/MM/yyyy");
    private final SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm");

    public ResponseController() {
        super();
        this.responseService = new DefaultResponseService();
        this.questionService = new DefaultQuestionService();
        this.questionChoiceService = new DefaultQuestionChoiceService();
        this.distributionService = new DefaultDistributionService();
    }

    @Get("/questions/:questionId/responses")
    @ApiDoc("List all the responses to a specific question")
    @ResourceFilter(ShareAndOwner.class)
    @SecuredAction(value = CONTRIB_RESOURCE_RIGHT, type = ActionType.RESOURCE)
    public void list(HttpServerRequest request) {
        String questionId = request.getParam("questionId");
        String nbLines = request.params().get("nbLines");
        String formId = request.params().get("formId");

        distributionService.listByFormAndStatus(formId, FINISHED, nbLines, getDistribsEvent -> {
            if (getDistribsEvent.isLeft()) {
                log.error("[Formulaire@listResponse] Fail to list finished distributions for form wih id : " + formId);
                renderInternalError(request, getDistribsEvent);
                return;
            }
            if (getDistribsEvent.right().getValue().isEmpty()) {
                String message = "[Formulaire@listResponse] No distribution found for form with id " + formId;
                log.error(message);
                notFound(request, message);
                return;
            }

            responseService.list(questionId, nbLines, getDistribsEvent.right().getValue(), arrayResponseHandler(request));
        });
    }

    @Get("/questions/:questionId/distributions/:distributionId/responses")
    @ApiDoc("List all my responses to a specific question for a specific distribution")
    @ResourceFilter(ShareAndOwner.class)
    @SecuredAction(value = RESPONDER_RESOURCE_RIGHT, type = ActionType.RESOURCE)
    public void listMineByDistribution(HttpServerRequest request) {
        String questionId = request.getParam("questionId");
        String distributionId = request.getParam("distributionId");
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                String message = "[Formulaire@listMineByDistribution] User not found in session.";
                log.error(message);
                unauthorized(request, message);
                return;
            }
            responseService.listMineByDistribution(questionId, distributionId, user, arrayResponseHandler(request));
        });
    }

    @Get("/distributions/:distributionId/responses")
    @ApiDoc("List all responses for a specific distribution")
    @ResourceFilter(ShareAndOwner.class)
    @SecuredAction(value = RESPONDER_RESOURCE_RIGHT, type = ActionType.RESOURCE)
    public void listByDistribution(HttpServerRequest request) {
        String distributionId = request.getParam("distributionId");
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                String message = "[Formulaire@listByDistribution] User not found in session.";
                log.error(message);
                unauthorized(request, message);
                return;
            }
            responseService.listByDistribution(distributionId, arrayResponseHandler(request));
        });
    }

    @Get("/forms/:formId/responses")
    @ApiDoc("List all the responses to a specific form")
    @ResourceFilter(ShareAndOwner.class)
    @SecuredAction(value = CONTRIB_RESOURCE_RIGHT, type = ActionType.RESOURCE)
    public void listByForm(HttpServerRequest request) {
        String formId = request.params().get("formId");
        responseService.listByForm(formId, arrayResponseHandler(request));
    }

    @Get("/responses/count")
    @ApiDoc("Count responses by questionId")
    @ResourceFilter(AccessRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void countByQuestions(HttpServerRequest request){
        JsonArray questionIds = new JsonArray();
        for (Integer i = 0; i < request.params().size(); i++) {
            questionIds.add(request.getParam(i.toString()));
        }
        if (questionIds.size() <= 0) {
            log.error("[Formulaire@countByQuestions] No questionIds to count.");
            noContent(request);
            return;
        }
        responseService.countByQuestions(questionIds, defaultResponseHandler(request));
    }

    @Get("/responses/:responseId")
    @ApiDoc("Get a specific response by id")
    @ResourceFilter(AccessRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void get(HttpServerRequest request) {
        String responseId = request.getParam("responseId");

        // TODO get user infos
        // TODO get response
        // TODO get formId (via distrib ?)
        // TODO getMyFormRights
        // TODO if connected user neither responder nor has rights then unauthorized()

        responseService.get(responseId, defaultResponseHandler(request));
    }

    @Post("/questions/:questionId/responses")
    @ApiDoc("Create a response")
    @ResourceFilter(ShareAndOwner.class)
    @SecuredAction(value = RESPONDER_RESOURCE_RIGHT, type = ActionType.RESOURCE)
    public void create(HttpServerRequest request) {
        String questionId = request.getParam("questionId");
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                String message = "[Formulaire@createResponse] User not found in session.";
                log.error(message);
                unauthorized(request, message);
                return;
            }

            RequestUtils.bodyToJson(request, response -> {
                if (response == null || response.isEmpty()) {
                    log.error("[Formulaire@createResponse] No response to create.");
                    noContent(request);
                    return;
                }

                questionService.get(questionId, questionEvt -> {
                    if (questionEvt.isLeft()) {
                        log.error("[Formulaire@createResponse] Fail to get question corresponding to id : " + questionId);
                        renderInternalError(request, questionEvt);
                        return;
                    }
                    if (questionEvt.right().getValue().isEmpty()) {
                        String message = "[Formulaire@createResponse] No question found for id " + questionId;
                        log.error(message);
                        notFound(request, message);
                        return;
                    }

                    JsonObject question = questionEvt.right().getValue();
                    int question_type = question.getInteger("question_type");
                    Integer choice_id = response.getInteger("choice_id");

                    // If there is a choice it should match an existing QuestionChoice for this question
                    if (choice_id != null && Arrays.asList(4,5,9).contains(question_type)) {
                        questionChoiceService.get(choice_id.toString(), choiceEvt -> {
                            if (choiceEvt.isLeft()) {
                                log.error("[Formulaire@createResponse] Fail to get question choice corresponding to id : " + choice_id);
                                renderInternalError(request, choiceEvt);
                                return;
                            }
                            if (choiceEvt.right().getValue().isEmpty()) {
                                String message = "[Formulaire@createResponse] No choice found for id " + choice_id;
                                log.error(message);
                                notFound(request, message);
                                return;
                            }

                            JsonObject choice = choiceEvt.right().getValue();

                            // Check choice validity
                            if (!choice.getInteger("question_id").toString().equals(questionId) ||
                                    !choice.getString("value").equals(response.getString("answer"))) {
                                String message ="[Formulaire@updateResponse] Wrong choice for response " + response;
                                log.error(message);
                                badRequest(request, message);
                                return;
                            }
                            responseService.create(response, user, questionId, defaultResponseHandler(request));
                        });
                    }
                    else {
                        if (question_type == 6) {
                            try { dateFormatter.parse(response.getString("answer")); }
                            catch (ParseException e) { e.printStackTrace(); }
                        }
                        if (question_type == 7) {
                            try { timeFormatter.parse(response.getString("answer")); }
                            catch (ParseException e) { e.printStackTrace(); }
                        }
                        responseService.create(response, user, questionId, defaultResponseHandler(request));
                    }
                });
            });
        });
    }

    @Put("/responses/:responseId")
    @ApiDoc("Update a specific response")
    @ResourceFilter(ShareAndOwner.class)
    @SecuredAction(value = RESPONDER_RESOURCE_RIGHT, type = ActionType.RESOURCE)
    public void update(HttpServerRequest request) {
        String responseId = request.getParam("responseId");
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                String message = "[Formulaire@updateResponse] User not found in session.";
                log.error(message);
                unauthorized(request, message);
                return;
            }

            RequestUtils.bodyToJson(request, response -> {
                if (response == null || response.isEmpty()) {
                    log.error("[Formulaire@updateResponse] No response to update.");
                    noContent(request);
                    return;
                }

                Integer questionId = response.getInteger("question_id");
                questionService.get(questionId.toString(), questionEvt -> {
                    if (questionEvt.isLeft()) {
                        log.error("[Formulaire@updateResponse] Fail to get question corresponding to id : " + questionId);
                        renderBadRequest(request, questionEvt);
                        return;
                    }
                    if (questionEvt.right().getValue().isEmpty()) {
                        String message = "[Formulaire@updateResponse] No question found for id " + questionId;
                        log.error(message);
                        notFound(request, message);
                        return;
                    }

                    JsonObject question = questionEvt.right().getValue();
                    int question_type = question.getInteger("question_type");
                    Integer choice_id = response.getInteger("choice_id");

                    // If there is a choice it should match an existing QuestionChoice for this question
                    if (choice_id != null && Arrays.asList(4,5,9).contains(question_type)) {
                        questionChoiceService.get(choice_id.toString(), choiceEvt -> {
                            if (choiceEvt.isLeft()) {
                                log.error("[Formulaire@updateResponse] Fail to get question choice corresponding to id : " + choice_id);
                                renderBadRequest(request, choiceEvt);
                                return;
                            }
                            if (choiceEvt.right().getValue().isEmpty()) {
                                String message = "[Formulaire@updateResponse] No choice found for id " + choice_id;
                                log.error(message);
                                notFound(request, message);
                                return;
                            }

                            JsonObject choice = choiceEvt.right().getValue();

                            // Check choice validity
                            if (!choice.getInteger("question_id").equals(questionId) ||
                                    !choice.getString("value").equals(response.getString("answer"))) {
                                log.error("[Formulaire@updateResponse] Wrong choice for response " + response);
                                renderError(request);
                                return;
                            }

                            responseService.update(user, responseId, response, defaultResponseHandler(request));
                        });
                    }
                    else {
                        if (question_type == 6) {
                            try { dateFormatter.parse(response.getString("answer")); }
                            catch (ParseException e) { e.printStackTrace(); }
                        }
                        if (question_type == 7) {
                            try { timeFormatter.parse(response.getString("answer")); }
                            catch (ParseException e) { e.printStackTrace(); }
                        }
                        responseService.update(user, responseId, response, defaultResponseHandler(request));
                    }
                });
            });
        });
    }

    @Delete("/responses/:formId")
    @ApiDoc("Delete specific responses")
    @ResourceFilter(ShareAndOwner.class)
    @SecuredAction(value = RESPONDER_RESOURCE_RIGHT, type = ActionType.RESOURCE)
    public void delete(HttpServerRequest request) {
        String formId = request.getParam("formId");
        RequestUtils.bodyToJsonArray(request, responses -> {
            if (responses == null || responses.isEmpty()) {
                log.error("[Formulaire@deleteResponses] No responses to delete.");
                noContent(request);
                return;
            }

            responseService.delete(getIds(responses), formId, arrayResponseHandler(request));
        });
    }
}