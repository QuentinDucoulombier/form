package fr.openent.formulaire.controllers;

import fr.openent.form.core.enums.QuestionTypes;
import fr.openent.form.helpers.UtilsHelper;
import fr.openent.formulaire.helpers.ApiVersionHelper;
import fr.openent.formulaire.helpers.JsonHelper;
import fr.openent.formulaire.security.AccessRight;
import fr.openent.formulaire.security.CustomShareAndOwner;
import fr.openent.formulaire.service.*;
import fr.openent.formulaire.service.impl.*;
import fr.wseduc.rs.*;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.user.UserUtils;

import java.util.List;
import java.util.stream.Collectors;

import static fr.openent.form.core.constants.Constants.CONDITIONAL_QUESTIONS;
import static fr.openent.form.core.constants.Fields.*;
import static fr.openent.form.core.constants.ShareRights.CONTRIB_RESOURCE_RIGHT;
import static fr.openent.form.helpers.RenderHelper.renderInternalError;
import static fr.openent.form.helpers.UtilsHelper.getByProp;
import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;

public class QuestionController extends ControllerHelper {
    private static final Logger log = LoggerFactory.getLogger(QuestionController.class);
    private final QuestionService questionService;
    private final SectionService sectionService;
    private final QuestionSpecificFieldsService questionSpecificFieldsService;
    private final FormService formService;
    private final DistributionService distributionService;

    public QuestionController() {
        super();
        this.questionService = new DefaultQuestionService();
        this.sectionService = new DefaultSectionService();
        this.questionSpecificFieldsService = new DefaultQuestionSpecificFieldsService();
        this.formService = new DefaultFormService();
        this.distributionService = new DefaultDistributionService();
    }

