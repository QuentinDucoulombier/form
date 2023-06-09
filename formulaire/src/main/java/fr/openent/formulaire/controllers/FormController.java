package fr.openent.formulaire.controllers;

import fr.openent.form.core.enums.RgpdLifetimes;
import fr.openent.form.helpers.EventBusHelper;
import fr.openent.form.helpers.UtilsHelper;
import fr.openent.formulaire.export.FormQuestionsExportPDF;
import fr.openent.formulaire.helpers.DataChecker;
import fr.openent.form.helpers.FutureHelper;
import fr.openent.formulaire.security.*;
import fr.openent.formulaire.service.*;
import fr.openent.formulaire.service.impl.*;
import fr.wseduc.rs.*;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.*;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.events.EventStore;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.storage.Storage;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;

import java.util.*;
import java.util.stream.Collectors;

import static fr.openent.form.core.constants.EbFields.*;
import static fr.openent.form.core.constants.EbFields.ACTION;
import static fr.openent.form.core.constants.Tables.DB_SCHEMA;
import static fr.openent.form.core.enums.Events.CREATE;
import static fr.openent.form.core.constants.ConfigFields.ZIMBRA_MAX_RECIPIENTS;
import static fr.openent.form.core.constants.ConsoleRights.*;
import static fr.openent.form.core.constants.Constants.*;
import static fr.openent.form.core.constants.Fields.*;
import static fr.openent.form.core.constants.FolderIds.*;
import static fr.openent.form.core.constants.ShareRights.*;
import static fr.openent.form.helpers.RenderHelper.renderInternalError;
import static fr.openent.form.helpers.UtilsHelper.*;
import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;

public class FormController extends ControllerHelper {
    private static final Logger log = LoggerFactory.getLogger(FormController.class);
    private final EventStore eventStore;
    private final Storage storage;
    private final FormService formService;
    private final DistributionService distributionService;
    private final QuestionChoiceService questionChoiceService;
    private final ResponseService responseService;
    private final ResponseFileService responseFileService;
    private final FolderService folderService;
    private final RelFormFolderService relFormFolderService;

    public FormController(EventStore eventStore, Storage storage) {
        super();
        this.eventStore = eventStore;
        this.storage = storage;
        this.formService = new DefaultFormService();
        this.distributionService = new DefaultDistributionService();
        this.questionChoiceService = new DefaultQuestionChoiceService();
        this.responseService = new DefaultResponseService();
        this.responseFileService = new DefaultResponseFileService();
        this.folderService = new DefaultFolderService();
        this.relFormFolderService = new DefaultRelFormFolderService();
    }

    // Init classic rights

    @SecuredAction(CREATION_RIGHT)
    public void initCreationRight(final HttpServerRequest request) {
    }

    @SecuredAction(RESPONSE_RIGHT)
    public void initResponseRight(final HttpServerRequest request) {
    }

    @SecuredAction(CREATION_PUBLIC_RIGHT)
    public void initCreationPublicRight(final HttpServerRequest request) {
    }

    // Init sharing rights

    @SecuredAction(value = CONTRIB_RESOURCE_RIGHT, type = ActionType.RESOURCE)
    public void initContribResourceRight(final HttpServerRequest request) {
    }

    @SecuredAction(value = MANAGER_RESOURCE_RIGHT, type = ActionType.RESOURCE)
    public void initManagerResourceRight(final HttpServerRequest request) {
    }

    @SecuredAction(value = RESPONDER_RESOURCE_RIGHT, type = ActionType.RESOURCE)
    public void initResponderResourceRight(final HttpServerRequest request) {
    }

    // API

