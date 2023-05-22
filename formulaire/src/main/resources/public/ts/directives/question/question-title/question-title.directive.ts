import {Directive, ng} from "entcore";
import {Question, Types} from "@common/models";
import {I18nUtils} from "@common/utils";
import {IScope} from "angular";
import {RootsConst} from "../../../core/constants/roots.const";

interface IQuestionTitleProps {
    question: Question;
    hasFormResponses: boolean;
    matrixType: number;
    matrixTypes: Types[];
}

interface IViewModel extends ng.IController, IQuestionTitleProps {
    i18n: I18nUtils;
    types: typeof Types;
    onChangeMatrixType(matrixType: number): void;
}

interface IQuestionTitleScope extends IScope, IQuestionTitleProps {
    vm: IViewModel;
}

class Controller implements IViewModel {
    question: Question;
    hasFormResponses: boolean;
    matrixType: number;
    matrixTypes: Types[];
    i18n: I18nUtils;
    types: typeof Types;

    constructor(private $scope: IQuestionTitleScope, private $sce: ng.ISCEService) {
        this.types = Types;
        this.i18n = I18nUtils;
    }

    $onInit = async () : Promise<void> => {}

    $onDestroy = async () : Promise<void> => {}

    onChangeMatrixType = (matrixType: number) : void => {
        for (let child of this.question.children.all) {
            child.question_type = matrixType;
        }
    }
}

function directive() {
    return {
        restrict: 'E',
        templateUrl: `${RootsConst.directive}question/question-title/question-title.html`,
        transclude: true,
        scope: {
            question: '=',
            hasFormResponses: '=',
            matrixType: '=',
            matrixTypes: '='
        },
        controllerAs: 'vm',
        bindToController: true,
        controller: ['$scope', '$sce', Controller],
        /* interaction DOM/element */
        link: function ($scope: IQuestionTitleProps,
                        element: ng.IAugmentedJQuery,
                        attrs: ng.IAttributes,
                        vm: IViewModel) {
        }
    }
}

export const questionTitle: Directive = ng.directive('questionTitle', directive);
