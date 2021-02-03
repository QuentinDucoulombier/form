import {idiom, ng, notify, template} from "entcore";
import {Distribution, DistributionStatus, Question, Response} from "../models";
import {distributionService, formService, questionService} from "../services";
import {responseService} from "../services/ResponseService";

interface ViewModel {
    question: Question;
    response: Response;
    distribution: Distribution;
    nbQuestions: number;
    last: boolean;

    prev(): Promise<void>;
    next(): Promise<void>;
    saveAndQuit(): Promise<void>;
    send(): Promise<void>;
}

export const questionResponderController = ng.controller('QuestionResponderController', ['$scope',
    function ($scope) {

    const vm: ViewModel = this;
    vm.question = new Question();
    vm.response = new Response();
    vm.distribution = new Distribution();
    vm.nbQuestions = 1;
    vm.last = false;

    const init = async (): Promise<void> => {
        vm.question = $scope.question;
        vm.response = $scope.getDataIf200(await responseService.get(vm.question.id));
        if (!!!vm.response.question_id) { vm.response.question_id = vm.question.id; }
        vm.distribution = $scope.getDataIf200(await distributionService.get(vm.question.form_id));
        vm.nbQuestions = $scope.form.nbQuestions;
        vm.last = vm.question.position == vm.nbQuestions;

        $scope.safeApply();
    };

    vm.prev = async (): Promise<void> => {
        await responseService.save(vm.response);
        let prevPosition: number = vm.question.position - 1;

        if (prevPosition > 0) {
            $scope.redirectTo(`/form/${vm.question.form_id}/question/${prevPosition}`);
            $scope.safeApply();
            let question = await questionService.getByPosition(vm.question.form_id, prevPosition);
            $scope.question = question.data;
            init();
        }
    };

    vm.next = async (): Promise<void> => {
        await responseService.save(vm.response);
        let nextPosition: number = vm.question.position + 1;

        if (nextPosition <= vm.nbQuestions) {
            $scope.redirectTo(`/form/${vm.question.form_id}/question/${nextPosition}`);
            $scope.safeApply();
            let question = await questionService.getByPosition(vm.question.form_id, nextPosition);
            $scope.question = question.data;
            init();
        }
    };

    vm.saveAndQuit = async (): Promise<void> => {
        await responseService.save(vm.response);
        if (vm.distribution.status == DistributionStatus.TO_DO) {
            vm.distribution.status = DistributionStatus.IN_PROGRESS;
            await distributionService.update(vm.distribution);
        }
        notify.success(idiom.translate('formulaire.success.responses.save'));
        $scope.redirectTo(`/list/responses`);
        $scope.safeApply();
    };

    vm.send = async (): Promise<void> => {
        await responseService.save(vm.response);
        if (await checkMandatoryQuestions()) {
            vm.distribution.status = DistributionStatus.FINISHED;
            await distributionService.update(vm.distribution);
            notify.success(idiom.translate('formulaire.success.responses.save'));
            $scope.redirectTo(`/list/responses`);
            $scope.safeApply();
        }
        else {
            notify.error(idiom.translate('formulaire.warning.send.missing.responses.missing'));
        }
    };

    const checkMandatoryQuestions = async (): Promise<boolean> => {
        try {
            let questions = $scope.getDataIf200(await questionService.list(vm.question.form_id));
            questions = questions.filter(question => question.mandatory === true);
            for (let question of questions) {
                let response = $scope.getDataIf200(await responseService.get(question.id));
                if (!!!response.answer) {
                    return false;
                }
            }
            return true;
        }
        catch (e) {
            throw e;
        }
    };

    init();
}]);