package fr.openent.formulaire.service.impl;

import fr.openent.formulaire.Formulaire;
import fr.openent.formulaire.service.FolderService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;
import org.entcore.common.user.UserInfos;

public class DefaultFolderService implements FolderService {
    @Override
    public void list(UserInfos user, Handler<Either<String, JsonArray>> handler) {
        String query = "SELECT * FROM " + Formulaire.FOLDER_TABLE + " WHERE user_id = ?;";
        JsonArray params = new JsonArray().add(user.getUserId());

        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
    }

    @Override
    public void listByIds(JsonArray folderIds, Handler<Either<String, JsonArray>> handler) {
        String query = "SELECT * FROM " + Formulaire.FOLDER_TABLE + " WHERE id IN " + Sql.listPrepared(folderIds);
        JsonArray params = new JsonArray().addAll(folderIds);

        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
    }

    @Override
    public void get(String folderId, Handler<Either<String, JsonObject>> handler) {
        String query = "SELECT * FROM " + Formulaire.FOLDER_TABLE + " WHERE id = ?;";
        JsonArray params = new JsonArray().add(folderId);

        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void create(JsonObject folder, UserInfos user, Handler<Either<String, JsonObject>> handler) {
        String query = "INSERT INTO " + Formulaire.FOLDER_TABLE + " (parent_id, name, user_id) VALUES (?, ?, ?) RETURNING *;";
        JsonArray params = new JsonArray()
                .add(folder.getInteger("parent_id",1))
                .add(folder.getString("name"))
                .add(user.getUserId());

        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void update(String folderId, JsonObject folder, Handler<Either<String, JsonObject>> handler) {
        String query = "UPDATE " + Formulaire.FOLDER_TABLE + " SET parent_id = ?, name = ? WHERE id = ? RETURNING *;";
        JsonArray params = new JsonArray()
                .add(folder.getInteger("parent_id",1))
                .add(folder.getString("name"))
                .add(folderId);

        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void delete(JsonArray folderIds, Handler<Either<String, JsonArray>> handler) {
        String query = "DELETE FROM " + Formulaire.FOLDER_TABLE + " WHERE id IN " + Sql.listPrepared(folderIds) +
                " RETURNING parent_id";
        JsonArray params = new JsonArray().addAll(folderIds);

        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
    }

    @Override
    public void move(JsonArray folderIds, String parentId, Handler<Either<String, JsonArray>> handler) {
        String query = "UPDATE " + Formulaire.FOLDER_TABLE + " SET parent_id = ? WHERE id IN " + Sql.listPrepared(folderIds);
        JsonArray params = new JsonArray().add(parentId).addAll(folderIds);

        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
    }

    @Override
    public void syncNbChildren(UserInfos user, JsonArray newFolderIds, Handler<Either<String, JsonArray>> handler) {
        String query = "UPDATE " + Formulaire.FOLDER_TABLE + " folder " +
                "SET nb_folder_children = CASE WHEN counts.nb_folders IS NULL THEN 0 ELSE counts.nb_folders END, " +
                "nb_form_children = CASE WHEN counts.nb_forms IS NULL THEN 0 ELSE counts.nb_forms END " +
                "FROM ( " +
                    "SELECT * FROM ( " +
                        "SELECT COUNT(*) AS nb_folders, parent_id FROM " + Formulaire.FOLDER_TABLE +
                        " WHERE user_id = ? GROUP BY parent_id " +
                    ") AS f " +
                    "FULL JOIN ( " +
                        "SELECT COUNT(*) AS nb_forms, folder_id FROM " + Formulaire.REL_FORM_FOLDER_TABLE +
                        " WHERE user_id = ? GROUP BY folder_id " +
                    ") AS rff ON rff.folder_id = f.parent_id " +
                ") AS counts " +
                "WHERE folder.id IN " + Sql.listPrepared(newFolderIds) +
                " AND folder.id = counts.parent_id OR folder.id = counts.folder_id;";
        JsonArray params = new JsonArray().add(user.getUserId()).add(user.getUserId()).addAll(newFolderIds);

        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
    }
}
