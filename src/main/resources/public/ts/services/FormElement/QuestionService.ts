import {idiom, ng, notify} from 'entcore';
import http from 'axios';
import {DataUtils} from "../../utils";
import {Question} from "../../models";
import {Mix} from "entcore-toolkit";

export interface QuestionService {
    list(id: number, isForSection?: boolean) : Promise<any>;
    get(questionId: number) : Promise<any>;
    save(question: Question) : Promise<any>;
    create(question: Question) : Promise<any>;
    update(questions: Question[]) : Promise<Question[]>;
    delete(questionId: number) : Promise<any>;
}

export const questionService: QuestionService = {

    async list (id: number, isForSection: boolean = false) : Promise<any> {
        try {
            let parentEntity = isForSection ? 'sections' : 'forms';
            return DataUtils.getData(await http.get(`/formulaire/${parentEntity}/${id}/questions`));
        } catch (err) {
            notify.error(idiom.translate('formulaire.error.questionService.list'));
            throw err;
        }
    },

    async get(questionId: number) : Promise<any> {
        try {
            return DataUtils.getData(await http.get(`/formulaire/questions/${questionId}`));
        } catch (err) {
            notify.error(idiom.translate('formulaire.error.questionService.get'));
            throw err;
        }
    },

    async save(question: Question) : Promise<any> {
        return question.id ? (await this.update([question]))[0] : await this.create(question);
    },

    async create(question: Question) : Promise<any> {
        try {
            return DataUtils.getData(await http.post(`/formulaire/forms/${question.form_id}/questions`, question));
        } catch (err) {
            notify.error(idiom.translate('formulaire.error.questionService.create'));
            throw err;
        }
    },

    async update(questions: Question[]) : Promise<Question[]> {
        try {
            if (questions.length <= 0) {
                return []
            }
            return Mix.castArrayAs(Question, DataUtils.getData(await http.put(`/formulaire/forms/${questions[0].form_id}/questions`, questions)));
        } catch (err) {
            notify.error(idiom.translate('formulaire.error.questionService.update'));
            throw err;
        }
    },

    async delete(questionId: number) : Promise<any> {
        try {
            return DataUtils.getData(await http.delete(`/formulaire/questions/${questionId}`));
        } catch (err) {
            notify.error(idiom.translate('formulaire.error.questionService.delete'));
            throw err;
        }
    }
};

export const QuestionService = ng.service('QuestionService', (): QuestionService => questionService);