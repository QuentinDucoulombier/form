import {idiom, idiom as lang} from 'entcore';
import {Question, QuestionChoice, Response, Types} from "@common/models";
import {ColorUtils} from "@common/utils/color";
import ApexCharts from 'apexcharts';

export class GraphUtils {

    // Results page
    /**
     * Generate data, options and render graph of the results of a question according to its type (for result page view)
     * @param question    Question object which we want to display the results
     * @param responses   Array of responses which we want to display the results
     * @param isExportPDF Boolean to determine if we generate a graph for result or for PDF Export
     * @param charts      ApexCharts to store and render at the end
     * @param distribs  Distrib's number for each question
     */
    static generateGraphForResult = async (question: Question, charts: ApexChart[], responses: Response[],
                                           distribs: number, isExportPDF: boolean) : Promise<void> => {
        switch (question.question_type) {
            case Types.SINGLEANSWER:
            case Types.SINGLEANSWERRADIO:
                await GraphUtils.generateSingleAnswerChart(question, charts, isExportPDF);
                break;
            case Types.MULTIPLEANSWER:
                await GraphUtils.generateMultipleAnswerChart(question, charts, distribs, isExportPDF);
                break;
            case Types.MATRIX:
                await GraphUtils.generateMatrixChart(question, charts, isExportPDF);
                break;
            case Types.CURSOR:
                await GraphUtils.generateCursorChart(question, charts, responses, isExportPDF);
                break;
            case Types.RANKING:
                await GraphUtils.generateRankingChart(question, charts, responses, isExportPDF);
                break;
            default:
                break;
        }
    };

    /**
     * Generate and render graph of the results of a single answer question
     * @param question    Question object which we want to display the results
     * @param charts      ApexChart to render at the end
     * @param isExportPDF Boolean to determine if we generate a graph for result or for PDF Export
     */
    private static generateSingleAnswerChart = async (question: Question, charts: ApexChart[], isExportPDF: boolean) : Promise<void> => {
        let choices: QuestionChoice[] = question.choices.all.filter((c: QuestionChoice) => c.nbResponses > 0);

        let series: number[] = [];
        let labels: string[] = [];
        let i18nValue: string = idiom.translate('formulaire.response');
        i18nValue = i18nValue.charAt(0).toUpperCase() + i18nValue.slice(1);

        for (let choice of choices) {
            series.push(choice.nbResponses); // Fill data
            let index: number = question.choices.all.indexOf(choice) + 1;
            !choice.id ? labels.push(idiom.translate('formulaire.response.empty')) : labels.push(i18nValue + " " + index); // Fill labels
        }

        // Generate options with labels and colors
        let baseHeight: number = 40 * question.choices.all.length;
        let height: number = baseHeight < 200 ? 200 : (baseHeight > 500 ? 500 : baseHeight);

        let colors: string[] = ColorUtils.generateColorList(labels.length);

        let newOptions: any = isExportPDF ?
            GraphUtils.generateOptions(question.question_type, colors, labels)
            :
            GraphUtils.generateOptions(question.question_type, colors, labels, height, '100%');

        newOptions.series = series;

        await GraphUtils.renderChartForResult(newOptions, charts, question, isExportPDF);
    };

    /**
     * Generate and render graph of the results of a matrix question
     * @param question  Question object which we want to display the results
     * @param charts     ApexChart to render at the end
     * @param isExportPDF Boolean to determine if we generate a graph for result or for PDF Export
     */
    private static generateMatrixChart = async (question: Question, charts: ApexChart[], isExportPDF: boolean) : Promise<void> => {
        let choices: QuestionChoice[] = question.choices.all;

        let series: any[] = [];
        let labels: string[] = question.children.all.map((child: Question) => child.title);

        for (let choice of choices) {
            let serie: any = {
                name: choice.value,
                data: []
            };

            // Fill serie data with nb responses of this choice for this child question
            for (let child of question.children.all) {
                let matchingChoice: QuestionChoice[] = child.choices.all.filter((c: QuestionChoice) => c.value === choice.value);
                serie.data.push(matchingChoice.length == 1 ? matchingChoice[0].nbResponses : 0);
            }

            series.push(serie); // Fill series
        }

        // Generate options with labels and colors
        let colors: string[] = ColorUtils.generateColorList(series.length);

        let newOptions: any = isExportPDF ?
            GraphUtils.generateOptions(question.question_type, colors, labels, null, null)
            :
            GraphUtils.generateOptions(question.question_type, colors, labels, '100%', '100%');

        newOptions.series = series;

        await GraphUtils.renderChartForResult(newOptions, charts, question, isExportPDF);
    };

