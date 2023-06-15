import {idiom, model, ng, notify} from "entcore";
import {
    Distribution,
    Form,
    FormElement,
    FormElements,
    Question,
    QuestionChoice,
    Response,
    Responses,
    Section,
    Types
} from "../models";
import {responseFileService, responseService} from "../services";
import {FORMULAIRE_BROADCAST_EVENT, FORMULAIRE_EMIT_EVENT, FORMULAIRE_FORM_ELEMENT_EMIT_EVENT} from "@common/core/enums";
import {FormElementType} from "@common/core/enums/form-element-type";

interface ViewModel {
    formElements: FormElements;
    formElement: FormElement;
    distribution: Distribution;
    selectedIndexList: any;
    responsesChoicesList: any;
    filesList: any;
    form: Form;
    nbFormElements: number;
    longestPath: number;
    loading : boolean;
    historicPosition: number[];
    currentResponses: Map<Question, Responses>;
    currentFiles: Map<Question, Array<File>>;

    $onInit() : Promise<void>;
    prev() : Promise<void>;
    prevGuard() : void;
    next() : Promise<void>;
    nextGuard() : void;
    saveAndQuit() : Promise<void>;
    saveAndQuitGuard() : void;
    getHtmlDescription(description: string) : string;
}

export const respondQuestionController = ng.controller('RespondQuestionController', ['$scope', '$sce',
    function ($scope, $sce) {

    const vm: ViewModel = this;
    vm.formElements = new FormElements();
    vm.distribution = new Distribution();
    vm.form = new Form();
    vm.nbFormElements = 1;
    vm.longestPath = 1;
    vm.loading = true;
    vm.currentResponses = new Map();
    vm.currentFiles = new Map();

    vm.$onInit = async () : Promise<void> => {
        await initRespondQuestionController();
    };

    const initRespondQuestionController = async () : Promise<void> => {
        vm.loading = true;
        vm.form = $scope.form;
        vm.distribution = $scope.distribution;
        await vm.formElements.sync(vm.form.id);
        vm.formElement = vm.formElements.all[$scope.responsePosition - 1];
        vm.nbFormElements = vm.formElements.all.length;
        vm.historicPosition = $scope.historicPosition.length > 0 ? $scope.historicPosition : [1];

        initFormElementResponses();
        vm.longestPath = findLongestPath(vm.formElement.id, vm.formElements);
        window.setTimeout(() => vm.loading = false,500);
        $scope.safeApply();
    }


    const findLongestPath = (nodeId: number, nodes: FormElements): number => {
        //TODO vérifier les autres types de questions sans choix mais avec un next_form_element_id
        let currentNode: any = nodes.all.find(node => node.id === nodeId);
        let choices: any[] = currentNode.isSection ? (<Section>currentNode).questions.all.map(elm => elm.choices.all) : (<Question>currentNode).choices.all;
        let paths: number[] = [];
        if(!choices)return 1;
        for (let choice of choices) {
            if (!choice.next_form_element_id) {
                paths.push(1)
            } else {
                paths.push(recursivePathFinder(choice.next_form_element_id, nodes));
            }
        }
        return Math.max(...paths + 1);
    }


    const recursivePathFinder = (nodeId: number, nodes: FormElements): number => {
        let subChoice: any = nodes.all.find(node => node.id === nodeId);
        let subChoices: any[] = subChoice.isSection ? (<Section>subChoice).questions.all.map(elm => elm.choices.all) : (<Question>subChoice).choices.all;
        if(!subChoices)return 1;
        return Math.max(subChoices.map(elemt => recursivePathFinder(elemt.next_form_element_id, nodes)) + 1);
    }

    const initFormElementResponses = () : void => {
        let nbQuestions: number = vm.formElement instanceof Question ? 1 : (vm.formElement as Section).questions.all.length;
        for (let i = 0; i < nbQuestions; i++) {
            let question: Question = vm.formElement instanceof Question ? vm.formElement : (vm.formElement as Section).questions.all[i];
            let questionResponses: Responses = new Responses();

            if (question.isTypeMultipleRep()) {
                for (let choice of question.choices.all) {
                    if (question.children.all.length > 0) {
                        for (let child of question.children.all) {
                            questionResponses.all.push(new Response(child.id, choice.id, choice.value, vm.distribution.id));
                        }
                    }
                    else if (!choice.is_custom) {
                        questionResponses.all.push(new Response(question.id, choice.id, choice.value, vm.distribution.id));
                    }
                    else {
                        questionResponses.all.push(new Response(question.id, choice.id, null, vm.distribution.id));
                    }
                }
            }
            else if (question.question_type === Types.CURSOR) {
                questionResponses.all.push(new Response(question.id, null, question.cursor_min_val, vm.distribution.id));
            }
            else if (question.isRanking()) {
                let questionChoices: QuestionChoice[] = question.choices.all;
                for (let i: number = 0; i < questionChoices.length; i++) {
                    let questionChoice: QuestionChoice = questionChoices[i];
                    questionResponses.all.push(new Response(question.id, questionChoice.id, questionChoice.value, vm.distribution.id, questionChoice.position));
                }
            }
            else {
                questionResponses.all.push(new Response(question.id, null, null, vm.distribution.id));
            }

            vm.currentResponses.set(question, questionResponses);
            vm.currentFiles.set(question, new Array<File>());
        }

        $scope.safeApply();
    };

    vm.prev = async () : Promise<void> => {
        let prevPosition: number = vm.historicPosition[vm.historicPosition.length - 2];
        if (prevPosition > 0) {
            await saveResponses();
            vm.formElement = vm.formElements.all[prevPosition - 1];
            vm.historicPosition.pop();
            goToFormElement();
        }
    };

    vm.prevGuard = () => {
        vm.prev().then();
    };

    vm.next = async () : Promise<void> => {
        let nextPosition: number = getNextPositionIfValid();
        if (nextPosition && nextPosition <= vm.nbFormElements) {
            await saveResponses();
            vm.formElement = vm.formElements.all[nextPosition - 1];
            vm.historicPosition.push(vm.formElement.position);
            goToFormElement();
        }
        else if (nextPosition !== undefined) {
            await saveResponses();
            let data = {
                path: `/form/${vm.form.id}/${vm.distribution.id}/questions/recap`,
                historicPosition: vm.historicPosition
            };
            $scope.$emit(FORMULAIRE_EMIT_EVENT.REDIRECT, data);
        }
    };

    vm.nextGuard = () => {
        vm.next().then();
    };

    const goToFormElement = () : void => {
        initFormElementResponses();
        window.scrollTo(0, 0);
        $scope.safeApply();
    };

    const getNextPositionIfValid = () : number => {
        let nextPosition: number = vm.formElement.position + 1;
        let conditionalQuestion: Question = null;
        let response: Response = null;

        if (vm.formElement instanceof Question && vm.formElement.conditional) {
            conditionalQuestion = vm.formElement;
            response = vm.currentResponses.get(conditionalQuestion).all[0];
        }
        else if (vm.formElement instanceof Section) {
            let conditionalQuestions = vm.formElement.questions.all.filter((q: Question) => q.conditional);
            if (conditionalQuestions.length === 1) {
                conditionalQuestion = conditionalQuestions[0];
                response = vm.currentResponses.get(conditionalQuestion).all[0];
            }
        }

        if (conditionalQuestion && response && !response.choice_id) {
            notify.info('formulaire.response.next.invalid');
            nextPosition = undefined;
        }
        else if (conditionalQuestion && response) {
            let choices: QuestionChoice[] = conditionalQuestion.choices.all.filter((c: QuestionChoice) => c.id === response.choice_id);
            let nextElementId: number = choices.length === 1 ? choices[0].next_form_element_id : null;
            let nextElementType: FormElementType = choices.length === 1 ? choices[0].next_form_element_type : null;
            let filteredElements: FormElement[] = vm.formElements.all.filter((e: FormElement) => e.id === nextElementId && e.form_element_type == nextElementType);
            let targetedElement: FormElement = filteredElements.length === 1 ? filteredElements[0] : null;
            nextPosition = targetedElement ? targetedElement.position : null;
        }
        else if (vm.formElement instanceof Section && vm.formElement.questions.all.filter((q: Question) => q.conditional).length == 0) {
            nextPosition = vm.formElement.getNextFormElementPosition(vm.formElements);
        }

        return nextPosition;
    };

    vm.saveAndQuit = async () : Promise<void> => {
        if (await saveResponses()) {
            notify.success(idiom.translate('formulaire.success.responses.save'));
            window.setTimeout(function () { $scope.redirectTo(`/list/responses`); }, 1000);
        }
    };

    vm.saveAndQuitGuard = () => {
        vm.saveAndQuit().then();
    };

    vm.getHtmlDescription = (description: string) : string => {
        return !!description ? $sce.trustAsHtml(description) : null;
    }

    const saveResponses = async () : Promise<boolean> => {
        let isSavingOk: boolean = false;

        if (!vm.loading) {
            if (vm.formElement instanceof Question) {
                isSavingOk = await saveQuestionResponses(vm.formElement);
            }
            else if (vm.formElement instanceof Section) {
                isSavingOk = true;
                for (let question of vm.formElement.questions.all) {
                    isSavingOk = isSavingOk && await saveQuestionResponses(question);
                }
            }
        }

        if (isSavingOk) {
            $scope.$broadcast(FORMULAIRE_FORM_ELEMENT_EMIT_EVENT.DESTROY_FILE_PICKER);
            return true;
        }
        else {
            return false;
        }
    };

    const saveQuestionResponses = async (question: Question) : Promise<boolean> => {
        let responses: Responses = vm.currentResponses.get(question);
        let files: File[] = vm.currentFiles.get(question);

        if (question.isTypeMultipleRep() || question.isRanking()) {
            await responseService.deleteByQuestionAndDistribution(question.id, vm.distribution.id);
            if (responses.selected.length > 0) {
                for (let response of responses.selected) {
                    await responseService.create(response);
                }
            }
            else if (question.children.all.length > 0) {
                for (let child of question.children.all) {
                    await responseService.create(new Response(child.id, null, null, vm.distribution.id));
                }
            }
            else if (question.isRanking()) { // In case of question type ranking, we need to add a choice index to each Response
                let promises: any = [];
                for (let resp of responses.all) {
                    promises.push(responseService.create(new Response(question.id, resp.choice_id, resp.answer, vm.distribution.id,  resp.choice_position)));
                }
                await Promise.all(promises);
            }
            else {
                await responseService.create(new Response(question.id, null, null, vm.distribution.id));
            }
            return true;
        }

        if (responses.all[0].choice_id) {
            let matchingChoices: QuestionChoice[] = question.choices.all.filter((c: QuestionChoice) => c.id === responses.all[0].choice_id);
            if (matchingChoices.length == 1) {
                if (!matchingChoices[0].is_custom) {
                    responses.all[0].answer = matchingChoices[0].value;
                    responses.all[0].custom_answer = null;
                }
                else {
                    responses.all[0].answer = null;
                }
            }
        }
        responses.all[0] = await responseService.save(responses.all[0], question.question_type);

        if (question.question_type === Types.FILE && files) {
            return (await saveFiles(responses.all[0], files));
        }

        return true;
    };

    const saveFiles = async (response: Response, files: File[]) : Promise<boolean> => {
        if (files.length > 10) {
            notify.info(idiom.translate('formulaire.response.file.tooMany'));
            return false;
        }
        else {
            await responseFileService.deleteAll(response.id);
            for (let i = 0; i < files.length; i++) {
                let filename = files[i].name;
                if (files[i].type && !$scope.form.anonymous) {
                    filename = model.me.firstName + model.me.lastName + "_" + filename;
                }
                let file = new FormData();
                file.append("file", files[i], filename);
                await responseFileService.create(response.id, file);
            }
            response.answer = files.length > 0 ? idiom.translate('formulaire.response.file.send') : "";
            await responseService.update(response);
            return true;
        }
    };

    $scope.$on(FORMULAIRE_BROADCAST_EVENT.INIT_RESPOND_QUESTION, () => { initRespondQuestionController() });
}]);