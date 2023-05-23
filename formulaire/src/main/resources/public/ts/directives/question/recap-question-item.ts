import {Directive, idiom, ng} from "entcore";
import {
    Distribution,
    DistributionStatus,
    Form,
    FormElements,
    Question,
    QuestionChoice,
    Response, ResponseFile,
    Responses, Section,
    Types
} from "../../models";
import {FORMULAIRE_EMIT_EVENT} from "@common/core/enums";

interface IViewModel {
    question: Question;
    responses: Responses;
    form: Form;
    formElements: FormElements;
    distribution: Distribution;
    historicPosition: number[];
    Types: typeof Types;
    DistributionStatus: typeof DistributionStatus;

    getHtmlDescription(description: string) : string;
    getStringResponse(): string;
    isSelectedChoice(choice: QuestionChoice, child?: Question) : boolean;
    getResponseFileNames() : string[];
    openQuestion(): void;
    filterQuestionResponses(): Response[];
}

export const recapQuestionItem: Directive = ng.directive('recapQuestionItem', ['$sce', ($sce) => {
    return {
        restrict: 'E',
        transclude: true,
        scope: {
            question: '=',
            responses: '=',
            form: '=',
            formElements: '<',
            distribution: '=',
            historicPosition: '<'
        },
        controllerAs: 'vm',
        bindToController: true,
        template: `
                <div class="question">
                    <div class="question-title">
                        <h4>[[vm.question.title]]<span ng-if="vm.question.mandatory" style="color:red; margin-left:10px">*</span></h4>
                    </div>
                    <div class="question-main">
                        <div ng-if="vm.question.question_type == vm.Types.FREETEXT">
                            <div ng-if="vm.question.statement" data-ng-bind-html="vm.getHtmlDescription(vm.question.statement)"></div>
                        </div>
                        <div ng-if="vm.question.question_type == vm.Types.SHORTANSWER">
                            <div data-ng-bind-html="vm.getStringResponse()"></div>
                        </div>
                        <div ng-if="vm.question.question_type == vm.Types.LONGANSWER">
                            <div data-ng-bind-html="vm.getStringResponse()"></div>
                        </div>
                        <div ng-if="vm.question.question_type == vm.Types.SINGLEANSWER">
                            <div data-ng-bind-html="vm.getStringResponse()"></div>
                        </div>
                        <div ng-if="vm.question.question_type == vm.Types.MULTIPLEANSWER">
                            <div ng-repeat="choice in vm.question.choices.all | orderBy:['position', 'id']">
                                <label>
                                    <input type="checkbox" disabled checked ng-if="vm.isSelectedChoice(choice)">
                                    <input type="checkbox" disabled ng-if="!vm.isSelectedChoice(choice)">
                                    <span style="cursor: default"></span>
                                    <span ng-if="!choice.is_custom || !vm.isSelectedChoice(choice)">[[choice.value]]</span>
                                    <span ng-if="choice.is_custom && vm.isSelectedChoice(choice)" ng-bind-html="vm.getStringResponse()"></span>
                                </label>
                            </div>
                        </div>
                        <div ng-if="vm.question.question_type == vm.Types.DATE">
                            <div data-ng-bind-html="vm.getStringResponse()"></div>
                        </div>
                        <div ng-if="vm.question.question_type == vm.Types.TIME">
                            <div data-ng-bind-html="vm.getStringResponse()"></div>
                        </div>
                        <div ng-if="vm.question.question_type == vm.Types.FILE">
                            <div ng-repeat="filename in vm.getResponseFileNames()">
                                <span data-ng-bind-html="vm.getHtmlDescription(filename)"></span>
                            </div>
                        </div>
                        <div ng-if="vm.question.question_type == vm.Types.SINGLEANSWERRADIO">
                            <div ng-repeat="choice in vm.question.choices.all | orderBy:['position', 'id']">
                                <label>
                                    <input type="radio" disabled checked ng-if="vm.isSelectedChoice(choice)">
                                    <input type="radio" disabled ng-if="!vm.isSelectedChoice(choice)">
                                    <span style="cursor: default"></span>
                                    <span ng-if="!choice.is_custom || !vm.isSelectedChoice(choice)">[[choice.value]]</span>
                                    <span ng-if="choice.is_custom && vm.isSelectedChoice(choice)" ng-bind-html="vm.getStringResponse()"></span>
                                </label>
                            </div>
                        </div>
                        <table ng-if="vm.question.question_type == vm.Types.MATRIX" class="twelve matrix-table">
                            <thead>
                                <tr>
                                    <th class="two"></th>
                                    <th ng-repeat="choice in vm.question.choices.all | orderBy:['position', 'id']">[[choice.value]]</th>
                                </tr>
                            </thead>
                            <tbody>
                                <tr ng-repeat="child in vm.question.children.all | orderBy:matrix_position" ng-init="childIndex = $index">
                                    <td>[[child.title]]</td>
                                    <td ng-repeat ="choice in vm.question.choices.all | orderBy:['position', 'id']">
                                        <label ng-if="vm.question.isMatrixSingle()">
                                            <input type="radio" disabled checked ng-if="vm.isSelectedChoice(choice, child)">
                                            <input type="radio" disabled ng-if="!vm.isSelectedChoice(choice, child)">
                                            <span style="cursor: default"></span>
                                        </label>
                                        <label ng-if="vm.question.isMatrixMultiple()">
                                            <input type="checkbox" disabled checked ng-if="vm.isSelectedChoice(choice, child)">
                                            <input type="checkbox" disabled ng-if="!vm.isSelectedChoice(choice, child)">
                                            <span></span>
                                        </label>
                                    </td>
                                </tr>
                            </tbody>
                        </table>
                        <div ng-if="vm.question.question_type == vm.Types.CURSOR">
                            <span><i18n>formulaire.selected.value</i18n></span>
                            <span ng-bind-html="vm.getStringResponse()"></span>
                        </div>
                        <div ng-if="vm.question.question_type == vm.Types.RANKING">
                            <div ng-repeat="resp in vm.filterQuestionResponses() | orderBy:['choice_position', 'id']">
                                <label>
                                    <span style="cursor: default"></span>
                                    <span class="ten eight-mobile">[[resp.answer]]</span>
                                </label>
                            </div>
                        </div>
                    </div>
                    <div class="question-edit" ng-if="(vm.form.editable || vm.distribution.status != vm.DistributionStatus.FINISHED) 
                    && vm.question.question_type != vm.Types.FREETEXT">
                        <a ng-click="vm.openQuestion()"><i18n>formulaire.edit</i18n></a>
                    </div>
                </div>
        `,

        controller: function ($scope) {
            const vm: IViewModel = <IViewModel> this;
        },
        link: function ($scope, $sce) {
            const vm: IViewModel = $scope.vm;
            vm.Types = Types;
            vm.DistributionStatus = DistributionStatus;
            let missingResponseHtml: string = "<em>" + idiom.translate('formulaire.response.missing') + "</em>";
            let otherHtml: string = "<em>" + idiom.translate('formulaire.other') + " : </em>";

            // Display helper functions

            vm.getHtmlDescription = (description: string) : string => {
                return !!description ? $sce.trustAsHtml(description) : null;
            }

            vm.getStringResponse = () : string => {
                let responses: Response[] = vm.filterQuestionResponses();
                if (responses == null || responses.length <= 0) {
                    return vm.getHtmlDescription(missingResponseHtml)
                }

                if (vm.question.canHaveCustomAnswers()) {
                    let customChoices: QuestionChoice[] = vm.question.choices.all.filter((c: QuestionChoice) => c.is_custom);
                    let customChoiceId: number = customChoices.length > 0 ? customChoices[0].id : null;
                    let customResponses: Response[] = responses.filter((r: Response) => r.choice_id === customChoiceId);
                    let customResponse: Response = customResponses.length > 0 ? customResponses[0] : null;

                    if (customResponse) {
                        let customAnswer: string = customResponse.custom_answer
                            ? customResponse.custom_answer.toString()
                            : missingResponseHtml;
                        return `${otherHtml}${customAnswer}`;
                    }
                }

                let answer: string = responses[0].answer.toString();
                return answer ? answer : missingResponseHtml;
            };

            vm.isSelectedChoice = (choice: QuestionChoice, child?) : boolean => {
                let selectedChoices: any = vm.responses.all
                    .filter((r: Response) => r.question_id === vm.question.id || (child && r.question_id === child.id))
                    .map((r: Response) => r.choice_id);
                return selectedChoices.includes(choice.id);
            };

            vm.getResponseFileNames = () : string[] => {
                let responses: Response[] = vm.responses.all.filter((r: Response) => r.question_id === vm.question.id);
                if (responses && responses.length === 1 && responses[0].files.all.length > 0) {
                    return responses[0].files.all.map((rf: ResponseFile) => rf.filename.substring(rf.filename.indexOf("_") + 1));
                }
                else {
                    return [missingResponseHtml];
                }
            };

            vm.openQuestion = () : void => {
                let formElementPosition: number = vm.question.position;
                if (!vm.question.position) {
                    let sections: Section[] = vm.formElements.getSections().all.filter((s: Section) => s.id === vm.question.section_id);
                    formElementPosition = sections.length === 1 ? sections[0].position : null;
                }
                let newHistoric: number[] = vm.historicPosition.slice(0, vm.historicPosition.indexOf(formElementPosition) + 1);
                let data: any = {
                    path: `/form/${vm.form.id}/${vm.distribution.id}`,
                    position: formElementPosition,
                    historicPosition: newHistoric
                };
                $scope.$emit(FORMULAIRE_EMIT_EVENT.REDIRECT, data);
            };

            vm.filterQuestionResponses = () : Response[] => {
                return vm.responses.all.filter((r: Response) => r.question_id === vm.question.id);
            }
        }
    };
}]);