    /**
     * Generate and render graph of the results of a cursor question
     * @param question    Question object which we want to display the results
     * @param charts      ApexChart to render at the end
     * @param responses   Array of responses which we want to display the results
     * @param isExportPDF Boolean to determine if we generate a graph for result or for PDF Export
     */
    private static generateCursorChart = async (question: Question, charts: ApexChart[], responses: Response[],
                                                isExportPDF: boolean) : Promise<void> => {
        // build array with all response
        let resp: number[] = responses.map((r: Response) => Number(r.answer)).sort((a: number, b: number) => a - b);

        // map to build object with response and number of each one
        const map: Map<number, number> = resp.reduce((acc: Map<number, number>, e: number) =>
            acc.set(e, (acc.get(e) || 0) + 1), new Map());

        let labels: number[] = Array.from(map.keys());
        let colors: string[] = ColorUtils.generateColorList(labels.length);

        let newPDFOptions: any = isExportPDF ?
            GraphUtils.generateOptions(question.question_type, colors, labels,null, null)
            :
            GraphUtils.generateOptions(question.question_type, colors, labels,'100%', '100%');

        newPDFOptions.series = [{ name: lang.translate('formulaire.number.responses'), data: Array.from(map.values()) }];

        await GraphUtils.renderChartForResult(newPDFOptions, charts, question, isExportPDF);
    }

    /**
     * Generate and render graph of the results of a ranking's question
     * @param question    Question object which we want to display the results
     * @param charts      ApexChart to render at the end
     * @param responses   Array of responses which we want to display the results
     * @param isExportPDF Boolean to determine if we generate a graph for result or for PDF Export
     */
    private static generateRankingChart = async (question: Question, charts: ApexChart[], responses: Response[],
                                                isExportPDF: boolean) : Promise<void> => {
        let choices: QuestionChoice[] = question.choices.all.filter((c: QuestionChoice) => c.nbResponses > 0);
        let series: any[] = [];

        // Initialize labels
        const labels: string[] = [];
        for (let i = 1; i <= choices.length; i++) {
            labels.push(i.toString());
        }

        // Build series
        const choiceMap: Map<string, any> = new Map();

        // Initialize data with zeros for each choice
        choices.forEach(choice => {
            const name: string = choice.value;
            const data: number[] = Array(choices.length).fill(0);
            choiceMap.set(name, {data});
        });

        // Iterate over responses and increment data for each choice position
        responses.forEach(response => {
            const choice: string = choiceMap.get(<string>response.answer);
            const position: number = response.choice_position - 1;
            const { data }: any  = choice;
            data[position]++;
        });

        // Iterate over choiceMap and push seriesOptions into series
        choiceMap.forEach(({ data }, name: string) => {
            const seriesOptions = {
                name,
                data
            };
            // Fill series
            series.push(seriesOptions);
        });

        let colors: string[] = ColorUtils.generateColorList(choices.length);
        let newOptions: any = isExportPDF ?
            GraphUtils.generateOptions(question.question_type, colors, labels, null, null)
            :
            GraphUtils.generateOptions(question.question_type, colors, labels, '100%', '100%');

        newOptions.series = series;
        await GraphUtils.renderChartForResult(newOptions, charts, question, isExportPDF);
    }

    /**
     * Generate and render graph of the results of a multiple answers question
     * @param question    Question object which we want to display the results
     * @param charts      ApexCharts to store and render at the end
     * @param distribs    Distrib's number for each question
     * @param isExportPDF Boolean to identify if it's a PDF export case
     */
    static generateMultipleAnswerChart = async (question: Question, charts: ApexChart[], distribs: number, isExportPDF: boolean) : Promise<void> => {
        if (question.question_type != Types.MULTIPLEANSWER) {
            return null;
        }

        let choices: QuestionChoice[] = question.choices.all;

        let series: number[] = [];
        let labels: string[] = [];
        let seriesPercent: number[] = [];

        for (let choice of choices) {
            series.push(choice.nbResponses); // Fill data
            // Fill labels
            !choice.id ?
                labels.push(idiom.translate('formulaire.response.empty')) :
                labels.push(choice.value.substring(0, 40) + (choice.value.length > 40 ? "..." : ""))
            seriesPercent.push((choice.nbResponses/distribs)*100)
        }

        let colors: string[] = ColorUtils.generateColorList(labels.length);
        let newOptions: any = GraphUtils.generateOptions(question.question_type, colors, labels, null, null, seriesPercent);
        newOptions.series = [{ data: series }];

        await GraphUtils.renderChartForResult(newOptions, charts, question, isExportPDF);
    }

    /**
     * Render graph with ApexChart based on given options
     * @param options     ApexChart options to render the graph
     * @param charts      ApexCharts to store and render at the end
     * @param question    Question object which we want to display the results
     * @param isExportPDF Boolean to identify if it's a PDF export case
     */
    static renderChartForResult = async (options: any, charts: any, question: Question, isExportPDF: boolean) : Promise<void> => {
        if (isExportPDF) {
            charts.push(new ApexCharts(document.querySelector(`#pdf-response-chart-${charts.length}`), options));
        } else {
            charts.push(new ApexCharts(document.querySelector(`#chart-${question.id}`), options));
        }
        await charts[charts.length - 1].render();
    };


    // Options generation for graphs

