import {Directive, ng} from "entcore";
import {Direction} from "@common/core/enums";
import {
    Distribution,
    Question,
    QuestionChoice,
    Response,
    ResponseFiles,
    Responses,
    Types
} from "@common/models";
import {FormElementUtils, I18nUtils} from "@common/utils";
import {PropPosition} from "@common/core/enums/prop-position";
import * as Sortable from "sortablejs";
import {RankingUtils} from "@common/utils/ranking";
import {RootsConst} from "../../../core/constants/roots.const";
import {IScope} from "angular";

interface IRespondQuestionItemScopeProps {
    question: Question;
    responses: Responses;
    distribution: Distribution;
    direction: typeof Direction;
    files: Array<File>;
    Types: typeof Types;
    I18n: I18nUtils;
    mapChoiceResponseIndex: Map<QuestionChoice, number>;
}

interface IViewModel extends ng.IController, IRespondQuestionItemScopeProps {
    $onInit() : Promise<void>;
    moveResponse(resp: Response, direction: string): void;
    getHtmlDescription(description: string) : string;
    $onChanges(changes: any): void;
    isSelectedChoiceCustom(choiceId: number): boolean;
    deselectIfEmpty(choice: QuestionChoice) : void;
    onClickChoice(choice: QuestionChoice): void;
    resetDate(): void;
    initDrag(): void;
}

interface IRespondQuestionItemScope extends IScope, IRespondQuestionItemScopeProps {
    vm: IViewModel;
}

class Controller implements ng.IController, IViewModel {
    question: Question;
    responses: Responses;
    distribution: Distribution;
    direction: typeof Direction;
    files: Array<File>;
    Types: typeof Types;
    I18n: I18nUtils;
    mapChoiceResponseIndex: Map<QuestionChoice, number>;

    constructor(private $scope: IRespondQuestionItemScope, private $sce: ng.ISCEService) {
        this.Types = Types;
        this.I18n = I18nUtils;
        this.direction = Direction;
    }

    $onInit = async () : Promise<void> => {
        await this.initRespondQuestionItem();
        if (this.question.question_type === Types.RANKING) {
            this.initDrag();
        }
        this.$scope.$apply();
    };

    $onChanges = (changes: any) : void => {
        this.question = changes.question.currentValue;
        this.$onInit();
    };

    initRespondQuestionItem = async () : Promise<void> => {
        if (this.question.question_type === Types.CURSOR) {
            this.responses.all[0].answer = this.question.cursor_min_val;
        }

        if (this.question.isTypeMultipleRep()) {
            let existingResponses: Responses = new Responses();
            if (this.distribution) await existingResponses.syncMine(this.question.id, this.distribution.id);
            this.mapChoiceResponseIndex = new Map();
            for (let choice of this.question.choices.all) {
                // Get potential existing response for this choice
                let existingMatchingResponses: Response[] = existingResponses.all.filter((r:Response) => r.choice_id == choice.id);

                // Get default response matching this choice and get its index in list
                let matchingResponses: Response[] = this.responses.all.filter((r:Response) => r.choice_id == choice.id);
                if (matchingResponses.length != 1) console.error("Be careful, 'this.responses' has been badly implemented !!");
                let matchingIndex = this.responses.all.indexOf(matchingResponses[0]);

                // If there was an existing response we use it to replace the default one
                if (existingMatchingResponses.length == 1) {
                    this.responses.all[matchingIndex] = existingMatchingResponses[0];
                    this.responses.all[matchingIndex].selected = true;
                }

                this.mapChoiceResponseIndex.set(choice, matchingIndex);
            }
        }
        else if (this.distribution) {
            let responses: Responses = new Responses();
            await responses.syncMine(this.question.id, this.distribution.id);
            if (responses.all.length > 0) {
                this.responses.all = [...responses.all];
                if (this.question.question_type === Types.CURSOR) {
                    let answer: number = Number.parseInt(this.responses.all[0].answer.toString());
                    this.responses.all[0].answer = Number.isNaN(answer) ? this.question.cursor_min_val : answer;
                }
            }
            if (!this.responses.all[0].question_id) this.responses.all[0].question_id = this.question.id;
            if (!this.responses.all[0].distribution_id) this.responses.all[0].distribution_id = this.distribution.id;
        }

        if (this.question.question_type === Types.TIME && typeof this.responses.all[0].answer == "string") {
            this.responses.all[0].answer = new Date("January 01 1970 " + this.responses.all[0].answer);
        }

        if (this.question.question_type === Types.FILE) {
            this.files = new Array<File>();
            if (this.responses.all[0].id) {
                let responseFiles: ResponseFiles = new ResponseFiles();
                await responseFiles.sync(this.responses.all[0].id);
                for (let repFile of responseFiles.all) {
                    if (repFile.id)  {
                        let file: File = new File([repFile.id], repFile.filename);
                        this.files.push(file);
                    }
                }
            }
        }
    };

