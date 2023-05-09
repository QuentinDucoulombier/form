import { ng, routes } from 'entcore';
import * as controllers from './controllers';
import * as services from './services';
import * as directives from './directives';

for(let controller in controllers){
    ng.controllers.push(controllers[controller]);
}

for (let service in services) {
	ng.services.push(services[service]);
}

for (let directive in directives) {
	ng.directives.push(directives[directive]);
}

routes.define(function($routeProvider){
	$routeProvider
		.when('/', { action: 'list' })
		.when('/list', { action: 'list' })
		.when('/list/mine', { action: 'formsList' })
		.when('/list/responses', { action: 'formsResponses' })
		.when('/form/create', { action: 'createForm' })
		.when('/form/:formId/edit', { action: 'editForm' })
		.when('/form/:formId/tree', { action: 'treeViewForm' })
		.when('/form/:formId/properties', { action: 'propForm' })
		.when('/form/:formId/results/empty', { action: 'emptyResults' })
		.when('/form/:formId/results/:position', { action: 'resultsForm' })
		.when('/form/:formId/rgpd', { action: 'rgpdQuestion' })
		.when('/form/:formId/:distributionId', { action: 'respondForm' })
		.when('/form/:formId/:distributionId/questions/recap', { action: 'recapQuestions' })
		.when('/e403', { action: 'e403' })
		.when('/e404', { action: 'e404' })
		.when('/e409', { action: 'e409' })
		.otherwise({ redirectTo: '/e404' });
});