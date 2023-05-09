import {angular, idiom, ng, notify, template} from 'entcore';
import {
    Form,
    FormElement,
    FormElements,
    Question,
    QuestionChoice,
    QuestionTypes,
    Response,
    Responses,
    Section,
    Types
} from "../models";
import {
    distributionService,
    folderService,
    formElementService,
    formService,
    questionChoiceService,
    questionService,
    responseService,
    sectionService
} from "../services";
import {
    Direction,
    FORMULAIRE_BROADCAST_EVENT,
    FORMULAIRE_EMIT_EVENT,
    FORMULAIRE_FORM_ELEMENT_EMIT_EVENT,
    Pages
} from "@common/core/enums";
import * as Sortable from "sortablejs";
import {FormElementUtils} from "@common/utils";
import {Constants} from "@common/core/constants";
import {PropPosition} from "@common/core/enums/prop-position";
import {FormElementType} from "@common/core/enums/form-element-type";
import {IconUtils} from "@common/utils/icon";

enum PreviewPage { RGPD = 'rgpd', QUESTION = 'question', RECAP = 'recap'}

interface ViewModel {
    form: Form;
    formElements: FormElements;
    newElement: Question|Section;
    parentSection: Section;
    dontSave: boolean;
    nbFormElements: number;
    questionTypes: QuestionTypes;
    display: {
        lightbox: {
            newElement: boolean,
            reorganization: boolean,
            delete: boolean,
            undo: boolean
        }
    };
    preview: {
        formElement: FormElement, // Question for preview
        responses: Map<Question, Responses>, // Responses list for preview
        files: Map<Question, Array<File>>,
        historicPosition: number[],
        page: string,
        last: boolean
    };
    PreviewPage: typeof PreviewPage;
    nestedSortables: any[];
    iconUtils: IconUtils;

    $onInit() : Promise<void>;

    // Editor functions
    saveAll(displaySuccess?: boolean) : Promise<void>;
    return() : Promise<void>;
    createNewElement(parentSection?: Section) : Promise<void>;
    doCreateNewElement(code?: number, parentSection?: Section) : void;
    goTreeView(): void;
    organizeFormElements() : Promise<void>;
    doOrganizeFormElements() : Promise<void>;
    cancelOrganizeFormElements() : Promise<void>;
    duplicateQuestion(question: Question) : Promise<void>;
    deleteFormElement() : Promise<void>;
    doDeleteFormElement() : Promise<void>;
    undoFormElementChanges() : Promise<void>;
    doUndoFormElementChanges() : Promise<void>;
    validateSection(): Promise<void>;
    closeLightbox(action: string): void;
    displayTypeName(typeInfo: string) : string;
    displayTypeDescription(description : string) : string;
    moveQuestion(formElement: FormElement, direction: string) : void;

    // Preview functions
    goPreview() : void;
    backToEditor() : void;
    prev() : void;
    next() : void;
    getHtmlDescription(description: string) : string;
}


