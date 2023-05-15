package fr.openent.formulaire.service;

import fr.wseduc.webutils.Either;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

import java.util.List;

public interface DistributionService {
    /**
     * List all the distributions of the forms sent by me
     * @param user user connected
     * @param handler function handler returning JsonArray data
     */
    void listBySender(UserInfos user, Handler<Either<String, JsonArray>> handler);

    /**
     * List all the distributions of the forms sent to me
     * @param user user connected
     * @param handler function handler returning JsonArray data
     */
    void listByResponder(UserInfos user, Handler<Either<String, JsonArray>> handler);

    /**
     * List all the distributions of a specific form
     * @param formId form identifier
     * @param handler function handler returning JsonArray data
     */
    void listByForm(String formId, Handler<Either<String, JsonArray>> handler);

    /**
     * List all the distributions of specific forms
     * @param formIds form identifiers
     */
    Future<JsonArray> listByForms(JsonArray formIds);

    /**
     * List all the distributions for a specific form sent to me
     * @param formId form identifier
     * @param user user connected
     * @param handler function handler returning JsonArray data
     */
    void listByFormAndResponder(String formId, UserInfos user, Handler<Either<String, JsonArray>> handler);

    /**
     * List all the distributions of a specific form with specific status
     * @param formId form identifier
     * @param status status
     * @param nbLines number of lines already loaded
     * @param handler function handler returning JsonArray data
     */
    void listByFormAndStatus(String formId, String status, String nbLines, Handler<Either<String, JsonArray>> handler);

    /**
     * List all the distributions of a specific question of a specific form with specific status
     * @param formId form identifier
     * @param status status
     * @param questionId question identifier
     * @param nbLines number of lines already loaded
     * @param handler function handler returning JsonArray data
     */
    void listByFormAndStatusAndQuestion(String formId, String status, String questionId, String nbLines, Handler<Either<String, JsonArray>> handler);

    /**
     * Get the number of distributions for a specific form
     * @param formId form identifier
     * @param handler function handler returning JsonObject data
     */
    void countFinished(String formId, Handler<Either<String, JsonObject>> handler);

    /**
     * Get the number of distributions with TO_DO status for a specific form
     * @param formId form identifier
     * @param user user connected
     */
    Future<JsonObject> countMyToDo(String formId, UserInfos user);

    /**
     * Get the number of distributions with FINISHED status for a specific form
     * @param formId form identifier
     * @param user user connected
     */
    Future<JsonObject> countMyFinished(String formId, UserInfos user);

    /**
     * Get a specific distribution by id
     * @param distributionId distribution identifier
     * @param handler function handler returning JsonObject data
     */
    void get(String distributionId, Handler<Either<String, JsonObject>> handler);

    /**
     * Get a specific distribution by form, responder and status
     * @param formId form identifier
     * @param user user connected
     * @param handler function handler returning JsonObject data
     */
    void getByFormResponderAndStatus(String formId, UserInfos user, Handler<Either<String, JsonObject>> handler);

    /**
     * Create a new distribution based on an already existing one
     * @param distribution JsonObject data
     */
    Future<JsonObject> add(JsonObject distribution);

    /**
     * Duplicate a distribution by id
     * @param distributionId distribution identifier
     */
    Future<JsonObject> duplicateWithResponses(String distributionId);

    /**
     * Update a specific distribution
     * @param distributionId distribution identifier
     * @param distribution JsonObject data
     * @param handler function handler returning JsonObject data
     */
    void update(String distributionId, JsonObject distribution, Handler<Either<String, JsonObject>> handler);

    /**
     * Delete a specific distribution
     * @param distributionId distribution identifier
     * @param handler function handler returning JsonObject data
     */
    void delete(String distributionId, Handler<Either<String, JsonObject>> handler);

    /**
     *
     * @param formId form identifier
     * @param handler function handler returning JsonArray data
     */
    void getResponders(String formId, Handler<Either<String, JsonArray>> handler);

    /**
     * Distribute a form to a list of responders who do not already have respond right
     * @param formId form identifier
     * @param user user connected
     * @param newResponders responders to whom we give respond right
     * @param handler function handler returning JsonObject data
     */
    void createMultiple(String formId, UserInfos user, List<JsonObject> newResponders, Handler<Either<String, JsonObject>> handler);

    /**
     * Update active status of the wanted distributions for a specific form
     * @param active status value to give to all the distributions
     * @param formId form identifier
     * @param responder_ids ids of the responders of the distributions to update
     * @param handler function handler returning JsonObject data
     */
    void setActiveValue(boolean active, String formId, List<String> responder_ids, Handler<Either<String, JsonObject>> handler);

    /**
     * Delete distributions too old according to lifetime RGPD chosen
     * @param handler function handler returning JsonArray data
     */
    void deleteOldDistributions(Handler<Either<String, JsonArray>> handler);
}