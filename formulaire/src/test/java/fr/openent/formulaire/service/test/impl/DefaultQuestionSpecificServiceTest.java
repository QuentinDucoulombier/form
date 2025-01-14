package fr.openent.formulaire.service.test.impl;

import fr.openent.form.core.constants.Fields;
import fr.openent.formulaire.service.impl.DefaultQuestionSpecificFieldsService;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.sql.Sql;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.Async;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static fr.openent.form.core.constants.EbFields.FORMULAIRE_ADDRESS;
import static fr.openent.form.core.constants.Fields.*;
import static fr.openent.form.core.constants.Tables.*;

@RunWith(VertxUnitRunner.class)
public class DefaultQuestionSpecificServiceTest {
    private Vertx vertx;
    private DefaultQuestionSpecificFieldsService defaultQuestionSpecificFieldsService;

    @Before
    public void setUp(){
        vertx = Vertx.vertx();
        defaultQuestionSpecificFieldsService = new DefaultQuestionSpecificFieldsService();
        Sql.getInstance().init(vertx.eventBus(), FORMULAIRE_ADDRESS);
    }

    @Test
    public void testListSpecificField(TestContext ctx) {
        Async async = ctx.async();
        JsonArray questionIds = new JsonArray().add("1").add("2").add("3");
        String expectedQuery = "SELECT * FROM " + QUESTION_SPECIFIC_FIELDS_TABLE + " WHERE question_id IN " + Sql.listPrepared(questionIds);
        JsonArray expectedParams = new JsonArray().addAll(questionIds);

        vertx.eventBus().consumer(FORMULAIRE_ADDRESS, message -> {
            JsonObject body = (JsonObject) message.body();
            ctx.assertEquals(PREPARED, body.getString(ACTION));
            ctx.assertEquals(expectedQuery, body.getString(STATEMENT));
            ctx.assertEquals(expectedParams.toString(), body.getJsonArray(VALUES).toString());
            async.complete();
        });
        defaultQuestionSpecificFieldsService.listByIds(questionIds)
                .onSuccess(result -> async.complete());

        async.awaitSuccess(10000);
    }

    @Test
    public void create(TestContext ctx) {
        Async async = ctx.async();
        String expectedQuery = "INSERT INTO " + QUESTION_SPECIFIC_FIELDS_TABLE + " (question_id, cursor_min_val, cursor_max_val, " +
                "cursor_step, cursor_min_label, cursor_max_label) VALUES (?, ?, ?, ?, ?, ?) RETURNING *;";

        String questionId = "1";
        JsonObject question = new JsonObject().put(Fields.QUESTION_TYPE, 11);

        JsonArray expectedParams = new JsonArray()
                .add(questionId)
                .add(question.getInteger(CURSOR_MIN_VAL, 1))
                .add(question.getInteger(CURSOR_MAX_VAL, 10))
                .add(question.getInteger(CURSOR_STEP,  1))
                .add(question.getString(CURSOR_MIN_LABEL, ""))
                .add(question.getString(CURSOR_MAX_LABEL, ""));

        vertx.eventBus().consumer(FORMULAIRE_ADDRESS, message -> {
            JsonObject body = (JsonObject) message.body();
            ctx.assertEquals(PREPARED, body.getString(ACTION));
            ctx.assertEquals(expectedQuery, body.getString(STATEMENT));
            ctx.assertEquals(expectedParams.toString(), body.getJsonArray(VALUES).toString());
            async.complete();
        });
        defaultQuestionSpecificFieldsService.create(question, questionId, null);
    }

    @Test
    public void update(TestContext ctx) {
        Async async = ctx.async();

        JsonObject question1 = new JsonObject();
        question1.put(Fields.QUESTION_TYPE, 11)
                .put(CURSOR_MIN_VAL, 1)
                .put(CURSOR_MAX_VAL, 10)
                .put(CURSOR_STEP, 1)
                .put(CURSOR_MIN_LABEL, "label_min_val")
                .put(CURSOR_MAX_LABEL, "label_max_val")
                .put(ID, 1);

        JsonObject question2 = new JsonObject();
        question2.put(Fields.QUESTION_TYPE, 11)
                .put(CURSOR_MIN_VAL, 2)
                .put(CURSOR_MAX_VAL, 12)
                .put(CURSOR_STEP, 2)
                .put(CURSOR_MIN_LABEL, "label_mined_val")
                .put(CURSOR_MAX_LABEL, "label_maxed_val")
                .put(ID, 2);

        JsonArray questions = new JsonArray();
        questions.add(question1)
                .add(question2);

        String expectedQuery = "[{\"action\":\"raw\",\"command\":\"BEGIN;\"}," +
                "{\"action\":\"prepared\",\"statement\":\"UPDATE " + QUESTION_SPECIFIC_FIELDS_TABLE + " SET cursor_min_val = ?, cursor_max_val = ?, cursor_step = ?, cursor_min_label = ?, cursor_max_label = ? WHERE question_id = ? RETURNING *;\",\"values\":[1,10,1,\"label_min_val\",\"label_max_val\",1]}," +
                "{\"action\":\"prepared\",\"statement\":\"UPDATE " + QUESTION_SPECIFIC_FIELDS_TABLE + " SET cursor_min_val = ?, cursor_max_val = ?, cursor_step = ?, cursor_min_label = ?, cursor_max_label = ? WHERE question_id = ? RETURNING *;\",\"values\":[2,12,2,\"label_mined_val\",\"label_maxed_val\",2]}," +
                "{\"action\":\"raw\",\"command\":\"COMMIT;\"}]";

        vertx.eventBus().consumer(FORMULAIRE_ADDRESS, message -> {
            JsonObject body = (JsonObject) message.body();
            ctx.assertEquals(TRANSACTION, body.getString(ACTION));
            ctx.assertEquals(expectedQuery, body.getJsonArray(STATEMENTS).toString());
            async.complete();
        });
        defaultQuestionSpecificFieldsService.update(questions, null);
    }
}
