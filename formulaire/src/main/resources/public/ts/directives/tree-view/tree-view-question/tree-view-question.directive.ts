import {Directive, idiom, ng} from "entcore";
import {Question} from "@common/models";
import {RootsConst} from "../../../core/constants/roots.const";
import {IScope} from "angular";
import {IconUtils} from "@common/utils/icon";

interface ITreeViewQuestionProps {
    question: Question;
}

interface IViewModel extends ng.IController, ITreeViewQuestionProps {
    iconUtils: IconUtils;

    getTitle(title: string): string;
}

interface ITreeViewQuestionScope extends IScope, ITreeViewQuestionProps{
    vm: IViewModel;
}

class Controller implements IViewModel {
    question: Question;
    iconUtils = IconUtils;

    constructor(private $scope: ITreeViewQuestionScope, private $sce: ng.ISCEService) {
    }

    $onInit = async () : Promise<void> => {}

    $onDestroy = async () : Promise<void> => {}

    getTitle = (title: string) : string => {
        return idiom.translate('formulaire.' + title);
    }
}

function directive() {
    return {
        restrict: 'E',
        templateUrl: `${RootsConst.directive}tree-view/tree-view-question/tree-view-question.html`,
        transclude: true,
        scope: {
            question: '='
        },
        controllerAs: 'vm',
        bindToController: true,
        controller: ['$scope', '$sce', Controller],
        /* interaction DOM/element */
        link: function ($scope: ITreeViewQuestionScope,
                        element: ng.IAugmentedJQuery,
                        attrs: ng.IAttributes,
                        vm: IViewModel) {
        }
    }
}

export const treeViewQuestion: Directive = ng.directive('treeViewQuestion', directive);