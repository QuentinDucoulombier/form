package fr.openent.formulaire.controllers;

import fr.openent.formulaire.Formulaire;
import fr.openent.formulaire.helpers.DataChecker;
import fr.openent.formulaire.helpers.RenderHelper;
import fr.openent.formulaire.helpers.FutureHelper;
import fr.openent.formulaire.helpers.UtilsHelper;
import fr.openent.formulaire.security.CreationRight;
import fr.openent.formulaire.service.FolderService;
import fr.openent.formulaire.service.FormService;
import fr.openent.formulaire.service.RelFormFolderService;
import fr.openent.formulaire.service.impl.DefaultFolderService;
import fr.openent.formulaire.service.impl.DefaultFormService;
import fr.openent.formulaire.service.impl.DefaultRelFormFolderService;
import fr.wseduc.rs.*;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.http.response.DefaultResponseHandler;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;

import java.util.ArrayList;
import java.util.List;

import static fr.wseduc.webutils.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;

public class FolderController extends ControllerHelper {
    private static final Logger log = LoggerFactory.getLogger(FormController.class);
    private final FolderService folderService;
    private final FormService formService;
    private final RelFormFolderService relFormFolderService;

    public FolderController() {
        super();
        this.folderService = new DefaultFolderService();
        this.formService = new DefaultFormService();
        this.relFormFolderService = new DefaultRelFormFolderService();
    }

