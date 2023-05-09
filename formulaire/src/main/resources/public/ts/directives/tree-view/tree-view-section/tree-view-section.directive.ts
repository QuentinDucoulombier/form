import {Directive, idiom, ng} from "entcore";
import {Section} from "@common/models";
import {RootsConst} from "../../../core/constants/roots.const";
import {IScope} from "angular";

interface ITreeViewSectionProps {
    section: Section;
}

interface IViewModel extends ng.IController, ITreeViewSectionProps {
    getTitle(title: string): string;
}

interface ITreeViewSectionScope extends IScope, ITreeViewSectionProps{
    vm: IViewModel;
}

class Controller implements IViewModel {
    section: Section;

    constructor(private $scope: ITreeViewSectionScope, private $sce: ng.ISCEService) {
    }

    $onInit = async (): Promise<void> => {}

    $onDestroy = async (): Promise<void> => {}

    getTitle = (title: string) : string => {
        return idiom.translate('formulaire.' + title);
    }
}

function directive() {
    return {
        restrict: 'E',
        templateUrl: `${RootsConst.directive}tree-view/tree-view-section/tree-view-section.html`,
        transclude: true,
        scope: {
            section: '='
        },
        controllerAs: 'vm',
        bindToController: true,
        controller: ['$scope', '$sce', Controller],
        /* interaction DOM/element */
        link: function ($scope: ITreeViewSectionScope,
                        element: ng.IAugmentedJQuery,
                        attrs: ng.IAttributes,
                        vm: IViewModel) {
        }
    }
}

export const treeViewSection: Directive = ng.directive('treeViewSection', directive);