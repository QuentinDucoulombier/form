import {Behaviours, idiom, model, ng, notify, template} from 'entcore';
import {
	Distribution,
	DistributionStatus,
	Form,
	Question,
	QuestionTypes,
	Types,
	Folder
} from "../models";
import {distributionService, formElementService, formService, utilsService} from "../services";
import {
	Direction,
	Exports,
	FiltersFilters,
	FiltersOrders,
	FORMULAIRE_BROADCAST_EVENT,
	FORMULAIRE_EMIT_EVENT,
	Pages
} from "@common/core/enums";
import {FormElementUtils, I18nUtils} from "@common/utils";
import Clipboard from "clipboard";

export const mainController = ng.controller('MainController', ['$scope', 'route', '$location',
	($scope, route, $location) => {
		$scope.idiom = idiom;
		$scope.template = template;

		// Init variables
		$scope.FiltersOrders = FiltersOrders;
		$scope.FiltersFilters = FiltersFilters;
		$scope.Direction = Direction;
		$scope.Types = Types;
		$scope.Exports = Exports;
		$scope.Pages = Pages;
		$scope.DistributionStatus = DistributionStatus;
		$scope.I18nUtils = I18nUtils;
		$scope.FormElementUtils = FormElementUtils;
		$scope.currentPage = Pages.FORMS_LIST;
		$scope.form = new Form();
		$scope.distribution = new Distribution();
		$scope.formElement = new Question();
		$scope.questionTypes = new QuestionTypes();
		$scope.folder = $scope.folder ? $scope.folder : new Folder();
		$scope.isMobile = window.screen.width <= 500;
		$scope.responsePosition = 1;
		$scope.historicPosition = [];

		const init = async () : Promise<void> => {
			await $scope.questionTypes.sync();
			$scope.userPreferences = await utilsService.getUserPreferences();

			// Clipboard
			let clipboard = new Clipboard('.clipboard-link-field');
			clipboard.on('success', function(e) {
				e.clearSelection();
				notify.info('formulaire.link.copy.success');
			});
			clipboard.on('error', function(e) {
				notify.error('formulaire.link.copy.error');
			});
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
			editForm: async (params) => {
				$scope.currentPage = Pages.EDIT_FORM;
				await $scope.getFormWithRights(params.formId);
				if ($scope.canCreate() && $scope.hasShareRightContrib($scope.form)) {
					if ($scope.form.id) {
						$scope.$broadcast(FORMULAIRE_BROADCAST_EVENT.INIT_FORM_EDITOR);
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
			treeViewForm: async (params) => {
				$scope.currentPage = Pages.VISUALIZE_FORM;
				await $scope.getFormWithRights(params.formId);
				if ($scope.canCreate() && $scope.hasShareRightContrib($scope.form)) {
					if ($scope.form.id) {
						template.open('main', 'containers/tree-view');
					}
					else {
						$scope.redirectTo('/list/mine');
					}
				}
				else {
					$scope.redirectTo('/e403');
				}
			},
			propForm: async (params) => {
				$scope.currentPage = Pages.PROP_FORM;
				await $scope.getFormWithRights(params.formId);
				if ($scope.canCreate() && $scope.hasShareRightContrib($scope.form)) {
					$scope.$broadcast(FORMULAIRE_BROADCAST_EVENT.INIT_FORM_PROP);
					template.open('main', 'containers/prop-form');
				}
				else {
					$scope.redirectTo('/e403');
				}
			},
			emptyResults: async (params) => {
				$scope.currentPage = Pages.EMPTY_RESULTS;
				template.open('main', 'containers/empty-results');
			},
			resultsForm: async (params) => {
				$scope.currentPage = Pages.RESULTS_FORM;
				await $scope.getFormWithRights(params.formId);
				if ($scope.canCreate() && $scope.hasShareRightContrib($scope.form)) {
					$scope.form.nb_responses = (await distributionService.count(params.formId)).count;

					if ($scope.form.nb_responses > 0) {
						$scope.form.nbFormElements = (await formElementService.countFormElements(params.formId)).count;
						if (params.position < 1) {
							$scope.redirectTo(`/form/${params.formId}/results/1`);
						}
						else if (params.position > $scope.form.nbFormElements) {
							$scope.redirectTo(`/form/${params.formId}/results/${$scope.form.nbFormElements}`);
						}
						else {
							$scope.formElement = (await formElementService.getByPosition(params.formId, params.position));
							$scope.$broadcast(FORMULAIRE_BROADCAST_EVENT.INIT_FORM_RESULTS);
							template.open('main', 'containers/results-form');
						}
					}
					else {
						$scope.redirectTo(`/form/${params.formId}/results/empty`);
					}
				}
				else {
					$scope.redirectTo('/e403');
				}
			},
			rgpdQuestion: async (params) => {
				$scope.currentPage = Pages.RGPD_QUESTIONS;
				await $scope.getFormWithRights(params.formId);
				if ($scope.form.rgpd) {
					if ($scope.canRespond() && $scope.hasShareRightResponse($scope.form) && !$scope.form.archived) {
						if ($scope.form.date_opening < new Date() && ($scope.form.date_ending ? ($scope.form.date_ending > new Date()) : true)) {
							let correctedUrl = window.location.origin + window.location.pathname + `#/form/${$scope.form.id}/rgpd`;
							window.location.assign(correctedUrl);
							$scope.safeApply();
							$scope.$broadcast(FORMULAIRE_BROADCAST_EVENT.INIT_RGPD_QUESTION);
							template.open('main', 'containers/rgpd-question');
						}
						else {
							$scope.redirectTo('/e403');
						}
					}
					else {
						$scope.redirectTo('/e403');
					}
				}
				else {
					$scope.redirectTo('/list/responses');
				}
			},
			respondForm: async (params) => {
				$scope.currentPage = Pages.RESPOND_QUESTION;
				await $scope.getFormWithRights(params.formId);
				if ($scope.canRespond() && $scope.hasShareRightResponse($scope.form) && !$scope.form.archived) {
					if ($scope.form.date_opening < new Date() && ($scope.form.date_ending ? ($scope.form.date_ending > new Date()) : true)) {
						$scope.distribution = new Distribution();
						if (!isNaN(params.distributionId)) {
							$scope.distribution = await distributionService.get(params.distributionId);
						}
						else {
							let distribs: Distribution[] = await distributionService.listByFormAndResponder($scope.form.id);
							let toDoDistribs: Distribution[] = distribs.filter(d => d.status == DistributionStatus.TO_DO);
							let finishedDistribs: Distribution[] = distribs.filter(d => d.status == DistributionStatus.FINISHED);
							if ($scope.form.multiple || finishedDistribs.length == 0) {
								$scope.distribution = toDoDistribs[0] ? toDoDistribs[0] : await distributionService.add(distribs[0].id);
							}
						}

						if (!$scope.distribution.id) {
							$scope.redirectTo('/e409');
						}
						else if ($scope.distribution.form_id != params.formId) {
							$scope.redirectTo('/e403');
						}
						else {
							let conditionsOk: boolean = false;
							if ($scope.distribution.status && $scope.distribution.status != DistributionStatus.FINISHED) {
								conditionsOk = true;
							}
							else if ($scope.form.editable) {
								let distribs: Distribution[] = await distributionService.listByFormAndResponder($scope.form.id);
								let distrib: Distribution = distribs.filter(d => d.status == DistributionStatus.ON_CHANGE)[0];
								$scope.distribution = distrib ? distrib : await distributionService.duplicateWithResponses($scope.distribution.id);
								conditionsOk = true;
							}

							if (conditionsOk) {
								$scope.form.nbFormElements = (await formElementService.countFormElements($scope.form.id)).count;
								if ($scope.responsePosition > $scope.form.nbFormElements) {
									$scope.redirectTo(`/form/${$scope.form.id}/${$scope.distribution.id}/questions/recap`);
								}
								else {
									$scope.responsePosition = $scope.responsePosition < 1 ? 1 : $scope.responsePosition;
									let correctedUrl: string = window.location.origin + window.location.pathname + `#/form/${$scope.form.id}/${$scope.distribution.id}`;
									window.location.assign(correctedUrl);
									$scope.safeApply();
									$scope.$broadcast(FORMULAIRE_BROADCAST_EVENT.INIT_RESPOND_QUESTION);
									template.open('main', 'containers/respond-question');
								}
							}
							else {
								$scope.redirectTo('/e403');
							}
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
			recapQuestions: async (params) => {
				$scope.currentPage = Pages.RECAP_QUESTIONS;
				await $scope.getFormWithRights(params.formId);
				if ($scope.canRespond() && $scope.hasShareRightResponse($scope.form) && !$scope.form.archived) {
					$scope.distribution = await distributionService.get(params.distributionId);
					if ($scope.distribution && $scope.distribution.form_id == params.formId) {
						let hasRespondLastQuestion: boolean = await FormElementUtils.hasRespondedLastQuestion($scope.form, $scope.distribution);
						if (hasRespondLastQuestion) {
							if ($scope.distribution.status && $scope.distribution.status === DistributionStatus.FINISHED && $scope.form.editable) {
								let distribs: Distribution[] = await distributionService.listByFormAndResponder($scope.form.id);
								let distrib: Distribution = distribs.filter(d => d.status == DistributionStatus.ON_CHANGE && d.original_id === $scope.distribution.id)[0];
								if (distrib) {
									$scope.distribution = distrib;
								}
								else {
									$scope.distribution = await distributionService.duplicateWithResponses($scope.distribution.id);
								}
							}
							template.open('main', 'containers/recap-questions');
						}
						else {
							$scope.redirectTo(`/form/${$scope.form.id}/${$scope.distribution.id}`);
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

		$scope.displayDate = (dateToFormat: Date) : string => {
			let locales: string[] = $scope.userPreferences ? [$scope.userPreferences] : [];
			return new Date(dateToFormat).toLocaleString(locales, {day: '2-digit', month: '2-digit', year:'numeric', hour: '2-digit', minute:'2-digit'});
		};

		$scope.getTypeNameByCode = (code: number) : string => {
			return $scope.questionTypes.all.filter(type => type.code === code)[0].name;
		};

		$scope.getFormWithRights = async (formId : number) : Promise<void> => {
			$scope.form.setFromJson(await formService.get(formId));
			$scope.form.myRights = (await formService.getMyFormRights(formId)).map(right => right.action);
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
		$scope.$on(FORMULAIRE_EMIT_EVENT.UPDATE_FOLDER, (event, data) => { $scope.folder = data });
		$scope.$on(FORMULAIRE_EMIT_EVENT.REDIRECT, (event, data) => {
			$scope.responsePosition = data.position ? data.position : 1;
			$scope.historicPosition = data.historicPosition ? data.historicPosition : [];
			if (data.form) $scope.form = data.form;
			$scope.safeApply();
			$scope.redirectTo(data.path);
		});


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

		$scope.canSeeRgpd = () => {
			return $scope.hasRight(Behaviours.applicationsBehaviours.formulaire.rights.workflow.rgpd);
		};

		$scope.canCreatePublic = () => {
			return $scope.hasRight(Behaviours.applicationsBehaviours.formulaire.rights.workflow.creationPublic);
		};

		$scope.hasShareRightManager = (form : Form) => {
			return form.owner_id === model.me.userId || (form.myRights && form.myRights.includes(Behaviours.applicationsBehaviours.formulaire.rights.resources.manager.right));
		};

		$scope.hasShareRightContrib = (form : Form) => {
			return form.owner_id === model.me.userId || (form.myRights && form.myRights.includes(Behaviours.applicationsBehaviours.formulaire.rights.resources.contrib.right));
		};

		$scope.hasShareRightResponse = (form : Form) => {
			return form.myRights.includes(Behaviours.applicationsBehaviours.formulaire.rights.resources.comment.right);
		};

		init();
}]);
