import {idiom, ng, notify} from 'entcore';
import {FormElement, Question, Section} from "../../models";
import {questionService, sectionService} from "../../services";
import {DataUtils, FormElementUtils} from "../../utils";
import http from "axios";
import * as fs from "fs";
import * as JSONStream from "jsonstream";

export interface FormElementService {
    countFormElements(formId: number) : Promise<any>;
    get(formElement: FormElement) : Promise<any>;
    getByPosition(idForm: number, position: number) : Promise<any>;
    save(formElement: FormElement) : Promise<any>;
    create(formElement: FormElement) : Promise<any>;
    update(formElements: FormElement[]) : Promise<any>;
    delete(formElement: FormElement) : Promise<any>;
}

export const formElementService: FormElementService = {

    async countFormElements (formId: number) : Promise<any> {
        try {
            return DataUtils.getData(await http.get(`/formulaire/forms/${formId}/elements/count`));
        } catch (err) {
            notify.error(idiom.translate('formulaire.error.formElementService.count'));
            throw err;
        }
    },

    async get(formElement: FormElement) : Promise<any> {
        try {
            if (formElement instanceof Question) {
                return await questionService.get(formElement.id);
            }
            else if (formElement instanceof Section) {
                let section: Section = await sectionService.get(formElement.id);
                await section.questions.sync(section.id, true);
                return section;
            }
            else {
                throw new TypeError();
            }
        } catch (err) {
            notify.error(idiom.translate('formulaire.error.formElementService.get'));
            throw err;
        }
    },

    async getByPosition(idForm : number, position: number) : Promise<any> {
        try {
            let formElement = DataUtils.getData(await http.get(`/formulaire/forms/${idForm}/elements/${position}`));
            let castedElement = FormElementUtils.castFormElement(formElement);
            if (castedElement instanceof Section) {
                await castedElement.questions.sync(castedElement.id, true);
            }
            return castedElement;
        } catch (err) {
            notify.error(idiom.translate('formulaire.error.formElementService.get'));
            throw err;
        }
    },

    async save(formElement: FormElement) : Promise<any> {
        return formElement.id ? (await this.update([formElement]))[0] : await this.create(formElement);
    },

    async create(formElement: FormElement) : Promise<any> {
        try {
            if (formElement instanceof Question) {
                return await questionService.create(formElement);
            }
            else if (formElement instanceof Section) {
                return await sectionService.create(formElement);
            }
            else {
                throw new TypeError();
            }
        } catch (err) {
            notify.error(idiom.translate('formulaire.error.formElementService.create'));
            throw err;
        }
    },

    async update(formElements: FormElement[]) : Promise<any> {
        try {
            let questions: Question[] = formElements.filter((f: FormElement) => f.isQuestion()).map((f: FormElement) => f as Question);
            let sections: Section[] = formElements.filter((f: FormElement) => f.isSection()).map((f: FormElement) => f as Section);

            if (questions.length > 0 && sections.length === 0) {
                return questionService.update(questions);
            }
            else if (sections.length > 0 && questions.length === 0) {
                return sectionService.update(sections);
            }
            else if (sections.length > 0 && questions.length > 0) {
                let out = "[";
                for (let i = 0; i < formElements.length - 1; i++) {
                    const cache = new Set();
                    const jsonString = JSON.stringify(formElements[i], (key, value) => {
                        if (typeof value === 'object' && value !== null) {
                            if (cache.has(value)) {
                                // Circular reference found, discard key
                                return;
                            }
                            // Store value in our collection
                            cache.add(value);
                        }
                        return value;
                    });
                    if (jsonString) {
                        out += jsonString + ",";
                    }
                }
                out += JSON.stringify(formElements[formElements.length - 1]) + "]";
                const result = await http.put(`/formulaire/forms/${formElements[0].form_id}/elements`, out);
                return DataUtils.getData(result);
            }
            else {
                return [];
            }

        } catch (err) {
            notify.error(idiom.translate('formulaire.error.formElementService.update'));
            throw err;
        }
    },

    async delete(formElement: FormElement) : Promise<any> {
        try {
            if (formElement instanceof Question) {
                return await questionService.delete(formElement.id);
            }
            else if (formElement instanceof Section) {
                return await sectionService.delete(formElement.id);
            }
            else {
                throw new TypeError();
            }
        } catch (err) {
            notify.error(idiom.translate('formulaire.error.formElementService.delete'));
            throw err;
        }
    }
};

export const FormElementService = ng.service('FormElementService',(): FormElementService => formElementService);