    @Get("/forms/:formId/questions")
    @ApiDoc("List all the questions of a specific form")
    @ResourceFilter(AccessRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void listForForm(HttpServerRequest request) {
        String formId = request.getParam(PARAM_FORM_ID);

        questionService.listForForm(formId, listQuestionsEvt -> {
            if (listQuestionsEvt.isLeft()) {
                log.error("[Formulaire@listForFrom] Fail to list questions for form with id : " + formId);
                renderInternalError(request, listQuestionsEvt);
                return;
            }
            JsonArray questions = listQuestionsEvt.right().getValue();
            questionSpecificFieldsService.syncQuestionSpecs(questions)
                    .onSuccess(result -> renderJson(request, result))
                    .onFailure(error -> renderError(request));
        });
    }

    @Get("/sections/:sectionId/questions")
    @ApiDoc("List all the questions of a specific section")
    @ResourceFilter(AccessRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void listForSection(HttpServerRequest request) {
        String sectionId = request.getParam(PARAM_SECTION_ID);

        questionService.listForSection(sectionId, getQuestionEvt -> {
            if (getQuestionEvt.isLeft()) {
                log.error("[Formulaire@listForSection] Fail to list questions for section with id : " + sectionId);
                renderInternalError(request, getQuestionEvt);
                return;
            }
            JsonArray questions = getQuestionEvt.right().getValue();

            questionSpecificFieldsService.syncQuestionSpecs(questions)
                    .onSuccess(result -> renderJson(request, result))
                    .onFailure(error -> renderError(request));
        });
    }

    @Get("/questions/children")
    @ApiDoc("List all the children questions of a list of questions")
    @ResourceFilter(AccessRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void listChildren(HttpServerRequest request) {
        JsonArray questionIds = new JsonArray();
        for (Integer i = 0; i < request.params().size(); i++) {
            questionIds.add(request.getParam(i.toString()));
        }
        if (questionIds.size() <= 0) {
            log.error("[Formulaire@listChildren] No questionIds to get children.");
            noContent(request);
            return;
        }
        questionService.listChildren(questionIds, arrayResponseHandler(request));
    }

    @Get("/questions/:questionId")
    @ApiDoc("Get a specific question by id")
    @ResourceFilter(CustomShareAndOwner.class)
    @SecuredAction(value = CONTRIB_RESOURCE_RIGHT, type = ActionType.RESOURCE)
    public void get(HttpServerRequest request) {
        String questionId = request.getParam(PARAM_QUESTION_ID);
        questionService.get(questionId, defaultResponseHandler(request));
    }

    @Post("/forms/:formId/questions")
    @ApiDoc("Create a question in a specific form")
    @ResourceFilter(CustomShareAndOwner.class)
    @SecuredAction(value = CONTRIB_RESOURCE_RIGHT, type = ActionType.RESOURCE)
    public void create(HttpServerRequest request) {
        String formId = request.getParam(PARAM_FORM_ID);
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                String message = "[Formulaire@createQuestion] User not found in session.";
                log.error(message);
                unauthorized(request, message);
                return;
            }

            RequestUtils.bodyToJson(request, question -> {
                if (question == null || question.isEmpty()) {
                    log.error("[Formulaire@createQuestion] No question to create.");
                    noContent(request);
                    return;
                }

                Long sectionId = question.getLong(SECTION_ID);
                Long sectionPosition = question.getLong(SECTION_POSITION);

                // Check section infos validity
                if (sectionId == null ^ sectionPosition == null) { // ^ = XOR -> return true if they have different value, return false if they both have same value
                    String message = "[Formulaire@createQuestion] sectionId and sectionPosition must be both null or both not null.";
                    log.error(message);
                    badRequest(request, message);
                    return;
                } else if (sectionId != null && (sectionId <= 0 || sectionPosition <= 0)) {
                    String message = "[Formulaire@createQuestion] sectionId and sectionPosition must have values greater than 0.";
                    log.error(message);
                    badRequest(request, message);
                    return;
                } else if (sectionPosition != null && question.getInteger(POSITION) != null) {
                    String message = "[Formulaire@createQuestion] A question is either in or out of a section, it cannot have a position and a section_position.";
                    log.error(message);
                    badRequest(request, message);
                    return;
                } else if (question.getBoolean(CONDITIONAL) && !CONDITIONAL_QUESTIONS.contains(question.getInteger(QUESTION_TYPE))) {
                    String message = "[Formulaire@createQuestion] A question conditional question must be of type : " + CONDITIONAL_QUESTIONS;
                    log.error(message);
                    badRequest(request, message);
                    return;
                }

                formService.get(formId, user, formEvt -> {
                    if (formEvt.isLeft()) {
                        log.error("[Formulaire@createQuestion] Failed to get form with id : " + formId);
                        renderInternalError(request, formEvt);
                        return;
                    }
                    if (formEvt.right().getValue().isEmpty()) {
                        String message = "[Formulaire@createQuestion] No form found for form with id " + formId;
                        log.error(message);
                        notFound(request, message);
                        return;
                    }

                    JsonObject form = formEvt.right().getValue();

                    // Check if form is not already responded
                    distributionService.countFinished(formId, countRepEvt -> {
                        if (countRepEvt.isLeft()) {
                            log.error("[Formulaire@createQuestion] Failed to count finished distributions form form with id : " + formId);
                            renderInternalError(request, countRepEvt);
                            return;
                        }

                        int nbResponseTot = countRepEvt.right().getValue().getInteger(COUNT, 0);
                        if (nbResponseTot > 0) {
                            String message = "[Formulaire@createQuestion] You cannot create a question for a form already responded";
                            log.error(message);
                            badRequest(request, message);
                            return;
                        }

                        // Check if the type of question if it's for a public form (type FILE is forbidden)
                        if (form.getBoolean(IS_PUBLIC) && question.getInteger(QUESTION_TYPE) != null && question.getInteger(QUESTION_TYPE) == 8) {
                            String message = "[Formulaire@createQuestion] You cannot create a question type FILE for the public form with id " + formId;
                            log.error(message);
                            badRequest(request, message);
                            return;
                        }

                        // If it's a conditional question in a section, check if another one already exists
                        if (question.getBoolean(CONDITIONAL) && sectionId != null) {
                            questionService.getSectionIdsWithConditionalQuestions(formId, new JsonArray(), sectionIdsEvt -> {
                                if (sectionIdsEvt.isLeft()) {
                                    log.error("[Formulaire@createQuestion] Failed to get section ids for form with id : " + formId);
                                    renderInternalError(request, sectionIdsEvt);
                                    return;
                                }
                                JsonArray sectionIds = getByProp(sectionIdsEvt.right().getValue(), SECTION_ID);
                                if (sectionIds.contains(sectionId)) {
                                    String message = "[Formulaire@createQuestion] A conditional question is already existing for the section with id : " + sectionId;
                                    log.error(message);
                                    badRequest(request, message);
                                    return;
                                }
                                createQuestion(request, question, formId);
                            });
                        }
                        else {
                            createQuestion(request, question, formId);
                        }
                    });
                });
            });
        });
    }