    /**
     *  Generate and return ApexCharts options according to the type of the question to display
     * @param type          Type of the question
     * @param colors        Colors to use for the graph
     * @param labels        Labels to display on the cart
     * @param height        Height of the chart to display (optional)
     * @param width         Width of the chart to display (optional)
     * @param seriesPercent Percentage to use for the graph (optional)
     */
    static generateOptions = (type: Types, colors: string[], labels: (string | number)[], height?: any, width?: any,
                              seriesPercent?: number[]) : any => {
        let options: any;
        if (type === Types.SINGLEANSWER || type === Types.SINGLEANSWERRADIO) {
            options = {
                chart: {
                    type: 'pie',
                    height: height ? height : 400,
                    width: width ? width : 600,
                    animations: {
                        enabled: false
                    }
                },
                colors: colors,
                labels: labels
            }
        }
        else if (type === Types.MULTIPLEANSWER) {
            options = {
                chart: {
                    type: 'bar',
                    height: height ? height : 400,
                    width: width ? width : 600,
                    animations: {
                        enabled: false
                    }
                },
                plotOptions: {
                    bar: {
                        borderRadius: 4,
                        horizontal: true,
                        distributed: true
                    }
                },
                dataLabels: {
                    enable: true,
                    formatter: function (val: number, opt: any): string {
                        return (val + " (" + seriesPercent[opt.dataPointIndex].toFixed(2)  + "%)")
                    },
                },
                colors: colors,
                xaxis: {
                    categories: labels,
                }
            }
        }
        else if (type === Types.MATRIX) {
            options = {
                chart: {
                    type: 'bar',
                    height: height ? height : 400,
                    width: width ? width : 600,
                    animations: {
                        enabled: false
                    }
                },
                colors: colors,
                dataLabels: {
                    enabled: false
                },
                xaxis: {
                    categories: labels,
                },
                yaxis: {
                    title: {
                        text: lang.translate('formulaire.number.responses'),
                        style: {
                            fontSize: '14px',
                            fontFamily: 'Helvetica, Arial, sans-serif',
                            fontWeight: 500
                        },
                    },
                },
                fill: {
                    opacity: 1
                }
            };
        }
        else if (type === Types.CURSOR) {
            options = {
                chart: {
                    type: 'area',
                    height: height ? height : 400,
                    width: width ? width : 600,
                    animations: {
                        enabled: false
                    },
                    toolbar: {
                        show: false
                    },
                    zoom: {
                        enabled: false
                    }
                },
                colors: colors,
                dataLabels: {
                    enabled: false
                },
                tooltip: {
                    y: {
                        title: {
                            formatter: function () {
                                return ''
                            }
                        },
                        formatter: function(value, { seriesIndex, w }) {
                            const seriesName = w.globals.seriesNames[seriesIndex];
                            const dataValue = value.toFixed(0);
                            return `${seriesName} : ${dataValue}`;
                        }
                    }
                },
                xaxis: {
                    categories: labels,
                    title: {
                        text: lang.translate('formulaire.selected.values'),
                        style: {
                            fontSize: '14px',
                            fontFamily: 'Helvetica, Arial, sans-serif',
                            fontWeight: 500
                        },
                        position: 'bottom'
                    }
                },
                yaxis: {
                    opposite: true,
                    labels: {
                        formatter: (value) => {
                            return Math.floor(value)
                        }
                    },
                    title: {
                        text: lang.translate('formulaire.nb.responses'),
                        style: {
                            fontSize: '14px',
                            fontFamily: 'Helvetica, Arial, sans-serif',
                            fontWeight: 500
                        }
                    }
                },
                fill: {
                    type: "gradient",
                    gradient: {
                        shadeIntensity: 1,
                        opacityFrom: 0.7,
                        opacityTo: 0.9,
                        stops: [0, 90, 100]
                    }
                }
            }
        }
        else if (type === Types.RANKING) {
            options = {
                chart: {
                    type: 'bar',
                    height: height ? height : 400,
                    width: width ? width : 600,
                    toolbar: {
                        show: false
                    },
                    animations: {
                        enabled: false
                    }
                },
                plotOptions: {
                    bar: {
                        horizontal: true
                    }
                },
                dataLabels: {
                    enabled: false
                },
                colors: colors,
                stroke: {
                    show: true,
                    width: 1,
                    colors: ['#fff']
                },
                tooltip: {
                    x: {
                        show: false
                    },
                    y: {
                        title: {
                            formatter: function () {
                                return ''
                            }
                        },
                        formatter: function(value, { seriesIndex, w }) {
                            const seriesName = w.globals.seriesNames[seriesIndex];
                            const dataValue = value.toFixed(0);
                            return `${seriesName} : ${dataValue}`;
                        }
                    }
                },
                xaxis: {
                    categories: labels,
                    labels: {
                        formatter: function (val) {
                            return val === Math.round(val) ? val.toFixed() : null;
                        }
                    },
                    title: {
                        text: lang.translate('formulaire.number.responses'),
                        style: {
                            fontSize: '14px',
                            fontFamily: 'Helvetica, Arial, sans-serif',
                            fontWeight: 500
                        }
                    }
                },
                yaxis: {
                    title: {
                        text: lang.translate('formulaire.position.selected'),
                        style: {
                            fontSize: '14px',
                            fontFamily: 'Helvetica, Arial, sans-serif',
                            fontWeight: 500
                        }
                    }
                }
            }
        }
        return options;
    }
}