package fr.openent.formulaire.service.test.impl;

import fr.openent.formulaire.service.impl.DefaultFormService;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.entcore.common.sql.Sql;
import org.entcore.common.user.UserInfos;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import static fr.openent.form.core.constants.EbFields.FORMULAIRE_ADDRESS;
import static fr.openent.form.core.constants.Fields.*;
import static fr.openent.form.core.constants.ShareRights.MANAGER_RESOURCE_BEHAVIOUR;
import static fr.openent.form.core.constants.Tables.*;

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
        defaultFormService.listByIds(formIds, null);
    }

    @Test
    public void testListForLinkerWithEmptyGroupsAndUser(TestContext ctx) {
        Async async = ctx.async();
        List<String> groupsAndUserIds = new ArrayList<>();
        UserInfos user = new UserInfos();
        user.setUserId("userId");

        String expectedQuery = "SELECT f.* FROM " + FORM_TABLE + " f " +
                "LEFT JOIN " + FORM_SHARES_TABLE + " fs ON f.id = fs.resource_id " +
                "LEFT JOIN " + MEMBERS_TABLE + " m ON (fs.member_id = m.id AND m.group_id IS NOT NULL) " +
                "WHERE (f.owner_id = ?) AND f.archived = ? " +
                "AND (f.is_public = ? OR f.id IN (SELECT DISTINCT form_id FROM " + DISTRIBUTION_TABLE + " WHERE active = ?)) " +
                "GROUP BY f.id " +
                "ORDER BY f.date_modification DESC;";

        JsonArray expectedParams = new JsonArray().add(user.getUserId()).add(false).add(true).add(true);

        vertx.eventBus().consumer(FORMULAIRE_ADDRESS, message -> {
            JsonObject body = (JsonObject) message.body();
            ctx.assertEquals(PREPARED, body.getString(ACTION));
            ctx.assertEquals(expectedQuery, body.getString(STATEMENT));
            ctx.assertEquals(expectedParams.toString(), body.getJsonArray(VALUES).toString());
            async.complete();
        });

        defaultFormService.listForLinker(groupsAndUserIds, user)
            .onSuccess(result -> async.complete());

        async.awaitSuccess(10000);
    }

    @Test
    public void testListForLinkerWithGroupsAndUser(TestContext ctx) {
        Async async = ctx.async();
        List<String> groupsAndUserIds = new ArrayList<>();
        groupsAndUserIds.add("groupId");
        UserInfos user = new UserInfos();
        user.setUserId("userId");

        String expectedQuery = "SELECT f.* FROM " + FORM_TABLE + " f " +
                "LEFT JOIN " + FORM_SHARES_TABLE + " fs ON f.id = fs.resource_id " +
                "LEFT JOIN " + MEMBERS_TABLE + " m ON (fs.member_id = m.id AND m.group_id IS NOT NULL) " +
                "WHERE ((fs.member_id IN " + Sql.listPrepared(groupsAndUserIds.toArray()) + " AND fs.action = ?) OR f.owner_id = ?) " +
                "AND f.archived = ? AND (f.is_public = ? OR f.id IN (SELECT DISTINCT form_id FROM " + DISTRIBUTION_TABLE + " WHERE active = ?)) " +
                "GROUP BY f.id " +
                "ORDER BY f.date_modification DESC;";

        JsonArray expectedParams = new JsonArray();
        for (String groupOrUser : groupsAndUserIds) {
            expectedParams.add(groupOrUser);
        }
        expectedParams.add(MANAGER_RESOURCE_BEHAVIOUR).add(user.getUserId()).add(false).add(true).add(true);

        vertx.eventBus().consumer(FORMULAIRE_ADDRESS, message -> {
            JsonObject body = (JsonObject) message.body();
            ctx.assertEquals(PREPARED, body.getString(ACTION));
            ctx.assertEquals(expectedQuery, body.getString(STATEMENT));
            ctx.assertEquals(expectedParams.toString(), body.getJsonArray(VALUES).toString());
            async.complete();
        });

        defaultFormService.listForLinker(groupsAndUserIds, user)
                .onSuccess(result -> async.complete());

        async.awaitSuccess(10000);
    }

    @Test
    public void testListSentFormsOpeningToday(TestContext ctx) {
        Async async = ctx.async();

        String expectedQuery = "SELECT * FROM " + FORM_TABLE + " WHERE sent = ? " +
                "AND date_opening >= TO_CHAR(NOW(),'YYYY-MM-DD')::date " +
                "AND date_opening < TO_CHAR(NOW() + INTERVAL '1 day','YYYY-MM-DD')::date";
        JsonArray expectedParams = new JsonArray().add(true);

        vertx.eventBus().consumer(FORMULAIRE_ADDRESS, message -> {
            JsonObject body = (JsonObject) message.body();
            ctx.assertEquals(PREPARED, body.getString(ACTION));
            ctx.assertEquals(expectedQuery, body.getString(STATEMENT));
            ctx.assertEquals(expectedParams.toString(), body.getJsonArray(VALUES).toString());
            async.complete();
        });

        defaultFormService.listSentFormsOpeningToday()
                .onSuccess(result -> async.complete());

        async.awaitSuccess(10000);
    }

    @Test
    public void testCheckFormsRights(TestContext ctx) {
        Async async = ctx.async();
        UserInfos user = new UserInfos();
        user.setUserId("44444");
        List<String> groupsAndUserIds = new ArrayList<>();
        groupsAndUserIds.add("11111");
        groupsAndUserIds.add("22222");
        groupsAndUserIds.add("33333");

        List<String> formIds = new ArrayList<>();
        formIds.add("1");
        formIds.add("2");
        formIds.add("3");;
        String expectedQuery = "SELECT COUNT(DISTINCT f.id) FROM " + FORM_TABLE + " f " +
                "LEFT JOIN " + FORM_SHARES_TABLE + " fs ON fs.resource_id = f.id " +
                "WHERE ((member_id IN " + Sql.listPrepared(groupsAndUserIds)+ " AND action = ?) OR owner_id = ?) AND id IN " + Sql.listPrepared(formIds);
        JsonArray expectedParams = new JsonArray().addAll(new JsonArray(groupsAndUserIds)).add("manager").add(user.getUserId()).addAll(new JsonArray(formIds));

        vertx.eventBus().consumer(FORMULAIRE_ADDRESS, message -> {
            JsonObject body = (JsonObject) message.body();
            ctx.assertEquals(PREPARED, body.getString(ACTION));
            ctx.assertEquals(expectedQuery, body.getString(STATEMENT));
            ctx.assertEquals(expectedParams.toString(), body.getJsonArray(VALUES).toString());
            async.complete();
        });

        defaultFormService.checkFormsRights(groupsAndUserIds, user, "manager", new JsonArray(formIds), null);
        async.awaitSuccess(10000);
    }
}
