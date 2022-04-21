package fr.openent.formulaire.controllers;

import fr.openent.formulaire.Formulaire;
import fr.openent.formulaire.core.ChoiceTypes;
import fr.openent.formulaire.helpers.RenderHelper;
import fr.openent.formulaire.helpers.UtilsHelper;
import fr.openent.formulaire.security.AccessRight;
import fr.openent.formulaire.security.ShareAndOwner;
import fr.openent.formulaire.service.QuestionChoiceService;
import fr.openent.formulaire.service.QuestionService;
import fr.openent.formulaire.service.impl.DefaultQuestionChoiceService;
import fr.openent.formulaire.service.impl.DefaultQuestionService;
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

import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;

public class QuestionChoiceController extends ControllerHelper {
    private static final Logger log = LoggerFactory.getLogger(QuestionChoiceController.class);
    private final QuestionChoiceService questionChoiceService;
    private final QuestionService questionService;

    public QuestionChoiceController() {
        super();
        this.questionChoiceService = new DefaultQuestionChoiceService();
        this.questionService = new DefaultQuestionService();
    }

    @Get("/questions/:questionId/choices")
    @ApiDoc("List all the choices of a specific question")
    @ResourceFilter(AccessRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void list(HttpServerRequest request) {
        String questionId = request.getParam("questionId");
        questionChoiceService.list(questionId, arrayResponseHandler(request));
    }

    @Get("/questions/choices/all")
    @ApiDoc("List all the choices of specific questions")
    @ResourceFilter(AccessRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void listChoices(HttpServerRequest request){
        JsonArray questionIds = new JsonArray();
        for (Integer i = 0; i < request.params().size(); i++) {
            questionIds.add(request.getParam(i.toString()));
        }
        if (questionIds.size() <= 0) {
            log.error("[Formulaire@listChoices] No choiceIds to list.");
            noContent(request);
            return;
        }
        questionChoiceService.listChoices(questionIds, arrayResponseHandler(request));
    }

    @Get("/choices/:choiceId")
    @ApiDoc("Get a specific by id")
    @ResourceFilter(AccessRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void get(HttpServerRequest request) {
        String choiceId = request.getParam("choiceId");
        questionChoiceService.get(choiceId, defaultResponseHandler(request));
    }

    @Post("/questions/:questionId/choices")
    @ApiDoc("Create a choice for a specific question")
    @ResourceFilter(ShareAndOwner.class)
    @SecuredAction(value = Formulaire.CONTRIB_RESOURCE_RIGHT, type = ActionType.RESOURCE)
    public void create(HttpServerRequest request) {
        String questionId = request.getParam("questionId");
        RequestUtils.bodyToJson(request, choice -> {
            if (choice == null || choice.isEmpty()) {
                log.error("[Formulaire@createQuestionChoice] No choice to create.");
                noContent(request);
                return;
            }

            // Check choice type validity
            if (!choice.getString("type").equals(ChoiceTypes.TXT.getName())) {
                String message = "[Formulaire@createQuestionChoice] Invalid choice type : " + choice.getString("type");
                log.error(message);
                badRequest(request, message);
                return;
            }

            Integer nextSectionId = choice.getInteger("next_section_id", null);
            if (nextSectionId != null) {
                questionService.getSectionIdsByForm(questionId, sectionsEvent -> {
                    if (sectionsEvent.isLeft()) {
                        log.error("[Formulaire@createQuestionChoice] Failed to get section for form of the question with id : " + questionId);
                        RenderHelper.internalError(request, sectionsEvent);
                        return;
                    }
                    if (sectionsEvent.right().getValue().isEmpty()) {
                        String message = "[Formulaire@createQuestionChoice] No section found for form of question with id " + questionId;
                        log.error(message);
                        notFound(request, message);
                        return;
                    }

                    // Check if values of next_section_id exist
                    JsonArray sections = sectionsEvent.right().getValue();
                    JsonArray sectionIds = UtilsHelper.getIds(sections, false);
                    if (!sectionIds.contains(nextSectionId)) {
                        String message = "[Formulaire@createQuestionChoice] Wrong value for the next_section_id, this form has no section with id : " + nextSectionId;
                        log.error(message);
                        badRequest(request, message);
                        return;
                    }

                    // Check if targeted section is after the current question
                    questionService.getFormPosition(questionId, positionEvent -> {
                        if (positionEvent.isLeft()) {
                            log.error("[Formulaire@createQuestionChoice] Failed to get form position for question with id : " + questionId);
                            RenderHelper.internalError(request, sectionsEvent);
                            return;
                        }
                        if (positionEvent.right().getValue().isEmpty()) {
                            String message = "[Formulaire@createQuestionChoice] No position found for question with id " + questionId;
                            log.error(message);
                            notFound(request, message);
                            return;
                        }

                        Integer currentQuestionPosition = positionEvent.right().getValue().getInteger("position");
                        JsonObject targetedSection = null;
                        int i = 0;
                        while (targetedSection == null && i < sections.size()) {
                            JsonObject section = sections.getJsonObject(i);
                            if (section.getInteger("id").equals(nextSectionId)) {
                                targetedSection = section;
                            }
                            i++;
                        }

                        if (targetedSection != null && currentQuestionPosition >= targetedSection.getInteger("position")) {
                            String message = "[Formulaire@createQuestionChoice] You cannot target a section placed before your question : " + targetedSection;
                            log.error(message);
                            badRequest(request, message);
                            return;
                        }

                        questionChoiceService.create(questionId, choice, defaultResponseHandler(request));
                    });
                });
            }
            else {
                questionChoiceService.create(questionId, choice, defaultResponseHandler(request));
            }
        });
    }

    @Put("/choices/:choiceId")
    @ApiDoc("Update a specific choice")
    @ResourceFilter(ShareAndOwner.class)
    @SecuredAction(value = Formulaire.CONTRIB_RESOURCE_RIGHT, type = ActionType.RESOURCE)
    public void update(HttpServerRequest request) {
        String choiceId = request.getParam("choiceId");
        RequestUtils.bodyToJson(request, choice -> {
            if (choice == null || choice.isEmpty()) {
                log.error("[Formulaire@updateQuestionChoice] No choice to update.");
                noContent(request);
                return;
            }

            // Check choice type validity
            if (!choice.getString("type").equals(ChoiceTypes.TXT.getName())) {
                String message = "[Formulaire@updateQuestionChoice] Invalid choice type : " + choice.getString("type");
                log.error(message);
                badRequest(request, message);
                return;
            }

            String questionId = choice.getInteger("question_id").toString();
            Integer nextSectionId = choice.getInteger("next_section_id", null);
            if (nextSectionId != null) {
                questionService.getSectionIdsByForm(questionId, sectionsEvent -> {
                    if (sectionsEvent.isLeft()) {
                        log.error("[Formulaire@updateQuestionChoice] Failed to get section for form of the question with id : " + questionId);
                        RenderHelper.internalError(request, sectionsEvent);
                        return;
                    }
                    if (sectionsEvent.right().getValue().isEmpty()) {
                        String message = "[Formulaire@updateQuestionChoice] No sections found for form of the question with id " + questionId;
                        log.error(message);
                        notFound(request, message);
                        return;
                    }

                    // Check if values of next_section_id exist
                    JsonArray sections = sectionsEvent.right().getValue();
                    JsonArray sectionIds = UtilsHelper.getIds(sections, false);
                    if (sections.isEmpty() || !sectionIds.contains(nextSectionId)) {
                        String message = "[Formulaire@updateQuestionChoice] Wrong value for the next_section_id, this form has no section with id : " + nextSectionId;
                        log.error(message);
                        badRequest(request, message);
                        return;
                    }

                    // Check if targeted section is after the current question
                    questionService.getFormPosition(questionId, positionEvent -> {
                        if (positionEvent.isLeft()) {
                            log.error("[Formulaire@updateQuestionChoice] Failed to get form position for question with id : " + questionId);
                            RenderHelper.internalError(request, sectionsEvent);
                            return;
                        }
                        if (positionEvent.right().getValue().isEmpty()) {
                            String message = "[Formulaire@updateQuestionChoice] No position found for question with id " + questionId;
                            log.error(message);
                            notFound(request, message);
                            return;
                        }

                        Integer currentQuestionPosition = positionEvent.right().getValue().getInteger("position");
                        JsonObject targetedSection = null;
                        int i = 0;
                        while (targetedSection == null && i < sections.size()) {
                            JsonObject section = sections.getJsonObject(i);
                            if (section.getInteger("id").equals(nextSectionId)) {
                                targetedSection = section;
                            }
                            i++;
                        }

                        if (targetedSection != null && currentQuestionPosition >= targetedSection.getInteger("position")) {
                            String message = "[Formulaire@updateQuestionChoice] You cannot target a section placed before your question : " + targetedSection;
                            log.error(message);
                            badRequest(request, message);
                            return;
                        }

                        questionChoiceService.update(choiceId, choice, defaultResponseHandler(request));
                    });
                });
            }
            else {
                questionChoiceService.update(choiceId, choice, defaultResponseHandler(request));
            }
        });
    }

    @Delete("/choices/:choiceId")
    @ApiDoc("Delete a specific choice")
    @ResourceFilter(ShareAndOwner.class)
    @SecuredAction(value = Formulaire.CONTRIB_RESOURCE_RIGHT, type = ActionType.RESOURCE)
    public void delete(HttpServerRequest request) {
        String choiceId = request.getParam("choiceId");
        questionChoiceService.delete(choiceId, defaultResponseHandler(request));
    }
}