    private void createQuestion(HttpServerRequest request, JsonObject question, String formId) {
        sectionService.list(formId)
            .compose(sections -> {
                // Check the question's position and if some sections already use it
                Integer questionPosition = question.getInteger(POSITION);
                JsonArray sectionPositions = UtilsHelper.getByProp(sections, POSITION);
                if (questionPosition != null && sectionPositions.contains(questionPosition)) {
                    String message = "Position " + questionPosition + " is already used by a section.";
                    return Future.failedFuture(message);
                }

                return questionService.create(question, formId);
            })
            .compose(createdQuestion -> {
                // If question is cursor type, you insert fields in a specific table
                if (question.getInteger(QUESTION_TYPE) == QuestionTypes.CURSOR.getCode()) {
                    String questionId = createdQuestion.getInteger(ID).toString();
                    return questionSpecificFieldsService.create(question, questionId);
                }

                return Future.succeededFuture(createdQuestion);
            })
            .onSuccess(createdQuestionAndSpecifics -> {
                renderJson(request, createdQuestionAndSpecifics);
            })
            .onFailure(err -> {
                log.error("[Formulaire@QuestionController::createQuestion] Failed to create question " + question + " : " + err.getMessage());
                renderError(request);
            });
    }

    @Put("/forms/:formId/questions")
    @ApiDoc("Update specific questions")
    @ResourceFilter(CustomShareAndOwner.class)
    @SecuredAction(value = CONTRIB_RESOURCE_RIGHT, type = ActionType.RESOURCE)
    public void update(HttpServerRequest request) {
        String formId = request.getParam(PARAM_FORM_ID);
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                String message = "[Formulaire@QuestionController::update] User not found in session.";
                log.error(message);
                unauthorized(request, message);
                return;
            }

