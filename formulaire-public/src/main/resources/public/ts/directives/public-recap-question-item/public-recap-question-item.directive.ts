import {Directive, idiom, ng, template} from "entcore";
import {
    FormElements,
    Question,
    QuestionChoice,
    Response,
    Responses, Section,
    Types
} from "@common/models";
import {IScope} from "angular";
import {RootsConst} from "../../core/constants/roots.const";

interface IPublicRecapQuestionItemProps {
    question: Question;
    responses: Responses;
    formElements: FormElements;
    historicPosition: number[];
}

interface IViewModel {
    types: typeof Types;
    missingResponseHtml: string;
    otherHtml: string;

    getHtmlDescription(description: string) : string;
    getStringResponse(): string;
    isSelectedChoice(choice: QuestionChoice, child?: Question) : boolean;
    openQuestion(): void;
    filterQuestionResponses(): Response[];
}

interface IPublicRecapQuestionItemScope extends IScope, IPublicRecapQuestionItemProps {
    vm: IViewModel;
}

class Controller implements IViewModel {
    question: Question;
    responses: Responses;
    formElements: FormElements;
    historicPosition: number[];
    types: typeof Types;
    missingResponseHtml: string;
    otherHtml: string;

    constructor(private $scope: IPublicRecapQuestionItemScope, private $sce: ng.ISCEService) {
        this.types = Types;
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
        let selectedChoices: number[] = this.responses.all
            .filter((r: Response) => r.question_id === this.question.id || (child && r.question_id === child.id))
            .map((r: Response) => r.choice_id);
        return (selectedChoices as any).includes(choice.id);
    };

    openQuestion = () : void => {
        let formElementPosition: number = this.question.position;
        if (!this.question.position) {
            let sections: Section[] = this.formElements.getSections().all.filter((s: Section) => s.id === this.question.section_id);
            formElementPosition = sections.length === 1 ? sections[0].position : null;
        }
        this.historicPosition = this.historicPosition.slice(0, this.historicPosition.indexOf(formElementPosition) + 1);
        sessionStorage.setItem('historicPosition', JSON.stringify(this.historicPosition));
        template.open('main', 'containers/respond-question');
    };

    filterQuestionResponses = () : Response[] => {
        return this.responses.all.filter((r: Response) => r.question_id === this.question.id);
    }
}

function directive() {
    return {
        restrict: 'E',
        templateUrl: `${RootsConst.directive}public-recap-question-item/public-recap-question-item.html`,
        transclude: true,
        scope: {
            question: '=',
            responses: '=',
            formElements: '<',
            historicPosition: '='
        },
        controllerAs: 'vm',
        bindToController: true,
        controller: ['$scope', '$sce', Controller],
        /* interaction DOM/element */
        link: function ($scope: IPublicRecapQuestionItemScope,
                        element: ng.IAugmentedJQuery,
                        attrs: ng.IAttributes,
                        vm: IViewModel) {
        }
    }
}

export const publicRecapQuestionItem: Directive = ng.directive('publicRecapQuestionItem', directive);
