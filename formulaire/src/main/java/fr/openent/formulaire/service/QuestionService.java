package fr.openent.formulaire.service;

import fr.wseduc.webutils.Either;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public interface QuestionService {
    /**
     * List all the questions out of sections of a specific form
     * @param formId form identifier
     * @param handler function handler returning JsonArray data
     */
    void listForForm(String formId, Handler<Either<String, JsonArray>> handler);

    /**
     * List all the questions out of sections of a specific form
     * @param formId form identifier
     */
    Future<JsonArray> listForForm(String formId);

    /**
     * List all the questions in a specific section
     * @param sectionId section identifier
     * @param handler function handler returning JsonArray data
     */
    void listForSection(String sectionId, Handler<Either<String, JsonArray>> handler);

    /**
     * List all the questions (in and out of sections) of a specific form
     * @param formId section identifier
     */
    Future<JsonArray> listForFormAndSection(String formId);

    /**
     * List all the children questions of a list of questions
     * @param questionIds JsonArray identifier
     */
    Future<JsonArray> listChildren(JsonArray questionIds);

    /**
     * List all the children questions of a list of questions
     * @param questionIds question identifiers
     * @param handler function handler returning JsonArray data
     */
    void listChildren(JsonArray questionIds, Handler<Either<String, JsonArray>> handler);

    /**
     * List all the questions of a specific form without freetext questions
     * @param formId form identifier
     * @param isPdf is export for PDF
     * @param handler function handler returning JsonArray data
     */
    void export(String formId, boolean isPdf, Handler<Either<String, JsonArray>> handler);


    /**
     * List all the questions of a specific form without freetext questions
     * @param formId form identifier
     * @param isPdf is export for PDF
     */
    Future<JsonArray> export(String formId, boolean isPdf);

    /**
     * Get a specific question by id
     * @param questionId question identifier
     * @param handler function handler returning JsonObject data
     */
    void get(String questionId, Handler<Either<String, JsonObject>> handler);

    /**
     * Get the section ids with conditional questions for a specific form
     * @param formId form identifier
     * @param questionIds question identifiers to omit
     * @param handler function handler returning JsonArray data
     */
    void getSectionIdsWithConditionalQuestions(String formId, JsonArray questionIds, Handler<Either<String, JsonArray>> handler);

    /**
     * Get the section ids of a specific form thanks to a question id
     * @param questionId question identifier
     * @param handler function handler returning JsonArray data
     */
    void getSectionIdsByForm(String questionId, Handler<Either<String, JsonArray>> handler);

    /**
     * Get form position corresponding to a specific question
     * @param questionId question identifier
     * @param handler function handler returning JsonObject data
     */
    void getFormPosition(String questionId, Handler<Either<String, JsonObject>> handler);

    /**
     * Create a question in a specific form
     * @param question JsonObject data
     * @param formId form identifier
     * @param handler function handler returning JsonObject data
     */
    void create(JsonObject question, String formId, Handler<Either<String, JsonObject>> handler);

    /**
     * Create a question in a specific form
     * @param question JsonObject data
     * @param formId form identifier
     */
    Future<JsonObject> create(JsonObject question, String formId);

    /**
     * Update specific questions
     * @param formId question identifier
     * @param questions JsonArray data
     * @param handler function handler returning JsonArray data
     */
    void update(String formId, JsonArray questions, Handler<Either<String, JsonArray>> handler);

    /**
     * Update specific questions
     * @param formId question identifier
     * @param questions JsonArray data
     */
    Future<JsonArray> update(String formId, JsonArray questions);

    /**
     * Delete a specific question
     * @param question question
     * @param handler function handler returning JsonObject data
     */
    void delete(JsonObject question, Handler<Either<String, JsonObject>> handler);
}