            JsonHelper.bodyToJsonArrayNoXss(request, questions -> {
                if (questions == null || questions.isEmpty()) {
                    log.error("[Formulaire@QuestionController::update] No questions to update.");
                    noContent(request);
                    return;
                }

                // Check section infos validity
                int i = 0;
                while (i < questions.size()) {
                    JsonObject question = questions.getJsonObject(i);
                    Long sectionId = question.getLong(SECTION_ID);
                    Long sectionPosition = question.getLong(SECTION_POSITION);

                    if (sectionId == null ^ sectionPosition == null) { // ^ = XOR -> return true if they have different value, return false if they both have same value
                        String message = "[Formulaire@QuestionController::update] sectionId and sectionPosition must be both null or both not null : " + question;
                        log.error(message);
                        badRequest(request, message);
                        return;
                    } else if (sectionId != null && (sectionId <= 0 || sectionPosition <= 0)) {
                        String message = "[Formulaire@QuestionController::update] sectionId and sectionPosition must have values greater than 0 : " + question;
                        log.error(message);
                        badRequest(request, message);
                        return;
                    } else if (sectionPosition != null && question.getInteger(POSITION) != null) {
                        String message = "[Formulaire@QuestionController::update] A question is either in or out of a section, it cannot have a position and a section_position : " + question;
                        log.error(message);
                        badRequest(request, message);
                        return;
                    } else if (question.getBoolean(CONDITIONAL) && !CONDITIONAL_QUESTIONS.contains(question.getInteger(QUESTION_TYPE))) {
                        String message = "[Formulaire@QuestionController::update] A question conditional question must be of type : " + CONDITIONAL_QUESTIONS;
                        log.error(message);
                        badRequest(request, message);
                        return;
                    }
                    i++;
                }

                formService.get(formId, user, formEvt -> {
                    if (formEvt.isLeft()) {
                        log.error("[Formulaire@QuestionController::update] Failed to get form with id : " + formId);
                        renderInternalError(request, formEvt);
                        return;
                    }
                    if (formEvt.right().getValue().isEmpty()) {
                        String message = "[Formulaire@QuestionController::update] No form found for form with id " + formId;
                        log.error(message);
                        notFound(request, message);
                        return;
                    }

                    JsonObject form = formEvt.right().getValue();

                    // Check if the type of question if it's for a public form (type FILE is forbidden)
                    JsonArray questionTypes = getByProp(questions, QUESTION_TYPE);
                    if (form.getBoolean(IS_PUBLIC) && questionTypes.contains(8)) {
                        String message = "[Formulaire@QuestionController::update] You cannot create a question type FILE for the public form with id " + formId;
                        log.error(message);
                        badRequest(request, message);
                        return;
                    }

                    // Check if sections of conditional questions to create already have conditional questions
                    JsonArray questionIds = UtilsHelper.getIds(questions);
                    questionService.getSectionIdsWithConditionalQuestions(formId, questionIds, sectionIdsEvt -> {
                        if (sectionIdsEvt.isLeft()) {
                            log.error("[Formulaire@QuestionController::update] Failed to get section ids for form with id : " + formId);
                            renderInternalError(request, sectionIdsEvt);
                            return;
                        }

                        JsonArray sectionIds = getByProp(sectionIdsEvt.right().getValue(), SECTION_ID);
                        Long conflictingSectionId = null;
                        int j = 0;
                        while (conflictingSectionId == null && j < questions.size()) {
                            JsonObject question = questions.getJsonObject(j);
                            if (question.getBoolean(CONDITIONAL) && sectionIds.contains(question.getLong(SECTION_ID))) {
                                conflictingSectionId = question.getLong(SECTION_ID);
                            }
                            j++;
                        }

                        if (conflictingSectionId != null) {
                            String message = "[Formulaire@QuestionController::update] A conditional question is already existing for the section with id : " + conflictingSectionId;
                            log.error(message);
                            badRequest(request, message);
                            return;
                        }

                        // If no conditional question conflict, we update
                        JsonObject composeInfos = new JsonObject();
                        sectionService.list(formId)
                            .compose(sections -> {
                                JsonArray questionPositions = UtilsHelper.getByProp(questions, POSITION);
                                JsonArray sectionPositions = UtilsHelper.getByProp(sections, POSITION);

                                List<Object> doubles = questionPositions.stream().filter(sectionPositions::contains).collect(Collectors.toList());
                                if (doubles.size() > 0) {
                                    String message = "Position(s) " + doubles + " are/is already used by some question(s).";
                                    return Future.failedFuture(message);
                                }
                                return questionService.update(formId, questions);
                            })
                            .compose(updatedQuestionsInfos -> {
                                JsonArray updatedQuestions = new JsonArray();
                                for (int k = 0; k < updatedQuestionsInfos.size(); k++) {
                                    updatedQuestions.addAll(updatedQuestionsInfos.getJsonArray(k));
                                }
                                composeInfos.put(QUESTIONS, updatedQuestions);

                                return questionSpecificFieldsService.update(questions);
                            })
                            .onSuccess(updatedSpecifics -> {
                                JsonArray updatedQuestionsAndSpecifics = UtilsHelper.mergeQuestionsAndSpecifics(composeInfos.getJsonArray(QUESTIONS), updatedSpecifics);
                                renderJson(request, updatedQuestionsAndSpecifics);
                            })
                            .onFailure(err -> {
                                log.error("[Formulaire@QuestionController::update] Failed to update questions " + questions + " : " + err.getMessage());
                                renderError(request);
                            });
                    });
                });
            });
        });
    }

    @Delete("/questions/:questionId")
    @ApiDoc("Delete a specific question")
    @ResourceFilter(CustomShareAndOwner.class)
    @SecuredAction(value = CONTRIB_RESOURCE_RIGHT, type = ActionType.RESOURCE)
    public void delete(HttpServerRequest request) {
        String questionId = request.getParam(PARAM_QUESTION_ID);
        questionService.get(questionId, questionsEvt -> {
            if (questionsEvt.isLeft()) {
                log.error("[Formulaire@deleteQuestion] Failed to get question with id : " + questionId);
                renderInternalError(request, questionsEvt);
                return;
            }
            questionService.delete(questionsEvt.right().getValue(), defaultResponseHandler(request));
        });
    }
}