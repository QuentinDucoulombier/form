package fr.openent.formulaire.export;

import fr.openent.formulaire.service.DistributionService;
import fr.openent.formulaire.service.QuestionService;
import fr.openent.formulaire.service.ResponseService;
import fr.openent.formulaire.service.SectionService;
import fr.openent.formulaire.service.impl.DefaultDistributionService;
import fr.openent.formulaire.service.impl.DefaultQuestionService;
import fr.openent.formulaire.service.impl.DefaultResponseService;
import fr.openent.formulaire.service.impl.DefaultSectionService;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.data.FileResolver;
import fr.wseduc.webutils.http.Renders;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.storage.Storage;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import org.entcore.common.pdf.Pdf;
import org.entcore.common.pdf.PdfFactory;
import org.entcore.common.pdf.PdfGenerator;

import static fr.openent.form.core.constants.Constants.MAX_RESPONSES_EXPORT_PDF;
import static fr.openent.form.helpers.RenderHelper.renderInternalError;
import static fr.wseduc.webutils.http.Renders.*;


public class FormResponsesExportPDF {
    private static final Logger log = LoggerFactory.getLogger(FormResponsesExportPDF.class);
    private String node;
    private final HttpServerRequest request;
    private final JsonObject config;
    private final Vertx vertx;
    private final Storage storage;
    private final Renders renders;
    private final JsonObject form;
    private final ResponseService responseService = new DefaultResponseService();
    private final QuestionService questionService = new DefaultQuestionService();
    private final SectionService sectionService = new DefaultSectionService();
    private final DistributionService distributionService = new DefaultDistributionService();
    private final SimpleDateFormat dateGetter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("dd/MM/yyyy HH:mm");
    private final PdfFactory pdfFactory;

    public FormResponsesExportPDF(HttpServerRequest request, Vertx vertx, JsonObject config, Storage storage, JsonObject form) {
        this.request = request;
        this.config = config;
        this.vertx = vertx;
        this.storage = storage;
        this.renders = new Renders(this.vertx, config);
        this.form = form;
        dateFormatter.setTimeZone(TimeZone.getTimeZone("Europe/Paris")); // TODO to adapt for not France timezone
        pdfFactory = new PdfFactory(vertx, new JsonObject().put("node-pdf-generator",
                config.getJsonObject("node-pdf-generator", new JsonObject())));
    }

    public void launch() {
        String formId = form.getInteger("id").toString();
        questionService.export(formId, true, getQuestionsEvt -> {
            if (getQuestionsEvt.isLeft()) {
                log.error("[Formulaire@FormExportPDF] Failed to retrieve all questions for the form with id " + formId);
                renderInternalError(request, getQuestionsEvt);
                return;
            }
            if (getQuestionsEvt.right().getValue().isEmpty()) {
                String message = "[Formulaire@FormExportPDF] No questions found for form with id " + formId;
                log.error(message);
                notFound(request, message);
                return;
            }

            JsonArray questionsInfo = getQuestionsEvt.right().getValue();
            sectionService.list(formId, getSectionsEvt -> {
                if (getSectionsEvt.isLeft()) {
                    log.error("[Formulaire@FormExportPDF] Failed to retrieve all sections for the form with id " + formId);
                    renderInternalError(request, getSectionsEvt);
                    return;
                }

                JsonArray sectionsInfos = getSectionsEvt.right().getValue();
                distributionService.countFinished(form.getInteger("id").toString(), countRepEvt -> {
                    if (countRepEvt.isLeft()) {
                        log.error("[Formulaire@FormExportPDF] Failed to count nb responses for the form with id " + formId);
                        renderInternalError(request, countRepEvt);
                        return;
                    }
                    if (countRepEvt.right().getValue().isEmpty()) {
                        String message = "[Formulaire@FormExportPDF] No distributions found for form with id " + formId;
                        log.error(message);
                        notFound(request, message);
                        return;
                    }

                    int nbResponseTot = countRepEvt.right().getValue().getInteger("count");
                    boolean hasTooManyResponses = nbResponseTot > MAX_RESPONSES_EXPORT_PDF;

                    responseService.exportPDFResponses(formId, getResponsesEvt -> {
                        if (getResponsesEvt.isLeft()) {
                            log.error("[Formulaire@FormExportPDF] Failed to get data for PDF export for the form with id " + formId);
                            renderInternalError(request, getResponsesEvt);
                            return;
                        }
                        if (getResponsesEvt.right().getValue().isEmpty()) {
                            String message = "[Formulaire@FormExportPDF] No responses found for form with id " + formId;
                            log.error(message);
                            notFound(request, message);
                            return;
                        }

                        formatData(getResponsesEvt.right().getValue(), questionsInfo, sectionsInfos, hasTooManyResponses, formatDataEvt -> {
                            if (formatDataEvt.isLeft()) {
                                log.error("[Formulaire@FormExportPDF] Failed to format data for the form with id" + formId);
                                renderInternalError(request, formatDataEvt);
                                return;
                            }

                            JsonObject results = formatDataEvt.right().getValue();
                            results.put("nbResponseTot", nbResponseTot);

                            generatePDF(request, results,"results.xhtml", pdf ->
                                request.response()
                                        .putHeader("Content-Type", "application/pdf; charset=utf-8")
                                        .putHeader("Content-Disposition", "attachment; filename=Réponses_" + form.getString("title") + ".pdf")
                                        .end(pdf)
                            );
                        });
                    });
                });
            });
        });
    }

