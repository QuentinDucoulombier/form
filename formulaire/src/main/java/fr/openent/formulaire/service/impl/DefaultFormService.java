package fr.openent.formulaire.service.impl;

import fr.openent.form.core.enums.I18nKeys;
import fr.openent.form.helpers.FutureHelper;
import fr.openent.form.helpers.I18nHelper;
import fr.openent.formulaire.service.FormService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;
import org.entcore.common.sql.SqlStatementsBuilder;
import org.entcore.common.user.UserInfos;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static fr.openent.form.core.constants.Constants.*;
import static fr.openent.form.core.constants.DistributionStatus.FINISHED;
import static fr.openent.form.core.constants.Fields.*;
import static fr.openent.form.core.constants.ShareRights.*;
import static fr.openent.form.core.constants.Tables.*;

public class DefaultFormService implements FormService {
    private final Sql sql = Sql.getInstance();
    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    @Override
    public void list(List<String> groupsAndUserIds, UserInfos user, Handler<Either<String, JsonArray>> handler) {
        StringBuilder query = new StringBuilder();
        JsonArray params = new JsonArray();

        query.append("SELECT f.*, d.nb_responses, e.nb_elements, rff.folder_id ")
                .append("FROM ").append(FORM_TABLE).append(" f ")
                .append("LEFT JOIN ").append(FORM_SHARES_TABLE).append(" fs ON f.id = fs.resource_id ")
                .append("LEFT JOIN ").append(MEMBERS_TABLE).append(" m ON (fs.member_id = m.id AND m.group_id IS NOT NULL) ")
                .append("LEFT JOIN (SELECT form_id, COUNT(form_id) AS nb_responses FROM ").append(DISTRIBUTION_TABLE)
                .append(" WHERE status = ? GROUP BY form_id) d ON d.form_id = f.id ")
                .append("LEFT JOIN (SELECT form_id, COUNT(form_id) AS nb_elements FROM (SELECT id, form_id FROM ")
                .append(QUESTION_TABLE).append(" UNION SELECT id, form_id FROM ").append(SECTION_TABLE)
                .append(") AS e GROUP BY form_id) e ON e.form_id = f.id ")
                .append("LEFT JOIN ").append(REL_FORM_FOLDER_TABLE).append(" rff ON rff.form_id = f.id AND rff.user_id = f.owner_id");
        params.add(FINISHED);

        query.append(" WHERE (fs.member_id IN ").append(Sql.listPrepared(groupsAndUserIds.toArray()));
        for (String groupOrUser : groupsAndUserIds) {
            params.add(groupOrUser);
        }

        query.append(" AND (fs.action = ? OR fs.action = ?)) OR f.owner_id = ? ")
                .append("GROUP BY f.id, d.nb_responses, e.nb_elements, rff.folder_id ")
                .append("ORDER BY f.date_modification DESC;");
        params.add(MANAGER_RESOURCE_BEHAVIOUR).add(CONTRIB_RESOURCE_BEHAVIOUR).add(user.getUserId());

        Sql.getInstance().prepared(query.toString(), params, SqlResult.validResultHandler(handler));
    }

    @Override
    public void listByIds(JsonArray formIds, Handler<Either<String, JsonArray>> handler) {
        String query = "SELECT * FROM " + FORM_TABLE + " WHERE id IN " + Sql.listPrepared(formIds) + ";";
        JsonArray params = new JsonArray().addAll(formIds);
        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
    }

    @Override
    public void listSentForms(UserInfos user, Handler<Either<String, JsonArray>> handler) {
        String query = "SELECT f.* FROM " + FORM_TABLE + " f " +
                "LEFT JOIN " + DISTRIBUTION_TABLE + " d ON f.id = d.form_id " +
                "WHERE d.responder_id = ? AND NOW() BETWEEN date_opening AND COALESCE(date_ending, NOW() + interval '1 year') " +
                "AND active = ? " +
                "GROUP BY f.id " +
                "ORDER BY title;";
        JsonArray params = new JsonArray().add(user.getUserId()).add(true);
        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
    }

