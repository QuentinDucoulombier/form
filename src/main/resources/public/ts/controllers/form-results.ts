import {idiom, ng, template} from 'entcore';
import * as ApexCharts from 'apexcharts';
import {
    Distributions,
    DistributionStatus,
    Form,
    Question,
    QuestionChoice,
    QuestionChoices,
    Questions,
    Responses,
    Types
} from "../models";
import {Mix} from "entcore-toolkit";
import {ColorUtils} from "../utils/color";
import {Exports} from "../core/enums";
import http from "axios";

interface ViewModel {
    types: typeof Types;
    question: Question;
    questions: Questions;
    results: Responses;
    distributions: Distributions;
    form: Form;
    nbResults: number;
    nbLines: number;
    nbLinesDisplay: number;
    nbQuestions: number;
    last: boolean;
    navigatorValue: number;
    singleAnswerResponseChart: any;
    colors: string[];
    typeExport: Exports;
    display: {
        lightbox: {
            download: boolean;
        }
    }
    choicesForGraph : QuestionChoices;
    pdfResponseCharts: any;

    export(typeExport: Exports) : void;
    doExport() : void;
    downloadFile(responseId: number) : void;
    zipAndDownload() : void;
    getDataByDistrib(distribId: number) : any;
    prev() : Promise<void>;
    next() : Promise<void>;
    goTo(position: number) : Promise<void>;
    getWidth(nbResponses: number, divisor: number) : number;
    getColor(choiceId: number) : string;
    getGraphQuestions() : Question[];
}


