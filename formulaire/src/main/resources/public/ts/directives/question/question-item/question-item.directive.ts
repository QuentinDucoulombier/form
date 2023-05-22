import {Directive, idiom, ng} from "entcore";
import {FormElement, FormElements, Question, Section, Types} from "@common/models";
import {FORMULAIRE_FORM_ELEMENT_EMIT_EVENT} from "@common/core/enums";
import {Constants} from "@common/core/constants";
import {RootsConst} from "../../../core/constants/roots.const";
import {IScope} from "angular";

interface IQuestionItemProps {
    question: Question;
    reorder: boolean;
    hasFormResponses: boolean;
    formElements: FormElements;
}

interface IViewModel extends ng.IController, IQuestionItemProps {
    types: typeof Types;
    matrixType: number;

    getTitle(title: string): string;
    duplicateQuestion(): void;
    deleteQuestion(): Promise<void>;
    undoQuestionChanges(): void;
    showConditionalSwitch(): boolean;
    onSwitchMandatory(isMandatory: boolean): void;
    onSwitchConditional(isConditional: boolean): void;
    cursorChoiceIsConsistent(): boolean;
}

interface IQuestionItemScope extends IScope, IQuestionItemProps{
    vm: IViewModel;
}

class Controller implements IViewModel {
    question: Question;
    reorder: boolean;
    hasFormResponses: boolean;
    formElements: FormElements;
    types: typeof Types;
    matrixType: number;
    matrixTypes: Types[];

    constructor(private $scope: IQuestionItemScope, private $sce: ng.ISCEService) {
        this.types = Types;
    }

    $onInit = async () : Promise<void> => {
        this.matrixType = this.question.children.all.length > 0 && this.question.children.all[0].question_type ?
            this.question.children.all[0].question_type : this.types.SINGLEANSWERRADIO;
        this.matrixTypes = [this.types.SINGLEANSWERRADIO, this.types.MULTIPLEANSWER];
    }

    $onDestroy = async () : Promise<void> => {}

    getTitle = (title: string) : string => {
        if (title == 'mandatory.explanation' && this.question.question_type == this.types.MATRIX) {
            return idiom.translate('formulaire.' + title + '.matrix');
        }
        return idiom.translate('formulaire.' + title);
    }

    duplicateQuestion = () : void => {
        if (!this.hasFormResponses) {
            this.$scope.$emit(FORMULAIRE_FORM_ELEMENT_EMIT_EVENT.DUPLICATE_ELEMENT);
        }
    }

    deleteQuestion =  async() : Promise<void> => {
        if (!this.hasFormResponses) {
            this.$scope.$emit(FORMULAIRE_FORM_ELEMENT_EMIT_EVENT.DELETE_ELEMENT);
        }
    }

    undoQuestionChanges = () : void => {
        this.$scope.$emit(FORMULAIRE_FORM_ELEMENT_EMIT_EVENT.UNDO_CHANGES);
    }


    showConditionalSwitch = () : boolean => {
        let isConditionalQuestionType = this.question.question_type === Types.SINGLEANSWER || this.question.question_type === Types.SINGLEANSWERRADIO;
        if (!isConditionalQuestionType) {
            return false;
        }
        else if (!this.question.section_id) {
            return true;
        }
        else {
            let parentSection = this.formElements.all.filter(e => e.id === this.question.section_id)[0];
            return (parentSection as Section).questions.all.filter(q => q.id != this.question.id && q.conditional).length <= 0;
        }
    }

    onSwitchMandatory = (isMandatory: boolean) : void => {
        if (this.hasFormResponses) {
            this.question.mandatory = !isMandatory;
        }
        if (!isMandatory && this.question.conditional) {
            this.question.mandatory = true;
        }
        this.$scope.$apply();
    }

    onSwitchConditional = (isConditional: boolean) : void => {
        if (this.hasFormResponses) {
            this.question.conditional = !isConditional;
        }
        this.question.mandatory = this.question.conditional;
        this.question.setChoicesNextFormElementsProps(this.formElements);
        this.question.setParentSectionNextFormElements(this.formElements);
        this.$scope.$apply();
    }

    cursorChoiceIsConsistent = () : boolean => {
        const minVal: number = this.question.cursor_min_val != null ? this.question.cursor_min_val : Constants.DEFAULT_CURSOR_MIN_VALUE;
        const maxVal: number = this.question.cursor_max_val != null ? this.question.cursor_max_val : Constants.DEFAULT_CURSOR_MAX_VALUE;
        const step: number = this.question.cursor_step != null ? this.question.cursor_step : Constants.DEFAULT_CURSOR_STEP;
        return (maxVal - minVal) % step == 0;
    }

    getPosition = () : number => {
        return this.question.position ?
            this.question.position :
            this.formElements.all.filter((e: FormElement) => e.id === this.question.section_id)[0].position;
    }

    getNextElementId = () : number => {
        let nextElements: FormElement[] = this.formElements.all.filter((e: FormElement) => e.position === this.getPosition() + 1);
        return nextElements.length > 0 ? nextElements[0].id : null;
    }
}

function directive() {
    return {
        restrict: 'E',
        templateUrl: `${RootsConst.directive}question/question-item/question-item.html`,
        transclude: true,
        scope: {
            question: '=',
            reorder: '=',
            hasFormResponses: '=',
            formElements: '<'
        },
        controllerAs: 'vm',
        bindToController: true,
        controller: ['$scope', '$sce', Controller],
        /* interaction DOM/element */
        link: function ($scope: IQuestionItemScope,
                        element: ng.IAugmentedJQuery,
                        attrs: ng.IAttributes,
                        vm: IViewModel) {
        }
    }
}

export const questionItem: Directive = ng.directive('questionItem', directive);