    @Override
    public Future<JsonArray> listForLinker(List<String> groupsAndUserIds, UserInfos user) {
        Promise<JsonArray> promise = Promise.promise();

        JsonArray params = new JsonArray();

        String checkGroupsAndUserIds = "";
        if (groupsAndUserIds != null && groupsAndUserIds.size() > 0) {
            checkGroupsAndUserIds =  "(fs.member_id IN " + Sql.listPrepared(groupsAndUserIds.toArray()) + " AND fs.action = ?) OR ";
            for (String groupOrUser : groupsAndUserIds) {
                params.add(groupOrUser);
            }
            params.add(MANAGER_RESOURCE_BEHAVIOUR);
        }

        String query = "SELECT f.* FROM " + FORM_TABLE + " f " +
                "LEFT JOIN " + FORM_SHARES_TABLE + " fs ON f.id = fs.resource_id " +
                "LEFT JOIN " + MEMBERS_TABLE + " m ON (fs.member_id = m.id AND m.group_id IS NOT NULL) " +
                "WHERE (" + checkGroupsAndUserIds + "f.owner_id = ?) AND f.archived = ? " +
                "AND (f.is_public = ? OR f.id IN (SELECT DISTINCT form_id FROM " + DISTRIBUTION_TABLE + " WHERE active = ?)) " +
                "GROUP BY f.id " +
                "ORDER BY f.date_modification DESC;";

        params.add(user.getUserId()).add(false).add(true).add(true);

        String errorMessage = "[Formulaire@DefaultFormService::listForLinker] Fail to list forms for linker for user with id " + user.getUserId();
        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(FutureHelper.handlerEither(promise, errorMessage)));

