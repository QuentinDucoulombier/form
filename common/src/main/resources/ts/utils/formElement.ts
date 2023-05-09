import {
    Distribution,
    Form,
    FormElement,
    FormElements,
    Question, QuestionChoice,
    Questions,
    Response,
    Responses,
    Section
} from "../models";
import {Mix} from "entcore-toolkit";
import {Direction} from "../core/enums";
import {angular, idiom, notify} from "entcore";
import {formElementService, questionService} from "../services";
import {PropPosition} from "@common/core/enums/prop-position";

export class FormElementUtils {
    static castFormElement = (formElement: any) : Question|Section => {
        if (formElement.statement !== undefined) {
            return Mix.castAs(Question, formElement);
        }
        else {
            return Mix.castAs(Section, formElement);
        }
    };

    /**
     * @deprecated Should instead use method isQuestion() from FormElement model directly
     */
    static isQuestion = (formElement: FormElement) : boolean => {
        return formElement instanceof Question;
    };

    static switchPositions = (elements: any, index: number, direction: string, propPosition: PropPosition) : void => {
        switch (direction) {
            case Direction.UP: {
                elements.all[index][propPosition]--;
                elements.all[index - 1][propPosition]++;
                break;
            }
            case Direction.DOWN: {
                elements.all[index][propPosition]++;
                elements.all[index + 1][propPosition]--;
                break;
            }
            default:
                notify.error(idiom.translate('formulaire.error.question.reorganization'));
                break;
        }
    }

    // Checking functions for validation form ending

    static hasRespondedLastQuestion = async (form: Form, distribution: Distribution) : Promise<boolean> => {
        let formElements = new FormElements();
        let responses = new Responses();
        await formElements.sync(form.id);
        if (FormElementUtils.isElementAValidLast(formElements)) {
            return true;
        }
        else {
            let lastQuestions = FormElementUtils.getConditionalQuestionsTargetingEnd(formElements);
            await responses.syncByDistribution(distribution.id);
            return responses.all.filter((r: Response) => FormElementUtils.isValidLastResponse(r, lastQuestions)).length > 0;
        }
    };

    static isElementAValidLast = (formElements: FormElements) : boolean => {
        let lastElement = formElements.all[formElements.all.length - 1];
        // Last question should not be conditional or should have all choices targeting end
        let isLastElementValidQuestion = lastElement instanceof Question && (!lastElement.conditional || lastElement.choices.all.every((c: QuestionChoice) => !c.next_form_element_id));
        // Last section should have one conditional question with all choices targeting end
        let isLastElementValidSectionAndHasConditionalQuestion = lastElement instanceof Section
            && lastElement.questions.all.filter((q: Question) => q.conditional).length === 1
            && lastElement.questions.all.filter((q: Question) => q.conditional)[0].choices.all.every((c: QuestionChoice) => !c.next_form_element_id);
        // Or last section should have no conditional question but a next_form_element_id targeting end (= null)
        let isLastElementValidSectionAndHasNotConditionalQuestion = lastElement instanceof Section
            && lastElement.questions.all.filter((q: Question) => q.conditional).length === 0
            && lastElement.next_form_element_id == null;
        return isLastElementValidQuestion || isLastElementValidSectionAndHasConditionalQuestion || isLastElementValidSectionAndHasNotConditionalQuestion;
    };

    static getConditionalQuestionsTargetingEnd = (formElements: FormElements) : any => {
        let lastQuestions = [];
        for (let e of formElements.all) {
            if (e instanceof Question) {
                if (e.conditional && e.choices.all.filter((c: QuestionChoice) => !c.next_form_element_id).length > 0) {
                    lastQuestions.push(e);
                }
            }
            else if (e instanceof Section) {
                let conditionalQuestions = e.questions.all.filter((q: Question) => q.conditional);
                if (conditionalQuestions.length == 0 && e.next_form_element_id == null) {
                    lastQuestions.concat(e.questions.all);
                }
                else if (conditionalQuestions.length === 1 && conditionalQuestions[0].choices.all.filter((c: QuestionChoice) => !c.next_form_element_id).length > 0) {
                    lastQuestions.push(conditionalQuestions[0]);
                }
            }
        }
        return lastQuestions;
    };

    static isValidLastResponse = (response: Response, lastQuestions: any) : boolean => {
        let matchingQuestions = lastQuestions.filter((q: Question) => q.id === response.question_id);
        let question = matchingQuestions.length > 0 ? matchingQuestions[0] : null;
        return question && (question.conditional ? !!response.answer : true);
    };