    @Get("/forms")
    @ApiDoc("List all the forms created by me or shared with me")
    @ResourceFilter(CreationRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void list(HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                String message = "[Formulaire@listForms] User not found in session.";
                log.error(message);
                unauthorized(request, message);
                return;
            }

            final List<String> groupsAndUserIds = new ArrayList<>();
            groupsAndUserIds.add(user.getUserId());
            if (user.getGroupsIds() != null) {
                groupsAndUserIds.addAll(user.getGroupsIds());
            }
            formService.list(groupsAndUserIds, user, arrayResponseHandler(request));
        });
    }

    @Get("/forms/sent")
    @ApiDoc("List all the forms sent to me")
    @ResourceFilter(ResponseRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void listSentForms(HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                String message = "[Formulaire@listSentForms] User not found in session.";
                log.error(message);
                unauthorized(request, message);
                return;
            }
            formService.listSentForms(user, arrayResponseHandler(request));
        });
    }

    @Get("/forms/linker")
    @ApiDoc("List all the forms for the linker")
    @ResourceFilter(AccessRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void listForLinker(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                String message = "[Formulaire@listForLinker] User not found in session.";
                log.error(message);
                unauthorized(request, message);
                return;
            }

            final List<String> groupsAndUserIds = new ArrayList<>();
            groupsAndUserIds.add(user.getUserId());
            if (user.getGroupsIds() != null) {
                groupsAndUserIds.addAll(user.getGroupsIds());
            }

            formService.listForLinker(groupsAndUserIds, user)
                .onSuccess(result -> renderJson(request, result))
                .onFailure(err -> {
                    log.error(err.getMessage());
                    renderError(request);
                });
        });
    }

    @Get("/forms/:formId")
    @ApiDoc("Get a specific form by id")
    @ResourceFilter(CustomShareAndOwner.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void get(HttpServerRequest request) {
        String formId = request.getParam(PARAM_FORM_ID);
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                String message = "[Formulaire@getForm] User not found in session.";
                log.error(message);
                unauthorized(request, message);
                return;
            }

            formService.get(formId, user, defaultResponseHandler(request));
        });
    }

    @Post("/forms")
    @ApiDoc("Create a form")
    @ResourceFilter(CreationRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void create(HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                String message = "[Formulaire@createForm] User not found in session.";
                log.error(message);
                unauthorized(request, message);
                return;
            }

            RequestUtils.bodyToJson(request, form -> {
                if (form == null || form.isEmpty()) {
                    log.error("[Formulaire@createForm] No form to create.");
                    noContent(request);
                    return;
                }

                // Check if the user has right to create a public form
                boolean isFormPublic = form.getBoolean(IS_PUBLIC);
                if (isFormPublic && !WorkflowActionUtils.hasRight(user, WorkflowActions.CREATION_RIGHT.toString())) {
                    String message = "[Formulaire@createForm] You are not authorized to create a public form.";
                    log.error(message);
                    unauthorized(request, message);
                    return;
                }

                // date_ending should be after date_opening if not null
                boolean areDateValid = DataChecker.checkFormDatesValidity(form);
                if (!areDateValid) {
                    String message = "[Formulaire@createForm] You cannot create a form with a ending date before the opening date.";
                    log.error(message);
                    badRequest(request, message);
                    return;
                }

                // RGPD lifetime should be in [3, 6, 9, 12]
                boolean isRGPDLifetimeOk = DataChecker.checkRGPDLifetimeValidity(new JsonArray().add(form));
                if (!isRGPDLifetimeOk) {
                    String message = "[Formulaire@createForm] Wrong RGPD lifetime value : " + form.getInteger(RGPD_LIFETIME);
                    log.error(message);
                    badRequest(request, message);
                    return;
                }

                // Check if parent folder is Archive or Share folders
                Integer folderId = form.getInteger(FOLDER_ID) != null ? form.getInteger(FOLDER_ID) : ID_ROOT_FOLDER;
                if (folderId == ID_SHARED_FOLDER || folderId == ID_ARCHIVED_FOLDER) {
                    String message = "[Formulaire@createForm] You cannot create a folder into the folder with id : " + folderId;
                    log.error(message);
                    badRequest(request, message);
                    return;
                }

                folderService.get(folderId.toString(), folderEvt -> {
                    if (folderEvt.isLeft()) {
                        log.error("[Formulaire@createForm] Fail to get folder for id " + folderId);
                        renderInternalError(request, folderEvt);
                        return;
                    }
                    if (folderEvt.right().getValue().isEmpty()) {
                        String message = "[Formulaire@createForm] No folder found for id " + folderId;
                        log.error(message);
                        notFound(request, message);
                        return;
                    }

                    // Check if the folder is owned by the connected user (if custom folder)
                    JsonObject folder = folderEvt.right().getValue();
                    if (folderId != ID_ROOT_FOLDER && !folder.getString(USER_ID).equals(user.getUserId())) {
                        String message = "[Formulaire@createForm] Your not owner of the folder with id " + folderId;
                        log.error(message);
                        unauthorized(request, message);
                        return;
                    }

                    formService.create(form, user, createEvt -> {
                        if (createEvt.isLeft() || createEvt.right().getValue().isEmpty()) {
                            log.error("[Formulaire@createForm] Failed to create form : " + form);
                            renderInternalError(request, createEvt);
                            return;
                        }

                        eventStore.createAndStoreEvent(CREATE.name(), request);
                        String formId = createEvt.right().getValue().getInteger(ID).toString();
                        relFormFolderService.create(user, new JsonArray().add(formId), folderId, createRelEvt -> {
                            if (createRelEvt.isLeft() || createEvt.right().getValue().isEmpty()) {
                                log.error("[Formulaire@createForm] Failed to create relation form-folder for form : " + form);
                                renderInternalError(request, createRelEvt);
                                return;
                            }

                            if (folderId != ID_ROOT_FOLDER) { // We do not sync root folder counts (useless)
                                folderService.syncNbChildren(user, new JsonArray().add(folderId), syncEvt -> {
                                    if (syncEvt.isLeft()) {
                                        log.error("[Formulaire@moveForm] Error in sync children counts for folder " + folderId);
                                        renderInternalError(request, syncEvt);
                                        return;
                                    }
                                    renderJson(request, createEvt.right().getValue());
                                });
                            }
                            else {
                                renderJson(request, createEvt.right().getValue());
                            }
                        });
                    });
                });
            });
        });
    }

    @Post("/forms/multiple")
    @ApiDoc("Create several forms")
    @ResourceFilter(CreationRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void createMultiple(HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                String message = "[Formulaire@createMultipleForm] User not found in session.";
                log.error(message);
                unauthorized(request, message);
                return;
            }

            RequestUtils.bodyToJsonArray(request, forms -> {
                if (forms == null || forms.isEmpty()) {
                    log.error("[Formulaire@createMultipleForm] No forms to create.");
                    noContent(request);
                    return;
                }

                // Check if the user has right to create a public form
                if (DataChecker.hasPublicForm(forms) && !WorkflowActionUtils.hasRight(user, WorkflowActions.CREATION_RIGHT.toString())) {
                    String message = "[Formulaire@createMultipleForm] You are not authorized to create a public form.";
                    log.error(message);
                    unauthorized(request, message);
                    return;
                }

                // date_ending should be after date_ending if not null
                boolean areDateValid = DataChecker.checkFormDatesValidity(forms);
                if (!areDateValid) {
                    String message = "[Formulaire@createMultipleForm] You cannot create a form with an ending date before the opening date.";
                    log.error(message);
                    badRequest(request, message);
                    return;
                }

                // RGPD lifetime should be in [3, 6, 9, 12]
                boolean isRGPDLifetimeOk = DataChecker.checkRGPDLifetimeValidity(forms);
                if (!isRGPDLifetimeOk) {
                    String message = "[Formulaire@createMultipleForm] A RGPD lifetime value should be in " + RgpdLifetimes.getAllValues();
                    log.error(message);
                    badRequest(request, message);
                    return;
                }

                JsonArray folderIds = getByProp(forms, FOLDER_ID);
                folderService.listByIds(folderIds, foldersEvt -> {
                    if (foldersEvt.isLeft()) {
                        log.error("[Formulaire@createMultipleForm] Fail to list folders for ids " + folderIds);
                        renderInternalError(request, foldersEvt);
                        return;
                    }
                    if (foldersEvt.right().getValue().isEmpty()) {
                        String message = "[Formulaire@createMultipleForm] No folders found for ids " + folderIds;
                        log.error(message);
                        notFound(request, message);
                        return;
                    }

                    // Check if one of the folders is not owned by the connected user
                    JsonArray folders = foldersEvt.right().getValue();
                    boolean areUserIdsOk = DataChecker.checkFolderIdsValidity(folders, user.getUserId());
                    if (!areUserIdsOk) {
                        String message = "[Formulaire@createMultipleForm] Your not owner of one of the folders with ids " + folderIds;
                        log.error(message);
                        unauthorized(request, message);
                        return;
                    }

                    formService.createMultiple(forms, user, formsEvt -> {
                        if (formsEvt.isLeft()) {
                            log.error("[Formulaire@createMultipleForm] Failed to create forms : " + forms);
                            renderInternalError(request, formsEvt);
                            return;
                        }
                        eventStore.createAndStoreEvent(CREATE.name(), request);

                        // Get result of creation
                        JsonArray createdFormsInfos = formsEvt.right().getValue();
                        JsonArray createdForms = new JsonArray();
                        for (int k = 0; k < createdFormsInfos.size(); k++) {
                            createdForms.addAll(createdFormsInfos.getJsonArray(k));
                        }

                        JsonArray formIds = getIds(createdForms, false);
                        JsonArray finalFolderIds = getByProp(forms, FOLDER_ID);
                        relFormFolderService.createMultiple(user, formIds, finalFolderIds, createRelEvt -> {
                            if (createRelEvt.isLeft()) {
                                log.error("[Formulaire@createMultipleForm] Failed to create relations form-folder for forms : " + formIds);
                                renderInternalError(request, createRelEvt);
                                return;
                            }

                            JsonArray folderIdsToSync = new JsonArray();
                            for (int j = 0; j < finalFolderIds.size(); j++) {
                                if (finalFolderIds.getInteger(j) != ID_ROOT_FOLDER) {
                                    folderIdsToSync.add(finalFolderIds.getInteger(j));
                                }
                            }

                            if (folderIdsToSync.size() > 0) {
                                folderService.syncNbChildren(user, folderIdsToSync, syncEvt -> {
                                    if (syncEvt.isLeft()) {
                                        log.error("[Formulaire@createMultipleForm] Error in sync children counts for folders with ids " + folderIdsToSync);
                                        renderInternalError(request, syncEvt);
                                        return;
                                    }
                                    renderJson(request, createdForms);
                                });
                            }
                            else {
                                renderJson(request, createdForms);
                            }
                        });
                    });
                });
            });
        });
    }

    @Post("/forms/duplicate/:folderId")
    @ApiDoc("Duplicate several forms and put them into a specific folder")
    @ResourceFilter(CreationRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void duplicate(HttpServerRequest request) {
        Integer folderId = Integer.parseInt(request.getParam(PARAM_FOLDER_ID));
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                String message = "[Formulaire@duplicateForms] User not found in session.";
                log.error(message);
                unauthorized(request, message);
                return;
            }

            RequestUtils.bodyToJsonArray(request, formIds -> {
                if (formIds == null || formIds.isEmpty()) {
                    log.error("[Formulaire@duplicateForms] No forms to duplicate.");
                    noContent(request);
                    return;
                }

                final List<String> groupsAndUserIds = new ArrayList<>();
                groupsAndUserIds.add(user.getUserId());
                if (user.getGroupsIds() != null) {
                    groupsAndUserIds.addAll(user.getGroupsIds());
                }

                formService.checkFormsRights(groupsAndUserIds, user, CONTRIB_RESOURCE_BEHAVIOUR, formIds, hasRightsEvt -> {
                    if (hasRightsEvt.isLeft()) {
                        log.error("[Formulaire@duplicateForms] Fail to check rights for method " + hasRightsEvt);
                        renderInternalError(request, hasRightsEvt);
                        return;
                    }
                    if (hasRightsEvt.right().getValue().isEmpty()) {
                        String message = "[Formulaire@duplicateForms] No rights found for forms with ids " + formIds;
                        log.error(message);
                        notFound(request, message);
                        return;
                    }

                    // Check if user is owner or contributor to all the forms
                    Long count = hasRightsEvt.right().getValue().getLong(COUNT);
                    if (count == null || count != formIds.size()) {
                        String message = "[Formulaire@duplicateForms] You're missing rights on one form or more.";
                        log.error(message);
                        unauthorized(request, message);
                        return;
                    }

                    folderService.get(folderId.toString(), folderEvt -> {
                        if (folderEvt.isLeft()) {
                            log.error("[Formulaire@duplicateForms] Fail to get folder for id " + folderId);
                            renderInternalError(request, folderEvt);
                            return;
                        }
                        if (folderEvt.right().getValue().isEmpty()) {
                            String message = "[Formulaire@duplicateForms] No folder found for id " + folderId;
                            log.error(message);
                            notFound(request, message);
                            return;
                        }

                        // Check if the folder is not owned by the connected user
                        JsonObject folder = folderEvt.right().getValue();
                        if (folderId != ID_ROOT_FOLDER && !folder.getString(USER_ID).equals(user.getUserId())) {
                            String message = "[Formulaire@duplicateForms] You're not owner of the folder with id " + folderId;
                            log.error(message);
                            unauthorized(request, message);
                            return;
                        }

                        List<Future> formsInfos = new ArrayList<>();
                        // Duplicates form and all questions inside
                        duplicatesFormsQuestions(request, folderId, user, formIds, formsInfos);
                    });
                });
            });
        });
    }

    private void duplicatesFormsQuestions(HttpServerRequest request, Integer folderId, UserInfos user, JsonArray formIds, List<Future> formsInfos) {
        for (int i = 0; i < formIds.size(); i++) {
            Promise<JsonArray> promise = Promise.promise();
            formsInfos.add(promise.future());
            formService.duplicate(formIds.getInteger(i), user, I18n.acceptLanguage(request), FutureHelper.handlerEither(promise));
        }
        CompositeFuture.all(formsInfos).onComplete(formsInfosEvt -> {
            if (formsInfosEvt.failed()) {
                log.error("[Formulaire@duplicateForms] Failed to retrieve info for forms with ids " + formIds);
                renderInternalError(request, formsInfosEvt);
                return;
            }
            // Duplicates all potential question choices
            duplicatesQuestionChoices(request, folderId, user, formIds, formsInfosEvt);
        });
    }

    private void duplicatesQuestionChoices(HttpServerRequest request, Integer folderId, UserInfos user, JsonArray formIds, AsyncResult<CompositeFuture> questionsEvt) {
        List<Future> questionsInfosFutures = new ArrayList<>();

        for (Object questions : questionsEvt.result().list()) {
            JsonArray questionsInfos = ((JsonArray) questions).getJsonArray(1);
            if (questionsInfos.size() > 0
                && questionsInfos.getJsonObject(0).getInteger(ID) != null
                && questionsInfos.getJsonObject(0).getInteger(FORM_ID) != null) {
                for (int i = 0; i < questionsInfos.size(); i++) {
                    JsonObject questionInfo = questionsInfos.getJsonObject(i);
                    int formId = questionInfo.getInteger(FORM_ID);
                    int questionId = questionInfo.getInteger(ID);
                    int originalQuestionId = questionInfo.getInteger(ORIGINAL_QUESTION_ID);
                    int question_type = questionInfo.getInteger(QUESTION_TYPE);
                    if (CHOICES_TYPE_QUESTIONS.contains(question_type)) {
                        Promise<JsonObject> promise = Promise.promise();
                        questionsInfosFutures.add(promise.future());
                        questionChoiceService.duplicate(formId, questionId, originalQuestionId, FutureHelper.handlerEither(promise));
                    }
                }
            }
        }
        CompositeFuture.all(questionsInfosFutures).onComplete(choicesEvt -> {
            if (choicesEvt.failed()) {
                log.error("[Formulaire@duplicateForms] Failed to duplicate choices for questions of forms with ids " + formIds);
                renderInternalError(request, choicesEvt);
                return;
            }
            // Sync folders with this new forms
            syncFoldersForms(request, folderId, user, formIds, questionsEvt);
        });
    }

    private void syncFoldersForms(HttpServerRequest request, Integer folderId, UserInfos user, JsonArray formIds, AsyncResult<CompositeFuture> questionsEvt) {
        eventStore.createAndStoreEvent(CREATE.name(), request);
        JsonArray newFormIds = new JsonArray();
        for (Object question : questionsEvt.result().list()) {
            newFormIds.add(((JsonArray) question).getJsonArray(1).getJsonObject(0).getInteger(FORM_ID));
        }
        relFormFolderService.create(user, newFormIds, folderId, createRelEvt -> {
            if (createRelEvt.isLeft()) {
                log.error("[Formulaire@duplicateForms] Error in moving forms with ids " + formIds);
                renderInternalError(request, createRelEvt);
                return;
            }

            if (folderId != ID_ROOT_FOLDER) { // We do not sync root folder counts (useless)
                folderService.syncNbChildren(user, new JsonArray().add(folderId), arrayResponseHandler(request));
            }
            else {
                renderJson(request, createRelEvt.right().getValue());
            }
        });
    }

    @Put("/forms/:formId")
    @ApiDoc("Update a specific form")
    @ResourceFilter(CustomShareAndOwner.class)
    @SecuredAction(value = CONTRIB_RESOURCE_RIGHT, type = ActionType.RESOURCE)
    public void update(HttpServerRequest request) {
        String formId = request.getParam(PARAM_FORM_ID);
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                String message = "[Formulaire@updateForm] User not found in session.";
                log.error(message);
                unauthorized(request, message);
                return;
            }

            RequestUtils.bodyToJson(request, form -> {
                if (form == null || form.isEmpty()) {
                    log.error("[Formulaire@updateForm] No form to update.");
                    noContent(request);
                    return;
                }

                // Check if the user has right to update a public form
                boolean isFormPublic = form.getBoolean(IS_PUBLIC);
                if (isFormPublic && !WorkflowActionUtils.hasRight(user, WorkflowActions.CREATION_RIGHT.toString())) {
                    String message = "[Formulaire@updateForm] You are not authorized to create a public form.";
                    log.error(message);
                    unauthorized(request, message);
                    return;
                }

                // date_ending should be after date_ending if not null
                boolean areDateValid = DataChecker.checkFormDatesValidity(form);
                if (!areDateValid) {
                    String message = "[Formulaire@updateForm] You cannot update a form with an ending date before the opening date.";
                    log.error(message);
                    badRequest(request, message);
                    return;
                }

                // RGPD lifetime should be in [3, 6, 9, 12]
                boolean isRGPDLifetimeOk = DataChecker.checkRGPDLifetimeValidity(new JsonArray().add(form));
                if (!isRGPDLifetimeOk) {
                    String message = "[Formulaire@updateForm] Wrong RGPD lifetime value : " + form.getInteger(RGPD_LIFETIME);
                    log.error(message);
                    badRequest(request, message);
                    return;
                }

                // Check if some properties can be changed (unable if there are already responses)
                responseService.listByForm(formId, responsesEvt -> {
                    if (responsesEvt.isLeft()) {
                        log.error("[Formulaire@updateForm] Fail to get responses for form with id " + formId);
                        renderInternalError(request, responsesEvt);
                        return;
                    }

                    if (!responsesEvt.right().getValue().isEmpty()) {
                        formService.get(formId, user, formEvt -> {
                            if (formEvt.isLeft()) {
                                log.error("[Formulaire@updateForm] Fail to get form for id " + formId);
                                renderInternalError(request, formEvt);
                                return;
                            }
                            if (formEvt.right().getValue().isEmpty()) {
                                String message = "[Formulaire@updateForm] No form found for id " + formId;
                                log.error(message);
                                notFound(request, message);
                                return;
                            }

                            // Reset props ''multiple', 'anonymous' and 'rgpd' to their current values
                            JsonObject formRef = formEvt.right().getValue();
                            form.put(MULTIPLE, formRef.getBoolean(MULTIPLE));
                            form.put(ANONYMOUS, formRef.getBoolean(ANONYMOUS));
                            form.put(RGPD, formRef.getBoolean(RGPD));

                            formService.update(formId, form, defaultResponseHandler(request));
                        });
                    }
                    else {
                        formService.update(formId, form, defaultResponseHandler(request));
                    }
                });
            });
        });
    }

    @Delete("/forms/:formId")
    @ApiDoc("Delete a specific form")
    @ResourceFilter(CustomShareAndOwner.class)
    @SecuredAction(value = MANAGER_RESOURCE_RIGHT, type = ActionType.RESOURCE)
    public void delete(HttpServerRequest request) {
        String formId = request.getParam(PARAM_FORM_ID);
        responseFileService.listByForm(formId, responseFileIdsEvt -> {
            if (responseFileIdsEvt.isLeft()) {
                log.error("[Formulaire@deleteForm] Failed to retrieve files' ids for form : " + formId);
                renderInternalError(request, responseFileIdsEvt);
                return;
            }

            JsonArray responseFileIds = responseFileIdsEvt.right().getValue();
            if (responseFileIds != null && responseFileIds.size() > 0) {
                JsonArray fileIds = getStringIds(responseFileIdsEvt.right().getValue());

                ResponseFileController.deleteFiles(storage, fileIds, deleteFilesEvt -> {
                    if (deleteFilesEvt.isLeft()) {
                        log.error("[Formulaire@deleteForm] Fail to delete files in storage");
                        renderInternalError(request, deleteFilesEvt);
                        return;
                    }

                    responseFileService.deleteAll(fileIds, deleteResponseFilesEvt -> {
                        if (deleteResponseFilesEvt.isLeft()) {
                            log.error("[Formulaire@deleteForm] Fail to delete response files in BDD");
                            renderInternalError(request, deleteResponseFilesEvt);
                            return;
                        }

                        formService.delete(formId, defaultResponseHandler(request));
                    });
                });
            }
            else {
                formService.delete(formId, defaultResponseHandler(request));
            }
        });
    }

    @Put("/forms/move/:folderId")
    @ApiDoc("Move specific forms to a specific folder")
    @ResourceFilter(CreationRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void move(HttpServerRequest request) {
        Integer targetFolderId = Integer.parseInt(request.getParam(PARAM_FOLDER_ID));
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                String message = "[Formulaire@moveForms] User not found in session.";
                log.error(message);
                unauthorized(request, message);
                return;
            }

            RequestUtils.bodyToJsonArray(request, formIds -> {
                if (formIds == null || formIds.isEmpty()) {
                    log.error("[Formulaire@moveForms] No forms to move.");
                    noContent(request);
                    return;
                }

                final List<String> groupsAndUserIds = new ArrayList<>();
                groupsAndUserIds.add(user.getUserId());
                if (user.getGroupsIds() != null) {
                    groupsAndUserIds.addAll(user.getGroupsIds());
                }

                formService.checkFormsRights(groupsAndUserIds, user, CONTRIB_RESOURCE_BEHAVIOUR, formIds, hasRightsEvt -> {
                    if (hasRightsEvt.isLeft()) {
                        log.error("[Formulaire@moveForms] Fail to check rights for method : " + hasRightsEvt.left().getValue());
                        renderInternalError(request, hasRightsEvt);
                        return;
                    }
                    if (hasRightsEvt.right().getValue().isEmpty()) {
                        String message = "[Formulaire@moveForms] No rights found for forms with ids " + formIds;
                        log.error(message);
                        notFound(request, message);
                        return;
                    }

                    // Check if user is owner or contributor to all the forms
                    Long count = hasRightsEvt.right().getValue().getLong(COUNT);
                    if (count == null || count != formIds.size()) {
                        String message = "[Formulaire@moveForms] You're missing rights on one form or more.";
                        log.error(message);
                        unauthorized(request, message);
                        return;
                    }

                    relFormFolderService.listMineByFormIds(user, formIds, relFormFolderEvt -> {
                        if (relFormFolderEvt.isLeft()) {
                            log.error("[Formulaire@moveForms] Failed to get relation form-folders for forms with id " + formIds);
                            renderInternalError(request, relFormFolderEvt);
                            return;
                        }

                        // Check case if form shared with me and doesn't have rel_form_folder
                        if (relFormFolderEvt.right().getValue().isEmpty() && targetFolderId == ID_ARCHIVED_FOLDER) {
                            relFormFolderService.create(user, formIds, targetFolderId, arrayResponseHandler(request));
                            return;
                        }
                        else if (relFormFolderEvt.right().getValue().isEmpty()) {
                            String message = "[Formulaire@moveForms] No relation form-folders found for forms with ids " + formIds;
                            log.error(message);
                            notFound(request, message);
                            return;
                        }

                        // Check if one of the folders is not owned by the connected user
                        JsonArray relFormFolders = relFormFolderEvt.right().getValue();
                        List<Integer> checker = new ArrayList<Integer>(UtilsHelper.getByProp(relFormFolders, FOLDER_ID).getList());
                        checker.retainAll(BASE_FOLDER_IDS);
                        if (relFormFolders.size() != (formIds.size() - checker.size())) {
                            String message = "[Formulaire@moveForms] You're not owner of all the folders containing forms with ids " + formIds;
                            log.error(message);
                            unauthorized(request, message);
                            return;
                        }

                        folderService.get(targetFolderId.toString(), targetedFolderEvt -> {
                            if (targetedFolderEvt.isLeft()) {
                                log.error("[Formulaire@moveForms] Failed to get folder with id " + targetFolderId);
                                renderInternalError(request, targetedFolderEvt);
                                return;
                            }
                            if (targetedFolderEvt.right().getValue().isEmpty()) {
                                String message = "[Formulaire@moveForms] No folder found with id " + targetFolderId;
                                log.error(message);
                                notFound(request, message);
                                return;
                            }

                            // Check if targetFolderId is not owned by the connected user
                            String folderOwner = targetedFolderEvt.right().getValue().getString(USER_ID);
                            if (!BASE_FOLDER_IDS.contains(targetFolderId) &&
                                (folderOwner == null || !user.getUserId().equals(folderOwner))) {
                                String message = "[Formulaire@moveForms] You're not owner of the targeted folder with id " + targetFolderId;
                                log.error(message);
                                unauthorized(request, message);
                                return;
                            }

                            relFormFolderService.update(user, formIds, targetFolderId, updateEvt -> {
                                if (updateEvt.isLeft()) {
                                    log.error("[Formulaire@moveForms] Error in moving forms with ids " + formIds);
                                    renderInternalError(request, updateEvt);
                                    return;
                                }

                                JsonArray folderIds = getByProp(relFormFolders, FOLDER_ID).add(targetFolderId);
                                JsonArray folderIdsToSync = new JsonArray();
                                for (int j = 0; j < folderIds.size(); j++) {
                                    if (folderIds.getInteger(j) != ID_ROOT_FOLDER) {
                                        folderIdsToSync.add(folderIds.getInteger(j));
                                    }
                                }

                                if (folderIdsToSync.size() > 0) {
                                    folderService.syncNbChildren(user, folderIdsToSync, arrayResponseHandler(request));
                                }
                                else {
                                    renderJson(request, updateEvt.right().getValue());
                                }
                            });
                        });
                    });
                });
            });
        });
    }

    @Put("/forms/restore")
    @ApiDoc("Restore specific forms to a specific folder")
    @ResourceFilter(CreationRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void restore(HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                String message = "[Formulaire@restoreForms] User not found in session.";
                log.error(message);
                unauthorized(request, message);
                return;
            }

            RequestUtils.bodyToJsonArray(request, formIds -> {
                if (formIds == null || formIds.isEmpty()) {
                    log.error("[Formulaire@restoreForms] No forms to move.");
                    noContent(request);
                    return;
                }

                final List<String> groupsAndUserIds = new ArrayList<>();
                groupsAndUserIds.add(user.getUserId());
                if (user.getGroupsIds() != null) {
                    groupsAndUserIds.addAll(user.getGroupsIds());
                }

                formService.checkFormsRights(groupsAndUserIds, user, CONTRIB_RESOURCE_BEHAVIOUR, formIds, hasRightsEvt -> {
                    if (hasRightsEvt.isLeft()) {
                        log.error("[Formulaire@restoreForms] Fail to check rights for method : " + hasRightsEvt.left().getValue());
                        renderInternalError(request, hasRightsEvt);
                        return;
                    }
                    if (hasRightsEvt.right().getValue().isEmpty()) {
                        String message = "[Formulaire@restoreForms] No rights found for forms with ids " + formIds;
                        log.error(message);
                        notFound(request, message);
                        return;
                    }

                    // Check if user is owner or contributor to all the forms
                    Long count = hasRightsEvt.right().getValue().getLong(COUNT);
                    if (count == null || count != formIds.size()) {
                        String message = "[Formulaire@restoreForms] You're missing rights on one form or more.";
                        log.error(message);
                        unauthorized(request, message);
                        return;
                    }

                    relFormFolderService.updateForRestoration(formIds, relFormFolderEvt -> {
                        if (relFormFolderEvt.isLeft()) {
                            log.error("[Formulaire@restoreForms] Failed to update relation form-folders for forms with id " + formIds + " : " + relFormFolderEvt.left().getValue());
                            renderInternalError(request, relFormFolderEvt);
                            return;
                        }

                        formService.listByIds(formIds, formsEvt -> {
                            if (formsEvt.isLeft()) {
                                log.error("[Formulaire@restoreForms] Failed to list forms with ids " + formIds + " : " + formsEvt.left().getValue());
                                renderInternalError(request, formsEvt);
                                return;
                            }

                            JsonArray forms = formsEvt.right().getValue();
                            for (int i = 0; i < forms.size(); i++) {
                                forms.getJsonObject(i).put(ARCHIVED, false);
                            }

                            formService.updateMultiple(forms, arrayResponseHandler(request));
                        });
                    });
                });
            });
        });
    }

    @Post("/forms/:formId/remind")
    @ApiDoc("Send a reminder by mail to all the necessary responders")
    @ResourceFilter(CustomShareAndOwner.class)
    @SecuredAction(value = CONTRIB_RESOURCE_RIGHT, type = ActionType.RESOURCE)
    public void sendReminder(HttpServerRequest request) {
        String formId = request.getParam(PARAM_FORM_ID);
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                String message = "[Formulaire@FormController::sendReminder] User not found in session.";
                log.error(message);
                unauthorized(request);
                return;
            }

            RequestUtils.bodyToJson(request, mail -> {
                if (mail == null || mail.isEmpty()) {
                    log.error("[Formulaire@FormController::sendReminder] No mail to send.");
                    noContent(request);
                    return;
                }

                formService.get(formId, user, formEvt -> {
                    if (formEvt.isLeft()) {
                        log.error("[Formulaire@FormController::sendReminder] Fail to get form " + formId + " : " + formEvt.left().getValue());
                        renderInternalError(request, formEvt);
                        return;
                    }
                    if (formEvt.right().getValue().isEmpty()) {
                        String message = "[Formulaire@FormController::sendReminder] No form found for id " + formId;
                        log.error(message);
                        notFound(request);
                        return;
                    }

                    JsonObject form = formEvt.right().getValue();
                    distributionService.listByForm(formId, distributionsEvt -> {
                        if (distributionsEvt.isLeft()) {
                            log.error("[Formulaire@FormController::sendReminder] Fail to retrieve distributions for form with id : " + formId);
                            renderInternalError(request, distributionsEvt);
                            return;
                        }
                        if (distributionsEvt.right().getValue().isEmpty()) {
                            String message = "[Formulaire@FormController::sendReminder] No distributions found for form with id " + formId;
                            log.error(message);
                            notFound(request);
                            return;
                        }

                        JsonArray distributions = distributionsEvt.right().getValue();

                        // If form prop is not consistent with number of distributions
                        if (!form.getBoolean(SENT)) {
                            form.put(SENT, true);
                            formService.update(formId, form, updatedFormEvt -> {
                                if (formEvt.isLeft()) {
                                    log.error("[Formulaire@FormController::sendReminder] Fail to update form " + formId + " : " + formEvt.left().getValue());
                                    renderInternalError(request, formEvt);
                                    return;
                                }

                                doSendReminder(request, formId, form, distributions, mail, user);
                            });
                        }
                        else {
                            doSendReminder(request, formId, form, distributions, mail, user);
                        }
                    });
                });
            });
        });
    }

    private void doSendReminder(HttpServerRequest request, String formId, JsonObject form, JsonArray distributions, JsonObject mail, UserInfos user) {
        JsonArray listMails = new JsonArray();
        List<JsonObject> distributionsList = new ArrayList<>(distributions.getList());

        // Generate list of mails to send
        for (int i = 0; i < distributions.size(); i++) {
            List<String> localRespondersIds = distributionsList.stream()
                .filter(d -> (form.getBoolean(MULTIPLE) || form.getBoolean(ANONYMOUS) || d.getString(DATE_RESPONSE) == null))
                .map(d -> d.getString(RESPONDER_ID))
                .collect(Collectors.toList());

            // Generate new mail object if limit or end loop are reached
            if (i == distributions.size() - 1 || localRespondersIds.size() == config.getInteger(ZIMBRA_MAX_RECIPIENTS, 50)) {
                JsonObject message = new JsonObject()
                        .put(SUBJECT, mail.getString(SUBJECT, ""))
                        .put(BODY, mail.getString(BODY, ""))
                        .put(TO, new JsonArray())
                        .put(CCI, localRespondersIds);

                JsonObject action = new JsonObject()
                        .put(ACTION, SEND)
                        .put(PARAM_USER_ID, user.getUserId())
                        .put(USERNAME, user.getUsername())
                        .put(MESSAGE, message);

                listMails.add(action);
            }
        }


        // Prepare futures to get message responses
        List<Future> mails = new ArrayList<>();

        // Code to send mails
        for (int i = 0; i < listMails.size(); i++) {
            Future future = Promise.promise().future();
            mails.add(future);

            // Send mail via Conversation app if it exists or else with Zimbra
            eb.request(CONVERSATION_ADDRESS, listMails.getJsonObject(i), (Handler<AsyncResult<Message<JsonObject>>>) messageEvt -> {
                if (!messageEvt.result().body().getString(STATUS).equals(OK)) {
                    log.error("[Formulaire@FormController::sendReminder] Failed to send reminder : " + messageEvt.cause());
                    future.handle(Future.failedFuture(messageEvt.cause()));
                }
                else {
                    future.handle(Future.succeededFuture(messageEvt.result().body()));
                }
            });
        }

        // Try to send effectively mails with code below and get results
        CompositeFuture.all(mails).onComplete(sendMailsEvt -> {
            if (sendMailsEvt.failed()) {
                log.error("[Formulaire@FormController::sendReminder] Failed to send reminder : " + sendMailsEvt.cause());
                renderInternalError(request, sendMailsEvt);
                return;
            }

            // Update 'reminded' prop of the form
            form.put(REMINDED, true);
            formService.update(formId, form, updateEvt -> {
                if (updateEvt.isLeft()) {
                    log.error("[Formulaire@FormController::sendReminder] Fail to update form " + formId + " : " + updateEvt.left().getValue());
                    renderInternalError(request, updateEvt);
                    return;
                }
                renderJson(request, updateEvt.right().getValue());
            });
        });
    }

    @Get("/forms/:formId/rights")
    @ApiDoc("Get my rights for a specific form")
    @ResourceFilter(AccessRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void getMyFormRights(HttpServerRequest request) {
        String formId = request.getParam(PARAM_FORM_ID);
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                String message = "[Formulaire@getMyFormRights] User not found in session.";
                log.error(message);
                unauthorized(request, message);
                return;
            }

            List<String> groupsAndUserIds = new ArrayList();
            groupsAndUserIds.add(user.getUserId());
            if (user.getGroupsIds() != null) {
                groupsAndUserIds.addAll(user.getGroupsIds());
            }
            formService.getMyFormRights(formId, groupsAndUserIds, arrayResponseHandler(request));
        });
    }

    @Get("/forms/rights/all")
    @ApiDoc("Get my rights for all the forms")
    @ResourceFilter(AccessRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void getAllMyFormRights(HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                String message = "[Formulaire@getAllMyFormRights] User not found in session.";
                log.error(message);
                unauthorized(request, message);
                return;
            }

            List<String> groupsAndUserIds = new ArrayList();
            groupsAndUserIds.add(user.getUserId());
            if (user.getGroupsIds() != null) {
                groupsAndUserIds.addAll(user.getGroupsIds());
            }
            formService.getAllMyFormRights(groupsAndUserIds, arrayResponseHandler(request));
        });
    }

    // Export / Import

    @Post("/forms/export/:fileType")
    @ApiDoc("Export forms in a file (ZIP or PDF)")
    @ResourceFilter(CreationRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void exportForm(final HttpServerRequest request) {
        String fileType = request.getParam(PARAM_FILE_TYPE);
        RequestUtils.bodyToJsonArray(request, formIds -> {
            UserUtils.getUserInfos(eb, request, user -> {
                if (user == null) {
                    String message = "[Formulaire@exportForms] User not found in session.";
                    log.error(message);
                    unauthorized(request, message);
                    return;
                }

                final List<String> groupsAndUserIds = new ArrayList<>();
                groupsAndUserIds.add(user.getUserId());
                if (user.getGroupsIds() != null) {
                    groupsAndUserIds.addAll(user.getGroupsIds());
                }

                formService.checkFormsRights(groupsAndUserIds, user, MANAGER_RESOURCE_BEHAVIOUR, formIds, hasRightsEvt -> {
                    if (hasRightsEvt.isLeft()) {
                        log.error("[Formulaire@exportForms] Fail to check rights for method " + hasRightsEvt);
                        renderInternalError(request, hasRightsEvt);
                        return;
                    }
                    if (hasRightsEvt.right().getValue().isEmpty()) {
                        String message = "[Formulaire@exportForms] No rights found for forms with ids " + formIds;
                        log.error(message);
                        notFound(request, message);
                        return;
                    }

                    // Check if user is owner or manager to all the forms
                    Long count = hasRightsEvt.right().getValue().getLong(COUNT);
                    if (count == null || count != formIds.size()) {
                        String message = "[Formulaire@exportForms] You're missing rights on one form or more.";
                        log.error(message);
                        unauthorized(request, message);
                        return;
                    }

                    switch (fileType) {
                        case ZIP:
                            // Create the directory in the file system
                            JsonObject ebMessage = new JsonObject()
                                    .put(ACTION, START)
                                    .put(PARAM_USER_ID, user.getUserId())
                                    .put(LOCALE, I18n.acceptLanguage(request))
                                    .put(APPS, new JsonArray().add(DB_SCHEMA))
                                    .put(PARAM_RESOURCES_IDS, new JsonArray().addAll(formIds));

                            EventBusHelper.requestJsonObject(EXPORT_ADDRESS, eb, ebMessage)
                                    .onSuccess(res -> renderJson(request, res))
                                    .onFailure(err -> {
                                        String message = "[Formulaire@FormController::exportForm] Failed to export data : " + err.getMessage();
                                        log.error(message);
                                        renderInternalError(request, message);
                                    });
                            break;
                        case PDF:
                            formService.get(String.valueOf(formIds.getInteger(0)), user)
                                    .onSuccess(form -> {
                                        if(form.isEmpty()){
                                            String errMessage = "[Formulaire@exportForm] No form found for id " + formIds;
                                            log.error(errMessage);
                                            notFound(request, errMessage);
                                        } else {
                                            new FormQuestionsExportPDF(request, vertx, config, storage, form).launch();
                                        }
                                    })
                                    .onFailure(err -> {
                                        log.error("[Formulaire@exportForm] Error in getting form to export questions of form " + formIds);
                                        renderInternalError(request, err.toString());
                                    });
                            break;
                        default:
                            String message = "[Formulaire@exportForms] Wrong export format type : " + fileType;
                            log.error(message);
                            badRequest(request, message);
                            break;
                    }


                });
            });
        });
    }
}