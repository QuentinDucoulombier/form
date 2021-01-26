import {idiom, ng, notify} from 'entcore';
import http, {AxiosResponse} from 'axios';
import {Question} from "../models";

export interface QuestionService {
    list(formId : number): Promise<AxiosResponse>;
    get(questionId : number): Promise<AxiosResponse>;
    getByPosition(idForm : number, position : number): Promise<AxiosResponse>;
    save(question : Question): Promise<AxiosResponse>;
    create(question : Question): Promise<AxiosResponse>;
    update(question : Question): Promise<AxiosResponse>;
    delete(questionId : number): Promise<AxiosResponse>;
}

export const questionService: QuestionService = {

    async list (formId : number): Promise<AxiosResponse> {
        try {
            return await http.get(`/formulaire/forms/${formId}/questions`);
        } catch (err) {
            notify.error(idiom.translate('formulaire.error.questionService.list'));
            throw err;
        }
    },

    async get(questionId : number): Promise<AxiosResponse> {
        try {
            return await http.get(`/formulaire/questions/${questionId}`);
        } catch (err) {
            notify.error(idiom.translate('formulaire.error.questionService.get'));
            throw err;
        }
    },

    async getByPosition(idForm : number, position : number): Promise<AxiosResponse> {
        try {
            return await http.get(`/formulaire/forms/${idForm}/questions/${position}`);
        } catch (err) {
            notify.error(idiom.translate('formulaire.error.questionService.get'));
            throw err;
        }
    },

    async save(question : Question): Promise<AxiosResponse> {
        return question.id ? await this.update(question) : await this.create(question);
    },

    async create(question : Question): Promise<AxiosResponse> {
        try {
            return await http.post(`/formulaire/forms/${question.form_id}/questions`, question);
        } catch (err) {
            notify.error(idiom.translate('formulaire.error.questionService.create'));
            throw err;
        }
    },

    async update(question : Question): Promise<AxiosResponse> {
        try {
            return await http.put(`/formulaire/questions/${question.id}`, question);
        } catch (err) {
            notify.error(idiom.translate('formulaire.error.questionService.update'));
            throw err;
        }
    },

    async delete(questionId : number): Promise<AxiosResponse> {
        try {
            return await http.delete(`/formulaire/questions/${questionId}`);
        } catch (err) {
            notify.error(idiom.translate('formulaire.error.questionService.delete'));
            throw err;
        }
    }
};

export const QuestionService = ng.service('QuestionService', (): QuestionService => questionService);