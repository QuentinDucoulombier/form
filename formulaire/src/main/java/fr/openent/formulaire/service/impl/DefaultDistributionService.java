package fr.openent.formulaire.service.impl;

import fr.openent.form.helpers.FutureHelper;
import fr.openent.formulaire.service.DistributionService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;
import org.entcore.common.sql.SqlStatementsBuilder;
import org.entcore.common.user.UserInfos;

import java.util.ArrayList;
import java.util.List;

import static fr.openent.form.core.constants.Constants.*;
import static fr.openent.form.core.constants.DistributionStatus.*;
import static fr.openent.form.core.constants.Fields.*;
import static fr.openent.form.core.constants.Tables.*;

public class DefaultDistributionService implements DistributionService {
    private static final Logger log = LoggerFactory.getLogger(DefaultDistributionService.class);
    private final Sql sql = Sql.getInstance();

    @Override
    public void listBySender(UserInfos user, Handler<Either<String, JsonArray>> handler) {
        String query = "SELECT * FROM " + DISTRIBUTION_TABLE + " WHERE sender_id = ? " +
                "ORDER BY date_sending DESC;";
        JsonArray params = new JsonArray().add(user.getUserId());
        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
    }

    @Override
    public void listByResponder(UserInfos user, Handler<Either<String, JsonArray>> handler) {
        String query = "SELECT * FROM " + DISTRIBUTION_TABLE + " WHERE responder_id = ? AND active = ? " +
                "ORDER BY date_sending DESC;";
        JsonArray params = new JsonArray().add(user.getUserId()).add(true);
        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
    }

    @Override
    public void listByForm(String formId, Handler<Either<String, JsonArray>> handler) {
        String query = "SELECT * FROM " + DISTRIBUTION_TABLE + " WHERE form_id = ? AND active = ? " +
                "ORDER BY date_sending DESC;";
        JsonArray params = new JsonArray().add(formId).add(true);
        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
    }

    @Override
    public Future<JsonArray> listByForms(JsonArray formIds) {
        Promise<JsonArray> promise = Promise.promise();

        if (formIds == null || formIds.size() <= 0) {
            String errorMessage = "[Formulaire@DefaultDistributionService::listByForms] formIds is null or empty";
            log.warn(errorMessage);
            return Future.succeededFuture(new JsonArray());
        }

        String query = "SELECT * FROM " + DISTRIBUTION_TABLE + " WHERE form_id IN " + Sql.listPrepared(formIds) + ";";
        JsonArray params = new JsonArray().addAll(formIds);

        String errorMessage = "[Formulaire@DefaultDistributionService::listByForms] " +
                "Failed to list distributions with form ids in " + formIds;
        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(FutureHelper.handlerEither(promise, errorMessage)));