    // Drag and drop

    static onEndDragAndDrop = async (evt: any, formElements: FormElements) : Promise<void> => {
        let elem = evt.item.firstElementChild.firstElementChild;
        let scopeElem = angular.element(elem).scope().vm;
        let itemId: number = scopeElem.question ? scopeElem.question.id : scopeElem.section.id;
        let newSectionId: number = evt.to.id.split("-")[1] != "0" ? parseInt(evt.to.id.split("-")[1]) : null;
        let oldContainerId: number = evt.from.id.split("-")[1] != "0" ? parseInt(evt.from.id.split("-")[1]) : null;
        let oldSection: Section = oldContainerId ? (formElements.all.filter((e: FormElement) => e instanceof Section && e.id === oldContainerId)[0]) as Section : null;
        let item: any = null;
        if (scopeElem.section) {
            item = formElements.all.filter((e: FormElement) => e.id === itemId)[0] as Section;
        }
        else {
            let oldSiblings: any = oldSection ? oldSection.questions : formElements;
            item = oldSiblings.all.filter((e: FormElement) => e.id === itemId)[0] as Question;
        }
        let oldIndex: number = evt.oldIndex;
        let newIndex: number = evt.newIndex;
        let indexes: any = FormElementUtils.getStartEndIndexes(newIndex, oldIndex);

        // We cannot move a section into another section
        if (newSectionId && item instanceof Section) {
            return;
        }

        if (!newSectionId) {
            if (oldSection) { // Item moved FROM oldSection TO vm.formElements
                FormElementUtils.updateSiblingsPositions(oldSection.questions, false, null, oldIndex);
                FormElementUtils.updateSiblingsPositions(formElements, true, null, newIndex);
                item.position = newIndex + 1;
                item.section_id = null;
                item.section_position = null;
                oldSection.questions.all = oldSection.questions.all.filter((q: Question) => q.id != item.id);
                formElements.all.push(item);
                FormElementUtils.rePositionFormElements(oldSection.questions, PropPosition.SECTION_POSITION);
                FormElementUtils.rePositionFormElements(formElements, PropPosition.POSITION);
                FormElementUtils.updateNextFormElementValues(formElements);
                await formElementService.update(FormElementUtils.getSectionsAndInsideConditionalQuestions(formElements));
                await questionService.update(oldSection.questions.all);
            }
            else { // Item moved FROM vm.formElements TO vm.formElements
                FormElementUtils.updateSiblingsPositions(formElements, true, indexes.goUp, indexes.startIndex, indexes.endIndex);
                item.position = newIndex + 1;
                item.section_id = null;
                item.section_position = null;
                FormElementUtils.rePositionFormElements(formElements, PropPosition.POSITION);
                FormElementUtils.updateNextFormElementValues(formElements);
                await formElementService.update(FormElementUtils.getSectionsAndInsideConditionalQuestions(formElements));
            }
        }
        else {
            let newSection: Section = (formElements.all.filter((e: FormElement) => e instanceof Section && e.id === newSectionId)[0]) as Section;
            if (oldSection) { // Item moved FROM oldSection TO section with id 'newSectionId'
                if (newSection.id != oldSection.id) {
                    FormElementUtils.updateSiblingsPositions(oldSection.questions, false, null, oldIndex);
                    FormElementUtils.updateSiblingsPositions(newSection.questions, true, null, newIndex);
                }
                else {
                    FormElementUtils.updateSiblingsPositions(newSection.questions, true, indexes.goUp, indexes.startIndex, indexes.endIndex);
                }
                item.position = null;
                item.section_id = newSectionId;
                item.section_position = newIndex + 1;
                if (newSection.id != oldSection.id) {
                    oldSection.questions.all = oldSection.questions.all.filter((q: Question) => q.id != item.id);
                    newSection.questions.all.push(item);
                    FormElementUtils.rePositionFormElements(oldSection.questions, PropPosition.SECTION_POSITION);
                }
                FormElementUtils.rePositionFormElements(newSection.questions, PropPosition.SECTION_POSITION);
                FormElementUtils.updateNextFormElementValues(formElements);
                await questionService.update(newSection.questions.all.concat(oldSection.questions.all));
            }
            else { // Item moved FROM vm.formElements TO section with id 'newSectionId'
                FormElementUtils.updateSiblingsPositions(formElements, false, null, oldIndex);
                FormElementUtils.updateSiblingsPositions(newSection.questions, true, null, newIndex);
                item.position = null;
                item.section_id = newSectionId;
                item.section_position = newIndex + 1;
                newSection.questions.all.push(item);
                formElements.all = formElements.all.filter((e: FormElement) => e.id != item.id);
                FormElementUtils.rePositionFormElements(newSection.questions, PropPosition.SECTION_POSITION);
                FormElementUtils.rePositionFormElements(formElements, PropPosition.POSITION);
                FormElementUtils.updateNextFormElementValues(formElements);
                await questionService.update(newSection.questions.all);
                await formElementService.update(FormElementUtils.getSectionsAndInsideConditionalQuestions(formElements));
            }
        }
    };