    getHtmlDescription = (description: string) : string => {
        return !!description ? this.$sce.trustAsHtml(description) : null;
    }

    isSelectedChoiceCustom = (choiceId: number) : boolean => {
        let selectedChoice: QuestionChoice = this.question.choices.all.find((c: QuestionChoice) => c.id === choiceId);
        return selectedChoice && selectedChoice.is_custom;
    }

    deselectIfEmpty = (choice: QuestionChoice) : void => { // Unselected choice if custom answer is empty
        if (this.question.question_type === Types.SINGLEANSWERRADIO) {
            this.responses.all[0].choice_id = this.responses.all[0].custom_answer.length > 0 ? choice.id : null;
        }
        else if (this.question.question_type === Types.MULTIPLEANSWER) {
            let response: Response = this.responses.all[this.mapChoiceResponseIndex.get(choice)];
            response.selected = response.custom_answer.length > 0;
        }
        else return;
    }

    moveResponse = (resp: Response, direction: string) : void => {
        FormElementUtils.switchPositions(this.responses, resp.choice_position - 1, direction, PropPosition.CHOICE_POSITION);
        this.responses.all.sort((a: Response, b: Response) => a.choice_position - b.choice_position);
    }

    onClickChoice = (choice: QuestionChoice) : void => {
        this.responses.all[0].choice_id = (this.responses.all[0].choice_id != choice.id) ? choice.id : null;
    }

    resetDate = () : void => {
        this.responses.all[0].answer = new Date();
    }

    initDrag = (): void => {
        // Loop through each sortable response for DragAndDrop in view response
        window.setTimeout((): void => {
            let respDrag = document.querySelectorAll(".drag-container");
            for (let i = 0; i < respDrag.length; i++) {
                Sortable.create(respDrag[i], {
                    group: "drag-container",
                    animation: 150,
                    fallbackOnBody: true,
                    swapThreshold: 0.65,
                    ghostClass: "sortable-ghost",
                    onEnd: async (evt): Promise<void> => {
                        await RankingUtils.onEndRankingDragAndDrop(evt, this.responses);
                        this.$scope.$apply();
                    }
                });
            }
        }, 500);
    };

}

function directive() {
    return {
        restrict: 'E',
        templateUrl: `${RootsConst.directive}question/respond-question-item/respond-question-item.html`,
        transclude: true,
        scope: {
            question: '<',
            responses: '=',
            distribution: '=',
            files: '='
        },
        controllerAs: 'vm',
        bindToController: true,
        controller: ['$scope', '$sce', Controller],
        /* interaction DOM/element */
        link: function ($scope: IRespondQuestionItemScope,
                        element: ng.IAugmentedJQuery,
                        attrs: ng.IAttributes,
                        vm: IViewModel) {
        }
    }
}

export const respondQuestionItem: Directive = ng.directive('respondQuestionItem', directive);
