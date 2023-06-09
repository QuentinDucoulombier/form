package fr.openent.formulaire.export;

import fr.openent.form.core.enums.I18nKeys;
import fr.openent.form.core.enums.QuestionTypes;
import fr.openent.form.helpers.I18nHelper;
import fr.openent.formulaire.service.QuestionChoiceService;
import fr.openent.formulaire.service.QuestionService;
import fr.openent.formulaire.service.QuestionSpecificFieldsService;
import fr.openent.formulaire.service.SectionService;
import fr.openent.formulaire.service.impl.DefaultQuestionChoiceService;
import fr.openent.formulaire.service.impl.DefaultQuestionService;
import fr.openent.formulaire.service.impl.DefaultQuestionSpecificFieldsService;
import fr.openent.formulaire.service.impl.DefaultSectionService;
import fr.wseduc.webutils.data.FileResolver;
import fr.wseduc.webutils.http.Renders;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.pdf.Pdf;
import org.entcore.common.pdf.PdfFactory;
import org.entcore.common.pdf.PdfGenerator;
import org.entcore.common.storage.Storage;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static fr.openent.form.core.constants.ConfigFields.NODE_PDF_GENERATOR;
import static fr.openent.form.core.constants.Fields.*;
import static fr.openent.form.helpers.RenderHelper.renderInternalError;
import static fr.openent.form.helpers.UtilsHelper.*;

public class FormQuestionsExportPDF extends ControllerHelper {
    private static final Logger log = LoggerFactory.getLogger(FormQuestionsExportPDF.class);
    private String node;
    private final HttpServerRequest request;
    private final JsonObject config;
    private final Vertx vertx;
    private final Renders renders;
    private final JsonObject form;
    private final QuestionService questionService = new DefaultQuestionService();
    private final SectionService sectionService = new DefaultSectionService();
    private final QuestionSpecificFieldsService questionSpecificFieldsService = new DefaultQuestionSpecificFieldsService();
    private final QuestionChoiceService questionChoiceService = new DefaultQuestionChoiceService();
    private final PdfFactory pdfFactory;
    private JsonArray questionsInfos;
    private JsonArray sectionsInfos;



    public FormQuestionsExportPDF(HttpServerRequest request, Vertx vertx, JsonObject config, Storage storage, JsonObject form) {
        this.request = request;
        this.config = config;
        this.vertx = vertx;
        this.renders = new Renders(this.vertx, config);
        this.form = form;
        pdfFactory = new PdfFactory(vertx, new JsonObject().put(NODE_PDF_GENERATOR, config.getJsonObject(NODE_PDF_GENERATOR, new JsonObject())));
    }

    public JsonArray getQuestionsInfos(){
        return this.questionsInfos;
    }

    public void setQuestionsInfos(JsonArray questionsInfos){
        this.questionsInfos = questionsInfos;
    }

    public JsonArray getSectionsInfos(){
        return this.sectionsInfos;
    }

    public void setSectionsInfos(JsonArray sectionsInfos){
        this.sectionsInfos = sectionsInfos;
    }


    public void launch() {
        String formId = form.getInteger(ID).toString();
        fetchQuestionsInfos(formId);
        fetchSectionsInfos(formId);
        questionSpecificFieldsService.syncQuestionSpecs(questionsInfos);
        processSectionsAndQuestions(formId, questionsInfos, sectionsInfos);
    };


    private void fetchSectionsInfos(String formId) {
        // On récupère les propriétés des sections
        sectionService.list(formId)
                .onSuccess(this::setSectionsInfos)
                .onFailure(err -> {
                    log.error("[Formulaire@FormResponsesExportPDF::launch] Failed to retrieve all sections for the form with id " + formId);
                    renderInternalError(request, err.toString());
                });
    }

    private void fetchQuestionsInfos(String formId){
        // On récupère les propriétés des questions
        questionService.export(formId, true)
                .onSuccess(infos -> {
                    if(infos.isEmpty()) {
                        String errMessage = "[Formulaire@FormQuestionsExportPDF::launch] No questions found for form with id " + formId;
                        log.error(errMessage);
                        notFound(request, errMessage);
                    }else {
                        setQuestionsInfos(infos);
                    }
                })
                .onFailure(err -> {
                    log.error("[Formulaire@FormQuestionsExportPDF::launch] Failed to retrieve all questions for the form with id " + formId);
                    renderInternalError(request, err.toString());
                });
    }