    static onEndOrgaDragAndDrop = async (evt: any, formElements: FormElements) : Promise<boolean> => {
        let elem = evt.item.firstElementChild.firstElementChild;
        let scopeElem = angular.element(elem).scope().vm;
        let itemId: number = scopeElem.formElement.id;
        let newSectionId: number = evt.to.id.split("-")[2] != "0" ? parseInt(evt.to.id.split("-")[2]) : null;
        newSectionId ? elem.classList.add("sectionChild") : elem.classList.remove("sectionChild");
        let oldContainerId: number = evt.from.id.split("-")[2] != "0" ? parseInt(evt.from.id.split("-")[2]) : null;
        let oldSection: Section = oldContainerId ? (formElements.all.filter((e: FormElement) => e instanceof Section && e.id === oldContainerId)[0]) as Section : null;
        let item: any = null;
        if (scopeElem.section) {
            item = formElements.all.filter((e: FormElement) => e.id === itemId)[0] as Section;
        }
        else {
            let oldSiblings: any = oldSection ? oldSection.questions : formElements;
            item = oldSiblings.all.filter((q: Question) => q.id === itemId)[0] as Question;
        }
        let oldIndex: number = evt.oldIndex;
        let newIndex: number = evt.newIndex;
        let indexes: any = FormElementUtils.getStartEndIndexes(newIndex, oldIndex);

        // We cannot move a section into another section
        if (newSectionId && item instanceof Section) {
            return true;
        }

        if (!newSectionId) {
            if (oldSection) { // Item moved FROM oldSection TO vm.formElements
                FormElementUtils.updateSiblingsPositions(oldSection.questions, false, null, oldIndex);
                FormElementUtils.updateSiblingsPositions(formElements, true, null, newIndex);
                item.position = newIndex + 1;
                item.section_id = null;
                item.section_position = null;
                formElements.all.push(item);
                oldSection.questions.all = oldSection.questions.all.filter((q: Question) => q.id != item.id);
                FormElementUtils.rePositionFormElements(formElements, PropPosition.POSITION);
                FormElementUtils.rePositionFormElements(oldSection.questions, PropPosition.SECTION_POSITION);
                FormElementUtils.updateNextFormElementValues(formElements);
            }
            else { // Item moved FROM vm.formElements TO vm.formElements
                FormElementUtils.updateSiblingsPositions(formElements, true, indexes.goUp, indexes.startIndex, indexes.endIndex);
                item.position = newIndex + 1;
                item.section_id = null;
                item.section_position = null;
                FormElementUtils.rePositionFormElements(formElements, PropPosition.POSITION);
                FormElementUtils.updateNextFormElementValues(formElements);
            }
        }
        else {
            let newSection: Section = (formElements.all.filter((e: FormElement) => e instanceof Section && e.id === newSectionId)[0]) as Section;
            if (oldSection) { // Item moved FROM oldSection TO section with id 'newSectionId'
                if (newSection.id != oldSection.id) {
                    FormElementUtils.updateSiblingsPositions(oldSection.questions, false, null, oldIndex);
                    FormElementUtils.updateSiblingsPositions(newSection.questions, true, null, newIndex);
                }
                else {
                    FormElementUtils.updateSiblingsPositions(newSection.questions, true, indexes.goUp, indexes.startIndex, indexes.endIndex);
                }
                item.position = null;
                item.section_id = newSectionId;
                item.section_position = newIndex + 1;
                if (newSection.id != oldSection.id) {
                    oldSection.questions.all = oldSection.questions.all.filter((q: Question) => q.id != item.id);
                    newSection.questions.all.push(item);
                    FormElementUtils.rePositionFormElements(oldSection.questions, PropPosition.SECTION_POSITION);
                }
                FormElementUtils.rePositionFormElements(newSection.questions, PropPosition.SECTION_POSITION);
                FormElementUtils.updateNextFormElementValues(formElements);
            }
            else { // Item moved FROM vm.formElements TO section with id 'newSectionId'
                FormElementUtils.updateSiblingsPositions(formElements, false, null, oldIndex);
                FormElementUtils.updateSiblingsPositions(newSection.questions, true, null, newIndex);
                item.position = null;
                item.section_id = newSectionId;
                item.section_position = newIndex + 1;
                newSection.questions.all.push(item);
                formElements.all = formElements.all.filter((e: FormElement) => e.id != item.id);
                FormElementUtils.rePositionFormElements(newSection.questions, PropPosition.SECTION_POSITION);
                FormElementUtils.rePositionFormElements(formElements, PropPosition.POSITION);
                FormElementUtils.updateNextFormElementValues(formElements);
            }
        }

        formElements.all.sort((a: FormElement, b: FormElement) => a.position - b.position);
        return false;
    };