    private void formatData(JsonArray data, JsonArray questionsInfo, JsonArray sectionsInfos, boolean hasTooManyResponses, Handler<Either<String, JsonObject>> handler) {
        JsonObject results = new JsonObject();
        JsonArray questions = new JsonArray();
        List<Future> questionsGraphs = new ArrayList<>();

        // Fill final object with question's data
        int nbQuestions = questionsInfo.size();
        for (int i = 0; i < nbQuestions; i++) {
            JsonObject questionInfo = questionsInfo.getJsonObject(i);

            int question_type = questionInfo.getInteger("question_type");
            boolean isGraph = Arrays.asList(4,5,9).contains(question_type);

            if (!hasTooManyResponses || isGraph) {
                questions.add(new JsonObject()
                        .put("id", questionInfo.getInteger("id"))
                        .put("title", questionInfo.getString("title"))
                        .put("question_type", new JsonObject())
                        .put("statement",
                                "<div>" + questionInfo.getString("statement", "")
                                .replace("\"","'")
                                .replace("<o:p></o:p>", " ")
                                + "</div>"
                        )
                        .put("mandatory", questionInfo.getBoolean("mandatory"))
                        .put("section_id", questionInfo.getInteger("section_id"))
                        .put("position", questionInfo.getInteger("position"))
                        .put("responses", new JsonArray())
                );

                // Affect boolean to each type of answer (freetext, simple text, graph)
                // type_freetext (FREETEXT), type_text (SHORTANSWER, LONGANSWER, DATE, TIME, FILE), type_graph (SINGLEANSWER, MULTIPLEANSWER)
                questions.getJsonObject(questions.size() - 1).getJsonObject("question_type")
                    .put("question_type_id", question_type)
                    .put("type_freetext", question_type == 1)
                    .put("type_text", Arrays.asList(2,3,6,7,8).contains(question_type))
                    .put("type_graph", isGraph);


                // Prepare futures to get graph images
                if (Arrays.asList(4,5,9).contains(question_type)) {
                    questionsGraphs.add(Future.future());
                    getGraphData(questionInfo, questionsGraphs.get(questionsGraphs.size() - 1));
                }
            }
        }

        // Make a Map from the questions
        HashMap<Integer, JsonObject> mapQuestions = new HashMap<>();
        for (Object q : questions) {
            JsonObject question = (JsonObject)q;
            mapQuestions.put(question.getInteger("id"), question);
        }

        if (!hasTooManyResponses) {
            // Fill each question with responses' data
            for (int i = 0; i < data.size(); i++) {
                JsonObject response = data.getJsonObject(i);

                // Format answer (empty string, simple text, html)
                int questionType = mapQuestions.get(response.getInteger("question_id")).getJsonObject("question_type").getInteger("question_type_id");
                if (response.getString("answer").isEmpty()) {
                    response.put("answer", "-");
                }
                if (questionType == 1) {
                    response.put("answer",
                            "<div>" + response.getString("answer", "")
                                    .replace("<o:p></o:p>", " ")
                            + "</div>");
                }
                if (questionType == 2) {
                    response.put("answer", "<div>" + response.getString("answer") + "</div>");
                }
                else if (questionType == 3) {
                    response.put("answer", response.getString("answer", "").replace("\"","'"));
                }

                // Format date_response
                try {
                    Date displayDate = dateGetter.parse(response.getString("date_response"));
                    response.put("date_response", dateFormatter.format(displayDate));
                }
                catch (ParseException e) { e.printStackTrace(); }

                mapQuestions.get(response.getInteger("question_id")).getJsonArray("responses").add(response);
            }
        }

        // Get graph images, affect them to their respective questions and send the result
        CompositeFuture.all(questionsGraphs).onComplete(evt -> {
            if (evt.failed()) {
                log.error("[Formulaire@FormExportPDF] Failed to retrieve graphs' data : " + evt.cause());
                Future.failedFuture(evt.cause());
                return;
            }

            // Affect graph images to corresponding questions (it is sorted so we just have to do it by following the order)
            int j = 0;
            for (int i = 0; i < questions.size(); i++) {
                JsonObject question = questions.getJsonObject(i);
                if (question.getJsonObject("question_type").getBoolean("type_graph")) {
                    String graphData = questionsGraphs.get(j).result().toString();
                    question.put("graphData", graphData);
                    j++;
                }
            }

            JsonArray form_elements = fillFormElements(sectionsInfos, questions);

            // Finish to fill final object with useful form's data
            results.put("form_title", form.getString("title"));
            results.put("anonymous", form.getBoolean("anonymous"));
            results.put("form_elements", form_elements);
            handler.handle(new Either.Right<>(results));
        });
    }