        return promise.future();
    }

    public void listByFormAndResponder(String formId, UserInfos user, Handler<Either<String, JsonArray>> handler) {
        String query = "SELECT * FROM " + DISTRIBUTION_TABLE +
                " WHERE form_id = ? AND responder_id = ? AND active = ? " +
                "ORDER BY date_sending DESC;";
        JsonArray params = new JsonArray().add(formId).add(user.getUserId()).add(true);
        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
    }

    @Override
    public void listByFormAndStatus(String formId, String status, String nbLines, Handler<Either<String, JsonArray>> handler) {
        String query = "SELECT * FROM " + DISTRIBUTION_TABLE + " WHERE form_id = ? AND status = ? " +
                "ORDER BY date_response DESC";
        JsonArray params = new JsonArray().add(formId).add(status);

        if (nbLines != null && !nbLines.equals(NULL)) {
            query += " LIMIT ? OFFSET ?";
            params.add(NB_NEW_LINES).add(nbLines);
        }

        query += ";";
        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
    }

    @Override
    public void listByFormAndStatusAndQuestion(String formId, String status, String questionId, String nbLines, Handler<Either<String, JsonArray>> handler) {
        String query = "SELECT DISTINCT d.* FROM " + DISTRIBUTION_TABLE + " d " +
                "JOIN " + RESPONSE_TABLE + " r ON r.distribution_id = d.id " +
                "WHERE form_id = ? AND status = ? AND question_id = ? " +
                "ORDER BY date_response DESC";
        JsonArray params = new JsonArray().add(formId).add(status).add(questionId);

        if (nbLines != null && !nbLines.equals(NULL)) {
            query += " LIMIT ? OFFSET ?";
            params.add(NB_NEW_LINES).add(nbLines);
        }

        query += ";";
        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
    }

    @Override
    public void countFinished(String formId, Handler<Either<String, JsonObject>> handler) {
        String query = "SELECT COUNT(*) FROM " + DISTRIBUTION_TABLE + " WHERE form_id = ? AND status = ?;";
        JsonArray params = new JsonArray().add(formId).add(FINISHED);
        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public Future<JsonObject> countMyToDo(String formId, UserInfos user) {
        return countMineByStatus(formId, user, TO_DO);
    }

    @Override
    public Future<JsonObject> countMyFinished(String formId, UserInfos user) {
        return countMineByStatus(formId, user, FINISHED);
    }

    private Future<JsonObject> countMineByStatus(String formId, UserInfos user, String status) {
        Promise<JsonObject> promise = Promise.promise();

        String query = "SELECT COUNT(*) FROM " + DISTRIBUTION_TABLE + " WHERE form_id = ? AND responder_id = ? AND status = ?;";
        JsonArray params = new JsonArray().add(formId).add(user.getUserId()).add(status);

        String errorMessage = "[Formulaire@DefaultDistributionService::countMineByStatus] " +
                "Failed to count distribution with status " + status + " for user : " + user.getUserId();
        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(FutureHelper.handlerEither(promise, errorMessage)));

        return promise.future();
    }

    @Override
    public void get(String distributionId, Handler<Either<String, JsonObject>> handler) {
        String query = "SELECT * FROM " + DISTRIBUTION_TABLE + " WHERE id = ?;";
        JsonArray params = new JsonArray().add(distributionId);
        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void getByFormResponderAndStatus(String formId, UserInfos user, Handler<Either<String, JsonObject>> handler) {
        String query = "SELECT * FROM " + DISTRIBUTION_TABLE +
                " WHERE form_id = ? AND responder_id = ? AND status = ?;";
        JsonArray params = new JsonArray().add(formId).add(user.getUserId())
                .add(TO_DO);
        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public Future<JsonObject> add(JsonObject distribution) {
        Promise<JsonObject> promise = Promise.promise();

        String query = "INSERT INTO " + DISTRIBUTION_TABLE + " (form_id, sender_id, sender_name, " +
                "responder_id, responder_name, status, date_sending, active) " +
                " VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING *;";
        JsonArray params = new JsonArray()
                .add(distribution.getInteger(FORM_ID, null))
                .add(distribution.getString(SENDER_ID, ""))
                .add(distribution.getString(SENDER_NAME, ""))
                .add(distribution.getString(RESPONDER_ID, ""))
                .add(distribution.getString(RESPONDER_NAME, ""))
                .add(TO_DO)
                .add("NOW()")
                .add(true);

        String errorMessage = "[Formulaire@addDistribution] Failed to add distribution " + distribution;
        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(FutureHelper.handlerEither(promise, errorMessage)));

        return promise.future();
    }

    @Override
    public Future<JsonObject> duplicateWithResponses(String distributionId) {
        Promise<JsonObject> promise = Promise.promise();
        String query =
                "WITH newDistrib AS (" +
                    "INSERT INTO " + DISTRIBUTION_TABLE + " (form_id, sender_id, sender_name, " +
                    "responder_id, responder_name, status, date_sending, date_response, active, original_id) " +
                    "SELECT form_id, sender_id, sender_name, responder_id, responder_name, ?, " +
                    "date_sending, date_response, active, id FROM " + DISTRIBUTION_TABLE + " WHERE id = ? RETURNING *" +
                "), " +
                "newResponses AS (" +
                    "INSERT INTO " + RESPONSE_TABLE + " (question_id, answer, responder_id, choice_id, distribution_id, original_id, choice_position, custom_answer) " +
                    "SELECT question_id, answer, responder_id, choice_id, (SELECT id FROM newDistrib), id, choice_position, custom_answer " +
                    "FROM " + RESPONSE_TABLE + " WHERE distribution_id = ? RETURNING *" +
                ")," +
                "newResponseFiles AS (" +
                    "INSERT INTO " + RESPONSE_FILE_TABLE + " (id, response_id, filename, type) " +
                    "SELECT id, (SELECT id FROM newResponses WHERE original_id = response_id), filename, type " +
                    "FROM " + RESPONSE_FILE_TABLE + " WHERE response_id IN (SELECT original_id FROM newResponses)" +
                ")" +
                "SELECT * FROM newDistrib;";

        JsonArray params = new JsonArray().add(ON_CHANGE).add(distributionId).add(distributionId);
        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(FutureHelper.handlerEither(promise)));
        return promise.future();
    }

    @Override
    public void update(String distributionId, JsonObject distribution, Handler<Either<String, JsonObject>> handler) {
        String query = "UPDATE " + DISTRIBUTION_TABLE + " SET status = ?, structure = ?";
        JsonArray params = new JsonArray()
                .add(distribution.getString(STATUS, TO_DO))
                .add(distribution.getString(STRUCTURE, null));

        if (distribution.getString(STATUS).equals(FINISHED)) {
            query += ", date_response = ?";
            params.add("NOW()");
        }
        else if (distribution.getString(DATE_RESPONSE, null) != null) {
            query += ", date_response = ?";
            params.add(distribution.getString(DATE_RESPONSE));
        }

        query += " WHERE id = ? RETURNING *;";
        params.add(distributionId);

        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void delete(String distributionId, Handler<Either<String, JsonObject>> handler) {
        String query = "DELETE FROM " + DISTRIBUTION_TABLE + " WHERE id = ?;";
        JsonArray params = new JsonArray().add(distributionId);
        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(handler));
    }


    // Sync distributions functions (when form is sent/unsent/shared/unshared)

    @Override
    public void getResponders(String formId, Handler<Either<String, JsonArray>> handler) {
        String query = "SELECT responder_id AS id, active FROM " + DISTRIBUTION_TABLE + " WHERE form_id = ?;";
        JsonArray params = new JsonArray().add(formId);

        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
    }

    @Override
    public void createMultiple(String formId, UserInfos user, List<JsonObject> responders, Handler<Either<String, JsonObject>> handler) {
        ArrayList<JsonObject> respondersArray = new ArrayList<>();
        ArrayList<String> respondersArrayIds = new ArrayList<>();
        for (JsonObject responder : responders) {
            if (responder != null && !responder.isEmpty()) {
                if (!respondersArrayIds.contains(responder.getString(ID))) {
                    respondersArray.add(responder);
                    respondersArrayIds.add(responder.getString(ID));
                }
            }
        }

        if (!respondersArray.isEmpty()) {
            SqlStatementsBuilder s = new SqlStatementsBuilder();
            String query = "INSERT INTO " + DISTRIBUTION_TABLE + " (form_id, sender_id, sender_name, responder_id, " +
                    "responder_name, status, date_sending, active) VALUES (?, ?, ?, ?, ?, ?, ?, ?);";

            s.raw(TRANSACTION_BEGIN_QUERY);
            for (JsonObject responder : respondersArray) {
                JsonArray params = new JsonArray()
                        .add(formId)
                        .add(user.getUserId())
                        .add(user.getUsername())
                        .add(responder.getString(ID, ""))
                        .add(responder.getString(USERNAME, ""))
                        .add(TO_DO)
                        .add("NOW()")
                        .add(true);
                s.prepared(query, params);
            }
            s.raw(TRANSACTION_COMMIT_QUERY);

            sql.transaction(s.build(), SqlResult.validUniqueResultHandler(handler));
        }
        else {
            handler.handle(new Either.Right<>(new JsonObject()));
        }
    }

    @Override
    public void setActiveValue(boolean active, String formId, List<String> responder_ids, Handler<Either<String, JsonObject>> handler) {
        if (!responder_ids.isEmpty()) {
            SqlStatementsBuilder s = new SqlStatementsBuilder();
            String query = "UPDATE " + DISTRIBUTION_TABLE + " SET active = ? WHERE form_id = ? AND responder_id = ?;";

            s.raw(TRANSACTION_BEGIN_QUERY);
            for (String responder_id : responder_ids) {
                JsonArray params = new JsonArray().add(active).add(formId).add(responder_id);
                s.prepared(query, params);
            }
            s.raw(TRANSACTION_COMMIT_QUERY);

            sql.transaction(s.build(), SqlResult.validUniqueResultHandler(handler));
        }
        else {
            handler.handle(new Either.Right<>(new JsonObject()));
        }
    }

    @Override
    public void deleteOldDistributions(Handler<Either<String, JsonArray>> handler) {
        String query =
                "DELETE FROM " + DISTRIBUTION_TABLE + " " +
                "WHERE id IN (" +
                    "SELECT d.id FROM " + DISTRIBUTION_TABLE + " d " +
                    "JOIN " + FORM_TABLE + " f ON f.id = d.form_id " +
                    "WHERE f.rgpd = ? AND d.date_response IS NOT NULL " +
                    "AND (SELECT " +
                        "EXTRACT(year FROM AGE(NOW(), d.date_response::timestamp)) * 12 + " +
                        "EXTRACT(month FROM AGE(NOW(), d.date_response::timestamp))" +
                    ") > f.rgpd_lifetime" +
                ")" +
                "RETURNING *;";
        JsonArray params = new JsonArray().add(true);
        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
    }
}