    private void processSectionsAndQuestions(String formId, JsonArray questionsInfos, JsonArray sectionsInfos){
        JsonArray questionsIds = getIds(questionsInfos);
        JsonObject promiseInfos = new JsonObject();
        questionSpecificFieldsService.syncQuestionSpecs(questionsInfos)
                .compose(ignored -> questionChoiceService.listChoices(questionsIds))
                .compose(listChoices -> {
                    promiseInfos.put(QUESTIONS_CHOICES, listChoices);
                    return questionService.listChildren(questionsIds);
                })
                .onSuccess(listChildren -> {
                    JsonArray form_elements = new JsonArray();
                    Map<Integer, Integer> mapSectionIdPositionList = new HashMap<>();
                    Map<Integer, JsonObject> mapQuestions = new HashMap<>();
                    Map<Integer, JsonObject> mapSections = new HashMap<>();

                    for (int i = 0; i < sectionsInfos.size(); i++) {
                        JsonObject sectionInfo = sectionsInfos.getJsonObject(i);
                        sectionInfo.put(IS_SECTION, true)
                                .put(QUESTIONS, new JsonArray());
                        form_elements.add(sectionInfo);
                        mapSectionIdPositionList.put(sectionInfo.getInteger(ID), i);
                        int id = sectionInfo.getInteger(ID);
                        mapSections.put(id, sectionInfo);
                    }

                    for (int i = 0; i < questionsInfos.size(); i++) {
                        JsonObject question = questionsInfos.getJsonObject(i);
                        if (question.containsKey(SECTION_ID) && question.getInteger(SECTION_ID) == null) {
                            question.put(IS_QUESTION, true);
                            int id = question.getInteger(ID);
                            mapQuestions.put(id, question);
                        }

                        if (question.containsKey(SECTION_ID) && question.getInteger(SECTION_ID) != null) {
                            Integer sectionId = question.getInteger(SECTION_ID);
                            Integer positionSectionFormElt = mapSectionIdPositionList.get(sectionId);
                            JsonObject section = form_elements.getJsonObject(positionSectionFormElt);
                            section.getJsonArray(QUESTIONS).add(question);
                        }

                        for (int j = 0; j < promiseInfos.getJsonArray(QUESTIONS_CHOICES).size(); j++) {
                            JsonObject choice = promiseInfos.getJsonArray(QUESTIONS_CHOICES).getJsonObject(j);

                            if (Objects.equals(choice.getInteger(QUESTION_ID), question.getInteger(ID))) {
                                if (!question.containsKey(CHOICES)) {
                                    JsonArray choicesArray = new JsonArray();

                                    choicesArray.add(choice);
                                    question.put(CHOICES, choicesArray);

                                    if (question.containsKey(CONDITIONAL) && question.getBoolean(CONDITIONAL)) {
                                        question.put(IS_CONDITIONAL, true);
                                    }
                                } else {
                                    // If already exist, add choice to JsonArray
                                    JsonArray choicesArray = question.getJsonArray(CHOICES);
                                    choicesArray.add(choice);
                                }
                            }
                        }

                        for (JsonObject q : mapQuestions.values()) {
                            if (q.containsKey(CONDITIONAL) && q.getBoolean(CONDITIONAL)) {
                                Integer nextQuestionId;
                                JsonArray choices = q.getJsonArray(CHOICES);
                                if (choices != null) {
                                    for (int j = 0; j < choices.size(); j++) {
                                        JsonObject choice = choices.getJsonObject(j);
                                        if (choice.containsKey(NEXT_FORM_ELEMENT_ID)) {
                                            nextQuestionId = choice.getInteger(NEXT_FORM_ELEMENT_ID);
                                            if (nextQuestionId != null) {
                                                JsonObject nextQuestion = mapQuestions.get(nextQuestionId);
                                                if (nextQuestion != null) {
                                                    choice.put(TITLE_NEXT, nextQuestion.getString(TITLE));
                                                }
                                            }
                                            if (nextQuestionId == null) {
                                                choice.put(TITLE_NEXT, I18nHelper.getI18nValue(I18nKeys.END_FORM, request));
                                            }
                                        }
                                    }
                                }
                            }
                        }


                        for (int k = 0; k < listChildren.size(); k ++) {
                            JsonObject child = listChildren.getJsonObject(k);
                            if (Objects.equals(child.getInteger(MATRIX_ID), question.getInteger(ID))) {
                                if (question.containsKey(CHILDREN)) {
                                    question.getJsonArray(CHILDREN).add(child);
                                    if (Integer.valueOf(QuestionTypes.SINGLEANSWERRADIO.getCode()).equals(child.getInteger(QUESTION_TYPE))) {
                                        question.put(IS_MATRIX_SINGLE, true);
                                    }
                                    if (Integer.valueOf(QuestionTypes.MULTIPLEANSWER.getCode()).equals(child.getInteger(QUESTION_TYPE))) {
                                        question.put(IS_MATRIX_MULTIPLE, true);
                                    }
                                } else {
                                    question.put(CHILDREN, new JsonArray().add(child));
                                }
                            }
                        }

                        switch (QuestionTypes.values()[question.getInteger(QUESTION_TYPE) - 1]) {
                            case FREETEXT:
                                question.put(TYPE_FREETEXT, true);
                                break;
                            case SHORTANSWER:
                                question.put(SHORT_ANSWER, true);
                                break;
                            case LONGANSWER:
                                question.put(LONG_ANSWER, true);
                                break;
                            case SINGLEANSWERRADIO:
                            case SINGLEANSWER:
                                question.put(RADIO_BTN, true);
                                break;
                            case MULTIPLEANSWER:
                                question.put(MULTIPLE_CHOICE, true);
                                break;
                            case DATE:
                            case TIME:
                                question.put(DATE_HOUR, true);
                                break;
                            case MATRIX:
                                question.put(IS_MATRIX, true);
                                break;
                            case CURSOR:
                                question.put(IS_CURSOR, true);
                                break;
                            case RANKING:
                                question.put(IS_RANKING, true);
                                break;
                            default:
                                break;
                        }
                        form_elements.add(question);
                    }

                    // Sort sections & questions to display it in the right order
                    List<JsonObject> sorted_form_elements = form_elements.getList();
                    sorted_form_elements.removeIf(element -> element.getInteger(POSITION) == null);
                    Collections.sort(sorted_form_elements, Comparator.nullsFirst(Comparator.comparingInt(a -> a.getInteger(POSITION))));

                    JsonObject results = new JsonObject()
                            .put(FORM_ELEMENTS, sorted_form_elements)
                            .put(FORM_TITLE, form.getString(TITLE));

                    generatePDF(request, results,"questions.xhtml", pdf ->
                            request.response()
                                    .putHeader("Content-Type", "application/pdf; charset=utf-8")
                                    .putHeader("Content-Disposition", "attachment; filename=Questions_" + form.getString(TITLE) + ".pdf")
                                    .end(pdf)
                    );
                })
                .onFailure(error -> {
                    String errMessage = String.format("[Formulaire@FormQuestionsExportPDF::launch]  " +
                                    "No questions found for form with id: %s" + formId,
                            this.getClass().getSimpleName(), error.getMessage());
                    log.error(errMessage);
                });
    }

