import {Directive, ng} from "entcore";
import {
    Question, QuestionChoice,
    Response,
    Responses,
    Types
} from "@common/models";
import {I18nUtils} from "@common/utils";
import {RootsConst} from "../../core/constants/roots.const";
import {IScope} from "angular";
import {FORMULAIRE_FORM_ELEMENT_EMIT_EVENT} from "@common/core/enums";

interface IPublicRespondMatrixProps {
    question: Question;
    responses: Responses;
}

interface IViewModel {
    mapChildChoicesResponseIndex: Map<Question, Map<QuestionChoice, number>>;
    types: typeof Types;
    i18n: I18nUtils;

    switchValue(child: Question, choice: QuestionChoice): void;
    resetLine(child: Question): void;
}

interface IPublicRespondMatrixScope extends IScope, IPublicRespondMatrixProps {
    vm: IViewModel;
}

class Controller implements IViewModel {
    question: Question;
    responses: Responses;
    mapChildChoicesResponseIndex: Map<Question, Map<QuestionChoice, number>>;
    types: typeof Types;
    i18n: I18nUtils;

    constructor(private $scope: IPublicRespondMatrixScope, private $sce: ng.ISCEService) {
        this.types = Types;
        this.i18n = I18nUtils;
    }

    $onInit = async () : Promise<void> => {
        await this.init();

        // used when previous question is same type than this one
        this.$scope.$on(FORMULAIRE_FORM_ELEMENT_EMIT_EVENT.REFRESH_QUESTION, (event, data) => {
            this.responses = data.get(this.question);
            this.init();
        });
    }

    $onDestroy = async () : Promise<void> => {}

    init = async () : Promise<void> => {
        if (this.responses == null) return; // happens when previous question is not same type than this one

        this.mapChildChoicesResponseIndex = new Map();
        for (let child of this.question.children.all) {
            this.mapChildChoicesResponseIndex.set(child, new Map());
            for (let choice of this.question.choices.all) {
                let matchingResponses: Response[] = this.responses.all.filter((r:Response) => r.question_id == child.id && r.choice_id == choice.id);
                if (matchingResponses.length != 1) console.error("Be careful, 'vm.responses' has been badly implemented !!");
                this.mapChildChoicesResponseIndex.get(child).set(choice, this.responses.all.indexOf(matchingResponses[0]));
            }
        }
    }

    switchValue = (child: Question, choice: QuestionChoice) : void => {
        if (child.question_type == this.types.SINGLEANSWERRADIO) {
            for (let response of this.responses.all) {
                if (response.question_id == child.id && response.choice_id != choice.id) {
                    response.selected = false;
                }
            }
        }
        this.$scope.$apply();
    }

    resetLine = (child: Question) : void => {
        this.responses.all
            .filter((r: Response) => r.question_id == child.id)
            .forEach((r: Response) => r.selected = false);
    }
}

function directive() {
    return {
        restrict: 'E',
        templateUrl: `${RootsConst.directive}public-respond-matrix/public-respond-matrix.html`,
        transclude: true,
        scope: {
            question: '<',
            responses: '='
        },
        controllerAs: 'vm',
        bindToController: true,
        controller: ['$scope', '$sce', Controller],
        /* interaction DOM/element */
        link: function ($scope: IPublicRespondMatrixScope,
                        element: ng.IAugmentedJQuery,
                        attrs: ng.IAttributes,
                        vm: IViewModel) {
        }
    }
}

export const publicRespondMatrix: Directive = ng.directive('publicRespondMatrix', directive);