        return promise.future();
    }

    @Override
    public void listContributors(String formId, Handler<Either<String, JsonArray>> handler) {
        listUsersByRights(formId, CONTRIB_RESOURCE_BEHAVIOUR, handler);
    }

    @Override
    public void listManagers(String formId, Handler<Either<String, JsonArray>> handler) {
        listUsersByRights(formId, MANAGER_RESOURCE_BEHAVIOUR, handler);
    }

    private void listUsersByRights(String formId, String right, Handler<Either<String, JsonArray>> handler) {
        String query = "SELECT DISTINCT fs.member_id AS id FROM " + FORM_TABLE + " f " +
                "JOIN " + FORM_SHARES_TABLE + " fs ON fs.resource_id = f.id " +
                "WHERE f.id = ? AND fs.action = ? " +
                "UNION " +
                "SELECT owner_id FROM "  + FORM_TABLE + " WHERE id = ?;";
        JsonArray params = new JsonArray().add(formId).add(right).add(formId);
        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
    }

    @Override
    public Future<JsonArray> listSentFormsOpeningToday() {
        Promise<JsonArray> promise = Promise.promise();

        String query = "SELECT * FROM " + FORM_TABLE + " WHERE sent = ? " +
                "AND date_opening >= TO_CHAR(NOW(),'YYYY-MM-DD')::date " +
                "AND date_opening < TO_CHAR(NOW() + INTERVAL '1 day','YYYY-MM-DD')::date";
        JsonArray params = new JsonArray().add(true);

        String errorMessage = "[Formulaire@DefaultFormService::listFormsOpeningToday] Fail to list forms opening today";
        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(FutureHelper.handlerEither(promise, errorMessage)));

        return promise.future();
    }

    @Override
    public Future<JsonObject> get(String formId, UserInfos user) {
        Promise<JsonObject> promise = Promise.promise();

        String query =
                "WITH folder_id AS ( " +
                    "SELECT folder_id FROM " + REL_FORM_FOLDER_TABLE + " " +
                    "WHERE form_id = ? AND user_id = ? " +
                ") " +
                "SELECT *, (SELECT * FROM folder_id) " +
                "FROM " + FORM_TABLE + " WHERE id = ?;";
        JsonArray params = new JsonArray().add(formId).add(user.getUserId()).add(formId);

        String errorMessage = "[Formulaire@DefaultFormService::get] Fail to get form with id " + formId;
        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(FutureHelper.handlerEither(promise, errorMessage)));

        return promise.future();
    }

    @Override
    @Deprecated
    public void get(String formId, UserInfos user, Handler<Either<String, JsonObject>> handler) {
        get(formId, user)
            .onSuccess(result -> handler.handle(new Either.Right<>(result)))
            .onFailure(err -> handler.handle(new Either.Left<>(err.getMessage())));
    }

    @Override
    public void create(JsonObject form, UserInfos user, Handler<Either<String, JsonObject>> handler) {
        String public_key = null;
        if (form.getBoolean(IS_PUBLIC, false)) {
            if (form.getString(DATE_ENDING, null) == null || form.getString(DATE_OPENING, null) == null) {
                handler.handle(new Either.Left<>("A public form must have an ending date."));
            }
            else {
                try {
                    Date startDate = dateFormatter.parse(form.getString(DATE_OPENING));
                    Date endDate = dateFormatter.parse(form.getString(DATE_ENDING));
                    if (endDate.after(new Date()) && endDate.after(startDate)) {
                        public_key = UUID.randomUUID().toString();
                    }
                    else {
                        handler.handle(new Either.Left<>("This form is closed, you cannot access it anymore."));
                    }
                }
                catch (ParseException e) {
                    e.printStackTrace();
                    return;
                }
            }
        }

        String query = "INSERT INTO " + FORM_TABLE + " (owner_id, owner_name, title, description, picture, " +
                "date_creation, date_modification, date_opening, date_ending, multiple, anonymous, response_notified, " +
                "editable, rgpd, rgpd_goal, rgpd_lifetime, is_public" + (public_key != null ? ", public_key" : "") + ") " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?" + (public_key != null ? ", ?" : "") + ") RETURNING *;";
        JsonArray params = new JsonArray()
                .add(user.getUserId())
                .add(user.getUsername())
                .add(form.getString(TITLE, ""))
                .add(form.getString(DESCRIPTION, ""))
                .add(form.getString(PICTURE, ""))
                .add("NOW()").add("NOW()")
                .add(form.getString(DATE_OPENING, "NOW()"))
                .add(form.getString(DATE_ENDING, null))
                .add(form.getBoolean(MULTIPLE, false))
                .add(form.getBoolean(ANONYMOUS, false))
                .add(form.getBoolean(RESPONSE_NOTIFIED, false))
                .add(form.getBoolean(EDITABLE, false))
                .add(form.getBoolean(RGPD, false))
                .add(form.getString(RGPD_GOAL, ""))
                .add(form.getInteger(RGPD_LIFETIME, 12))
                .add(form.getBoolean(IS_PUBLIC, false));

        if (public_key != null) params.add(public_key);

        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void createMultiple(JsonArray forms, UserInfos user, Handler<Either<String, JsonArray>> handler) {
        if (!forms.isEmpty()) {
            SqlStatementsBuilder s = new SqlStatementsBuilder();

            s.raw(TRANSACTION_BEGIN_QUERY);
            for (int i = 0; i < forms.size(); i++) {
                JsonObject form = forms.getJsonObject(i);
                String public_key = null;
                if (form.getBoolean(IS_PUBLIC, false)) {
                    if (form.getString(DATE_ENDING, null) == null || form.getString(DATE_OPENING, null) == null) {
                        handler.handle(new Either.Left<>("A public form must have an ending date."));
                    }
                    else {
                        try {
                            Date startDate = dateFormatter.parse(form.getString(DATE_OPENING));
                            Date endDate = dateFormatter.parse(form.getString(DATE_ENDING));
                            if (endDate.after(new Date()) && endDate.after(startDate)) {
                                public_key = UUID.randomUUID().toString();
                            }
                            else {
                                handler.handle(new Either.Left<>("This form is closed, you cannot access it anymore."));
                            }
                        }
                        catch (ParseException e) {
                            e.printStackTrace();
                            return;
                        }
                    }
                }

                String query = "INSERT INTO " + FORM_TABLE + " (owner_id, owner_name, title, description, picture, " +
                        "date_creation, date_modification, date_opening, date_ending, multiple, anonymous, response_notified, " +
                        "editable, rgpd, rgpd_goal, rgpd_lifetime, is_public" + (public_key != null ? ", public_key" : "") + ") " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?" + (public_key != null ? ", ?" : "") + "); ";
                JsonArray params = new JsonArray()
                        .add(user.getUserId())
                        .add(user.getUsername())
                        .add(form.getString(TITLE, ""))
                        .add(form.getString(DESCRIPTION, ""))
                        .add(form.getString(PICTURE, ""))
                        .add("NOW()").add("NOW()")
                        .add(form.getString(DATE_OPENING, "NOW()"))
                        .add(form.getString(DATE_ENDING, null))
                        .add(form.getBoolean(MULTIPLE, false))
                        .add(form.getBoolean(ANONYMOUS, false))
                        .add(form.getBoolean(RESPONSE_NOTIFIED, false))
                        .add(form.getBoolean(EDITABLE, false))
                        .add(form.getBoolean(RGPD, false))
                        .add(form.getString(RGPD_GOAL, ""))
                        .add(form.getInteger(RGPD_LIFETIME, 12))
                        .add(form.getBoolean(IS_PUBLIC, false));

                if (public_key != null) params.add(public_key);

                s.prepared(query, params);
            }
            s.raw(TRANSACTION_COMMIT_QUERY);

            sql.transaction(s.build(), SqlResult.validResultsHandler(handler));
        }
        else {
            handler.handle(new Either.Right<>(new JsonArray()));
        }
    }

    @Override
    public void duplicate(int formId, UserInfos user, String locale, Handler<Either<String, JsonArray>> handler) {
        String COPY = I18nHelper.getI18nValue(I18nKeys.COPY, locale);

        SqlStatementsBuilder s = new SqlStatementsBuilder();
        s.raw(TRANSACTION_BEGIN_QUERY);

        String mainQuery =
                // Duplicate FORM
                "WITH new_form_id AS (" +
                    "INSERT INTO " + FORM_TABLE + " (owner_id, owner_name, title, description, picture, date_opening, date_ending, " +
                    "multiple, anonymous, response_notified, editable, rgpd, rgpd_goal, rgpd_lifetime, is_public, public_key, original_form_id) " +
                    "SELECT ?, ?, concat(title, ' - " + COPY + "'), description, picture, date_opening, date_ending, multiple, " +
                    "anonymous, response_notified, editable, rgpd, rgpd_goal, rgpd_lifetime, is_public, " +
                    "CASE is_public WHEN TRUE THEN '" + UUID.randomUUID() + "' END, id " +
                    "FROM " + FORM_TABLE + " WHERE id = ? RETURNING id" +
                "), " +
                // Duplicate SECTIONS of the form
                "new_sections AS (" +
                    "INSERT INTO " + SECTION_TABLE + " (form_id, title, description, position, next_form_element_id, next_form_element_type, is_next_form_element_default, original_section_id) " +
                    "SELECT (SELECT id from new_form_id), title, description, position, next_form_element_id, next_form_element_type, is_next_form_element_default, id " +
                    "FROM " + SECTION_TABLE + " WHERE form_id = ? " +
                    "RETURNING id, form_id, original_section_id" +
                "), " +
                // Create mapping between old questions and sections infos
                "new_sections_linked AS (" +
                    "SELECT ns.id, ns.original_section_id, q.id AS question_id, q.section_position FROM new_sections ns " +
                    "JOIN " + QUESTION_TABLE + " q ON ns.original_section_id = q.section_id" +
                "), " +
                // Duplicate QUESTIONS of the form
                "new_questions AS (" +
                    "INSERT INTO " + QUESTION_TABLE + " (form_id, title, position, question_type, statement, " +
                    "mandatory, original_question_id, section_id, section_position, conditional, placeholder) " +
                    "SELECT (SELECT id from new_form_id), title, position, question_type, statement, mandatory, id, " +
                    "(SELECT id FROM new_sections_linked WHERE original_section_id = q.section_id LIMIT 1), " +
                    "(SELECT section_position FROM new_sections_linked WHERE question_id = q.id), conditional, placeholder " +
                    "FROM " + QUESTION_TABLE + " q WHERE form_id = ? AND matrix_id IS NULL " +
                    "ORDER BY q.id " +
                    "RETURNING id, form_id, original_question_id, question_type" +
                "), " +
                // Duplicate CHILDREN questions of the MATRIX questions
                "new_children_questions AS (" +
                    "INSERT INTO " + QUESTION_TABLE + " (form_id, title, question_type, mandatory, original_question_id, matrix_id, matrix_position) " +
                    "SELECT (SELECT id from new_form_id), title, question_type, mandatory, id, " +
                    "(SELECT id FROM new_questions WHERE original_question_id = q.matrix_id LIMIT 1), matrix_position " +
                    "FROM " + QUESTION_TABLE + " q WHERE form_id = ? AND matrix_id IS NOT NULL " +
                    "ORDER BY q.id " +
                    "RETURNING id, form_id, original_question_id, question_type" +
                "), " +
                // Duplicate questions SPECIFICS fields of the QUESTIONS
                "new_questions_specifics AS (" +
                    "INSERT INTO " + QUESTION_SPECIFIC_FIELDS_TABLE + " (question_id, cursor_min_val, cursor_max_val, " +
                    "cursor_step, cursor_min_label, cursor_max_label) " +
                    "SELECT q.id, cursor_min_val, cursor_max_val, cursor_step, cursor_min_label, cursor_max_label " +
                    "FROM " + QUESTION_SPECIFIC_FIELDS_TABLE + " qsf " +
                    "JOIN new_questions q ON q.original_question_id = qsf.question_id " +
                    "WHERE question_type IN " + Sql.listPrepared(QUESTIONS_WITH_SPECIFICS) + " " +
                    "ORDER BY qsf.id " +
                    "RETURNING *" +
                "), " +
                // Aggregate all questions infos in a final result object
                "final_results AS (" +
                    "SELECT * FROM new_questions " +
                    "UNION ALL " +
                    "SELECT * FROM new_children_questions " +
                    "UNION ALL " +
                    "SELECT null, (SELECT id FROM new_form_id), null, null " +
                    "WHERE NOT EXISTS (SELECT * FROM new_questions)" +
                ") " +
                // Join questions specifics fields to these questions infos
                "SELECT fr.*, qsf.cursor_min_val, qsf.cursor_max_val, qsf.cursor_step, qsf.cursor_min_label, qsf.cursor_max_label FROM final_results fr " +
                "LEFT JOIN new_questions_specifics qsf ON fr.id = qsf.question_id " +
                "ORDER BY fr.id;";
        JsonArray mainParams = new JsonArray()
                .add(user.getUserId())
                .add(user.getUsername())
                .add(formId)
                .add(formId)
                .add(formId)
                .add(formId)
                .addAll(new JsonArray(QUESTIONS_WITH_SPECIFICS));

        // Update new SECTIONS of the form to set their next_form_element_id values
        String updateSectionsQuery =
                "WITH new_form_id AS (SELECT MAX(id) FROM " + FORM_TABLE + " WHERE original_form_id = ?), " +
                "new_form_elements_infos AS (" +
                    "SELECT * FROM (" +
                        "SELECT id, form_id, 'QUESTION' AS type, original_question_id AS original_id FROM " + QUESTION_TABLE +
                        " UNION " +
                        "SELECT id, form_id, 'SECTION' AS type, original_section_id AS original_id FROM " + SECTION_TABLE +
                    ") AS qs_infos " +
                    "WHERE form_id = (SELECT * FROM new_form_id) " +
                ") " +
                "UPDATE " + SECTION_TABLE + " s SET next_form_element_id = nfei.id " +
                "FROM new_form_elements_infos nfei " +
                "WHERE s.form_id = (SELECT * FROM new_form_id) " +
                "AND s.next_form_element_id = nfei.original_id " +
                "AND s.next_form_element_type = nfei.type " +
                "RETURNING *;";
        JsonArray updateSectionsParams = new JsonArray().add(formId);

        s.prepared(mainQuery, mainParams);
        s.prepared(updateSectionsQuery, updateSectionsParams);

        s.raw(TRANSACTION_COMMIT_QUERY);

        sql.transaction(s.build(), SqlResult.validResultsHandler(handler));
    }

    @Override
    public void update(String formId, JsonObject form, Handler<Either<String, JsonObject>> handler) {
        String query = "WITH nbResponses AS (SELECT COUNT(*) FROM " + DISTRIBUTION_TABLE +
                " WHERE form_id = ? AND status = ?) " +
                "UPDATE " + FORM_TABLE + " SET title = ?, description = ?, picture = ?, date_modification = ?, " +
                "date_opening = ?, date_ending = ?, sent = ?, collab = ?, reminded = ?, archived = ?, " +
                "multiple = CASE (SELECT count > 0 FROM nbResponses) " +
                "WHEN false THEN ? WHEN true THEN (SELECT multiple FROM " + FORM_TABLE +" WHERE id = ?) END, " +
                "anonymous = CASE (SELECT count > 0 FROM nbResponses) " +
                "WHEN false THEN ? WHEN true THEN (SELECT anonymous FROM " + FORM_TABLE +" WHERE id = ?) END, " +
                "response_notified = ?, editable = ?, rgpd = ?, rgpd_goal = ?, rgpd_lifetime = ?" +
                "WHERE id = ? RETURNING *;";

        JsonArray params = new JsonArray()
                .add(formId)
                .add(FINISHED)
                .add(form.getString(TITLE, ""))
                .add(form.getString(DESCRIPTION, ""))
                .add(form.getString(PICTURE, ""))
                .add("NOW()")
                .add(form.getString(DATE_OPENING, "NOW()"))
                .add(form.getString(DATE_ENDING, null))
                .add(form.getBoolean(SENT, false))
                .add(form.getBoolean(COLLAB, false))
                .add(form.getBoolean(REMINDED, false))
                .add(form.getBoolean(ARCHIVED, false))
                .add(form.getBoolean(MULTIPLE, false)).add(formId)
                .add(form.getBoolean(ANONYMOUS, false)).add(formId)
                .add(form.getBoolean(RESPONSE_NOTIFIED, false))
                .add(form.getBoolean(EDITABLE, false))
                .add(form.getBoolean(RGPD, false))
                .add(form.getString(RGPD_GOAL, ""))
                .add(form.getInteger(RGPD_LIFETIME, 12))
                .add(formId);

        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void updateMultiple(JsonArray forms, Handler<Either<String, JsonArray>> handler) {
        if (!forms.isEmpty()) {
            SqlStatementsBuilder s = new SqlStatementsBuilder();

            s.raw(TRANSACTION_BEGIN_QUERY);
            for (int i = 0; i < forms.size(); i++) {
                JsonObject form = forms.getJsonObject(i);
                int formId = form.getInteger(ID, null);

                String query = "WITH nbResponses AS (SELECT COUNT(*) FROM " + DISTRIBUTION_TABLE +
                        " WHERE form_id = ? AND status = ?) " +
                        "UPDATE " + FORM_TABLE + " SET title = ?, description = ?, picture = ?, date_modification = ?, " +
                        "date_opening = ?, date_ending = ?, sent = ?, collab = ?, reminded = ?, archived = ?, " +
                        "multiple = CASE (SELECT count > 0 FROM nbResponses) " +
                        "WHEN false THEN ? WHEN true THEN (SELECT multiple FROM " + FORM_TABLE +" WHERE id = ?) END, " +
                        "anonymous = CASE (SELECT count > 0 FROM nbResponses) " +
                        "WHEN false THEN ? WHEN true THEN (SELECT anonymous FROM " + FORM_TABLE +" WHERE id = ?) END, " +
                        "response_notified = ?, editable = ?, rgpd = ?, rgpd_goal = ?, rgpd_lifetime = ?" +
                        "WHERE id = ? RETURNING *;";

                JsonArray params = new JsonArray()
                        .add(formId)
                        .add(FINISHED)
                        .add(form.getString(TITLE, ""))
                        .add(form.getString(DESCRIPTION, ""))
                        .add(form.getString(PICTURE, ""))
                        .add("NOW()")
                        .add(form.getString(DATE_OPENING, "NOW()"))
                        .add(form.getString(DATE_ENDING, null))
                        .add(form.getBoolean(SENT, false))
                        .add(form.getBoolean(COLLAB, false))
                        .add(form.getBoolean(REMINDED, false))
                        .add(form.getBoolean(ARCHIVED, false))
                        .add(form.getBoolean(MULTIPLE, false)).add(formId)
                        .add(form.getBoolean(ANONYMOUS, false)).add(formId)
                        .add(form.getBoolean(RESPONSE_NOTIFIED, false))
                        .add(form.getBoolean(EDITABLE, false))
                        .add(form.getBoolean(RGPD, false))
                        .add(form.getString(RGPD_GOAL, ""))
                        .add(form.getInteger(RGPD_LIFETIME, 12))
                        .add(formId);

                s.prepared(query, params);
            }
            s.raw(TRANSACTION_COMMIT_QUERY);

            sql.transaction(s.build(), SqlResult.validResultsHandler(handler));
        }
        else {
            handler.handle(new Either.Right<>(new JsonArray()));
        }
    }

    @Override
    public void delete(String formId, Handler<Either<String, JsonObject>> handler) {
        String query = "DELETE FROM " + FORM_TABLE + " WHERE id = ?;";
        JsonArray params = new JsonArray().add(formId);
        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void getMyFormRights(String formId, List<String> groupsAndUserIds, Handler<Either<String, JsonArray>> handler) {
        String query = "SELECT action FROM " + FORM_SHARES_TABLE +
                " WHERE resource_id = ? AND member_id IN " + Sql.listPrepared(groupsAndUserIds) + " AND action IN (?, ?, ?);";
        JsonArray params = new JsonArray()
                .add(formId)
                .addAll(new fr.wseduc.webutils.collections.JsonArray(groupsAndUserIds))
                .add(CONTRIB_RESOURCE_BEHAVIOUR)
                .add(MANAGER_RESOURCE_BEHAVIOUR)
                .add(RESPONDER_RESOURCE_BEHAVIOUR);
        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
    }

    @Override
    public void getAllMyFormRights(List<String> groupsAndUserIds, Handler<Either<String, JsonArray>> handler) {
        String query = "SELECT resource_id, action FROM " + FORM_SHARES_TABLE +
                " WHERE member_id IN " + Sql.listPrepared(groupsAndUserIds) + " AND action IN (?, ?, ?);";
        JsonArray params = new JsonArray()
                .addAll(new fr.wseduc.webutils.collections.JsonArray(groupsAndUserIds))
                .add(CONTRIB_RESOURCE_BEHAVIOUR)
                .add(MANAGER_RESOURCE_BEHAVIOUR)
                .add(RESPONDER_RESOURCE_BEHAVIOUR);
        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
    }

    @Override
    public void checkFormsRights(List<String> groupsAndUserIds, UserInfos user, String right, JsonArray formIds, Handler<Either<String, JsonObject>> handler) {
        String query = "SELECT COUNT(DISTINCT f.id) FROM " + FORM_TABLE + " f " +
                "LEFT JOIN " + FORM_SHARES_TABLE + " fs ON fs.resource_id = f.id " +
                "WHERE ((member_id IN " + Sql.listPrepared(groupsAndUserIds) + " AND action = ?) OR owner_id = ?) AND id IN " + Sql.listPrepared(formIds);
        JsonArray params = (new JsonArray(groupsAndUserIds))
                .add(right)
                .add(user.getUserId())
                .addAll(formIds);

        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(handler));
    }
}