    @Get("/folders")
    @ApiDoc("List all the folders of the connected user")
    @ResourceFilter(CreationRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void list(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                String message = "[Formulaire@listFolders] User not found in session.";
                log.error(message);
                unauthorized(request, message);
                return;
            }
            folderService.list(user, arrayResponseHandler(request));
        });
    }

    @Get("/folders/:folderId")
    @ApiDoc("Get a folder by id")
    @ResourceFilter(CreationRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void get(final HttpServerRequest request) {
        String folderId = request.getParam("folderId");
        folderService.get(folderId, defaultResponseHandler(request));
    }

    @Post("/folders")
    @ApiDoc("Create a folder")
    @ResourceFilter(CreationRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void create(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                String message = "[Formulaire@createFolder] User not found in session.";
                log.error(message);
                unauthorized(request, message);
                return;
            }

            RequestUtils.bodyToJson(request, folder -> {
                if (folder == null || folder.isEmpty()) {
                    log.error("[Formulaire@createFolder] No folder to create.");
                    noContent(request);
                    return;
                }

                // Check if parent id is valid
                Integer parentId = folder.getInteger("parent_id");
                if (parentId == null || parentId == Formulaire.ID_SHARED_FOLDER || parentId == Formulaire.ID_ARCHIVED_FOLDER) {
                    String message = "[Formulaire@createFolder] Wrong parent folder id: " + parentId;
                    log.error(message);
                    badRequest(request, message);
                    return;
                }

                folderService.get(parentId.toString(), folderEvent -> {
                    if (folderEvent.isLeft()) {
                        log.error("[Formulaire@createFolder] Error in getting folder with id " + parentId);
                        RenderHelper.internalError(request, folderEvent);
                        return;
                    }
                    if (folderEvent.right().getValue().isEmpty()) {
                        String message = "[Formulaire@createFolder] No folder found for id " + parentId;
                        log.error(message);
                        notFound(request, message);
                        return;
                    }

                    // Check if parent folder is owned by the user
                    String ownerOfParentFolder = folderEvent.right().getValue().getString("user_id");
                    if (parentId != Formulaire.ID_ROOT_FOLDER && !user.getUserId().equals(ownerOfParentFolder)) {
                        String message = "[Formulaire@createFolder] Your not owner of the folder with id " + parentId;
                        log.error(message);
                        unauthorized(request, message);
                        return;
                    }

                    folderService.create(folder, user, createEvent -> {
                        if (createEvent.isLeft()) {
                            log.error("[Formulaire@createFolder] Error in creating folder " + folder);
                            RenderHelper.internalError(request, createEvent);
                            return;
                        }

                        folderService.syncNbChildren(user, new JsonArray().add(parentId), arrayResponseHandler(request));
                    });
                });
            });
        });
    }

    @Put("/folders/:folderId")
    @ApiDoc("Update a folder")
    @ResourceFilter(CreationRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void update(final HttpServerRequest request) {
        String folderId = request.getParam("folderId");
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                String message = "[Formulaire@updateFolder] User not found in session.";
                log.error(message);
                unauthorized(request, message);
                return;
            }

            RequestUtils.bodyToJson(request, folder -> {
                if (folder == null || folder.isEmpty()) {
                    log.error("[Formulaire@createFolder] No folder to update.");
                    noContent(request);
                    return;
                }

                folderService.get(folderId, folderEvent -> {
                    if (folderEvent.isLeft()) {
                        log.error("[Formulaire@updateFolder] Failed to get folder with id " + folderId);
                        RenderHelper.internalError(request, folderEvent);
                        return;
                    }
                    if (folderEvent.right().getValue().isEmpty()) {
                        String message = "[Formulaire@updateFolder] No folder found for id " + folderId;
                        log.error(message);
                        notFound(request, message);
                        return;
                    }

                    // Check if connected user is owner of the folder
                    JsonObject baseFolder = folderEvent.right().getValue();
                    if (baseFolder.isEmpty() || !baseFolder.getString("user_id").equals(user.getUserId())) {
                        String message = "[Formulaire@updateFolder] Your not owner of the folder with id " + folderId;
                        log.error(message);
                        unauthorized(request, message);
                        return;
                    }

                    // Check if parent id is valid
                    Integer parentId = folder.getInteger("parent_id");
                    if (parentId == null || parentId == Formulaire.ID_SHARED_FOLDER || parentId == Formulaire.ID_ARCHIVED_FOLDER) {
                        String message = "[Formulaire@updateFolder] Wrong parent folder id: " + parentId;
                        log.error(message);
                        badRequest(request, message);
                        return;
                    }

                    folderService.get(parentId.toString(), parentFolderEvent -> {
                        if (parentFolderEvent.isLeft()) {
                            log.error("[Formulaire@updateFolder] Failed to get folder with id " + parentId);
                            RenderHelper.internalError(request, parentFolderEvent);
                            return;
                        }
                        if (parentFolderEvent.right().getValue().isEmpty()) {
                            String message = "[Formulaire@updateFolder] No folder found for id " + parentId;
                            log.error(message);
                            notFound(request, message);
                            return;
                        }

                        // Check if parent folder is owned by the user
                        String ownerOfParentFolder = parentFolderEvent.right().getValue().getString("user_id");
                        if (parentId != Formulaire.ID_ROOT_FOLDER && !user.getUserId().equals(ownerOfParentFolder)) {
                            String message = "[Formulaire@updateFolder] Your not owner of the folder with id " + parentId;
                            log.error(message);
                            unauthorized(request, message);
                            return;
                        }

                        folderService.update(folderId, folder, defaultResponseHandler(request));
                    });
                });
            });
        });
    }

    @Delete("/folders")
    @ApiDoc("Delete multiple folders")
    @ResourceFilter(CreationRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void delete(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                String message = "[Formulaire@deleteFolders] User not found in session.";
                log.error(message);
                unauthorized(request, message);
                return;
            }

            RequestUtils.bodyToJsonArray(request, folderIds -> {
                if (folderIds == null || folderIds.isEmpty()) {
                    log.error("[Formulaire@deleteFolders] No folder to delete.");
                    noContent(request);
                    return;
                }

                // Check if folders to delete include Archive or Share folders
                List<Integer> checker = new ArrayList<Integer>(folderIds.getList());
                checker.retainAll(Formulaire.FORBIDDEN_FOLDER_IDS);
                if (checker.size() > 0) {
                    String message = "[Formulaire@deleteFolders] You cannot delete folders with ids " + folderIds;
                    log.error(message);
                    badRequest(request, message);
                    return;
                }

                folderService.listByIds(folderIds, foldersEvent -> {
                    if (foldersEvent.isLeft()) {
                        log.error("[Formulaire@deleteFolders] Fail to list folders with ids " + folderIds);
                        RenderHelper.internalError(request, foldersEvent);
                        return;
                    }
                    if (foldersEvent.right().getValue().isEmpty()) {
                        String message = "[Formulaire@deleteFolders] No folders found for ids " + folderIds;
                        log.error(message);
                        notFound(request, message);
                        return;
                    }

                    // Check if one of the folders is not owned by the connected user
                    JsonArray folders = foldersEvent.right().getValue();
                    boolean areUserIdsOk = DataChecker.checkFolderIdsValidity(folders, user.getUserId());
                    if (!areUserIdsOk) {
                        String message = "[Formulaire@deleteFolders] Delete a folder not owned is forbidden.";
                        log.error(message);
                        unauthorized(request, message);
                        return;
                    }

                    // Get all form ids to archive
                    relFormFolderService.listFormChildrenRecursively(folderIds, childrenEvent -> {
                        if (childrenEvent.isLeft()) {
                            log.error("[Formulaire@deleteFolders] Error in listing children forms for folders " + folderIds);
                            RenderHelper.internalError(request, childrenEvent);
                            return;
                        }
                        if (childrenEvent.right().getValue().isEmpty()) {
                            String message = "[Formulaire@deleteFolders] No children forms found for folder with ids " + folderIds;
                            log.error(message);
                            notFound(request, message);
                            return;
                        }

                        // Change status all children forms to "archived"
                        JsonArray forms = childrenEvent.right().getValue();
                        List<Future> syncFutures = new ArrayList<>();
                        for (int j = 0; j < forms.size(); j++) {
                            JsonObject form = forms.getJsonObject(j);
                            String formId = form.getInteger("id").toString();
                            form.remove("archived");
                            form.put("archived", true);
                            Promise<JsonObject> promise = Promise.promise();
                            syncFutures.add(promise.future());
                            formService.update(formId, form, FutureHelper.handlerJsonObject(promise));
                        }

                        CompositeFuture.all(syncFutures).onComplete(evt -> {
                            if (evt.failed()) {
                                String message = "[Formulaire@deleteFolders] Failed to sync number children of folders : " + evt.cause().getMessage();
                                log.error(message);
                                RenderHelper.internalError(request, message);
                                return;
                            }

                            // Change all children forms relation folder (replace with "3")
                            JsonArray formIds = UtilsHelper.getIds(forms);
                            Integer parentId = folders.getJsonObject(0).getInteger("parent_id");

                            if (formIds.size() > 0) {
                                relFormFolderService.update(user, formIds, Formulaire.ID_ARCHIVED_FOLDER, updateRelEvent -> {
                                    if (updateRelEvent.isLeft()) {
                                        log.error("[Formulaire@deleteFolders] Error in updating relation form-folder for forms with ids " + formIds);
                                        RenderHelper.internalError(request, updateRelEvent);
                                        return;
                                    }
                                    deleteFolders(request, user, parentId, folderIds);
                                });
                            }
                            else {
                                deleteFolders(request, user, parentId, folderIds);
                            }
                        });
                    });
                });
            });
        });
    }

    @Put("/folders/:folderId/move")
    @ApiDoc("Move several folders to a same new parent")
    @ResourceFilter(CreationRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void move(HttpServerRequest request) {
        Integer targetFolderId = Integer.parseInt(request.getParam("folderId"));

        // Check if target folder is Archive or Share folders
        if (targetFolderId == Formulaire.ID_SHARED_FOLDER || targetFolderId == Formulaire.ID_ARCHIVED_FOLDER) {
            String message = "[Formulaire@moveFolders] You cannot move folders into the folder with id : " + targetFolderId;
            log.error(message);
            badRequest(request, message);
            return;
        }

        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                String message = "[Formulaire@moveFolders] User not found in session.";
                log.error(message);
                unauthorized(request, message);
                return;
            }

            RequestUtils.bodyToJsonArray(request, folderIds -> {
                if (folderIds == null || folderIds.isEmpty()) {
                    log.error("[Formulaire@moveFolders] No folders to move.");
                    noContent(request);
                    return;
                }

                folderService.get(targetFolderId.toString(), folderEvent -> {
                    if (folderEvent.isLeft()) {
                        log.error("[Formulaire@moveFolders] Fail to get folder with id : " + targetFolderId);
                        RenderHelper.internalError(request, folderEvent);
                        return;
                    }
                    if (folderEvent.right().getValue().isEmpty()) {
                        String message = "[Formulaire@deleteFolders] No folder found for id " + targetFolderId;
                        log.error(message);
                        notFound(request, message);
                        return;
                    }

                    // Check if targeted folder is owned by the connected user
                    if (!folderEvent.right().getValue().getString("user_id").equals(user.getUserId())) {
                        String message = "[Formulaire@moveFolders] You cannot move folders into a folder you don't own : " + targetFolderId;
                        log.error(message);
                        badRequest(request, message);
                        return;
                    }

                    folderService.listByIds(folderIds, foldersEvent -> {
                        if (foldersEvent.isLeft()) {
                            log.error("[Formulaire@moveFolders] Fail to list folders with ids : " + folderIds);
                            RenderHelper.internalError(request, foldersEvent);
                            return;
                        }
                        if (foldersEvent.right().getValue().isEmpty()) {
                            String message = "[Formulaire@deleteFolders] No folders found for ids " + folderIds;
                            log.error(message);
                            notFound(request, message);
                            return;
                        }

                        // Check if one of the folders is not owned by the connected user
                        JsonArray folders = foldersEvent.right().getValue();
                        boolean areUserIdsOk = DataChecker.checkFolderIdsValidity(folders, user.getUserId());
                        if (!areUserIdsOk) {
                            String message = "[Formulaire@moveFolder] Move a folder not owned is forbidden.";
                            log.error(message);
                            unauthorized(request, message);
                            return;
                        }

                        folderService.move(folderIds, targetFolderId.toString(), moveEvent -> {
                            if (moveEvent.isLeft()) {
                                log.error("[Formulaire@moveFolder] Error in moving folders with ids : " + folderIds);
                                RenderHelper.internalError(request, moveEvent);
                                return;
                            }

                            // Get the folders to sync
                            JsonArray folderIdsToSync = new JsonArray().add(targetFolderId);
                            for (int j = 0; j < folderIds.size(); j++) {
                                if (folderIds.getInteger(j) != Formulaire.ID_ROOT_FOLDER) {
                                    folderIdsToSync.add(folderIds.getInteger(j));
                                }
                            }

                            // Sync the number of children form and folders
                            if (folderIdsToSync.size() > 0) {
                                folderService.syncNbChildren(user, folderIdsToSync, DefaultResponseHandler.arrayResponseHandler(request));
                            }
                            else {
                                RenderHelper.ok(request);
                            }
                        });
                    });
                });
            });
        });
    }

    private void deleteFolders(HttpServerRequest request, UserInfos user, Integer parentId, JsonArray folderIds) {
        // Delete folders (and their children by cascade effect)
        folderService.delete(folderIds, deleteEvent -> {
            if (deleteEvent.isLeft()) {
                log.error("[Formulaire@deleteFolders] Error in deleting folders with ids " + folderIds);
                RenderHelper.internalError(request, deleteEvent);
                return;
            }

            // Sync parent folder children counts
            if (parentId != Formulaire.ID_ROOT_FOLDER) { // We do not sync root folder counts (useless)
                folderService.syncNbChildren(user, new JsonArray().add(parentId), arrayResponseHandler(request));
            }
            else {
                RenderHelper.ok(request);
            }
        });
    }
}
