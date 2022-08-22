package fr.openent.formulaire.service.test.impl;

import fr.openent.formulaire.service.impl.DefaultFormService;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.entcore.common.sql.Sql;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static fr.openent.form.core.constants.EbFields.FORMULAIRE_ADDRESS;
import static fr.openent.form.core.constants.Fields.*;
import static fr.openent.form.core.constants.Tables.FORM_TABLE;

@RunWith(VertxUnitRunner.class)
public class DefaultFormServiceTest {
    private Vertx vertx;
    private DefaultFormService defaultFormService;

    @Before
    public void setUp(){
        vertx = Vertx.vertx();
        defaultFormService = new DefaultFormService();
        Sql.getInstance().init(vertx.eventBus(), FORMULAIRE_ADDRESS);
    }

    @Test
    public void testListByIds(TestContext ctx) {
        Async async = ctx.async();
        JsonArray formIds = new JsonArray().add("1").add("2").add("3");
        String expectedQuery = "SELECT * FROM " + FORM_TABLE + " WHERE id IN " + Sql.listPrepared(formIds) + ";";
        JsonArray expectedParams = new JsonArray().addAll(formIds);

        vertx.eventBus().consumer(FORMULAIRE_ADDRESS, message -> {
            JsonObject body = (JsonObject) message.body();
            ctx.assertEquals(PREPARED, body.getString(ACTION));
            ctx.assertEquals(expectedQuery, body.getString(STATEMENT));
            ctx.assertEquals(expectedParams.toString(), body.getJsonArray(VALUES).toString());
            async.complete();
        });
        defaultFormService.listByIds(formIds);
    }
}
