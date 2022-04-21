package fr.openent.formulaire.core;

public enum ChoiceTypes {
  TXT("TXT"),
  IMAGE("IMAGE"),
  VIDEO("VIDEO");

  private final String name;

  ChoiceTypes(String name) {
    this.name = name;
  }

  public String getName() {
    return this.name;
  }
}

