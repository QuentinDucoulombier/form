package fr.openent.form.core.enums;

import java.util.ArrayList;
import java.util.List;

public enum QuestionTypes {
  FREETEXT(1),
  SHORTANSWER(2),
  LONGANSWER(3),
  SINGLEANSWER(4),
  MULTIPLEANSWER(5),
  DATE(6),
  TIME(7),
  FILE(8),
  SINGLEANSWERRADIO(9),
  MATRIX(10),
  CURSOR(11),
  RANKING(12);

  private final int code;

  QuestionTypes(int code) {
    this.code = code;
  }

  public int getCode() {
    return this.code;
  }

  /**
   * Use "Constants.GRAPH_QUESTIONS" instead.
   * This function should be only use in the Constants file to generate the GRAPH_QUESTIONS constant.
   *
   * @return A List<Integer> of codes corresponding to the graph questions
   */
  public static List<Integer> getGraphQuestions() {
    List<Integer> graphQuestions = new ArrayList<>();
    graphQuestions.add(QuestionTypes.SINGLEANSWER.getCode());
    graphQuestions.add(QuestionTypes.MULTIPLEANSWER.getCode());
    graphQuestions.add(QuestionTypes.SINGLEANSWERRADIO.getCode());
    graphQuestions.add(QuestionTypes.MATRIX.getCode());
    graphQuestions.add(QuestionTypes.CURSOR.getCode());
    graphQuestions.add(QuestionTypes.RANKING.getCode());
    return graphQuestions;
  }

  /**
   * Use "Constants.CONDITIONAL_QUESTIONS" instead.
   * This function should be only use in the Constants file to generate the CONDITIONAL_QUESTIONS constant.
   *
   * @return A List<Integer> of codes corresponding to the conditional questions
   */
  public static List<Integer> getConditionalQuestions() {
    List<Integer> conditionalQuestions = new ArrayList<>();
    conditionalQuestions.add(QuestionTypes.SINGLEANSWER.getCode());
    conditionalQuestions.add(QuestionTypes.MULTIPLEANSWER.getCode());
    conditionalQuestions.add(QuestionTypes.SINGLEANSWERRADIO.getCode());
    return conditionalQuestions;
  }

  /**
   * Use "Constants.CHOICES_TYPE_QUESTIONS" instead.
   * This function should be only use in the Constants file to generate the CONDITIONAL_QUESTIONS constant.
   *
   * @return A List<Integer> of codes corresponding to the questions type having choices
   */
  public static List<Integer> getChoicesTypeQuestions() {
    return getGraphQuestions();
  }

  /**
   * Use "Constants.QUESTIONS_WITHOUT_RESPONSES" instead.
   * This function should be only use in the Constants file to generate the QUESTIONS_WITHOUT_RESPONSES constant.
   *
   * @return A List<Integer> of codes corresponding to the questions type without responses
   */
  public static List<Integer> getQuestionsWithoutResponses() {
    List<Integer> questionsWithoutResponses = new ArrayList<>();
    questionsWithoutResponses.add(QuestionTypes.FREETEXT.getCode());
    questionsWithoutResponses.add(QuestionTypes.MATRIX.getCode());
    return questionsWithoutResponses;
  }

  /**
   * Use "Constants.FORBIDDEN_QUESTIONS" instead.
   * This function should be only use in the Constants file to generate the FORBIDDEN_QUESTIONS constant.
   *
   * @return A List<Integer> of codes corresponding to the questions type the user is not allowed to answer
   */
  public static List<Integer> getForbiddenQuestions() {
    List<Integer> forbiddenQuestions = new ArrayList<>();
    forbiddenQuestions.add(QuestionTypes.MATRIX.getCode());
    return forbiddenQuestions;
  }

  /**
   * Use "Constants.MATRIX_CHILD_QUESTIONS" instead.
   * This function should be only use in the Constants file to generate the MATRIX_CHILD_QUESTIONS constant.
   *
   * @return A List<Integer> of codes corresponding to the questions type allowed to be a matrix's child
   */
  public static List<Integer> getMatrixChildQuestions() {
    List<Integer> matrixChildQuestions = new ArrayList<>();
    matrixChildQuestions.add(QuestionTypes.SINGLEANSWERRADIO.getCode());
    matrixChildQuestions.add(QuestionTypes.MULTIPLEANSWER.getCode());
    return matrixChildQuestions;
  }

  /**
   * Use "Constants.QUESTIONS_WITH_SPECIFICS" instead.
   * This function should be only use in the Constants file to generate the QUESTIONS_WITH_SPECIFICS constant.
   *
   * @return A List<Integer> of codes corresponding to the questions type having specific fields
   */
  public static List<Integer> getQuestionsWithSpecificsFields() {
    List<Integer> questionsWithSpecifics = new ArrayList<>();
    questionsWithSpecifics.add(QuestionTypes.CURSOR.getCode());
    return questionsWithSpecifics;
  }
}