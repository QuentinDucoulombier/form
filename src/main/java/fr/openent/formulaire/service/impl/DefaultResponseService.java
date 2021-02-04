package fr.openent.formulaire.service.impl;

import fr.openent.formulaire.Formulaire;
import fr.openent.formulaire.service.ResponseService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;
import org.entcore.common.user.UserInfos;

public class DefaultResponseService implements ResponseService {

    @Override
    public void list(String question_id, Handler<Either<String, JsonArray>> handler) {
        String query = "SELECT * FROM " + Formulaire.RESPONSE_TABLE + " WHERE question_id = ? ORDER BY created;";
        JsonArray params = new JsonArray().add(question_id);
        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
    }

    @Override
    public void get(String question_id, UserInfos user, Handler<Either<String, JsonObject>> handler) {
        String query = "SELECT * FROM " + Formulaire.RESPONSE_TABLE + " WHERE question_id = ? AND responder_id = ?;";
        JsonArray params = new JsonArray().add(question_id).add(user.getUserId());
        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void create(JsonObject response, UserInfos user, String question_id, Handler<Either<String, JsonObject>> handler) {
        String query = "INSERT INTO " + Formulaire.RESPONSE_TABLE + " (question_id, answer, responder_id) " +
                "VALUES (?, ?, ?) RETURNING *;";
        JsonArray params = new JsonArray()
                .add(question_id)
                .add(response.getString("answer", ""))
                .add(user.getUserId());

        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void update(UserInfos user, String id, JsonObject response, Handler<Either<String, JsonObject>> handler) {
        String query = "UPDATE " + Formulaire.RESPONSE_TABLE + " SET answer = ? WHERE responder_id = ? AND id = ?;";
        JsonArray params = new JsonArray()
                .add(response.getString("answer", ""))
                .add(user.getUserId())
                .add(id);

        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void delete(String id, Handler<Either<String, JsonObject>> handler) {
        String query = "DELETE FROM " + Formulaire.RESPONSE_TABLE + " WHERE id = ?;";
        JsonArray params = new JsonArray().add(id);
        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void exportResponses(String formId, Handler<Either<String, JsonArray>> handler) {
        String query = "SELECT d.form_id, d.responder_id, d.date_response, rep.position, rep.answer " +
            "FROM " + Formulaire.DISTRIBUTION_TABLE + " d JOIN " +
            "( " +
                "SELECT form_id, responder_id, q.id as question_id, title, answer, q.position as position " +
                "FROM " + Formulaire.QUESTION_TABLE + " q " +
                "JOIN " + Formulaire.RESPONSE_TABLE + " r ON r.question_id = q.id " +
                "WHERE form_id = ? " +
            ") rep ON d.form_id = rep.form_id AND d.responder_id = rep.responder_id " +
            "WHERE d.status = '" + Formulaire.FINISHED + "' " +
            "ORDER BY d.form_id, d.responder_id, rep.position;";

        JsonArray params = new JsonArray().add(formId);
        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
    }
}