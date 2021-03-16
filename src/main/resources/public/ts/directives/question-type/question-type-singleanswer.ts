import {Directive, ng} from "entcore";
import {Question, QuestionChoice} from "../../models";
import {questionChoiceService} from "../../services";
import {FORMULAIRE_EMIT_EVENT} from "../../core/enums/formulaire-event";

interface IViewModel {
    question: Question,

    createNewChoice(): void,
    deleteChoice(index: number): Promise<void>
}

export const questionTypeSingleanswer: Directive = ng.directive('questionTypeSingleanswer', () => {

    return {
        restrict: 'E',
        transclude: true,
        scope: {
            question: '='
        },
        controllerAs: 'vm',
        bindToController: true,
        replace: true,
        template: `
            <div class="seven">
                <div ng-repeat="choice in vm.question.choices.all">
                    <span>[[$index + 1]].</span>
                    <input type="text" class="eleven" ng-model="choice.value" placeholder="Choix [[$index + 1]]" ng-if="!vm.question.selected" disabled>
                    <input type="text" class="eleven" ng-model="choice.value" placeholder="Choix [[$index + 1]]" ng-if="vm.question.selected">
                    <img src="/formulaire/public/img/icons/cancel.svg" ng-click="vm.deleteChoice($index)" ng-if="vm.question.selected"/>
                </div>
                <div style="display: flex; justify-content: center;" ng-if="vm.question.selected">
                    <img src="/formulaire/public/img/icons/plus-circle.svg" ng-click="vm.createNewChoice()"/>
                </div>
            </div>
        `,

        controller: async ($scope) => {
            const vm: IViewModel = <IViewModel> this;
        },
        link: ($scope, $element) => {
            const vm: IViewModel = $scope.vm;

            vm.createNewChoice = () : void => {
                vm.question.choices.all.push(new QuestionChoice(vm.question.id));
                $scope.$emit(FORMULAIRE_EMIT_EVENT.REFRESH);
            };

            vm.deleteChoice = async (index: number) : Promise<void> => {
                if (!!vm.question.choices.all[index].id) {
                    await questionChoiceService.delete(vm.question.choices.all[index].id);
                }
                vm.question.choices.all.splice(index,1);
                $scope.$emit(FORMULAIRE_EMIT_EVENT.REFRESH);
            };
        }
    };
});