export const formResultsController = ng.controller('FormResultsController', ['$scope', '$rootScope',
    function ($scope, $rootScope) {
        let paletteColors = ['#0F2497','#2A9BC7','#77C4E1','#C0E5F2']; // Bleu foncé à bleu clair

        const vm: ViewModel = this;
        vm.types = Types;
        vm.question = new Question();
        vm.questions = new Questions();
        vm.results = new Responses();
        vm.distributions = new Distributions();
        vm.form = new Form();
        vm.nbResults = 0;
        vm.nbLines = 0;
        vm.nbLinesDisplay = 10;
        vm.nbQuestions = 1;
        vm.last = false;
        vm.navigatorValue = 1;
        vm.singleAnswerResponseChart = null;
        vm.colors = [];
        vm.typeExport = Exports.CSV;
        vm.display = {
            lightbox: {
                download: false
            }
        };
        vm.choicesForGraph = new QuestionChoices();
        vm.pdfResponseCharts = [];

        const init = async () : Promise<void> => {
            vm.form = $scope.form;
            vm.question = Mix.castAs(Question, $scope.question);
            vm.navigatorValue = vm.question.position;
            await vm.question.choices.sync(vm.question.id);
            await vm.questions.sync(vm.form.id);
            await vm.results.sync(vm.question.id, vm.question.question_type == Types.FILE);
            await vm.distributions.syncByForm(vm.form.id);

            vm.distributions.all = vm.distributions.all.filter(d => d.status === DistributionStatus.FINISHED);
            vm.nbResults = vm.distributions.all.length;
            vm.nbLines = vm.nbResults;
            vm.nbQuestions = $scope.form.nbQuestions;
            vm.last = vm.question.position === vm.nbQuestions;
            if (vm.question.question_type == vm.types.SINGLEANSWER || vm.question.question_type == vm.types.MULTIPLEANSWER) {
                vm.question = await initQCMandQCU(vm.question);
                let choices = vm.question.choices.all.filter(c => c.nbResponses > 0);
                vm.colors = ColorUtils.interpolateColors(paletteColors, choices.length);

                // Init charts
                if (vm.question.question_type == vm.types.SINGLEANSWER) {
                    initSingleAnswerChart();
                }
                vm.nbLines = vm.question.choices.all.length;
            }

            $scope.safeApply();
        };

        const initQCMandQCU = async (question: Question) : Promise<Question> => {
            // Count responses for each choice
            let results = new Responses();
            let distribIds : any = vm.distributions.all.map(d => d.id);
            if (question.id != vm.question.id) {
                await results.sync(question.id, false);
            }
            else {
                results = vm.results;
            }
            for (let result of results.all) {
                if (distribIds.includes(result.distribution_id)) { // We do not count results from distrib not FINISHED
                    for (let choice of question.choices.all) {
                        if (result.choice_id === choice.id) {
                            choice.nbResponses++;
                        }
                    }
                }
            }

            // Deal with no choice responses
            let finishedDistribIds : any = vm.distributions.all.map(d => d.id);
            let resultsDistribIds : any = results.all.map(r => r.distribution_id);
            let noResponseChoice = new QuestionChoice();
            let nbEmptyResponse = vm.distributions.all.filter(d => !resultsDistribIds.includes(d.id)).length;
            noResponseChoice.value = idiom.translate('formulaire.response.empty');
            noResponseChoice.nbResponses = question.question_type == vm.types.MULTIPLEANSWER ?
                nbEmptyResponse :
                nbEmptyResponse + results.all.filter(r => !!!r.choice_id && finishedDistribIds.includes(r.distribution_id)).length;

            question.choices.all.push(noResponseChoice);

            return question;
        }

        // Functions

        vm.export = (typeExport: Exports) : void => {
            vm.typeExport = typeExport;
            template.open('lightbox', 'lightbox/results-confirm-download-all');
            vm.display.lightbox.download = true;
            $scope.safeApply();
        };

        vm.doExport = async () : Promise<void> => {
            let doc;
            let blob;

            // Generate document (CSV or PDF) and store it in a blob
            if (vm.typeExport === Exports.CSV) {
                doc = await http.post(`/formulaire/export/csv/${vm.question.form_id}`, {});
                blob = new Blob(["\ufeff" + doc.data], {type: 'text/csv; charset=utf-18'});
            }
            else {
                let images = await prepareDataForPDF();
                doc = await http.post(`/formulaire/export/pdf/${vm.question.form_id}`, images, {responseType: "arraybuffer"});
                blob = new Blob([doc.data], {type: 'application/pdf; charset=utf-18'});
            }

            // Download the blob
            let link = document.createElement('a');
            link.href = window.URL.createObjectURL(blob);
            link.download =  doc.headers['content-disposition'].split('filename=')[1];
            document.body.appendChild(link);
            link.click();
            setTimeout(function() {
                document.body.removeChild(link);
                window.URL.revokeObjectURL(link.href);
            }, 100);

            // Delete PDF charts on the web page
            let chart : ApexCharts;
            for (chart of vm.pdfResponseCharts) {
                chart.destroy();
            }

            vm.display.lightbox.download = false;
            template.close('lightbox');
            $scope.safeApply();
        };

        vm.downloadFile = (fileId: number) : void => {
            window.open(`/formulaire/responses/files/${fileId}/download`);
        };

        vm.zipAndDownload = () : void => {
            window.open(`/formulaire/responses/${vm.question.id}/files/download/zip`);
        };

        vm.getDataByDistrib = (distribId: number) : any => {
            let results =  vm.results.all.filter(r => r.distribution_id === distribId && r.question_id === vm.question.id);
            for (let result of results) {
                if (result.answer == "") {
                    result.answer = "-";
                }
            }
            if (vm.question.question_type === Types.FILE) {
                return results.map(r => r.files)[0].all;
            }
            return results;
        };

        vm.prev = async () : Promise<void> => {
            let prevPosition: number = vm.question.position - 1;
            if (prevPosition > 0) {
                await vm.goTo(prevPosition);
            }
        };

        vm.next = async () : Promise<void> => {
            let nextPosition: number = vm.question.position + 1;
            if (nextPosition <= vm.nbQuestions) {
                await vm.goTo(nextPosition);
            }
        };

        vm.goTo = async (position: number) : Promise<void> => {
            $scope.redirectTo(`/form/${vm.question.form_id}/results/${position}`);
        };

        vm.getWidth = (nbResponses: number, divisor: number) : number => {
            let width = nbResponses / (!!vm.nbResults ? vm.nbResults : 1) * divisor;
            return width < 0 ? 0 : (width > divisor ? divisor : width);
        }

        vm.getColor = (choiceId: number) : string => {
            let colorIndex = vm.question.choices.all.filter(c => c.nbResponses > 0).findIndex(c => c.id === choiceId);
            return vm.colors[colorIndex];
        };

        vm.getGraphQuestions = () : Question[] => {
            return vm.questions.all.filter(q => q.question_type === Types.SINGLEANSWER || q.question_type === Types.MULTIPLEANSWER);
        }

        // Charts

        const initSingleAnswerChart = () : void => {
            let choices = vm.question.choices.all.filter(c => c.nbResponses > 0);

            let series = [];
            let labels = [];
            let i18nValue = idiom.translate('formulaire.response');
            i18nValue = i18nValue.charAt(0).toUpperCase() + i18nValue.slice(1);

            for (let choice of choices) {
                series.push(choice.nbResponses); // Fill data
                let i = vm.question.choices.all.indexOf(choice) + 1;
                !!!choice.id ? labels.push(idiom.translate('formulaire.response.empty')) : labels.push(i18nValue + " " + i); // Fill labels
            }

            // Generate options with labels and colors
            let baseHeight = 40 * vm.question.choices.all.length;
            let options = {
                chart: {
                    type: 'pie',
                    height: baseHeight < 200 ? 200 : (baseHeight > 500 ? 500 : baseHeight)
                },
                colors: vm.colors,
                labels: labels
            }

            // Generate chart with options and data
            if (vm.singleAnswerResponseChart) {
                vm.singleAnswerResponseChart.updateSeries(series, true);
                vm.singleAnswerResponseChart.updateOptions(options, true);
            }
            else {
                var newOptions = JSON.parse(JSON.stringify(options));
                newOptions.series = series;
                vm.singleAnswerResponseChart = new ApexCharts(document.querySelector('#singleanswer-response-chart'), newOptions);
                vm.singleAnswerResponseChart.render();
            }
        }

        // PDF

        const prepareDataForPDF = async () : Promise<any> => {
            vm.pdfResponseCharts = [];
            let images = {
                idImagesPerQuestion : {}, // id image for each id question of Type QCM or QCU
                idImagesForRemove : [] // all id images (to remove from storage after export PDF)
            };
            let questions = vm.getGraphQuestions();
            let question : Question = null;

            for (question of questions) {
                await question.choices.sync(question.id);
                question = await initQCMandQCU(question);
                let dataOptions = initChartsForPDF(question);
                let options = generateOptions(dataOptions, question.question_type);
                await renderGraphForPDF(options);
                let idImage = await storeChart(vm.pdfResponseCharts[vm.pdfResponseCharts.length-1]);
                images.idImagesPerQuestion[question.id] = idImage;
                images.idImagesForRemove.push(idImage);
            }
            $scope.safeApply();
            return images;
        }

        const initChartsForPDF = (question: Question) : any => {
            let choices = question.question_type === Types.SINGLEANSWER ?
                question.choices.all.filter(c => c.nbResponses > 0) :
                question.choices.all;

            let series = [];
            let labels = [];

            for (let choice of choices) {
                series.push(choice.nbResponses); // Fill data
                // Fill labels
                !!!choice.id ?
                    labels.push(idiom.translate('formulaire.response.empty')) :
                    labels.push(choice.value.substring(0, 40) + (choice.value.length > 40 ? "..." : ""));
            }

            return {
                series: series,
                labels: labels,
                colors: ColorUtils.interpolateColors(paletteColors, labels.length)
            };
        }

        const generateOptions = (dataOptions, type: Types) : any => {
            let newOptions;
            if (type === Types.SINGLEANSWER) {
                let options = {
                    chart: {
                        type: 'pie',
                        height: 400,
                        width: 600,
                        animations: {
                            enabled: false
                        }
                    },
                    colors: dataOptions.colors,
                    labels: dataOptions.labels
                }
                newOptions = JSON.parse(JSON.stringify(options));
                newOptions.series = dataOptions.series;
            }
            else {
                let options = {
                    chart: {
                        type: 'bar',
                        height: 400,
                        width: 600,
                        animations: {
                            enabled: false
                        }
                    },
                    plotOptions: {
                        bar: {
                            borderRadius: 4,
                            horizontal: true,
                        }
                    },
                    colors: ColorUtils.interpolateColors(paletteColors, 1),
                    xaxis: {
                        categories: dataOptions.labels,
                    }
                }
                newOptions = JSON.parse(JSON.stringify(options));
                newOptions.series = [{ data: dataOptions.series }];
            }
            return newOptions;
        }

        const renderGraphForPDF = async (options: any) : Promise<void> => {
            vm.pdfResponseCharts.push(new ApexCharts(document.querySelector('#pdf-response-chart-' + (vm.pdfResponseCharts.length)), options));
            await vm.pdfResponseCharts[vm.pdfResponseCharts.length - 1].render();
        }

        const storeChart = async (chart: ApexCharts) : Promise<number> => {
            let image = await chart.dataURI();
            let blob = new Blob([image["imgURI"]], {type: 'image/png'});
            let formData = new FormData();
            formData.append('file', blob);
            let response = await http.post('/formulaire/file/img', formData);
            return response.data._id;
        };

        init();

        $rootScope.$on( "$routeChangeSuccess", function(event, next, current) {
            window.location.reload();
        });
    }]);