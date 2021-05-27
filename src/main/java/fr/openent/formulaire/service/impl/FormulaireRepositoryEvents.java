package fr.openent.formulaire.service.impl;

import fr.openent.formulaire.Formulaire;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;
import org.entcore.common.sql.SqlStatementsBuilder;
import org.entcore.common.user.RepositoryEvents;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import fr.wseduc.webutils.Either;

import java.text.Format;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FormulaireRepositoryEvents implements RepositoryEvents {
    private static final Logger log = LoggerFactory.getLogger(FormulaireRepositoryEvents.class);
    private static final String deletedUser = "Utilisateur supprimé";
    private static final String deletedUserFile = "utilisateurSupprimé_Fichier";

    @Override
    public void exportResources(JsonArray resourcesIds, String exportId, String userId, JsonArray groups, String exportPath, String locale, String host, final Handler<Boolean> handler) {
        log.info("[Formulaire@FormulaireRepositoryEvents] exportResources event is not implemented");
    }

    @Override
    public void deleteGroups(JsonArray groups) {
        if (groups == null) {
            return;
        }

        for (int i = groups.size() - 1; i >= 0; i--) {
            if (groups.hasNull(i)) {
                groups.remove(i);
            }
        }

        if (groups.size() > 0) {
            final JsonArray groupsIds = new fr.wseduc.webutils.collections.JsonArray();
            for (Object o : groups) {
                if (o instanceof JsonObject) {
                    final JsonObject j = (JsonObject) o;
                    groupsIds.add(j.getString("id"));
                }
            }

            if (groupsIds.size() > 0) {
                SqlStatementsBuilder statementsBuilder = new SqlStatementsBuilder();
                statementsBuilder.prepared("DELETE FROM " + Formulaire.GROUPS_TABLE + " WHERE id IN " + Sql.listPrepared(groupsIds), groupsIds);

                Sql.getInstance().transaction(statementsBuilder.build(), SqlResult.validRowsResultHandler(deleteEvent -> {
                    if (deleteEvent.isRight()) {
                        log.info("[Formulaire@FormulaireRepositoryEvents] Sharing rights deleted for groups : " +
                                groupsIds.getList().toString());
                    }
                    else {
                        log.error("[Formulaire@FormulaireRepositoryEvents] Failed to remove sharing rights deleted for groups (" +
                                groupsIds.getList().toString() + ") : " + deleteEvent.left().getValue());
                    }
                }));
            }
        }
    }

    @Override
    public void deleteUsers(JsonArray users) {
        if (users == null) {
            return;
        }

        for (int i = users.size() - 1; i >= 0; i--) {
            if (users.hasNull(i)) {
                users.remove(i);
            }
        }

        if (users.size() > 0){
            final JsonArray userIds = new fr.wseduc.webutils.collections.JsonArray();
            for (Object o : users) {
                if (o instanceof JsonObject) {
                    final JsonObject j = (JsonObject) o;
                    userIds.add(j.getString("id"));
                }
            }

            if (userIds.size() > 0) {
                SqlStatementsBuilder statementsBuilder = new SqlStatementsBuilder();
                String query =
                        "SELECT id FROM " + Formulaire.FORM_TABLE + " f " +
                        "JOIN " + Formulaire.FORM_SHARES_TABLE + " fs ON fs.resource_id = f.id " +
                        "WHERE resource_id IN (" +
                            "SELECT id FROM " + Formulaire.FORM_TABLE +" f " +
                            "JOIN " + Formulaire.FORM_SHARES_TABLE + " fs ON fs.resource_id = f.id " +
                            "WHERE owner_id IN " + Sql.listPrepared(userIds) + " OR " +
                            "(action = " + Formulaire.MANAGER_RESOURCE_BEHAVIOUR + " AND member_id IN " + Sql.listPrepared(userIds) + ") " +
                            "GROUP BY id" +
                        ") AND resource_id NOT IN ( " +
                            "SELECT resource_id FROM " + Formulaire.FORM_SHARES_TABLE + " " +
                            "WHERE action = " + Formulaire.MANAGER_RESOURCE_BEHAVIOUR + " AND member_id NOT IN " + Sql.listPrepared(userIds) +
                        ") " +
                        "GROUP BY id;";
                JsonArray params = new JsonArray().addAll(userIds).addAll(userIds).addAll(userIds);

                statementsBuilder.prepared(query, params);
                statementsBuilder.prepared("DELETE FROM " + Formulaire.MEMBERS_TABLE + " WHERE user_id IN " + Sql.listPrepared(userIds), userIds);
                statementsBuilder.prepared("UPDATE " + Formulaire.DISTRIBUTION_TABLE + " " +
                        "SET responder_name = '" + deletedUser + "' WHERE responder_id IN " + Sql.listPrepared(userIds), userIds);

                statementsBuilder.prepared("UPDATE " + Formulaire.RESPONSE_FILE_TABLE + " " +
                        "SET filename = '" + deletedUserFile + "' WHERE responder_id IN " + Sql.listPrepared(userIds), userIds);

                Sql.getInstance().transaction(statementsBuilder.build(), SqlResult.validRowsResultHandler(deleteEvent -> {
                    if (deleteEvent.isRight()) {
                        log.info("[Formulaire@FormulaireRepositoryEvents] Sharing rights deleted for users : " +
                                userIds.getList().toString());
                    }
                    else {
                        log.error("[Formulaire@FormulaireRepositoryEvents] Failed to remove sharing rights deleted for users (" +
                                userIds.getList().toString() + ") : " + deleteEvent.left().getValue());
                    }
                }));
            }
        }
    }
}
