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
} from "@common/models";
import {FORMULAIRE_EMIT_EVENT} from "@common/core/enums";
import {IScope} from "angular";
import {RootsConst} from "../../../core/constants/roots.const";

interface IRecapQuestionItemProps {
    question: Question;
    responses: Responses;
    form: Form;
    formElements: FormElements;
    distribution: Distribution;
    historicPosition: number[];
}

interface IViewModel {
    types: typeof Types;
    distributionStatus: typeof DistributionStatus;
    missingResponseHtml: string;
    otherHtml: string;

    getHtmlDescription(description: string) : string;
    getStringResponse(): string;
    isSelectedChoice(choice: QuestionChoice, child?: Question) : boolean;
    getResponseFileNames() : string[];
    openQuestion(): void;
    filterQuestionResponses(): Response[];
}

interface IRecapQuestionItemScope extends IScope, IRecapQuestionItemProps {
    vm: IViewModel;
}

class Controller implements IViewModel {
    question: Question;
    responses: Responses;
    form: Form;
    formElements: FormElements;
    distribution: Distribution;
    historicPosition: number[];
    types: typeof Types;
    distributionStatus: typeof DistributionStatus;
    missingResponseHtml: string;
    otherHtml: string;

    constructor(private $scope: IRecapQuestionItemScope, private $sce: ng.ISCEService) {
        this.types = Types;
        this.distributionStatus = DistributionStatus;
        this.missingResponseHtml = "<em>" + idiom.translate('formulaire.public.response.missing') + "</em>";
        this.otherHtml = "<em>" + idiom.translate('formulaire.public.other') + " : </em>";
    }

    $onInit = async () : Promise<void> => {}

    $onDestroy = async () : Promise<void> => {}

    // Display helper functions

    getHtmlDescription = (description: string) : string => {
        return !!description ? this.$sce.trustAsHtml(description) : null;
    }

    getStringResponse = () : string => {
        let responses: Response[] = this.filterQuestionResponses();
        if (responses == null || responses.length <= 0) {
            return this.getHtmlDescription(this.missingResponseHtml)
        }

        if (this.question.canHaveCustomAnswers()) {
            let customChoices: QuestionChoice[] = this.question.choices.all.filter((c: QuestionChoice) => c.is_custom);
            let customChoiceId: number = customChoices.length > 0 ? customChoices[0].id : null;
            let customResponses: Response[] = responses.filter((r: Response) => r.choice_id === customChoiceId);
            let customResponse: Response = customResponses.length > 0 ? customResponses[0] : null;

            if (customResponse) {
                let customAnswer: string = customResponse.custom_answer
                    ? customResponse.custom_answer.toString()
                    : this.missingResponseHtml;
                return `${this.otherHtml}${customAnswer}`;
            }
        }

        let answer: string = responses[0].answer.toString();
        return answer ? answer : this.missingResponseHtml;
    };

    isSelectedChoice = (choice: QuestionChoice, child?) : boolean => {
        let selectedChoices: any = this.responses.all
            .filter((r: Response) => r.question_id === this.question.id || (child && r.question_id === child.id))
            .map((r: Response) => r.choice_id);
        return selectedChoices.includes(choice.id);
    };

    getResponseFileNames = () : string[] => {
        let responses: Response[] = this.responses.all.filter((r: Response) => r.question_id === this.question.id);
        if (responses && responses.length === 1 && responses[0].files.all.length > 0) {
            return responses[0].files.all.map((rf: ResponseFile) => rf.filename.substring(rf.filename.indexOf("_") + 1));
        }
        else {
            return [this.missingResponseHtml];
        }
    };

    openQuestion = () : void => {
        let formElementPosition: number = this.question.position;
        if (!this.question.position) {
            let sections: Section[] = this.formElements.getSections().all.filter((s: Section) => s.id === this.question.section_id);
            formElementPosition = sections.length === 1 ? sections[0].position : null;
        }
        let newHistoric: number[] = this.historicPosition.slice(0, this.historicPosition.indexOf(formElementPosition) + 1);
        let data: any = {
            path: `/form/${this.form.id}/${this.distribution.id}`,
            position: formElementPosition,
            historicPosition: newHistoric
        };
        this.$scope.$emit(FORMULAIRE_EMIT_EVENT.REDIRECT, data);
    };

    filterQuestionResponses = () : Response[] => {
        return this.responses.all.filter((r: Response) => r.question_id === this.question.id);
    }
}

function directive() {
    return {
        restrict: 'E',
        templateUrl: `${RootsConst.directive}/question/recap-question-item/recap-question-item.html`,
        transclude: true,
        scope: {
            question: '=',
            responses: '=',
            form: '=',
            formElements: '<',
            distribution: '=',
            historicPosition: '='
        },
        controllerAs: 'vm',
        bindToController: true,
        controller: ['$scope', '$sce', Controller],
        /* interaction DOM/element */
        link: function ($scope: IRecapQuestionItemScope,
                        element: ng.IAugmentedJQuery,
                        attrs: ng.IAttributes,
                        vm: IViewModel) {
        }
    }
}

export const recapQuestionItem: Directive = ng.directive('recapQuestionItem', directive);
