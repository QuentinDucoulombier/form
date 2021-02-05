import {idiom, ng, notify} from 'entcore';
import http, {AxiosResponse} from 'axios';
import {Question, QuestionChoice} from '../models';

export interface QuestionChoiceService {
    list(questionId: number): Promise<AxiosResponse>;
    get(choiceId: number): Promise<AxiosResponse>;
    save(choice : QuestionChoice): Promise<AxiosResponse>;
    create(choice: QuestionChoice): Promise<AxiosResponse>;
    update(choice: QuestionChoice): Promise<AxiosResponse>;
    delete(choiceId: number): Promise<AxiosResponse>;
}

export const questionChoiceService: QuestionChoiceService = {

    async list (questionId: number): Promise<AxiosResponse> {
        try {
            return http.get(`/formulaire/questions/${questionId}/choices`);
        } catch (err) {
            notify.error(idiom.translate('formulaire.error.questionChoiceService.list'));
            throw err;
        }
    },

    async get(choiceId : number): Promise<AxiosResponse> {
        try {
            return http.get(`/formulaire/choices/${choiceId}`);
        } catch (err) {
            notify.error(idiom.translate('formulaire.error.questionChoiceService.get'));
            throw err;
        }
    },

    async save(choice : QuestionChoice): Promise<AxiosResponse> {
        return choice.id ? await this.update(choice) : await this.create(choice);
    },

    async create(choice: QuestionChoice): Promise<AxiosResponse> {
        try {
            let response = await http.post(`/formulaire/questions/${choice.question_id}/choices`, choice);
            console.log(response);
            if (response.status == 200) return response.data;
            else throw new Error();
        } catch (err) {
            notify.error(idiom.translate('formulaire.error.questionChoiceService.create'));
            throw err;
        }
    },

    async update(choice : QuestionChoice): Promise<AxiosResponse> {
        try {
            return http.put(`/formulaire/choices/${choice.id}`, choice);
        } catch (err) {
            notify.error(idiom.translate('formulaire.error.questionChoiceService.update'));
            throw err;
        }
    },

    async delete(choiceId : number): Promise<AxiosResponse> {
        try {
            return http.delete(`/formulaire/choices/${choiceId}`);
        } catch (err) {
            notify.error(idiom.translate('formulaire.error.questionChoiceService.delete'));
            throw err;
        }
    }
};

export const QuestionChoiceService = ng.service('QuestionChoiceService',(): QuestionChoiceService => questionChoiceService);