    private void generatePDF(HttpServerRequest request, JsonObject templateProps, String templateName, Handler<Buffer> handler) {
        Promise<Pdf> promise = Promise.promise();
        final String templatePath = "./public/template/pdf/";
        final String baseUrl = getScheme(request) + "://" + Renders.getHost(request) + config.getString("app-address") + "/public/";

        final String path = FileResolver.absolutePath(templatePath + templateName);

        vertx.fileSystem().readFile(path, result -> {
            if (!result.succeeded()) {
                badRequest(request);
                return;
            }

            StringReader reader = new StringReader(result.result().toString(StandardCharsets.UTF_8));
            renders.processTemplate(request, templateProps, templateName, reader, writer -> {
                String processedTemplate = ((StringWriter) writer).getBuffer().toString();
                if (processedTemplate.isEmpty()) {
                    badRequest(request);
                    return;
                }
                JsonObject actionObject = new JsonObject();
                byte[] bytes;
                bytes = processedTemplate.getBytes(StandardCharsets.UTF_8);

                node = (String) vertx.sharedData().getLocalMap(SERVER).get(NODE);
                if (node == null) {
                    node = "";
                }

                actionObject.put(CONTENT, bytes).put(BASE_URL, baseUrl);
                generatePDF(TITLE, processedTemplate)
                        .onSuccess(res -> {
                            handler.handle(res.getContent());
                        })
                        .onFailure(error -> {
                            String message = "[Formulaire@FormQuestionsExportPDF::generatePDF] Failed to generatePDF : " + error.getMessage();
                            log.error(message);
                            promise.fail(message);
                        });
            });
        });
    }

    public Future<Pdf> generatePDF(String filename, String buffer) {
        Promise<Pdf> promise = Promise.promise();
        try {
            PdfGenerator pdfGenerator = pdfFactory.getPdfGenerator();
            pdfGenerator.generatePdfFromTemplate(filename, buffer, ar -> {
                if (ar.failed()) {
                    log.error("[Formulaire@FormQuestionsExportPDF::generatePDF] Failed to generatePdfFromTemplate : " + ar.cause().getMessage());
                    promise.fail(ar.cause().getMessage());
                } else {
                    promise.complete(ar.result());
                }
            });
        }
        catch (Exception e) {
            log.error("[Formulaire@FormQuestionsExportPDF::generatePDF] Failed to generatePDF: " + e.getMessage());
            promise.fail(e.getMessage());
        }
        return promise.future();
    }
}