export const formEditorController = ng.controller('FormEditorController', ['$scope', '$sce',
    function ($scope, $sce) {

        const vm: ViewModel = this;
        vm.form = new Form();
        vm.formElements = new FormElements();
        vm.parentSection = null;
        vm.dontSave = false;
        vm.nbFormElements = 0;
        vm.questionTypes = new QuestionTypes();
        vm.display = {
            lightbox: {
                newElement: false,
                reorganization: false,
                delete: false,
                undo: false
            }
        };
        vm.PreviewPage = PreviewPage;
        vm.nestedSortables = [];
        vm.iconUtils = IconUtils;

        vm.$onInit = async () : Promise<void> => {
            vm.form = $scope.form;
            vm.form.nb_responses = vm.form.id ? (await distributionService.count(vm.form.id)).count : 0;
            await vm.formElements.sync(vm.form.id);
            vm.dontSave = false;
            vm.nbFormElements = vm.formElements.all.length;
            vm.questionTypes.all = vm.form.is_public ? $scope.questionTypes.all.filter(qt => qt.code != Types.FILE) : $scope.questionTypes.all;
            $scope.safeApply();

            initNestedSortables();
            $scope.safeApply();
        };

        // Global functions

        vm.saveAll = async (displaySuccess= true) : Promise<void> => {
            vm.dontSave = true;

            // Check conditional questions
            let sectionQuestionsList: Question[][] = vm.formElements.getSections().all
                .filter((s: Section) => s.id)
                .map((s: Section) => s.questions.all);

            for (let sectionQuestions of sectionQuestionsList) {
                let conditionalQuestions: Question[] = sectionQuestions.filter(q => q.conditional);
                if (conditionalQuestions.length >= 2) {
                    notify.error(idiom.translate('formulaire.question.save.missing.field'));
                    return;
                }
            }

            // Check titles
            let wrongElements: FormElement[] = vm.formElements.all.filter(fe => !fe.title); // TODO check more than just titles later
            if (wrongElements.length > 0) {
                notify.error(idiom.translate('formulaire.question.save.missing.field'));
                return;
            }

            // Check cursor values
            let questionsTypeCursor: Question[] = vm.formElements.getAllQuestions().filter((q: Question) => q.question_type == Types.CURSOR);
            if (questionsTypeCursor.length > 0) {
                // We search for question where : (maxVal - minVal) % step == 0
                let inconsistencyCursorChoice: Question[] = questionsTypeCursor.filter((q: Question) => (
                        ((q.cursor_max_val != null ? q.cursor_max_val : Constants.DEFAULT_CURSOR_MAX_VALUE) -
                         (q.cursor_min_val != null ? q.cursor_min_val : Constants.DEFAULT_CURSOR_MIN_VALUE)) %
                        (q.cursor_step != null ? q.cursor_step : Constants.DEFAULT_CURSOR_STEP) != 0));
                if (inconsistencyCursorChoice.length > 0) {
                    notify.error(idiom.translate('formulaire.question.save.missing.field'));
                    return;
                }
            }

            await saveFormElements(displaySuccess && wrongElements.length <= 0);
            vm.dontSave = false;
        };

        vm.return = async() : Promise<void> => {
            vm.dontSave = true;
            let wrongElements = vm.formElements.all.filter(question => !question.title); // TODO check more than just titles later
            if (wrongElements.length > 0) {
                notify.error(idiom.translate('formulaire.question.save.missing.field'));
                vm.dontSave = false;
            } else {
                let folder = await folderService.get(vm.form.folder_id);
                $scope.$emit(FORMULAIRE_EMIT_EVENT.UPDATE_FOLDER, folder);
                $scope.redirectTo('/list/mine');
            }
        };

        vm.createNewElement = async (parentSection?) : Promise<void>=> {
            vm.parentSection = parentSection ? parentSection : null;
            template.open('lightbox', 'lightbox/new-element');
            vm.display.lightbox.newElement = true;
            $scope.safeApply();
        };

        vm.doCreateNewElement = async (code?, parentSection?) => {
            vm.dontSave = true;

            vm.newElement = code ? new Question() : new Section();
            if (vm.newElement instanceof Question) {
                vm.newElement.question_type = code;
                if (vm.newElement.isTypeChoicesQuestion()) {
                    for (let i = 0; i < Constants.DEFAULT_NB_CHOICES; i++) {
                        vm.newElement.choices.all.push(new QuestionChoice(vm.newElement.id, i+1));
                    }

                    if (vm.newElement.question_type === Types.MATRIX) {
                        for (let i = 0; i < Constants.DEFAULT_NB_CHILDREN; i++) {
                            vm.newElement.children.all.push(new Question(vm.newElement.id, Types.SINGLEANSWERRADIO, i+1));
                        }
                    }
                }
                if (parentSection) {
                    vm.newElement.section_id = parentSection.id;
                    vm.newElement.section_position = parentSection.questions.all.length + 1;
                    let elementSection: Section = vm.formElements.all.filter((e: FormElement) => e.id === parentSection.id)[0] as Section;
                    elementSection.questions.all.push(vm.newElement);
                }
            }

            vm.newElement.form_id = vm.form.id;
            vm.newElement.selected = true;
            if (!parentSection) {
                vm.newElement.position = vm.formElements.all.length + 1;
                vm.formElements.all.push(vm.newElement);
                vm.nbFormElements = vm.formElements.all.length;
            }

            vm.parentSection = null;
            vm.display.lightbox.newElement = false;
            template.close('lightbox');
            vm.dontSave = false;
            $scope.safeApply();
        };

        vm.goTreeView = () : void => {
            $scope.redirectTo(`/form/${vm.form.id}/tree`);
        };

        vm.organizeFormElements = async () : Promise<void> => {
            await template.open('lightbox', 'lightbox/questions-reorganization');
            vm.display.lightbox.reorganization = true;
            $scope.safeApply();
            initOrgaNestedSortables();
        };

        vm.doOrganizeFormElements = async () : Promise<void> => {
            for (let question of vm.formElements.getAllQuestions().all) {
                if ((question.section_id || question.section_position) && question.position) {
                    if (question.position > 0) {
                        question.section_id = null;
                        question.section_position = null;
                    }
                    else if (question.section_position > 0) {
                        question.position = null;
                    }
                }
            }

            await formElementService.update(vm.formElements.getAllSectionsAndAllQuestions());
            vm.display.lightbox.reorganization = false;
            template.close('lightbox');
            $scope.safeApply();
        };

        vm.cancelOrganizeFormElements = async () : Promise<void> => {
            await vm.formElements.sync(vm.form.id);
            vm.closeLightbox('reorganization');
        };

        // FormElements functions

        vm.duplicateQuestion = async (question: Question) : Promise<void> => {
            try {
                vm.dontSave = true;
                let questionId: number = (await questionService.save(question)).id;
                let duplicata: Question = question;
                if (question.section_id) {
                    let section: Section = vm.formElements.all.filter(e => e instanceof Section && e.id === question.section_id)[0] as Section;
                    for (let i = question.section_position; i < section.questions.all.length; i++) {
                        section.questions.all[i].section_position++;
                    }
                    await formElementService.update(section.questions.all.slice(question.section_position));
                    duplicata.section_position++;
                }
                else {
                    for (let i = question.position; i < vm.formElements.all.length; i++) {
                        vm.formElements.all[i].position++;
                    }
                    await formElementService.update(vm.formElements.all.slice(question.position));
                    duplicata.position++;
                }
                let newQuestion = await questionService.create(duplicata);
                if (question.isTypeChoicesQuestion()) {
                    question.choices.all.sort((a, b) => a.position - b.position);
                    for (let choice of question.choices.all) {
                        if (!choice.question_id) choice.question_id = questionId;
                        if (choice.value) {
                            await questionChoiceService.save(choice);
                            await questionChoiceService.create(new QuestionChoice(newQuestion.id, choice.position, choice.value));
                        }
                    }
                    if (question.question_type == Types.MATRIX) {
                        for (let child of question.children.all) {
                            child.form_id = question.form_id;
                            child.matrix_id = questionId;
                            if (child.title) {
                                await questionService.save(child);
                                let duplicateChild: Question = new Question(newQuestion.id, child.question_type, child.matrix_position);
                                duplicateChild.form_id = question.form_id;
                                duplicateChild.title = child.title;
                                await questionService.create(duplicateChild);
                            }
                        }
                    }
                }
                notify.success(idiom.translate('formulaire.success.question.duplicate'));
                await vm.formElements.sync(vm.form.id);
                vm.dontSave = false;
                $scope.safeApply();
            }
            catch (e) {
                throw e;
            }
        };

        vm.deleteFormElement = async () : Promise<void> => {
            let formElement = vm.formElements.getSelectedElement();
            if (formElement.id) {
                let responseCount = await responseService.countByFormElement(formElement);
                if (vm.form.sent && responseCount.count > 0){
                    notify.error(idiom.translate('formulaire.element.delete.response.fill.warning'));
                }
                else if (vm.form.sent && vm.formElements.all.length === 1) {
                    notify.error(idiom.translate('formulaire.element.delete.empty.warning'));
                }
                else {
                    vm.dontSave = true;
                    template.open('lightbox', 'lightbox/question-confirm-delete');
                    vm.display.lightbox.delete = true;
                }
            }
            else if (formElement instanceof Question && formElement.section_id) {
                let section = vm.formElements.getSections().all.filter(s => s.id === (formElement as Question).section_id)[0];
                section.questions.all = section.questions.all.filter(q => q.id);
            }
            else {
                vm.formElements.all = vm.formElements.all.filter(e => e.id);
            }
        };

        vm.doDeleteFormElement = async () : Promise<void> => {
            try {
                let formElement = vm.formElements.getSelectedElement();
                if (formElement.id) {
                    await formElementService.delete(formElement);
                    if (formElement instanceof Question && formElement.section_id) {
                        let section = vm.formElements.all.filter(e => e instanceof Section && e.id === (formElement as Question).section_id)[0] as Section;
                        for (let i = formElement.section_position; i < section.questions.all.length; i++) {
                            section.questions.all[i].section_position--;
                        }
                        await formElementService.update(section.questions.all.slice(formElement.section_position));
                    }
                    else {
                        for (let i = formElement.position; i < vm.formElements.all.length; i++) {
                            vm.formElements.all[i].position--;
                        }
                        await formElementService.update(vm.formElements.all.slice(formElement.position));
                    }
                }
                template.close('lightbox');
                vm.display.lightbox.delete = false;
                notify.success(idiom.translate('formulaire.success.element.delete'));
                vm.form.setFromJson(await formService.get(vm.form.id));
                await vm.formElements.sync(vm.form.id);
                vm.nbFormElements = vm.formElements.all.length;
                FormElementUtils.rePositionFormElements(vm.formElements, PropPosition.POSITION);
                vm.dontSave = false;
                $scope.safeApply();
            }
            catch (e) {
                throw e;
            }
        };

        vm.undoFormElementChanges = async () : Promise<void> => {
            let formElement = vm.formElements.getSelectedElement();
            if (formElement) {
                let hasChanged = false;
                if (formElement instanceof Question) {
                    hasChanged = (!!formElement.title || !!formElement.statement || formElement.mandatory || formElement.choices.all.length > 0);
                }
                else if (formElement instanceof Section) {
                    hasChanged = (!!formElement.title || !!formElement.description || formElement.questions.all.length > 0);
                }

                if (hasChanged) {
                    vm.dontSave = true;
                    template.open('lightbox', 'lightbox/question-confirm-undo');
                    vm.display.lightbox.undo = true;
                }
                else {
                    await vm.formElements.sync(vm.form.id);
                    $scope.safeApply();
                }
            }
        };

        vm.doUndoFormElementChanges = async () : Promise<void> => {
            await vm.formElements.sync(vm.form.id);
            template.close('lightbox');
            vm.display.lightbox.undo = true;
            vm.dontSave = false;
            $scope.safeApply();
        };

        vm.validateSection = async () : Promise<void> => {
            let formElement = vm.formElements.selected[0];
            if (formElement instanceof Section) {
                await sectionService.save(formElement);
                await vm.formElements.sync(vm.form.id);
                $scope.safeApply();
            }
        };

        vm.closeLightbox = (action: string) : void => {
            template.close('lightbox');
            vm.display.lightbox[action] = false;
            vm.dontSave = false;
            $scope.safeApply();
        };

        // Display functions

        vm.displayTypeName = (typeInfo: string|number) : string => {
            if (typeof typeInfo === "string") {
                return idiom.translate('formulaire.question.type.' + typeInfo);
            }
            else if (typeof typeInfo === "number") {
                let name = $scope.getTypeNameByCode(typeInfo);
                return idiom.translate('formulaire.question.type.' + name);
            }
            else {
                return "ERROR_TEXT";
            }
        };

        vm.displayTypeDescription = (description : string|number) :string => {
            return idiom.translate("formulaire.question.type.description." + description);
        };

        vm.moveQuestion = (formElement: FormElement, direction: string) : void => {
            let index: number = formElement.position - 1;
            let section_index: number = formElement instanceof Question ? formElement.section_position - 1 : null;

            if (formElement instanceof Section) {
                FormElementUtils.switchPositions(vm.formElements, index, direction, PropPosition.POSITION);
            }
            else if (formElement instanceof Question) {
                let question: Question = formElement as Question;
                if (!question.section_id) {
                    let target: FormElement = direction === Direction.UP ? vm.formElements.all[index - 1] : vm.formElements.all[index + 1];
                    if (target instanceof Question) { // Switch question with question target
                        FormElementUtils.switchPositions(vm.formElements, index, direction, PropPosition.POSITION);
                    }
                    else if (target instanceof Section) {
                        switch (direction) {
                            case Direction.UP: { // Put question at the end of the section target
                                question.position = null;
                                question.section_id = target.id;
                                question.section_position = target.questions.all.length + 1;
                                FormElementUtils.updateSiblingsPositions(vm.formElements, false, null, index + 1);
                                target.questions.all.push(question);
                                vm.formElements.all = vm.formElements.all.filter(e => e.id != question.id);
                                break;
                            }
                            case Direction.DOWN: { // Put question at the start of the section target
                                question.position = null;
                                question.section_id = target.id;
                                question.section_position = 1;
                                FormElementUtils.updateSiblingsPositions(vm.formElements, false, null, index + 1);
                                FormElementUtils.updateSiblingsPositions(target.questions, true, null, 0);
                                target.questions.all.push(question);
                                vm.formElements.all = vm.formElements.all.filter(e => e.id != question.id);
                                break;
                            }
                            default:
                                notify.error(idiom.translate('formulaire.error.question.reorganization'));
                                break;
                        }
                        target.questions.all.sort((a: Question, b: Question) => a.section_position - b.section_position);
                    }
                }
                else {
                    let parentSection: Section = vm.formElements.all.filter((e: FormElement) => e.id === question.section_id)[0] as Section;
                    if (question.section_position === 1 && direction === Direction.UP) { // Take question out (before) of the parentSection
                        question.position = parentSection.position;
                        question.section_id = null;
                        question.section_position = null;
                        FormElementUtils.updateSiblingsPositions(parentSection.questions, false, null, 0);
                        FormElementUtils.updateSiblingsPositions(vm.formElements, true, null, parentSection.position - 1);
                        parentSection.questions.all = parentSection.questions.all.filter((q: Question) => q.id != question.id);
                        vm.formElements.all.push(question);
                    }
                    else if (question.section_position === parentSection.questions.all.length && direction === Direction.DOWN) { // Take question out (after) of the parentSection
                        question.position = parentSection.position + 1;
                        question.section_id = null;
                        question.section_position = null;
                        FormElementUtils.updateSiblingsPositions(parentSection.questions, false, null, parentSection.questions.all.length - 1);
                        FormElementUtils.updateSiblingsPositions(vm.formElements, true, null, parentSection.position);
                        parentSection.questions.all = parentSection.questions.all.filter((q: Question) => q.id != question.id);
                        vm.formElements.all.push(question);
                    }
                    else { // Switch two questions into the parentSection
                        FormElementUtils.switchPositions(parentSection.questions, section_index, direction, PropPosition.SECTION_POSITION);
                    }
                    parentSection.questions.all.sort((a: Question, b: Question) => a.section_position - b.section_position);
                }
            }

            vm.formElements.all.sort((a: FormElement, b: FormElement) => a.position - b.position);
            $scope.safeApply();
        };

        // Preview functions

        vm.goPreview = async () : Promise<void> => {
            await vm.saveAll(false);
            vm.preview = {
                formElement: new Question(),
                responses: new Map(),
                files: new Map(),
                historicPosition: [],
                page: PreviewPage.QUESTION,
                last: false
            };

            for (let formElement of vm.formElements.all) {
                let nbQuestions: number = formElement instanceof Question ? 1 : (formElement as Section).questions.all.length;
                for (let i = 0; i < nbQuestions; i++) {
                    let question: Question = formElement instanceof Question ? formElement : (formElement as Section).questions.all[i];
                    let questionResponses: Responses = new Responses();

                    if (question.isTypeMultipleRep() || question.isRanking()) {
                        for (let choice of question.choices.all) {
                            if (question.children.all.length > 0) {
                                for (let child of question.children.all) {
                                    questionResponses.all.push(new Response(child.id, choice.id, choice.value));
                                }
                            }
                            else {
                                questionResponses.all.push(new Response(question.id, choice.id, choice.value,
                                    null, choice.position));
                            }
                        }
                    }
                    else {
                        questionResponses.all.push(new Response(question.id));
                    }

                    vm.preview.responses.set(question, questionResponses);
                    vm.preview.files.set(question, new Array<File>());
                }
            }
            if (vm.form.rgpd) {
                vm.preview.page = PreviewPage.RGPD;
            }
            else {
                vm.preview.formElement = vm.formElements.all[0];
                vm.preview.historicPosition = [1];
                vm.preview.last = vm.preview.formElement.position === vm.nbFormElements;
                vm.preview.page = PreviewPage.QUESTION;
            }
            $scope.currentPage = Pages.PREVIEW;
            $scope.safeApply();
        };

        vm.backToEditor = () : void => {
            $scope.currentPage = Pages.EDIT_FORM;
            $scope.safeApply();
        };

        vm.prev = () : void => {
            let prevPosition = vm.preview.historicPosition[vm.preview.historicPosition.length - 2];
            if (prevPosition > 0) {
                vm.preview.formElement = vm.formElements.all[prevPosition - 1];
                vm.preview.historicPosition.pop();
                vm.preview.last = prevPosition === vm.nbFormElements;
                window.scrollTo(0, 0);
                $scope.safeApply();
                // if (vm.preview.page === PreviewPage.RECAP) {
                //     vm.preview.formElement = vm.formElements.all[vm.nbFormElements - 1];
                //     vm.preview.elementResponses = vm.preview.formResponses[vm.nbFormElements - 1];
                //     vm.last = vm.preview.formElement.position === vm.nbFormElements;
                //     vm.preview.page = PreviewPage.QUESTION;
                // }
            }
            else if (vm.form.rgpd) {
                vm.preview.formElement = null;
                vm.preview.page = PreviewPage.RGPD;
                $scope.safeApply();
            }
        };

        vm.next = () : void => {
            let nextPosition: number = getNextPositionIfValid();

            // Update data to display next element
            if (nextPosition && nextPosition <= vm.nbFormElements) {
                vm.preview.formElement = vm.formElements.all[nextPosition - 1];
                vm.preview.historicPosition.push(vm.preview.formElement.position);
                vm.preview.last = nextPosition === vm.nbFormElements;
                if (vm.preview.page == PreviewPage.RGPD) vm.preview.page = PreviewPage.QUESTION;
                window.scrollTo(0, 0);
                $scope.safeApply();
                // if (vm.preview.page === PreviewPage.RECAP) {
                //     vm.preview.formElement = null;
                //     vm.preview.elementResponses = null;
                //     vm.preview.page = PreviewPage.RECAP;
                // }
            }
            else if (nextPosition != undefined) {
                vm.preview.page = PreviewPage.RECAP;
                $scope.safeApply();
            }
        };

        const getNextPositionIfValid = () : number => {
            let nextPosition: number = vm.preview.formElement.position + 1;
            let conditionalQuestion: Question = null;
            let response: Response = null;

            // Check if there are valid conditional questions and find next element position accordingly
            if (vm.preview.formElement instanceof Question && vm.preview.formElement.conditional) {
                conditionalQuestion = vm.preview.formElement;
                response = vm.preview.responses.get(conditionalQuestion).all[0];
            }
            else if (vm.preview.formElement instanceof Section) {
                let conditionalQuestions = vm.preview.formElement.questions.all.filter((q: Question) => q.conditional);
                if (conditionalQuestions.length === 1) {
                    conditionalQuestion = conditionalQuestions[0];
                    response = vm.preview.responses.get(conditionalQuestion).all[0];
                }
            }

            if (conditionalQuestion && response && !response.choice_id) {
                notify.info('formulaire.response.next.invalid');
                nextPosition = null;
            }
            else if (conditionalQuestion && response) {
                let choices: QuestionChoice[] = conditionalQuestion.choices.all.filter((c: QuestionChoice) => c.id === response.choice_id);
                let nextElementId: number = choices.length === 1 ? choices[0].next_form_element_id : null;
                let nextElementType: FormElementType = choices.length === 1 ? choices[0].next_form_element_type : null;
                let filteredElements: FormElement[] = vm.formElements.all.filter((e: FormElement) => e.id === nextElementId && e.form_element_type == nextElementType);
                let targetedElement: FormElement = filteredElements.length === 1 ? filteredElements[0] : null;
                nextPosition = targetedElement ? targetedElement.position : null;
            }
            else if (vm.preview.formElement instanceof Section && vm.preview.formElement.questions.all.filter((q: Question) => q.conditional).length == 0) {
                nextPosition = vm.preview.formElement.getNextFormElementPosition(vm.formElements);
            }

            return nextPosition;
        };

        vm.getHtmlDescription = (description: string) : string => {
            return !!description ? $sce.trustAsHtml(description) : null;
        }

        // Utils

        const initNestedSortables = () : void => {
            // Loop through each nested sortable element for DragAndDrop of questions
            for (let i = 0; i < vm.nestedSortables.length; i++) {
                vm.nestedSortables[i].destroy();
            }
            vm.nestedSortables = [];
            let nestedSortables = document.querySelectorAll(".nested-container");
            for (let i = 0; i < nestedSortables.length; i++) {
                vm.nestedSortables.push(Sortable.create(nestedSortables[i], {
                    group: 'nested',
                    animation: 150,
                    fallbackOnBody: true,
                    swapThreshold: 0.65,
                    ghostClass: "sortable-ghost",

                    onStart: function (evt) {
                        document.querySelector('header').style.pointerEvents = 'none';
                    },
                    onEnd: async function (evt) {
                        await FormElementUtils.onEndDragAndDrop(evt, vm.formElements);
                        document.querySelector('header').style.removeProperty('pointer-events');
                        $scope.safeApply();

                        await vm.$onInit();
                    }
                }));
            }
        }

        const switchDragAndDropTo = (state: boolean) : void => {
            for (let i = 0; i < vm.nestedSortables.length; i++) {
                vm.nestedSortables[i].option("disabled", !state);
            }
            $scope.safeApply();
        }

        const initOrgaNestedSortables = () : void => {
            // Loop through each nested sortable element for DragAndDrop in pop reorganization
            window.setTimeout(() : void => {
                let orgaNestedSortables = document.querySelectorAll(".orga-nested-container");
                for (let i = 0; i < orgaNestedSortables.length; i++) {
                    Sortable.create(orgaNestedSortables[i], {
                        group: 'orga-nested',
                        animation: 150,
                        fallbackOnBody: true,
                        swapThreshold: 0.65,
                        ghostClass: "sortable-ghost",
                        onEnd: async function (evt) {
                            let refresh = await FormElementUtils.onEndOrgaDragAndDrop(evt, vm.formElements);
                            $scope.safeApply();
                            if (refresh) {
                                await vm.formElements.sync(vm.form.id);
                                vm.organizeFormElements();
                            }
                        }
                    });
                }
                $scope.safeApply();
            }, 500);
        }

        const saveFormElements = async (displaySuccess: boolean = false) : Promise<void> => {
            try {
                switchDragAndDropTo(true);
                FormElementUtils.rePositionFormElements(vm.formElements, PropPosition.POSITION);
                let formElement: FormElement = vm.formElements.getSelectedElement();
                let isSection: boolean = formElement && formElement instanceof Section;
                let isQuestionNotCursor: boolean = formElement
                    && formElement instanceof Question
                    && formElement.question_type != Types.CURSOR;
                let isCursorQuestionAndValuesOk: boolean = formElement
                    && formElement instanceof Question
                    && formElement.question_type == Types.CURSOR
                    && formElement.cursor_min_val != null
                    && formElement.cursor_max_val != null
                    && formElement.cursor_min_val != formElement.cursor_max_val;

                if (isSection || isQuestionNotCursor || isCursorQuestionAndValuesOk) {
                    // Save form element
                    let savedElement: FormElement = await formElementService.save(formElement);
                    let newId: number = savedElement.id;
                    formElement.id = newId;

                    if (formElement instanceof Question) {
                        // Save choices
                        let registeredChoiceValues: string[] = [];
                        formElement.choices.replaceSpace();
                        let positionCounter: number = 1;
                        for (let choice of formElement.choices.all) {
                            if (choice.value && !registeredChoiceValues.find((v: string) => v === choice.value)) {
                                choice.position = positionCounter;
                                choice.question_id = newId;
                                choice.id = (await questionChoiceService.save(choice)).id;
                                registeredChoiceValues.push(choice.value);
                                positionCounter++;
                            }
                        }
                        // Save children (for MATRIX questions)
                        positionCounter = 1;
                        if (formElement.question_type === Types.MATRIX) {
                            let registeredChildrenTitles: string[] = [];
                            for (let child of formElement.children.all) {
                                if (child.title && !registeredChildrenTitles.find((t: string) => t === child.title)) {
                                    child.matrix_position = positionCounter;
                                    child.matrix_id = newId;
                                    child.form_id = formElement.form_id;
                                    child.id = (await questionService.save(child)).id;
                                    registeredChildrenTitles.push(child.title);
                                    positionCounter++;
                                }
                            }
                        }
                    }

                    if (displaySuccess) { notify.success(idiom.translate('formulaire.success.form.save')); }
                }
                else {
                    vm.form.setFromJson(await formService.update(vm.form));
                    if (displaySuccess) { notify.success(idiom.translate('formulaire.success.form.save')); }
                }
                await vm.$onInit();
            }
            catch (e) {
                throw e;
            }
        };

        const onClickQuestion = async (event) : Promise<void> => {
            if (!vm.dontSave && $scope.currentPage === Pages.EDIT_FORM) {
                let question = isInFocusable(event.target);
                if (question && question.id && question.id > 0) {
                    if (!question.selected) {
                        if (vm.formElements.hasSelectedElement()) {
                            await saveFormElements();
                        }
                        // Reselection of the question because the sync has removed the selections
                        question.selected = true;
                        switchDragAndDropTo(false);
                    }
                }
                else if (question && !question.id) {
                    question.selected = true;
                    switchDragAndDropTo(false);
                }
                else if(!isInDontSave(event.target)) {
                    await saveFormElements();
                }
                $scope.safeApply();
            }
        };

        const isInShowErrorZone = (el) : boolean => {
            if (!el) { return true; }
            else if (el.classList && el.classList.contains("dontShowError")) { return false; }
            return isInShowErrorZone(el.parentNode);
        };

        const isInFocusable = (el) : Question => {
            if (!el) { return null; }
            else if (el.classList && el.classList.contains("focusable")) { return angular.element(el).scope().vm.question; }
            return isInFocusable(el.parentNode);
        };

        const isInDontSave = (el) : boolean => {
            if (!el) { return false; }
            else if (el.classList && el.classList.contains("dontSave") || el.tagName === "LIGHTBOX" || el.tagName === "HEADER"
            || (el.tagName === "BUTTON" && el.id !== "organizeConfirm" && el.id !== "createNewEltConfirm1" && el.id !== "createNewEltConfirm2"))
            { return true; }
            return isInDontSave(el.parentNode);
        };

        document.onclick = e => { if ($scope.currentPage === Pages.EDIT_FORM) onClickQuestion(e); };

        $scope.$on(FORMULAIRE_FORM_ELEMENT_EMIT_EVENT.DUPLICATE_ELEMENT, (e) => { vm.duplicateQuestion(e.targetScope.vm.question) });
        $scope.$on(FORMULAIRE_FORM_ELEMENT_EMIT_EVENT.DELETE_ELEMENT, () => { vm.deleteFormElement() });
        $scope.$on(FORMULAIRE_FORM_ELEMENT_EMIT_EVENT.UNDO_CHANGES, () => { vm.undoFormElementChanges() });
        $scope.$on(FORMULAIRE_FORM_ELEMENT_EMIT_EVENT.VALIDATE_SECTION, () => { vm.validateSection() });
        $scope.$on(FORMULAIRE_FORM_ELEMENT_EMIT_EVENT.CREATE_QUESTION, (e) => { vm.createNewElement(e.targetScope.vm.section) });
        $scope.$on(FORMULAIRE_FORM_ELEMENT_EMIT_EVENT.MOVE_QUESTION, (event, data) => { vm.moveQuestion(data.formElement, data.direction) });
        $scope.$on(FORMULAIRE_BROADCAST_EVENT.INIT_FORM_EDITOR, () => { vm.$onInit() });
    }]);