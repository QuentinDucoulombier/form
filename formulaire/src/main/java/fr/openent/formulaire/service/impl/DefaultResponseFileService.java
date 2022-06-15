package fr.openent.formulaire.service.impl;

import fr.openent.form.core.constants.Tables;
import fr.openent.formulaire.service.ResponseFileService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;

public class DefaultResponseFileService implements ResponseFileService {

    @Override
    public void list(String responseId, Handler<Either<String, JsonArray>> handler) {
        String query = "SELECT * FROM " + Tables.RESPONSE_FILE + " WHERE response_id = ?;";
        JsonArray params = new JsonArray().add(responseId);
        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
    }

    @Override
    public void listByQuestion(String questionId, Handler<Either<String, JsonArray>> handler) {
        String query = "SELECT rf.*, d.date_response, d.responder_name FROM " + Tables.RESPONSE_FILE + " rf " +
                "INNER JOIN " + Tables.RESPONSE + " r ON r.id = rf.response_id " +
                "INNER JOIN " + Tables.DISTRIBUTION + " d ON d.id = r.distribution_id " +
                "WHERE r.question_id = ?" +
                "ORDER BY rf.response_id;";
        JsonArray params = new JsonArray().add(questionId);
        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
    }

    @Override
    public void listByForm(String formId, Handler<Either<String, JsonArray>> handler) {
        String query = "SELECT rf.id FROM " + Tables.RESPONSE_FILE + " rf " +
                "INNER JOIN " + Tables.RESPONSE + " r ON r.id = rf.response_id " +
                "INNER JOIN " + Tables.QUESTION + " q ON q.id = r.question_id " +
                "WHERE q.form_id = ?;";
        JsonArray params = new JsonArray().add(formId);
        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
    }

    @Override
    public void get(String fileId, Handler<Either<String, JsonObject>> handler) {
        String query = "SELECT * FROM " + Tables.RESPONSE_FILE + " WHERE id = ?;";
        JsonArray params = new JsonArray().add(fileId);
        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void create(String responseId, String fileId, String filename, String type, Handler<Either<String, JsonObject>> handler) {
        String query = "INSERT INTO " + Tables.RESPONSE_FILE + " (id, response_id, filename, type) VALUES (?, ?, ?, ?);";
        JsonArray params = new JsonArray().add(fileId).add(responseId).add(filename).add(type);
        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void deleteAllByResponse(JsonArray responseIds, Handler<Either<String, JsonArray>> handler) {
        String query = "DELETE FROM " + Tables.RESPONSE_FILE +
                " WHERE response_id IN " + Sql.listPrepared(responseIds) + " RETURNING id;";
        JsonArray params = new JsonArray().addAll(responseIds);
        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
    }

    @Override
    public void deleteAll(JsonArray fileIds, Handler<Either<String, JsonArray>> handler) {
        String query = "DELETE FROM " + Tables.RESPONSE_FILE +
                " WHERE id IN " + Sql.listPrepared(fileIds) + " RETURNING id;";
        JsonArray params = new JsonArray().addAll(fileIds);
        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
    }
}