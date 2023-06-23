package fr.openent.formulaire.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;

public interface MonitoringService {
    /**
     * Get all the form ids with duplicated positions for questions/sections
     */
    Future<JsonArray> getFormIdsWithPositionDuplicates();

    /**
     * List all the questions or sections with duplicated positions
     * @param formIds form ids with duplicated positions
     */
    Future<JsonArray> getPositionDuplicates(JsonArray formIds);

    /**
     * Reset duplicated position according to their id
     * @param nullyfiedElements ids of the nullyfied elements
     */
    Future<JsonArray> cleanPositionDuplicates(JsonArray nullyfiedElements);

    /**
     * Get all script files with their execution information.
     */
    Future<JsonArray> getScripts();
}