    private JsonArray fillFormElements(JsonArray sections, JsonArray questions) {
        HashMap<Integer, JsonObject> mapSectionsId = new HashMap<>();
        HashMap<Integer, JsonObject> mapSectionsPosition = new HashMap<>();
        for (Object s : sections) {
            JsonObject section = (JsonObject)s;
            section.put("description",
                    "<div>" + section.getString("description")
                    .replace("\"","'")
                    .replace("<o:p></o:p>", " ")
                    + "</div>"
            );
            section.put("questions", new JsonArray());
            mapSectionsId.put(section.getInteger("id"), section);
            mapSectionsPosition.put(section.getInteger("position"), section);
        }

        SortedMap<Integer, JsonObject> form_elements = new TreeMap<>();
        int i = 0;
        while (i < questions.size()) {
            JsonObject question = questions.getJsonObject(i);
            if (question.getInteger("section_id") != null) {
                JsonObject section = mapSectionsId.get(question.getInteger("section_id"));
                section.getJsonArray("questions").add(question);
            }
            else {
                question.put("is_question", true);
                form_elements.put(question.getInteger("position"), question);
            }
            i++;
        }
        form_elements.putAll(mapSectionsPosition);

        return new JsonArray(new ArrayList<>(form_elements.values()));
    }

    private void getGraphData(JsonObject question, Handler<AsyncResult<String>> handler) {
        String idFile = form.getJsonObject("images").getJsonObject("idImagesPerQuestion").getString(question.getLong("id").toString());

        if (idFile != null) {
            storage.readFile(idFile, readFileEvt -> {
                String graph = readFileEvt.getString(0, readFileEvt.length());
                handler.handle(Future.succeededFuture(graph));
            });
        }
        else {
            log.error("[Formulaire@getImage] : Wrong file id.");
            handler.handle(Future.succeededFuture("Wrong file id"));
        }
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

                node = (String) vertx.sharedData().getLocalMap("server").get("node");
                if (node == null) {
                    node = "";
                }

                actionObject.put("content", bytes).put("baseUrl", baseUrl);
                generatePDF("title", processedTemplate)
                    .onSuccess(res -> {
                        handler.handle(res.getContent());

                        // Remove image files generated for graph display on PDF
                        JsonArray removesFiles = templateProps.getJsonArray("idImagesFiles");
                        if (removesFiles != null) {
                            storage.removeFiles(removesFiles, removeEvt -> {
                                log.info(" [Formulaire@generatePDF] " + removeEvt.encode());
                            });
                        }
                    })
                    .onFailure(error -> {
                        String message = "[Formulaire@generatePDF] Failed to generatePDF : " + error.getMessage();
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
                    log.error("[Formulaire@generatePDF] Failed to generatePdfFromTemplate : " + ar.cause().getMessage());
                    promise.fail(ar.cause().getMessage());
                } else {
                    promise.complete(ar.result());
                }
            });
        }
        catch (Exception e) {
            log.error("[Formulaire@generatePDF] Failed to generatePDF: " + e.getMessage());
            promise.fail(e.getMessage());
        }
        return promise.future();
    }
}