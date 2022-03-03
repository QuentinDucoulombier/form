package fr.openent.formulaire.service.impl;

import fr.openent.formulaire.Formulaire;
import fr.openent.formulaire.service.SectionService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;

import java.util.List;

public class DefaultSectionService implements SectionService {

    @Override
    public void list(String formId, Handler<Either<String, JsonArray>> handler) {
        String query = "SELECT * FROM " + Formulaire.SECTION_TABLE + " WHERE form_id = ? ORDER BY position;";
        JsonArray params = new JsonArray().add(formId);
        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
    }

    @Override
    public void get(String sectionId, Handler<Either<String, JsonObject>> handler) {
        String query = "SELECT * FROM " + Formulaire.SECTION_TABLE + " WHERE id = ?;";
        JsonArray params = new JsonArray().add(sectionId);
        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void create(JsonObject section, String formId, Handler<Either<String, JsonObject>> handler) {
        String query = "INSERT INTO " + Formulaire.SECTION_TABLE + " (form_id, title, description, position) " +
                "VALUES (?, ?, ?, ?) RETURNING *;";
        JsonArray params = new JsonArray()
                .add(formId)
                .add(section.getString("title", ""))
                .add(section.getString("description", ""))
                .add(section.getInteger("position", 0));

        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void update(String sectionId, JsonObject section, Handler<Either<String, JsonObject>> handler) {
        String query = "UPDATE " + Formulaire.SECTION_TABLE + " SET title = ?, description = ?, position = ? WHERE id = ? RETURNING *;";
        JsonArray params = new JsonArray()
                .add(section.getString("title", ""))
                .add(section.getString("description", ""))
                .add(section.getInteger("position", 0))
                .add(sectionId);

        query += "UPDATE " + Formulaire.FORM_TABLE + " SET date_modification = ? WHERE id = ?;";
        params.add("NOW()").add(section.getInteger("form_id"));

        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void delete(String sectionId, Handler<Either<String, JsonObject>> handler) {
        String query = "DELETE FROM " + Formulaire.SECTION_TABLE + " WHERE id = ?;";
        JsonArray params = new JsonArray().add(sectionId);
        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(handler));
    }
}