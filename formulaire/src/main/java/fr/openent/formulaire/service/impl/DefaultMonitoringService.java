package fr.openent.formulaire.service.impl;

import fr.openent.form.helpers.FutureHelper;
import fr.openent.formulaire.service.MonitoringService;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;
import org.entcore.common.sql.SqlStatementsBuilder;

import static fr.openent.form.core.constants.Constants.TRANSACTION_BEGIN_QUERY;
import static fr.openent.form.core.constants.Constants.TRANSACTION_COMMIT_QUERY;
import static fr.openent.form.core.constants.Tables.QUESTION_TABLE;
import static fr.openent.form.core.constants.Tables.SECTION_TABLE;

public class DefaultMonitoringService implements MonitoringService {
    private final Sql sql = Sql.getInstance();

    @Override
    public Future<JsonArray> getFormIdsWithPositionDuplicates() {
        Promise<JsonArray> promise = Promise.promise();

        String query =
                "SELECT form_id FROM (" +
                    "SELECT form_id, position FROM (" +
                        "SELECT form_id, position FROM " + SECTION_TABLE + " " +
                        "UNION ALL " +
                        "SELECT form_id, position FROM " + QUESTION_TABLE + " " +
                    ") AS qs " +
                    "GROUP BY form_id, position " +
                    "HAVING position IS NOT NULL AND COUNT(*) > 1" +
                ") AS form_ids;";

        String errorMessage = "[Formulaire@DefaultMonitoringService::getFormIdsWithPositionDuplicates] Fail to get form ids of elements with duplicated positions : ";
        sql.prepared(query, new JsonArray(), SqlResult.validResultHandler(FutureHelper.handlerEither(promise, errorMessage)));

        return promise.future();
    }

    @Override
    public Future<JsonArray> getPositionDuplicates(JsonArray formIds) {
        Promise<JsonArray> promise = Promise.promise();

        if (formIds == null || formIds.isEmpty()) {
            promise.complete(new JsonArray());
            return promise.future();
        }

        String query =
                "SELECT * FROM ( " +
                    "SELECT id, form_id, title, position, true AS is_section FROM " + SECTION_TABLE + " " +
                    "UNION " +
                    "SELECT id, form_id, title, position, false AS is_section FROM " + QUESTION_TABLE + " " +
                ") AS qs " +
                "WHERE form_id IN " + Sql.listPrepared(formIds) + " " +
                "ORDER BY form_id, position, id;";
        JsonArray params = new JsonArray().addAll(formIds);

        String errorMessage = "[Formulaire@DefaultMonitoringService::getPositionDuplicates] Fail to get elements with duplicated positions : ";
        sql.prepared(query, params, SqlResult.validResultHandler(FutureHelper.handlerEither(promise, errorMessage)));

        return promise.future();
    }

    @Override
    public Future<JsonArray> cleanPositionDuplicates(JsonArray formIds) {
        Promise<JsonArray> promise = Promise.promise();

        if (formIds == null || formIds.isEmpty()) {
            promise.complete(new JsonArray());
            return promise.future();
        }

        SqlStatementsBuilder s = new SqlStatementsBuilder();
        s.raw(TRANSACTION_BEGIN_QUERY);

        // Nullifying query
        String nullifyingQuery =
                "WITH nullyfied_sections AS ( " +
                    "UPDATE " + SECTION_TABLE + " s " +
                    "SET position = null " +
                    "WHERE position IS NOT NULL AND form_id IN " + Sql.listPrepared(formIds) + " " +
                    "RETURNING id, true AS is_section " +
                "), " +
                "nullyfied_questions AS (" +
                    "UPDATE " + QUESTION_TABLE + " q " +
                    "SET position = null " +
                    "WHERE position IS NOT NULL AND form_id IN " + Sql.listPrepared(formIds) + " " +
                    "RETURNING id, false AS is_section " +
                ") " +
                "SELECT * FROM nullyfied_sections UNION SELECT * FROM nullyfied_questions " +
                "ORDER BY id, is_section;";
        s.prepared(nullifyingQuery, new JsonArray().addAll(formIds).addAll(formIds));

        // Updating query
        String updatingQuery =
                "WITH ranking_position AS ( " +
                    "SELECT *, RANK() OVER (PARTITION BY form_id ORDER BY position, id) AS rank " +
                    "FROM ( " +
                        "SELECT id, form_id, position, true AS is_section FROM " + SECTION_TABLE + " " +
                        "UNION " +
                        "SELECT id, form_id, position, false AS is_section FROM " + QUESTION_TABLE + " " +
                    ") AS elements " +
                "), " +
                "updated_sections AS ( " +
                    "UPDATE " + SECTION_TABLE + " s " +
                    "SET position = ( " +
                        "SELECT rank FROM ranking_position r " +
                        "WHERE s.id = r.id AND r.is_section = true " +
                    ") " +
                    "WHERE id IN (SELECT id FROM " + SECTION_TABLE + " WHERE form_id IN " + Sql.listPrepared(formIds) + ") " +
                    "RETURNING id, form_id, title, position, true AS is_section " +
                "), " +
                "updated_questions AS ( " +
                    "UPDATE " + QUESTION_TABLE + " q " +
                    "SET position = (" +
                        "SELECT rank FROM ranking_position r " +
                        "WHERE q.id = r.id AND r.is_section = false " +
                    ")" +
                    "WHERE id IN (SELECT id FROM " + QUESTION_TABLE + " WHERE form_id IN " + Sql.listPrepared(formIds) + ") " +
                    "AND (section_id IS NULL AND section_position IS NULL AND matrix_id IS NULL AND matrix_position IS NULL)" +
                    "RETURNING id, form_id, title, position, false AS is_section " +
                ") " +
                "SELECT * FROM updated_sections " +
                "UNION " +
                "SELECT * FROM updated_questions " +
                "ORDER BY form_id, position, id;";
        s.prepared(updatingQuery, new JsonArray().addAll(formIds).addAll(formIds));
        s.raw(TRANSACTION_COMMIT_QUERY);

        String errorMessage = "[Formulaire@DefaultMonitoringService::cleanPositionDuplicates] Fail to reset positions of elements with duplicated positions : ";
        sql.transaction(s.build(), SqlResult.validResultsHandler(FutureHelper.handlerEither(promise, errorMessage)));

        return promise.future();
    }

    @Override
    public Future<JsonArray> getScripts() {
        Promise<JsonArray> promise = Promise.promise();

        JsonArray values = new JsonArray();
        String query = "SELECT * FROM formulaire.scripts";


        sql.prepared(query, values, SqlResult.validResultHandler(event -> {
            if (event.isLeft()) {
                promise.fail(event.left().getValue());
            } else {
                promise.complete(event.right().getValue());
            }
        }));

        return promise.future();
    }

}
