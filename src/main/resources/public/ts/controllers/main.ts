import {Behaviours, idiom, model, ng, template} from 'entcore';
import {DistributionStatus, Form, Question, QuestionTypes} from "../models";
import {configService, distributionService, formService, questionService} from "../services";
import {AxiosResponse} from "axios";
import {FORMULAIRE_EMIT_EVENT} from "../core/enums";
import {Pages} from "../core/enums";

export const mainController = ng.controller('MainController', ['$scope', 'route', '$location', 'FormService',
	($scope, route, $location) => {
		$scope.lang = idiom;
		$scope.template = template;
		$scope.config = {};

		// Init variables
		$scope.Pages = Pages;
		$scope.currentPage = Pages.FORMS_LIST;
		$scope.form = new Form();
		$scope.question = new Question();
		$scope.questionTypes = new QuestionTypes();
		$scope.isMobile = window.screen.width <= 500;

		const init = async () : Promise<void> => {
			await $scope.getConfig();
			await $scope.questionTypes.sync();
		}

		// Routing & template opening
		route({
			list: () => {
				if ($scope.canCreate()) {
					$scope.redirectTo('/list/mine');
				}
				else if ($scope.canRespond()) {
					$scope.redirectTo('/list/responses');
				}
				else {
					$scope.redirectTo('/e403');
				}
			},
			formsList: () => {
				$scope.currentPage = Pages.FORMS_LIST;
				if ($scope.canCreate()) {
					template.open('main', 'containers/forms-list');
				}
				else {
					$scope.redirectTo('/e403');
				}
			},
			formsResponses: () => {
				$scope.currentPage = Pages.FORMS_RESPONSE;
				if ($scope.canRespond()) {
					template.open('main', 'containers/forms-responses');
				}
				else {
					$scope.redirectTo('/e403');
				}
			},
			createForm: () => {
				$scope.currentPage = Pages.CREATE_FORM;
				if ($scope.canCreate()) {
					$scope.form = new Form();
					template.open('main', 'containers/prop-form');
				}
				else {
					$scope.redirectTo('/e403');
				}
			},
			propForm: async (params) => {
				$scope.currentPage = Pages.PROP_FORM;
				await $scope.getFormWithRights(params.idForm);
				if ($scope.canCreate() && $scope.hasShareRightContrib($scope.form)) {
					template.open('main', 'containers/prop-form');
				}
				else {
					$scope.redirectTo('/e403');
				}
			},
			resultsForm: async (params) => {
				$scope.currentPage = Pages.RESULTS_FORM;
				await $scope.getFormWithRights(params.idForm);
				if ($scope.canCreate() && $scope.hasShareRightManager($scope.form)) {
					$scope.form.nbQuestions = $scope.getDataIf200(await questionService.countQuestions(params.idForm)).count;

					if (params.position < 1) {
						$scope.redirectTo(`/form/${params.idForm}/results/1`);
					}
					else if (params.position > $scope.form.nbQuestions) {
						$scope.redirectTo(`/form/${params.idForm}/results/${$scope.form.nbQuestions}`);
					}
					else {
						$scope.question = $scope.getDataIf200(await questionService.getByPosition(params.idForm, params.position));
						template.open('main', 'containers/results-form');
					}
				}
				else {
					$scope.redirectTo('/e403');
				}
			},
			editForm: async (params) => {
				$scope.currentPage = Pages.EDIT_FORM;
				await $scope.getFormWithRights(params.idForm);
				if ($scope.canCreate() && $scope.hasShareRightContrib($scope.form)) {
					if (!!$scope.form.id) {
						template.open('main', 'containers/edit-form');
					}
					else {
						$scope.redirectTo('/list/mine');
					}
				}
				else {
					$scope.redirectTo('/e403');
				}
			},
			respondForm: async (params) => {
				await $scope.getFormWithRights(params.idForm);
				if ($scope.canRespond() && $scope.hasShareRightResponse($scope.form)) {
					$scope.redirectTo(`/form/${params.idForm}/question/1`);
				}
				else {
					$scope.redirectTo('/e403');
				}
			},
			respondQuestion: async (params) => {
				$scope.currentPage = Pages.RESPOND_QUESTION;
				await $scope.getFormWithRights(params.idForm);
				if ($scope.canRespond() && $scope.hasShareRightResponse($scope.form)) {
					let distribution = $scope.getDataIf200(await distributionService.get(params.idForm));

					// If form not already responded && date ok
					if ($scope.form.date_opening < new Date() && ($scope.form.date_ending ? ($scope.form.date_ending > new Date()) : true)) {
						if ($scope.form.multiple || (!!distribution.status && distribution.status != DistributionStatus.FINISHED)) {
							$scope.form.nbQuestions = $scope.getDataIf200(await questionService.countQuestions(params.idForm)).count;

							if (params.position < 1) {
								$scope.redirectTo(`/form/${params.idForm}/question/1`);
							}
							else if (params.position > $scope.form.nbQuestions) {
								$scope.redirectTo(`/form/${params.idForm}/question/${$scope.form.nbQuestions}`);
							}
							else {
								$scope.question = $scope.getDataIf200(await questionService.getByPosition(params.idForm, params.position));
								template.open('main', 'containers/respond-question');
							}
						}
						else {
							$scope.redirectTo('/e409');
						}
					}
					else {
						$scope.redirectTo('/e403');
					}
				}
				else {
					$scope.redirectTo('/e403');
				}
			},
			e403: () => {
				$scope.currentPage = Pages.E403;
				template.open('main', 'containers/error/e403');
			},
			e404: () => {
				$scope.currentPage = Pages.E404;
				template.open('main', 'containers/error/e404');
			},
			e409: () => {
				$scope.currentPage = Pages.E409;
				template.open('main', 'containers/error/e409');
			}
		});


		// Utils

		$scope.getConfig = async (): Promise<void> => {
			try {
				$scope.config = $scope.getDataIf200(await configService.get());
			}
			catch (err) {
				console.log("Error in retrieving config : " + err);
				throw err;
			}
			$scope.safeApply();
		};

		$scope.getI18nWithParams = (key: string, params: string[]) : string => {
			let finalI18n = idiom.translate(key);
			for (let i = 0; i < params.length; i++) {
				finalI18n = finalI18n.replace(`{${i}}`, params[i]);
			}
			return finalI18n;
		};

		$scope.getTypeNameByCode = (code: number) : string => {
			return $scope.questionTypes.all.filter(type => type.code === code)[0].name;
		};

		$scope.getFormWithRights = async (formId : number) : Promise<void> => {
			$scope.form.setFromJson($scope.getDataIf200(await formService.get(formId)));
			$scope.form.myRights = $scope.getDataIf200(await formService.getMyFormRights(formId)).map(right => right.action);
		};

		$scope.getDataIf200 = (response: AxiosResponse) : any => {
			if ($scope.isStatusXXX(response, 200)) { return response.data; }
			else { return null; }
		};

		$scope.isStatusXXX = (response: AxiosResponse, status: number) : any => {
			return response.status === status;
		};

		$scope.redirectTo = (path: string) => {
			$location.path(path);
			$scope.safeApply();
		};

		$scope.safeApply = (fn?) => {
			const phase = $scope.$root.$$phase;
			if (phase == '$apply' || phase == '$digest') {
				if (fn && (typeof (fn) === 'function')) {
					fn();
				}
			} else {
				$scope.$apply(fn);
			}
		};

		$scope.$on(FORMULAIRE_EMIT_EVENT.REFRESH, () => { $scope.safeApply() });


		// Rights

		$scope.hasRight = (right: string) => {
			return model.me.hasWorkflow(right);
		};

		$scope.canAccess = () => {
			return $scope.hasRight(Behaviours.applicationsBehaviours.formulaire.rights.workflow.access);
		};

		$scope.canCreate = () => {
			return $scope.hasRight(Behaviours.applicationsBehaviours.formulaire.rights.workflow.creation);
		};

		$scope.canRespond = () => {
			return $scope.hasRight(Behaviours.applicationsBehaviours.formulaire.rights.workflow.response);
		};

		$scope.hasShareRightManager = (form : Form) => {
			return form.owner_id === model.me.userId || form.myRights.includes(Behaviours.applicationsBehaviours.formulaire.rights.resources.manager.right);
		};

		$scope.hasShareRightContrib = (form : Form) => {
			return form.owner_id === model.me.userId || form.myRights.includes(Behaviours.applicationsBehaviours.formulaire.rights.resources.contrib.right);
		};

		$scope.hasShareRightResponse = (form : Form) => {
			return form.myRights.includes(Behaviours.applicationsBehaviours.formulaire.rights.resources.comment.right);
		};

		$scope.hasWorkflowZimbra = function () {
			return model.me.hasWorkflow('fr.openent.zimbra.controllers.ZimbraController|view');
		};

		$scope.hasWorkflowMessagerie = function () {
			return model.me.hasWorkflow('org.entcore.conversation.controllers.ConversationController|view');
		};


		init();
}]);