    static updateSiblingsPositions = (formElements: FormElements|Questions, isAdd: boolean, goUp: boolean, startIndex: number, endIndex?: number) : void => {
        if (formElements instanceof Questions) {
            FormElementUtils.updateSectionPositionsAfter(formElements, isAdd, goUp, startIndex, endIndex);
        }
        else {
            FormElementUtils.updatePositionsAfter(formElements, isAdd, goUp, startIndex, endIndex);
        }
    };

    static updatePositionsAfter = (formElements: FormElements|Questions, isAdd: boolean, goUp: boolean, startIndex: number, endIndex?: number) : void => {
        endIndex = endIndex ? endIndex : formElements.all.length;
        for (let i = startIndex; i < endIndex; i++) {
            let formElement = formElements.all[i];
            if (goUp === null) {
                isAdd ? formElement.position++ : formElement.position--;
            }
            else {
                goUp ? formElement.position++ : formElement.position--;
            }
        }
    };

    static updateSectionPositionsAfter = (questions: Questions, isAdd: boolean, goUp: boolean, startIndex: number, endIndex?: number) : void => {
        endIndex = endIndex ? endIndex : questions.all.length;
        for (let i = startIndex; i < endIndex; i++) {
            let question = questions.all[i];
            if (goUp === null) {
                isAdd ? question.section_position++ : question.section_position--;
            }
            else {
                goUp ? question.section_position++ : question.section_position--;
            }
        }
    };

    static getStartEndIndexes = (newIndex: number, oldIndex: number) : any => {
        let indexes = {startIndex: -1, endIndex: -1, goUp: false};
        if (newIndex < oldIndex) {
            indexes.goUp = true;
            indexes.startIndex = newIndex;
            indexes.endIndex = oldIndex;
        }
        else {
            indexes.goUp = false;
            indexes.startIndex = oldIndex;
            indexes.endIndex = newIndex + 1;
        }
        return indexes;
    }

    static rePositionFormElements = (formElements: FormElements|Questions, propPosition: PropPosition) : void => {
        formElements.all.sort((a, b) => a[propPosition] - b[propPosition]);
        for (let i: number = 0; i < formElements.all.length; i++) {
            formElements.all[i][propPosition] = i + 1;
        }
    }

    static updateNextFormElementValues = (formElements: FormElements) : void => {
        let elementsToUpdate: FormElement[] = formElements.getAllSectionsAndAllQuestions();
        for (let element of elementsToUpdate) {
            if (element instanceof Section && element.is_next_form_element_default) {
                element.setNextFormElementValuesWithDefault(formElements);
            }
            else if (element instanceof Section && !element.is_next_form_element_default) {
                let nextElementPosition: number = element.getNextFormElementPosition(formElements);
                if (nextElementPosition && nextElementPosition <= element.position) {
                    element.setNextFormElementValuesWithDefault(formElements);
                }
            }
            else if (element instanceof Question && element.conditional) {
                for (let choice of element.choices.all) {
                    if (choice.is_next_form_element_default) {
                        choice.setNextFormElementValuesWithDefault(formElements, element);
                    }
                    else {
                        let nextElementPosition: number = choice.getNextFormElementPosition(formElements);
                        if (nextElementPosition && nextElementPosition <= element.getPosition(formElements)) {
                            choice.setNextFormElementValuesWithDefault(formElements, element);
                        }
                    }
                }
            }
        }
    }

    static getSectionsAndInsideConditionalQuestions = (formElements: FormElements) : FormElement[] => {
        let insideConditionalQuestions: FormElement[] = (formElements.all as any)
            .filter((e: FormElement) => e instanceof Section)
            .flatMap((s: Section) => s.questions.all)
            .filter((q: Question) => q.conditional);
        return formElements.all.concat(insideConditionalQuestions);
    }
}